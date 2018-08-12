package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.Node;

import ibm.ANACONDA.Core.MatrixUtility;
import ibm.ANACONDA.Core.MyMatrix;

public class FutureTrack {

	int numField;

	public FutureTrack(int numField) {
		this.numField = numField;
	}

	class StatusHolder {
		private byte[][][] AG = new byte[4][numField][numField];
		private byte[][][][] BB = new byte[10][6][numField][numField];
		private byte[][][] FC = new byte[4][numField][numField];
		private byte[][] WB = new byte[numField][numField];

		public void setAgent(int agent, int x, int y) {
			AG[agent - 10][x][y] = 1;
		}

		public boolean isAgentExist(int agent, int x, int y) {
			if (AG[agent - 10][x][y] == 1) return true;
			else return false;
		}

		public void setBomb(int life, int moveDirection, int x, int y, int power) {
			BB[life][moveDirection][x][y] = (byte) Math.max(BB[life][moveDirection][x][y], power);
		}

		public boolean isBombExist(int life, int moveDirection, int x, int y) {
			int power = getBombPower(life, moveDirection, x, y);
			if (power == 0) return false;
			else return true;
		}

		public int getBombPower(int life, int moveDirection, int x, int y) {
			return BB[life][moveDirection][x][y];
		}

		public void setFlameCenter(int life, int x, int y, int power) {
			FC[life][x][y] = (byte) Math.max(FC[life][x][y], power);
		}

		public boolean isFlameCenterExist(int life, int x, int y) {
			int power = getFlameCenterPower(life, x, y);
			if (power == 0) return false;
			else return true;
		}

		public int getFlameCenterPower(int life, int x, int y) {
			return FC[life][x][y];
		}

		public void setWoodBrake(int x, int y) {
			WB[x][y] = 1;
		}

		public boolean isWoodBrake(int x, int y) {
			if (WB[x][y] == 1) return true;
			else return false;
		}

		@Override
		public String toString() {
			String output = "";

			////////////////////////////////////////////////////////////////////////////////
			//
			// Agentの位置を出力する。
			//
			////////////////////////////////////////////////////////////////////////////////
			output += "========================================\n";
			output += "========================================\n";
			output += "========================================\n";
			output += "Agent\n";
			for (int x = 0; x < numField; x++) {
				String line1 = "";
				String line2 = "";
				String line3 = "";
				for (int y = 0; y < numField; y++) {
					String print1 = "";
					String print2 = "";
					String print3 = "";

					String[] as = new String[4];
					for (int i = 0; i < 4; i++) {
						if (this.isAgentExist(i + 10, x, y)) {
							as[i] = "●";
						} else {
							as[i] = "○";
						}
					}

					print1 = as[0] + "ー" + as[1];
					print2 = "｜＋｜";
					print3 = as[2] + "ー" + as[3];

					line1 += print1;
					line2 += print2;
					line3 += print3;
				}

				output += line1 + "\n";
				output += line2 + "\n";
				output += line3 + "\n";
			}

			////////////////////////////////////////////////////////////////////////////////
			//
			// 爆弾の位置を出力する。
			//
			////////////////////////////////////////////////////////////////////////////////

			for (int life = 9; life >= 1; life--) {
				boolean exist = false;
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
							if (this.isBombExist(life, moveDirection, x, y)) {
								exist = true;
							}
						}
					}
				}
				if (exist == false) continue;

				output += "========================================\n";
				output += "========================================\n";
				output += "========================================\n";
				output += "Bomb life=" + life + "\n";

				for (int x = 0; x < numField; x++) {
					String line1 = "";
					String line2 = "";
					String line3 = "";
					for (int y = 0; y < numField; y++) {
						String print1 = "";
						String print2 = "";
						String print3 = "";

						String[] as = new String[6];
						for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
							if (this.isBombExist(life, moveDirection, x, y)) {
								int power = this.getBombPower(life, moveDirection, x, y);
								as[moveDirection] = String.format("%d", power);
							} else {
								as[moveDirection] = " ";
							}
						}

						print1 = "// " + as[1] + " \\";
						print2 = "||" + as[3] + as[5] + as[4] + "|";
						print3 = "\\\\_" + as[2] + "_/";

						line1 += print1;
						line2 += print2;
						line3 += print3;
					}

