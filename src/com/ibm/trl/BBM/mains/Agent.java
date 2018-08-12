package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.trl.NN.BottomCell;
import com.ibm.trl.NN.SigmoidCell;
import com.ibm.trl.NN.TopCell;

import ibm.ANACONDA.Core.MyMatrix;

public class Agent {
	int me;
	static int numField = 11;
	static int numC = 21;

	static int numPast = 20;

	LinkedList<MyMatrix> boardOld = new LinkedList<MyMatrix>();
	LinkedList<MyMatrix> powerOld = new LinkedList<MyMatrix>();
	LinkedList<MyMatrix> lifeOld = new LinkedList<MyMatrix>();
	LinkedList<Node[][]> bombMapOld = new LinkedList<Node[][]>();

	static class Ability {
		boolean isAlive = true;
		int numMaxBomb = 1;
		int strength = 2;
		boolean kick = false;
		int numBombHold = 1;
		boolean justBombed = false;

		public Ability() {
		}

		public Ability(Ability a) {
			this.isAlive = a.isAlive;
			this.numMaxBomb = a.numMaxBomb;
			this.strength = a.strength;
			this.kick = a.kick;
			this.numBombHold = a.numBombHold;
			this.justBombed = a.justBombed;
		}
	}

	Ability[] abs = new Ability[4];

	static int[] indexList = new int[numC * numC * 36 + 1];
	static BT bt;
	static int frameCounter = 0;

