package com.ibm.trl.BBM.mains;

import java.util.LinkedList;
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
	// static int numC = 7;

	static int numPast = 20;
	// MyMatrix board_pre = null;
	// MyMatrix bomb_blast_strength_pre = null;
	// MyMatrix bomb_life_pre = null;

	LinkedList<MyMatrix> boardOld = new LinkedList<MyMatrix>();
	LinkedList<MyMatrix> powerOld = new LinkedList<MyMatrix>();
	LinkedList<MyMatrix> lifeOld = new LinkedList<MyMatrix>();

	class Ability {
		int numMaxBomb = 1;
		int strength = 2;
		boolean kick = false;
	}

	Ability[] abs = new Ability[4];

	static int[] indexList = new int[numC * numC * 36 + 1];
	static BT bt;
	static int counter = 0;

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

	public int act(int xMe, int yMe, int ammo, int blast_strength, boolean can_kick, MyMatrix board, MyMatrix bomb_blast_strength, MyMatrix bomb_life, MyMatrix alive, MyMatrix enemies)
			throws Exception {
		if (false) {
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("board");
			System.out.println(board);
			System.out.println("bomb_blast_strength");
			System.out.println(bomb_blast_strength);
			System.out.println("bomb_life");
			System.out.println(bomb_life);
		}

		// bt.child = null;

		if (boardOld.size() == 0) {
			for (int i = 0; i < numPast; i++) {
				boardOld.add(board);
				powerOld.add(bomb_blast_strength);
				lifeOld.add(bomb_life);
			}
		}

		// 生きてるかどうかのフラグ
		boolean[] isAlive = new boolean[4];
		int numAlive = alive.numt;
		for (int i = 0; i < numAlive; i++) {
			int index = (int) (alive.data[i][0] - 10);
			isAlive[index] = true;
		}

		// 相手がアイテムをとったかどうかを調べる。
		{
			MyMatrix board_pre = boardOld.get(0);
			for (int i = 0; i < numField; i++) {
				for (int j = 0; j < numField; j++) {
					if (board_pre.data[i][j] == 6 && board.data[i][j] >= 10 && board.data[i][j] <= 13) {
						int id = (int) (board.data[i][j] - 10);
						abs[id].numMaxBomb++;
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

		// とりあえず、人の予測モデルを作ってみる。
		if (true) {
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
					if (board.data[x][y] == Constant.Flames) {
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
				if (counter % 100 == 0) {
					Set<Integer> selected = new TreeSet<Integer>();
					bt.dig(selected);
					System.out.println(bt.print());
				}
				counter++;
			}
		}

		// 古いデータとして保存する。
		{
			boardOld.addFirst(board);
			powerOld.addFirst(bomb_blast_strength);
			lifeOld.addFirst(bomb_life);

			boardOld.removeLast();
			powerOld.removeLast();
			lifeOld.removeLast();
		}

		return 1;
	}

	double Gtotal = 0;
	double Gcount = 0;
}