					output += line1 + "\n";
					output += line2 + "\n";
					output += line3 + "\n";
				}
			}

			////////////////////////////////////////////////////////////////////////////////
			//
			// FlameCenterの位置を出力する。
			//
			////////////////////////////////////////////////////////////////////////////////
			output += "========================================\n";
			output += "========================================\n";
			output += "========================================\n";
			output += "FlameCenter\n";
			for (int x = 0; x < numField; x++) {
				String line1 = "";
				String line2 = "";
				String line3 = "";
				for (int y = 0; y < numField; y++) {
					String print1 = "";
					String print2 = "";
					String print3 = "";

					String[] as = new String[4];
					for (int life = 3; life >= 1; life--) {
						if (this.isFlameCenterExist(life, x, y)) {
							int power = this.getFlameCenterPower(life, x, y);
							as[life] = String.format("%d", power);
						} else {
							as[life] = " ";
						}
					}

					print1 = "/3:" + as[3] + " \\";
					print2 = "|2:" + as[2] + " |";
					print3 = "\\1:" + as[1] + " /";

					line1 += print1;
					line2 += print2;
					line3 += print3;
				}

				output += line1 + "\n";
				output += line2 + "\n";
				output += line3 + "\n";
			}

			////////////////////////////////////////////////////////////////////////////////
			//
			// FlameCenterの位置を出力する。
			//
			////////////////////////////////////////////////////////////////////////////////
			output += "========================================\n";
			output += "========================================\n";
			output += "========================================\n";
			output += "WoodBrake\n";
			for (int x = 0; x < numField; x++) {
				String line1 = "";
				String line2 = "";
				String line3 = "";
				for (int y = 0; y < numField; y++) {
					String print1 = "";
					String print2 = "";
					String print3 = "";

					if (this.isWoodBrake(x, y)) {
						print1 = "■■■";
						print2 = "■■■";
						print3 = "■■■";
					} else {
						print1 = "□□□";
						print2 = "□□□";
						print3 = "□□□";
					}
					line1 += print1;
					line2 += print2;
					line3 += print3;
				}

				output += line1 + "\n";
				output += line2 + "\n";
				output += line3 + "\n";
			}

			return output;
		}
	}

	static long time1, time2, time3, time4, time5, time6;

	private void RRR(int depth, MyMatrix board, Node[][] bombMap, int[][] actions, Ability absNow[], StatusHolder shNow, List<StatusHolder> sequence) throws Exception {

		// TODO 炎確定のところは、死亡確定OR移動できないという取扱をする。
		long timeStart;
		long timeEnd;

		if (depth >= 5) return;

		StatusHolder shNext = new StatusHolder();

		Ability[] absNext = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNext[i] = new Ability(absNow[i]);
		}

		// 各エージェントのactionsが爆弾使用だけで構成されていて、初秋爆弾数0だったら実現できないのでリターン。1以上持っていたら、所有爆弾数を1だけ減じる。
		for (int i = 0; i < 4; i++) {
			boolean actionIsBombOnly = true;
			for (int action : actions[i]) {
				if (action != 5) {
					actionIsBombOnly = false;
					break;
				}
			}

			if (actionIsBombOnly) {
				absNext[i].numBombHold--;
				if (absNext[i].numBombHold < 0) return;
				if (absNow[i].justBombed) return;
				absNext[i].justBombed = true;
			} else {
				absNext[i].justBombed = false;
			}
		}

		// 現在のステップから、次のステップに移動可能なところにフラグを立てていく。

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// エージェントを動かす。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		for (int agentIndex = 0; agentIndex < 4; agentIndex++) {
			Ability abNow = absNow[agentIndex];
			int[] actionss = actions[agentIndex];
			boolean doSomething = false;

			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int agentID = agentIndex + 10;
					if (shNow.isAgentExist(agentID, x, y) == false) continue;

					for (int action : actionss) {
						if (action == 0) {
							// 何もしない。その場にいとどまるケース
							shNext.setAgent(agentID, x, y);
							doSomething = true;
						} else if (action >= 1 && action <= 4) {
							int dir = action;

							int x2 = x;
							int y2 = y;
							if (dir == 1) {
								x2 = x - 1;
							} else if (dir == 2) {
								x2 = x + 1;
							} else if (dir == 3) {
								y2 = y - 1;
							} else if (dir == 4) {
								y2 = y + 1;
							}
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;

							int type2 = (int) board.data[x2][y2];
							if (type2 == Constant.Rigid) continue;
							if (type2 == Constant.Wood && shNow.isWoodBrake(x2, y2) == false) continue;

							// それ以外は移動できる。
							shNext.setAgent(agentID, x2, y2);
							doSomething = true;

							// 爆弾が置いてある可能性があって、キックできれば、爆弾をキックすることができる。
							if (abNow.kick) {
								int x3 = x;
								int y3 = y;
								if (dir == 1) {
									x3 = x - 2;
								} else if (dir == 2) {
									x3 = x + 2;
								} else if (dir == 3) {
									y3 = y - 2;
								} else if (dir == 4) {
									y3 = y + 2;
								}
								if (x3 > 0 && x3 < numField && y3 >= 0 && y3 < numField) {

									boolean isKickable = true;
									int type3 = (int) board.data[x3][y3];
									if (type3 == Constant.Rigid) {
										isKickable = false;
									}
									if (type3 == Constant.Wood && shNow.isWoodBrake(x3, y3) == false) {
										isKickable = false;
									}

									if (isKickable) {
										for (int life = 1; life < 10; life++) {
											for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
												if (shNow.isBombExist(life, moveDirection, x2, y2)) {
													int power = shNow.getBombPower(life, moveDirection, x2, y2);
													shNext.setBomb(life - 1, dir, x3, y3, power);
												}
											}
										}
									}
								}
							}
						} else if (action == 5) {
							// 爆弾を置くケース
							if (abNow.numBombHold > 0) {
								shNext.setBomb(9, 5, x, y, abNow.strength);
								shNext.setAgent(agentID, x, y);
								doSomething = true;
							}
						}
					}
				}
			}

			// なにも実行可能なアクションがないエージェントがいる場合、この分岐は終了する。
			if (absNow[agentIndex].isAlive == true && doSomething == false) {
				System.out.println("do nothing");
				return;
			}
		}
		timeEnd = System.currentTimeMillis();
		time1 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// 爆弾を移動させながらLifeを一個減らす。0になったらFCへ移動
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				for (int life = 1; life < 10; life++) {
					for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
						if (shNow.isBombExist(life, moveDirection, x, y) == false) continue;
						int power = shNow.getBombPower(life, moveDirection, x, y);

						int x2 = x;
						int y2 = y;
						if (moveDirection == 1) {
							x2 = x - 1;
						} else if (moveDirection == 2) {
							x2 = x + 1;
						} else if (moveDirection == 3) {
							y2 = y - 1;
						} else if (moveDirection == 4) {
							y2 = y + 1;
						}

						boolean isStop = false;
						if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
							isStop = true;
						} else {
							int type2 = (int) board.data[x2][y2];
							if (type2 == Constant.Rigid) {
								isStop = true;
							}
							if (type2 == Constant.Wood && shNow.isWoodBrake(x2, y2) == false) {
								isStop = true;
							}
						}

						if (isStop) {
							if (life - 1 == 0) {
								shNext.setFlameCenter(3, x, y, power);
							} else {
								shNext.setBomb(life - 1, 5, x, y, power);
							}
						} else {
							if (life - 1 == 0) {
								shNext.setFlameCenter(3, x2, y2, power);
							} else {
								shNext.setBomb(life - 1, moveDirection, x2, y2, power);
							}

							boolean isStopable = false;
							for (int ai = 0; ai < 4; ai++) {
								if (shNext.isAgentExist(ai + 10, x2, y2)) {
									isStopable = true;
									break;
								}
							}

							if (isStopable) {
								if (life - 1 == 0) {
									shNext.setFlameCenter(3, x, y, power);
								} else {
									shNext.setBomb(life - 1, 5, x, y, power);
								}
							}
						}
					}
				}
			}
		}
		timeEnd = System.currentTimeMillis();
		time2 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// FCを一個減らす。0になったら除外する。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				for (int life = 1; life < 4; life++) {
					if (shNow.isFlameCenterExist(life, x, y) == false) continue;
					int power = shNow.getFlameCenterPower(life, x, y);
					shNext.setFlameCenter(life - 1, x, y, power);
				}
			}
		}
		timeEnd = System.currentTimeMillis();
		time3 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// 1ステップ後のFCで連鎖する可能性のある爆弾があれば、それもFCに移動する。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		MyMatrix myBoard = new MyMatrix(board);
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) myBoard.data[x][y];
				if (type == Constant.Wood && shNow.isWoodBrake(x, y) == true) {
					myBoard.data[x][y] = Constant.Passage;
				}
			}
		}
		timeEnd = System.currentTimeMillis();
		time4 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// FCからFlamesを計算する。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		MyMatrix myFlame = new MyMatrix(numField, numField);
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				for (int life = 1; life <= 3; life++) {
					if (shNext.isFlameCenterExist(life, x, y) == false) continue;
					int power = shNext.getFlameCenterPower(life, x, y);
					BBMUtility.PrintFlame(myBoard, myFlame, x, y, power, 1);
				}
			}
		}

		while (true) {

			if (false) {
				System.out.println("=========================");
				MatrixUtility.OutputMatrix(myFlame);
				System.out.println("=========================");
			}

			boolean isChanged = false;
			// 炎に巻き込まれる爆弾で、FCに変化させた形跡が無いものは、FCに変化させる。
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					for (int life = 1; life <= 9; life++) {
						for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
							if (shNext.isFlameCenterExist(3, x, y) == false && myFlame.data[x][y] == 1 && shNext.isBombExist(life, moveDirection, x, y) == true) {
								int power = shNext.getBombPower(life, moveDirection, x, y);
								shNext.setFlameCenter(3, x, y, power);
								BBMUtility.PrintFlame(myBoard, myFlame, x, y, power, 1);
								isChanged = true;
							}
						}
					}
				}
			}
			if (isChanged == false) break;
		}
		timeEnd = System.currentTimeMillis();
		time5 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// 新規FCでWBを計算する。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				if (shNow.isWoodBrake(x, y)) {
					shNext.setWoodBrake(x, y);
				} else {
					int type = (int) board.data[x][y];
					if (type == Constant.Wood && myFlame.data[x][y] == 1) {
						shNext.setWoodBrake(x, y);
					}
				}
			}
		}

		// エージェント毎に死ぬパターンを数え上げる。
		for (int agentIndex = 0; agentIndex < 4; agentIndex++) {
			int agentID = agentIndex + 10;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (shNext.isAgentExist(agentID, x, y) == false) continue;

					counter_global[agentIndex][depth]++;
					if (myFlame.data[x][y] == 1) {
						death_global[agentIndex][depth]++;
					}
				}
			}
		}
		timeEnd = System.currentTimeMillis();
		time6 += timeEnd - timeStart;

		// 次ステップを呼び出す。
		// ここで、どの程度こまかく分岐するかを調整する。
		// まずは、「爆弾を置く」と「上下左右の移動」で場合分けする。
		for (int i = 0; i < 16; i++) {
			boolean[] move = new boolean[4];
			for (int k = 0; k < 4; k++) {
				int flag = (i >> k) & 1;
				if (flag == 0) {
					move[k] = false;
				} else {
					move[k] = true;
				}
			}

			int[][] actionsNext = new int[4][];
			for (int k = 0; k < 4; k++) {
				if (move[k] == true) {
					actionsNext[k] = new int[] { 0, 1, 2, 3, 4 };
				} else {
					actionsNext[k] = new int[] { 5 };
				}
			}

			// System.out.println("depth = " + depth + ", call child, i=" + i);
			sequence.add(shNext);
			RRR(depth + 1, board, bombMap, actionsNext, absNext, shNext, sequence);
			sequence.remove(shNext);
		}

		if (false) {
			// if (depth == 10) {
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			System.out.println("==");
			System.out.println("depth = " + depth);
			System.out.println("==");
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			for (StatusHolder sh : sequence) {
				System.out.println(sh.toString());
			}
			System.out.println("================================================================================");
			// return;
		}
	}

	double[][] counter_global = new double[4][100];
	double[][] death_global = new double[4][100];

	public void Compute(MyMatrix board, Node[][] bombMap, Ability abs[]) throws Exception {

		counter_global = new double[4][100];
		death_global = new double[4][100];

		StatusHolder shNow = new StatusHolder();

		// 時刻0の初期状態を求める。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (Constant.isAgent(type)) {
					shNow.setAgent(type, x, y);
				}
			}
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				Node node = bombMap[x][y];
				if (node == null) continue;
				if (node.type == Constant.Bomb) {
					shNow.setBomb(node.lifeBomb, node.moveDirection, x, y, node.power);
				} else if (node.type == Constant.Flames) {
					shNow.setFlameCenter(node.lifeFlameCenter, x, y, node.power);
				}
			}
		}

		Ability[] absNow = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNow[i] = new Ability(abs[i]);
		}

		List<StatusHolder> sequence = new ArrayList<StatusHolder>();

		for (int i = 0; i < 16; i++) {
			boolean[] move = new boolean[4];
			for (int k = 0; k < 4; k++) {
				int flag = (i >> k) & 1;
				if (flag == 0) {
					move[k] = false;
				} else {
					move[k] = true;
				}
			}

			int[][] actionsNext = new int[4][];
			for (int k = 0; k < 4; k++) {
				if (move[k] == true) {
					actionsNext[k] = new int[] { 0, 1, 2, 3, 4 };
				} else {
					actionsNext[k] = new int[] { 5 };
				}
			}

			// System.out.println("TOP, call child, i=" + i);
			sequence.add(shNow);
			RRR(0, board, bombMap, actionsNext, absNow, shNow, sequence);
			sequence.remove(shNow);
		}

		for (int i = 0; i < 4; i++) {
			for (int depth = 0; depth < 20; depth++) {
				double rate = death_global[i][depth] / counter_global[i][depth];
				System.out.print(String.format("%10.5f", rate));
			}
			System.out.println();
		}
		System.out.println();

		double t1 = time1 * 0.001;
		double t2 = time2 * 0.001;
		double t3 = time3 * 0.001;
		double t4 = time4 * 0.001;
		double t5 = time5 * 0.001;
		double t6 = time6 * 0.001;

		String line = String.format("1=%f, 2=%f, 3=%f, 4=%f, 5=%f, 6=%f", t1, t2, t3, t4, t5, t6);
		System.out.println(line);
	}
}