	static {
		// セルの予測器の準備
		try {
			int numd = numC * numC * 36;
			TopCell top = new TopCell(numd, 1);
			BottomCell bottom = new BottomCell(numd);
			SigmoidCell sig2 = new SigmoidCell(numd, 100);
			SigmoidCell sig = new SigmoidCell(100, 1);

			top.AddInput(sig);
			sig.AddInput(sig2);
			sig2.AddInput(bottom);

			bt = new BT(0, numd, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	Agent(int me) throws Exception {
		this.me = me;
		for (int i = 0; i < 4; i++) {
			abs[i] = new Ability();
		}
	}

	public void init_agent() {
		System.out.println("init_agent");
	}

	public void episode_end(int reward) {
		System.out.println("episode_end, reward = " + reward);
	}

	class Node {
		int owner;
		int x;
		int y;
		int type;
		int lifeBomb;
		int power;
		int moveDirection;
		int lifeFlameCenter;
		boolean selected = false;

		public Node(int owner, int x, int y, int type, int lifeB, int power, int moveDirection, int lifeFC) {
			this.owner = owner;
			this.x = x;
			this.y = y;
			this.type = type;
			this.lifeBomb = lifeB;
			this.power = power;
			this.moveDirection = moveDirection;
			this.lifeFlameCenter = lifeFC;
		}

		public Node(Node n) {
			this.owner = n.owner;
			this.x = n.x;
			this.y = n.y;
			this.type = n.type;
			this.lifeBomb = n.lifeBomb;
			this.power = n.power;
			this.moveDirection = n.moveDirection;
			this.lifeFlameCenter = n.lifeFlameCenter;
			this.selected = n.selected;
		}

		public String toString() {
			return String.format("● (%2d,%2d), type=%2d, lifeB=%2d, power=%2d, moveDirection=%2d, lifeFC=%2d, selected=%7b", x, y, type, lifeBomb, power, moveDirection, lifeFlameCenter, selected);
		}
	}

	class NodeMatchBest {
		List<Node> nodePairPre = new ArrayList<Node>();
		List<Node> nodePairNow = new ArrayList<Node>();
		double numBombMatched;
		double numFlameDiff;
		double moveIncorrect;

		public NodeMatchBest() {
			numBombMatched = -Double.MAX_VALUE;
			numFlameDiff = Double.MAX_VALUE;
			moveIncorrect = Double.MAX_VALUE;
		}
	}

	private void FindMatchingRecursive(MyMatrix board, List<Node> nodesPre, List<Node> nodesNow, List<Node> nodePairPre, List<Node> nodePairNow, NodeMatchBest best) throws Exception {

		boolean isAllSelected = nodesPre.size() == nodePairPre.size();
		if (isAllSelected) {
			// Preが全て選択されていたら、boardと整合性が取れているか調べる。

			if (false) {
				System.out.println("nodePre : " + nodesPre);
				System.out.println("nodeNow : " + nodesNow);
				System.out.println("nodePairPre : " + nodePairPre);
				System.out.println("nodePairNow : " + nodePairNow);
			}

			// NowのBombが全部選択されているか調べる。
			int numBomb = 0;
			int numBombMatched = 0;
			for (Node nodeNow : nodesNow) {
				if (nodeNow.type != Constant.Bomb) continue;
				numBomb++;
				if (nodeNow.selected) {
					numBombMatched++;
				}
			}

			// FlameCenterを爆発させて、Boardと食い違いがあるかどうか調べる。
			MyMatrix myFlame = new MyMatrix(numField, numField);
			// MyMatrix flameCenter = new MyMatrix(numField, numField);
			{
				List<Node> flames = new ArrayList<Node>();
				for (Node nodeNow : nodePairNow) {
					if (nodeNow.type != Constant.Flames) continue;
					flames.add(nodeNow);
				}

				Node[][] bombMapPre = bombMapOld.get(0);
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						Node node = bombMapPre[x][y];
						if (node == null) continue;
						if (node.type != Constant.Flames) continue;
						Node node2 = new Node(node);
						node2.lifeFlameCenter--;
						if (node2.lifeFlameCenter == 0) continue;
						flames.add(node2);
					}
				}

				MyMatrix[] BDs = new MyMatrix[4];
				BDs[0] = board;
				BDs[1] = boardOld.get(0);
				BDs[2] = boardOld.get(1);
				BDs[3] = boardOld.get(2);

				for (Node flame : flames) {
					int x = flame.x;
					int y = flame.y;
					int power = flame.power;
					int life = flame.lifeFlameCenter;
					BBMUtility.PrintFlame(BDs[3 - life + 1], myFlame, x, y, power, 1);
				}
			}

			int numFlameSame = 0;
			int numFlameDiff = 0;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (myFlame.data[x][y] == 1) {
						if (board.data[x][y] == Constant.Flames) {
							numFlameSame++;
						} else {
							numFlameDiff++;
						}
					} else {
						if (board.data[x][y] == Constant.Flames) {
							numFlameDiff++;
						} else {
							numFlameSame++;
						}
					}
				}
			}

			// Bomb→BomtとかBomb→Flameの移動状態が食い違ってないかを調べる。食い違いが最も少ないやつが選ばれるようにする。
			double moveIncorrect = 0;
			{
				int numPair = nodePairNow.size();
				for (int i = 0; i < numPair; i++) {
					Node nodePre = nodePairPre.get(i);
					Node nodeNow = nodePairNow.get(i);
					int dir = 5;
					if (nodePre.x == nodeNow.x && nodePre.y == nodeNow.y) {
						dir = 5;
					} else if (nodePre.x - 1 == nodeNow.x) {
						dir = 1;
					} else if (nodePre.x + 1 == nodeNow.x) {
						dir = 2;
					} else if (nodePre.y - 1 == nodeNow.y) {
						dir = 3;
					} else if (nodePre.y + 1 == nodeNow.y) {
						dir = 4;
					}
					nodeNow.moveDirection = dir;
				}

				MyMatrix[] BDs = new MyMatrix[4];
				BDs[0] = board;
				BDs[1] = boardOld.get(0);
				BDs[2] = boardOld.get(1);
				BDs[3] = boardOld.get(2);

				for (int i = 0; i < numPair; i++) {
					Node nodePre = nodePairPre.get(i);
					Node nodeNow = nodePairNow.get(i);

					int dirPre = nodePre.moveDirection;
					int dirNow = nodeNow.moveDirection;

					if (dirPre == 0 || dirNow == 0) throw new Exception("error");
					if (dirPre == -1 || dirNow == -1) throw new Exception("error");

					double cost = 0;

					if (dirPre == dirNow) {
						cost = 0;
					} else if (dirNow == 5) {
						// Bombが動いていたのに止まった場合

						int xCheck = nodePre.x;
						int yCheck = nodePre.y;
						if (dirPre == 1) {
							xCheck--;
						} else if (dirPre == 2) {
							xCheck++;
						} else if (dirPre == 3) {
							yCheck--;
						} else if (dirPre == 4) {
							yCheck++;
						}

						if (xCheck < 0 || xCheck >= numField || yCheck < 0 || yCheck >= numField) {
							cost = 0;
						} else {
							int typeCheckNow = (int) BDs[0].data[xCheck][yCheck];
							int typeCheckPre = (int) BDs[1].data[xCheck][yCheck];

							if (typeCheckPre == Constant.Rigid) {
								cost = 0;
							} else if (typeCheckPre == Constant.Wood) {
								cost = 0;
							} else if (Constant.isAgent(typeCheckNow)) {
								cost = 0;

								// 真っ向からKickableなAgentが進んできた場合は、停止したように見えるけど、逆サイドに動く。フラグを修正しておく。
								int xCheck2 = xCheck;
								int yCheck2 = yCheck;
								int dirNew = 5;
								if (dirPre == 1) {
									xCheck2--;
									dirNow = 2;
								} else if (dirPre == 2) {
									xCheck2++;
									dirNew = 1;
								} else if (dirPre == 3) {
									yCheck2--;
									dirNew = 4;
								} else if (dirPre == 4) {
									yCheck2++;
									dirNew = 3;
								}
								if (xCheck2 >= 0 && xCheck2 < numField && yCheck2 >= 0 && yCheck2 < numField) {
									int typeCheck2Pre = (int) BDs[1].data[xCheck2][yCheck2];
									if (typeCheck2Pre == typeCheckNow) {
										if (abs[typeCheckNow - 10].kick == true) {
											nodeNow.moveDirection = dirNew;
										}
									}
								}
							} else {
								// Bombが止まる理由がどこにもないので、コストを足す。
								cost = 1;
							}
						}

					} else {
						// Bombが止まっていたのに、動き出した場合

						if (nodeNow.type == Constant.Flames) {
							// 移動が始まった直後にFlameになった場合。現ステップのAgentはFlameの下で死亡。前ステップは確認できるので、そこだけチェックする。
							int xCheck = nodePre.x;
							int yCheck = nodePre.y;
							if (dirNow == 1) {
								xCheck++;
							} else if (dirNow == 2) {
								xCheck--;
							} else if (dirNow == 3) {
								yCheck++;
							} else if (dirNow == 4) {
								yCheck--;
							}

							if (xCheck >= 0 && xCheck < numField && yCheck >= 0 && yCheck < numField) {
								int typeCheckPre = (int) BDs[1].data[xCheck][yCheck];
								if (Constant.isAgent(typeCheckPre) && abs[typeCheckPre - 10].kick && abs[typeCheckPre - 10].isAlive == false) {
									cost = 0;
								} else {
									cost = 1;
								}
							} else {
								cost = 1;
							}
						} else {
							// Bombのまま移動が始まった場合。
							int xCheck = nodePre.x;
							int yCheck = nodePre.y;
							if (dirNow == 1) {
								xCheck++;
							} else if (dirNow == 2) {
								xCheck--;
							} else if (dirNow == 3) {
								yCheck++;
							} else if (dirNow == 4) {
								yCheck--;
							}

							if (xCheck >= 0 && xCheck < numField && yCheck >= 0 && yCheck < numField) {
								int typeCheckPre = (int) BDs[1].data[xCheck][yCheck];
								int typeCheckNow = (int) BDs[0].data[nodePre.x][nodePre.y];
								if (Constant.isAgent(typeCheckPre) && typeCheckNow == typeCheckPre && abs[typeCheckPre - 10].kick) {
									cost = 0;
								} else {
									cost = 1;
								}
							} else {
								cost = 1;
							}
						}
					}

					moveIncorrect += cost;
				}
			}

			boolean isBest = false;
			if (numBombMatched > best.numBombMatched) {
				isBest = true;
			} else if (numBombMatched == best.numBombMatched) {
				if (numFlameDiff < best.numFlameDiff) {
					isBest = true;
				} else if (numFlameDiff == best.numFlameDiff) {
					if (moveIncorrect < best.moveIncorrect) {
						isBest = true;
					}
				}
			}

			if (isBest) {
				best.nodePairPre.clear();
				best.nodePairNow.clear();
				best.nodePairPre.addAll(nodePairPre);
				best.nodePairNow.addAll(nodePairNow);
				best.numBombMatched = numBombMatched;
				best.numFlameDiff = numFlameDiff;
				best.moveIncorrect = moveIncorrect;

				if (false) {
					System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
					System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
					String line = String.format("bomb numbSelected/numb = %d/%d, flame same=%d, diff=%7d, moveIncorrect=%f", numBombMatched, numBomb, numFlameSame, numFlameDiff, moveIncorrect);
					System.out.println(line);
					System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
					System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
					System.out.println(myFlame.toString());
					System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
					System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
				}
			}

		} else {
			Node nodePre = null;
			for (Node nodePreTemp : nodesPre) {
				if (nodePreTemp.selected == false) {
					nodePre = nodePreTemp;
					break;
				}
			}

			for (Node nodeNow : nodesNow) {
				if (nodeNow.selected) continue;

				// マンハッタン距離1以下じゃないとペア不成立。
				int dis = Math.abs(nodePre.x - nodeNow.x) + Math.abs(nodePre.y - nodeNow.y);
				if (dis > 1) continue;

				// BombからBombの遷移の場合、ライフが1減ってるものじゃないとペア不成立。
				if (nodeNow.type == Constant.Bomb) {
					if (nodePre.lifeBomb - 1 != nodeNow.lifeBomb) {
						continue;
					}
				}

				// Powerが違っていたら、ペア不成立
				if (nodePre.power != nodeNow.power) {
					continue;
				}

				nodePre.selected = true;
				nodeNow.selected = true;
				nodePairPre.add(nodePre);
				nodePairNow.add(nodeNow);
				FindMatchingRecursive(board, nodesPre, nodesNow, nodePairPre, nodePairNow, best);
				nodePre.selected = false;
				nodeNow.selected = false;
				nodePairPre.remove(nodePairPre.size() - 1);
				nodePairNow.remove(nodePairNow.size() - 1);
			}
		}
	}

	public int act(int xMe, int yMe, int ammo, int blast_strength, boolean can_kick, MyMatrix board, MyMatrix bomb_blast_strength, MyMatrix bomb_life, MyMatrix alive, MyMatrix enemies)
			throws Exception {
		if (true) {
//			Thread.sleep(1000);

			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("board picture");
			BBMUtility.printBoard2(board, bomb_life, bomb_blast_strength);
			// System.out.println("board");
			// System.out.println(board);
			// System.out.println("bomb_blast_strength");
			// System.out.println(bomb_blast_strength);
			// System.out.println("bomb_life");
			// System.out.println(bomb_life);
		}

		if (boardOld.size() == 0) {
			for (int i = 0; i < numPast; i++) {
				boardOld.add(board);
				powerOld.add(bomb_blast_strength);
				lifeOld.add(bomb_life);
				bombMapOld.add(new Node[numField][numField]);
			}
		}

		// 生きてるかどうかのフラグを更新する。
		{
			for (int i = 0; i < 4; i++) {
				abs[i].isAlive = false;
			}
			int numAlive = alive.numt;
			for (int i = 0; i < numAlive; i++) {
				int index = (int) (alive.data[i][0] - 10);
				abs[index].isAlive = true;
			}
		}

		// 相手がアイテムをとったかどうかを調べる。
		{
			MyMatrix board_pre = boardOld.get(0);
			for (int i = 0; i < numField; i++) {
				for (int j = 0; j < numField; j++) {
					if (board_pre.data[i][j] == 6 && board.data[i][j] >= 10 && board.data[i][j] <= 13) {
						int id = (int) (board.data[i][j] - 10);
						abs[id].numMaxBomb++;
						abs[id].numBombHold++;
					} else if (board_pre.data[i][j] == 7 && board.data[i][j] >= 10 && board.data[i][j] <= 13) {
						int id = (int) (board.data[i][j] - 10);
						abs[id].strength++;
					} else if (board_pre.data[i][j] == 8 && board.data[i][j] >= 10 && board.data[i][j] <= 13) {
						int id = (int) (board.data[i][j] - 10);
						abs[id].kick = true;
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 爆弾の動きをトラッキングする。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		List<Node> nodePairPre;
		List<Node> nodePairNow;
		Node[][] bombMap = new Node[numField][numField];
		{
			MyMatrix board_now = board;
			MyMatrix board_pre = boardOld.get(0);
			MyMatrix life_now = bomb_life;
			MyMatrix life_pre = lifeOld.get(0);
			MyMatrix power_now = bomb_blast_strength;
			MyMatrix power_pre = powerOld.get(0);
			Node[][] bombMap_pre = bombMapOld.get(0);

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// 前ステップのマッチング候補を求める。マップ上の全てのBombがマッチング対象になる。
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			List<Node> nodesPre = new ArrayList<Node>();
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					Node node = bombMap_pre[x][y];
					if (node == null) continue;
					if (node.type != Constant.Bomb) continue;
					Node node2 = new Node(node);
					int power = (int) power_pre.data[x][y];
					int life = (int) life_pre.data[x][y];
					if (node2.power != power) throw new Exception("error");
					if (node2.lifeBomb != life) throw new Exception("error");
					nodesPre.add(node2);
				}
			}

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// 現ステップのマッチング候補を求める。
			// TODO 現行ロジックでマッチング候補を取りこぼす条件として、AgentがBombを置いたと同時にFlameに巻き込まれると、いきなりFlameが発生（多分）する。この状況を取りこぼす。
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// Lifeが8以下のBombを集める。
			// Lifeが9のBombは、新しく設置されたものなので、マッチング不能である。
			List<Node> nodesNow = new ArrayList<Node>();
			List<Node> nodesNowNew = new ArrayList<Node>();
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (life_now.data[x][y] != 0) {
						int power = (int) power_now.data[x][y];
						int life = (int) life_now.data[x][y];
						if (life == 9) {
							int typeNow = (int) board_now.data[x][y];
							if (Constant.isAgent(typeNow) == false) throw new Exception("error");
							Node node = new Node(typeNow, x, y, Constant.Bomb, life, power, 5, 3);
							nodesNowNew.add(node);
						} else {
							Node node = new Node(-1, x, y, Constant.Bomb, life, power, -1, 3);
							nodesNow.add(node);
						}
					}
				}
			}

			// boardと矛盾しない新規FlameCenter候補（前ステップのBombが現ステップでFlameになった可能性があるもの）を集める。
			for (int xPre = 0; xPre < numField; xPre++) {
				for (int yPre = 0; yPre < numField; yPre++) {
					if (life_pre.data[xPre][yPre] != 0) {
						int power = (int) power_pre.data[xPre][yPre];
						int life = (int) life_pre.data[xPre][yPre] - 1;
						for (int dx = -1; dx <= 1; dx++) {
							for (int dy = -1; dy <= 1; dy++) {
								if (dx * dy != 0) continue;
								int xNow = xPre + dx;
								int yNow = yPre + dy;
								if (xNow < 0 || xNow >= numField) continue;
								if (yNow < 0 || yNow >= numField) continue;

								int typeNowCenter = (int) board_now.data[xNow][yNow];
								if (typeNowCenter != Constant.Flames) continue;

								boolean isAllFlame = true;

								for (int dir = 0; dir < 4; dir++) {
									for (int w = 1; w < power; w++) {
										int xNowSeek = xNow;
										int yNowSeek = yNow;
										if (dir == 0) {
											xNowSeek = xNow - w;
										} else if (dir == 1) {
											xNowSeek = xNow + w;
										} else if (dir == 2) {
											yNowSeek = yNow - w;
										} else if (dir == 3) {
											yNowSeek = yNow + w;
										}
										if (xNowSeek < 0 || xNowSeek >= numField) break;
										if (yNowSeek < 0 || yNowSeek >= numField) break;
										int typeNowSeek = (int) board_now.data[xNowSeek][yNowSeek];

										// RidgeだったらFlameが止まるので終了
										if (typeNowSeek == Constant.Rigid) break;

										// Flameじゃなかったら、矛盾してるので、注目セルはFlameCenterではない。
										if (typeNowSeek != Constant.Flames) {
											isAllFlame = false;
											break;
										}

										// Flameの前身がWoodだったら、注目セルは炎の終点になっているのでシーク終了。
										int typePreSeek = (int) board_pre.data[xNowSeek][yNowSeek];
										if (typePreSeek == Constant.Wood) break;
									}
									if (isAllFlame == false) break;
								}
								if (isAllFlame == false) continue;

								Node node = new Node(-1, xNow, yNow, Constant.Flames, life, power, -1, 3);
								nodesNow.add(node);
							}
						}
					}
				}
			}

			System.out.println("#########################################");
			System.out.println("nodePre : " + nodesPre);
			System.out.println("nodeNow : " + nodesNow);
			System.out.println("#########################################");
			System.out.println("#########################################");
			NodeMatchBest best = new NodeMatchBest();
			List<Node> nodePairPre_local = new ArrayList<Node>();
			List<Node> nodePairNow_local = new ArrayList<Node>();
			FindMatchingRecursive(board_now, nodesPre, nodesNow, nodePairPre_local, nodePairNow_local, best);

			nodePairPre = best.nodePairPre;
			nodePairNow = best.nodePairNow;

			// 方向を設定する。
			int numPair = nodePairPre.size();
			for (int i = 0; i < numPair; i++) {
				Node nodePre = nodePairPre.get(i);
				Node nodeNow = nodePairNow.get(i);
				int dir = 5;
				if (nodePre.x == nodeNow.x && nodePre.y == nodeNow.y) {
					dir = 5;
				} else if (nodePre.x - 1 == nodeNow.x) {
					dir = 1;
				} else if (nodePre.x + 1 == nodeNow.x) {
					dir = 2;
				} else if (nodePre.y - 1 == nodeNow.y) {
					dir = 3;
				} else if (nodePre.y + 1 == nodeNow.y) {
					dir = 4;
				}
				nodeNow.moveDirection = dir;
				nodeNow.owner = nodePre.owner;
			}

			if (best.numFlameDiff > 0 || best.moveIncorrect > 0) {
				System.out.println("おかしい??");
			}

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// 爆弾オブジェクトをアップデートする。
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			Node[][] bombMapPre = bombMapOld.get(0);

			// 新規Bombを追加する。今回のフレームで検出した新規Bombを追加する。
			for (Node node : nodesNowNew) {
				bombMap[node.x][node.y] = node;
			}

			// 既存Flameを追加する。前ステップのFlameを追加する。Lifeを減らして、moveDirectionを5（停止）にする。
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					Node node = bombMapPre[x][y];
					if (node == null) continue;
					if (node.type == Constant.Flames && node.lifeFlameCenter > 1) {
						Node node2 = new Node(node);
						node2.lifeFlameCenter--;
						node2.moveDirection = 5;
						bombMap[x][y] = node2;
					}
				}
			}

			// 既存Bombを追加する。
			for (Node node : nodePairNow) {
				bombMap[node.x][node.y] = node;
				if (node.type == Constant.Flames) {
					node.lifeFlameCenter = 3;
					node.lifeBomb = 0;
				}
			}

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// 爆弾の動きをみて、Agentの保有爆弾数を調整する。
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			{
				for (Ability a : abs) {
					a.justBombed = false;
				}
				for (Node node : nodesNowNew) {
					int agent = node.owner - 10;
					abs[agent].numBombHold--;
					abs[agent].justBombed = true;
				}

				for (Node node : nodePairNow) {
					if (node.type == Constant.Flames) {
						int agent = node.owner - 10;
						abs[agent].numBombHold++;
					}
				}
			}

			// TODO bombMapを出力してみる。
			BBMUtility.printBombMap(board, bombMap);

			// TODO 出力してみる。
			for (int i = 0; i < 4; i++) {
				Ability a = abs[i];
				String line = String.format("%d, %d/%d", i + 1, a.numBombHold, a.numMaxBomb);
				System.out.println(line);
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// うまく分岐させながら、BombやAgentが存在しうる状態を列挙して、行動選択に活用する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// TODO
		if (true) {
			FutureTrack ft = new FutureTrack(numField);
			ft.Compute(board, bombMap, abs);
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// とりあえず予測モデルを作ってみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (true)

		{
			int center = numC / 2;
			int step = 1;

			long timeStart = System.currentTimeMillis();

			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int count = 0;

					//////////////////////////////////////////////////////////////
					// 説明変数を作る。
					//////////////////////////////////////////////////////////////

					// Boardを展開する。
					// 14枚
					MyMatrix boardThen = boardOld.get(step - 1);
					for (int i = 0; i < numC; i++) {
						for (int j = 0; j < numC; j++) {
							int x2 = x + i - center;
							int y2 = y + j - center;
							int panel;
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
								panel = Constant.Rigid;
							} else {
								panel = (int) boardThen.data[x2][y2];
							}

							if (panel >= 10 && panel <= 13) {
								if (panel == me) {
									panel = Constant.Agent0;
								} else {
									panel = Constant.Agent1;
								}
							}

							int index = panel * numC * numC + j * numC + i;
							indexList[count] = index;
							count++;
							// X.data[index][0] = 1;
						}
					}

					// BombのStrengthとLineを展開する。

					// bomb_blast_strength
					// 2-11の10枚
					MyMatrix powerThen = powerOld.get(step - 1);
					for (int i = 0; i < numC; i++) {
						for (int j = 0; j < numC; j++) {
							int x2 = x + i - center;
							int y2 = y + j - center;
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int value = (int) powerThen.data[x2][y2];
							if (value == 0) continue;
							if (value == 1) throw new Exception("error");
							if (value > 11) value = 11;
							for (int si = 2; si <= value; si++) {
								int index = (si - 2 + 14) * numC * numC + j * numC + i;
								indexList[count] = index;
								count++;
								// X.data[index][0] = 1;
							}
						}
					}

					// bomb_life
					// 1-9の9枚
					MyMatrix lifeThen = lifeOld.get(step - 1);
					for (int i = 0; i < numC; i++) {
						for (int j = 0; j < numC; j++) {
							int x2 = x + i - center;
							int y2 = y + j - center;
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int value = (int) lifeThen.data[x2][y2];
							if (value == 0) continue;
							int index = (value - 1 + 24) * numC * numC + j * numC + i;
							indexList[count] = index;
							count++;
							// X.data[index][0] = 1;
						}
					}

					// Bompの過去1ステップ分を展開する。
					// 1枚
					MyMatrix boardThenPre = boardOld.get(step - 1 + 1);
					for (int i = 0; i < numC; i++) {
						for (int j = 0; j < numC; j++) {
							int x2 = x + i - center;
							int y2 = y + j - center;
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int value = (int) boardThenPre.data[x2][y2];
							if (value != Constant.Bomb) continue;
							int index = (33) * numC * numC + j * numC + i;
							indexList[count] = index;
							count++;
							// X.data[index][0] = 1;
						}
					}

					// Flameの過去2ステップ分を展開する。
					// 2枚
					for (int k = 0; k < 2; k++) {
						MyMatrix flameThenPre = boardOld.get(step - 1 + k + 1);
						for (int i = 0; i < numC; i++) {
							for (int j = 0; j < numC; j++) {
								int x2 = x + i - center;
								int y2 = y + j - center;
								if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
								int value = (int) flameThenPre.data[x2][y2];
								if (value != Constant.Flames) continue;
								int index = (k + 34) * numC * numC + j * numC + i;
								indexList[count] = index;
								count++;
								// X.data[index][0] = 1;
							}
						}
					}

					indexList[count] = -1;
					count++;

					//////////////////////////////////////////////////////////////
					// 目的変数を作る。
					//////////////////////////////////////////////////////////////
					// とりあえず、敵の位置を予測してみる。
					// if (boardThen.data[x][y] != Constant.Wood) {
					// continue;
					// }

					int Y = 0;
					if (board.data[x][y] == Constant.Bomb) {
						// if (board.data[x][y] == Constant.Agent3) {
						Y = 1;
					}

					//////////////////////////////////////////////////////////////
					// 学習する。
					//////////////////////////////////////////////////////////////
					synchronized (bt) {
						bt.put(indexList, Y);
					}
				}
			}

			long timeEnd = System.currentTimeMillis();
			double timeSpan = (timeEnd - timeStart) * 0.001;
			// System.out.println("time, " + timeSpan);

			synchronized (bt) {
				if (frameCounter % 100 == 0) {
					Set<Integer> selected = new TreeSet<Integer>();
					bt.dig(selected);
					System.out.println(bt.print());
				}
				frameCounter++;
			}
		}

		// 古いデータとして保存する。
		{
			boardOld.addFirst(board);
			powerOld.addFirst(bomb_blast_strength);
			lifeOld.addFirst(bomb_life);
			bombMapOld.addFirst(bombMap);

			boardOld.removeLast();
			powerOld.removeLast();
			lifeOld.removeLast();
			bombMapOld.removeLast();
		}

		return 1;
	}

	double Gtotal = 0;
	double Gcount = 0;
}
