package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class KillScoreEvaluator {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final ForwardModel fm = new ForwardModel();
	static int[] phi = new int[45 * 45];

	static {
		try {
			learn();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static int index(Pack pack, int ai) throws Exception {

		AgentEEE[] agents = new AgentEEE[4];
		for (AgentEEE aaa : pack.sh.getAgentEntry()) {
			if (aaa == null) continue;
			agents[aaa.agentID - 10] = aaa;
		}

		AgentEEE target = agents[ai];
		if (target == null) return -1;

		////////////////////////////////////////////////////////////////////////////////////
		// 周囲のWallの数が2か3の時以外は除外する。
		////////////////////////////////////////////////////////////////////////////////////
		boolean[] isWall = new boolean[5];
		{
			int numWall = 0;
			int x = target.x;
			int y = target.y;
			for (int[] vec : GlobalParameter.onehopList) {
				int dir = vec[0];
				int dx = vec[1];
				int dy = vec[2];
				if (dir == 0) continue;
				int x2 = x + dx;
				int y2 = y + dy;
				if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
					isWall[dir] = true;
					numWall++;
				} else {
					int type = (int) pack.board.data[x2][y2];
					if (Constant.isWall(type)) {
						isWall[dir] = true;
						numWall++;
					}
				}
			}
			if (numWall != 2 && numWall != 3) return -1;
		}

		BombEEE[][] bombMap = new BombEEE[numField][numField];
		for (BombEEE bbb : pack.sh.getBombEntry()) {
			bombMap[bbb.x][bbb.y] = bbb;
		}

		////////////////////////////////////////////////////////////////////////////////////
		// 敵二人を探す。
		////////////////////////////////////////////////////////////////////////////////////
		// TODO どんなAgent配列でも動くように修正すべき。
		int enemy1 = -1, enemy2 = -1;
		if (ai == 0) {
			enemy1 = 1;
			enemy2 = 3;
		} else if (ai == 1) {
			enemy1 = 0;
			enemy2 = 2;
		} else if (ai == 2) {
			enemy1 = 1;
			enemy2 = 3;
		} else if (ai == 3) {
			enemy1 = 0;
			enemy2 = 2;
		}
		int[] enemies = new int[] { enemy1, enemy2 };

		////////////////////////////////////////////////////////////////////////////////////
		// ターゲットの四方を調べる。
		////////////////////////////////////////////////////////////////////////////////////
		int[][] ids = new int[5][2];
		if (true) {
			int x = target.x;
			int y = target.y;
			for (int dis = 1; dis <= 2; dis++) {
				for (int[] vec : GlobalParameter.onehopList) {
					int dir = vec[0];
					int dx = vec[1];
					int dy = vec[2];
					if (dir == 0) continue;
					int x2 = x + dx * dis;
					int y2 = y + dy * dis;
					int id = 0;
					if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
						id = 1;
					} else {
						int type = (int) pack.board.data[x2][y2];
						if (Constant.isWall(type)) {
							id = 1;
						} else if (type == Constant.Bomb) {
							BombEEE bbb = bombMap[x2][y2];
							if (bbb != null && bbb.dir == 0) {
								id = 2;
							}
						}
					}
					ids[dir][dis - 1] = id;
				}
			}

			for (int dir = 0; dir < 5; dir++) {
				if (ids[dir][0] == 1) {
					ids[dir][1] = 1;
				}
			}
		}

		////////////////////////////////////////////////////////////////////////////////////
		// 四方の通路に対して、敵エージェントがどの方向を担当するか紐付ける。
		// 敵エージェントが対応できる方向・距離のリストを作る。
		////////////////////////////////////////////////////////////////////////////////////
		int[][][] tantouList = new int[2][3][2];
		int x = target.x;
		int y = target.y;
		for (int i = 0; i < 2; i++) {
			int enemy = enemies[i];
			AgentEEE aaa = agents[enemy];

			int count = 0;
			if (aaa != null) {
				for (int[] vec : GlobalParameter.onehopList) {
					int dir = vec[0];
					int dx = vec[1];
					int dy = vec[2];
					if (dir == 0) continue;
					if (isWall[dir]) continue;
					for (int dis = 1; dis <= 2; dis++) {
						int x2 = x + dx * dis;
						int y2 = y + dy * dis;
						int ddd = Math.abs(aaa.x - x2) + Math.abs(aaa.y - y2);
						if (ddd == 0) {
							if (dis == 1) {
								tantouList[i][count][0] = dir;
								tantouList[i][count][1] = 0;
								count++;
							} else if (dis == 2) {
								tantouList[i][count][0] = dir;
								tantouList[i][count][1] = 1;
								count++;
							}
						}
					}
				}

				if (count == 0) {
					for (int[] vec : GlobalParameter.onehopList) {
						int dir = vec[0];
						int dx = vec[1];
						int dy = vec[2];
						if (dir == 0) continue;
						if (isWall[dir]) continue;
						for (int dis = 1; dis <= 2; dis++) {
							int x2 = x + dx * dis;
							int y2 = y + dy * dis;
							int ddd = Math.abs(aaa.x - x2) + Math.abs(aaa.y - y2);
							if (ddd == 1) {
								if (dis == 1) {
									tantouList[i][count][0] = dir;
									tantouList[i][count][1] = 2;
									count++;
								} else if (dis == 2) {
									tantouList[i][count][0] = dir;
									tantouList[i][count][1] = 3;
									count++;
								}
							}
						}
					}
				}
			}

			if (count == 0) {
				tantouList[i][count][0] = 100;
				count++;
			}
		}

		////////////////////////////////////////////////////////////////////////////////////
		// 担当する方向・距離の組に対して、特徴量を計算する。
		////////////////////////////////////////////////////////////////////////////////////
		for (int[] tantou1 : tantouList[0]) {
			int dir1 = tantou1[0];
			int pos1 = tantou1[1];
			if (dir1 == 0) break;
			for (int[] tantou2 : tantouList[1]) {
				int dir2 = tantou2[0];
				int pos2 = tantou2[1];
				if (dir2 == 0) break;
				if (dir1 != 100 && dir1 == dir2) continue;

				// 与えられたエージェント位置、周囲環境で、Indexを計算する。
				int[] apos = new int[] { 4, 4, 4, 4, 4 };
				if (dir1 != 100) apos[dir1] = pos1;
				if (dir2 != 100) apos[dir2] = pos2;

				int[] dirList = new int[] { -1, -1 };
				if (true) {
					int numOpen = 0;
					int numWall = 0;
					for (int dir = 1; dir < 5; dir++) {
						if (ids[dir][0] == 0 || ids[dir][0] == 2) {
							if (apos[dir] != 0 && apos[dir] != 2) {
								numOpen++;
							}
						} else if (ids[dir][0] == 1) {
							numWall++;
						}
					}

					if (numOpen > 0) continue;
					if (numWall < 2) continue;

					int count = 0;
					for (int dir = 1; dir < 5; dir++) {
						if (ids[dir][0] == 1) continue;
						dirList[count] = dir;
						count++;
					}
				}

				// dirListの方角に対して、ids, aposから特徴量を作る。
				int[][] stateList = new int[2][3];
				for (int i = 0; i < 2; i++) {
					int dir = dirList[i];
					if (dir == -1) {
						stateList[i] = new int[] { 1, 1, 0 };
					} else {
						stateList[i] = new int[] { ids[dir][0], ids[dir][1], apos[dir] };
					}
				}
				int index = state2index(stateList[0], stateList[1]);
				return index;
			}
		}

		return -1;
	}

	private static int state2index(int[] state1, int[] state2) {
		int[][] temp = new int[2][];
		temp[0] = state1;
		temp[1] = state2;
		int index = 0;
		for (int i = 0; i < 2; i++) {
			int type1 = temp[i][0];
			int type2 = temp[i][1];
			int apos = temp[i][2];
			int t = apos * 9 + type1 * 3 + type2;
			index = index * 45 + t;
		}
		return index;
	}

	private static List<int[]> generateNextState(int[] state_now) {
		List<int[]> state_next = new ArrayList<int[]>();
		{
			int type1 = state_now[0];
			int type2 = state_now[1];
			int apos = state_now[2];

			if (apos == 0) {

				// 外に移動する。
				state_next.add(new int[] { type1, type2, 2 });

				// 第2セルに移動する。
				if (type2 == 0) state_next.add(new int[] { type1, type2, 1 });

				// 爆弾を置く
				if (type1 == 0) state_next.add(new int[] { 2, type2, apos });

			} else if (apos == 1) {

				// 外に移動する。
				state_next.add(new int[] { type1, type2, 3 });

				// 第1セルに移動する。
				if (type1 == 0) state_next.add(new int[] { type1, type2, 0 });

				// 爆弾を置く
				if (type2 == 0) state_next.add(new int[] { type1, 2, apos });

			} else if (apos == 2) {

				// 外に移動する。
				state_next.add(new int[] { type1, type2, 4 });

				// 第1セルに移動する。
				if (type1 == 0) state_next.add(new int[] { type1, type2, 0 });

			} else if (apos == 3) {

				// 外に移動する。
				state_next.add(new int[] { type1, type2, 4 });

				// 第2セルに移動する。
				if (type2 == 0) state_next.add(new int[] { type1, type2, 1 });

			}
		}

		List<int[]> next2 = new ArrayList<int[]>();
		for (int[] state : state_next) {
			if (isTsume(state) == false) continue;
			next2.add(state);
		}

		return next2;
	}

	private static boolean isTsume(int[] state) {
		int type1 = state[0];
		int type2 = state[1];
		int apos = state[2];

		// Wallの上にエージェントがいたらおかしい。
		if (type1 == 1 && apos == 0) return false;
		if (type2 == 1 && apos == 1) return false;

		// 隣接セルが空白だったら詰めてる状態じゃないので、おかしい。
		if (type1 == 0 && apos != 0 && apos != 1 && apos != 2) return false;
		if (type1 == 1 && apos != 0 && apos != 1 && apos != 2 && apos != 3) return false;
		return true;
	}

	private static void learn() throws Exception {

		for (int i = 0; i < phi.length; i++) {
			phi[i] = Integer.MAX_VALUE;
		}

		// ゴールの遷移数を0に設定する。
		List<int[]> state_comp = new ArrayList<int[]>();
		state_comp.add(new int[] { 2, 2, 4 });
		state_comp.add(new int[] { 2, 1, 4 });
		for (int type2 = 0; type2 < 3; type2++) {
			for (int apos = 0; apos < 5; apos++) {
				state_comp.add(new int[] { 1, type2, apos });
			}
		}
		for (int[] s1 : state_comp) {
			for (int[] s2 : state_comp) {
				int index = state2index(s1, s2);
				phi[index] = 0;
			}
		}

		List<int[]> stateList = new ArrayList<int[]>();
		for (int type1 = 0; type1 < 3; type1++) {
			for (int type2 = 0; type2 < 3; type2++) {
				for (int apos = 0; apos < 5; apos++) {
					int[] state = new int[] { type1, type2, apos };
					// 状態としてあり得るか調べる。
					if (isTsume(state) == false) continue;
					stateList.add(state);
				}
			}
		}

		// 各状態から1ステップ操作を加えて、有限な遷移回数で詰められる状態に遷移したら、着目状態の遷移回数をUpdateする。
		while (true) {
			boolean changed = false;
			for (int[] state1 : stateList) {
				for (int[] state2 : stateList) {
					int indexNow = state2index(state1, state2);
					int phiNow = phi[indexNow];

					System.out.println(state1[0] + ", " + state1[1] + ", " + state1[2] + ", " + state2[0] + ", " + state2[1] + ", " + state2[2] + ", " + indexNow + ", " + phiNow);

					// s1を動かす。
					{
						List<int[]> nextList = generateNextState(state1);
						for (int[] state_next : nextList) {
							int indexNext = state2index(state_next, state2);
							if (phi[indexNext] != Integer.MAX_VALUE && phi[indexNext] + 1 < phi[indexNow]) {
								phi[indexNow] = phi[indexNext] + 1;
								changed = true;
							}
						}
					}

					// s2を動かす。
					{
						List<int[]> nextList = generateNextState(state2);
						for (int[] state_next : nextList) {
							int indexNext = state2index(state1, state_next);
							if (phi[indexNext] != Integer.MAX_VALUE && phi[indexNext] + 1 < phi[indexNow]) {
								phi[indexNow] = phi[indexNext] + 1;
								changed = true;
							}
						}
					}

				}
			}
			if (changed == false) {
				break;
			}
		}
	}

	public double[][] Do(int me, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife) throws Exception {
		List<BombTracker.Node> nodes = new ArrayList<BombTracker.Node>();
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				BombTracker.Node node = bombMap[x][y];
				if (node == null) continue;
				nodes.add(node);
			}
		}

		List<double[][]> scoresList = new ArrayList<double[][]>();
		LinkedList<BombEEE> bbbs = new LinkedList<BombEEE>();
		rrr(0, me, maxPower, abs, map, nodes, flameLife, bbbs, scoresList);

		double[][] scores = new double[4][2];
		for (double[][] temp : scoresList) {
			for (int ai = 0; ai < 4; ai++) {
				for (int i = 0; i < 2; i++) {
					scores[ai][i] += temp[ai][i];
				}
			}
		}
		return scores;
	}

	/**
	 * 
	 * 爆弾の移動方向が未確定の場合、1ステップ目でキックできる場合、条件を増やす。
	 * 
	 */
	private void rrr(int index, int me, int maxPower, Ability[] abs, MapInformation map, List<BombTracker.Node> nodes, MyMatrix flameLife, LinkedList<BombEEE> bbbs, List<double[][]> scoresList)
			throws Exception {
		if (index == nodes.size()) {
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			//
			// 基本データを作る。
			//
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			Ability[] abs2 = new Ability[4];
			for (int ai = 0; ai < 4; ai++) {
				abs2[ai] = new Ability(abs[ai]);
				if (ai + 10 == me) continue;
				abs2[ai].kick = true;
				abs2[ai].numMaxBomb = 3;
				abs2[ai].numBombHold = 3;
				if (abs2[ai].strength_fix == -1) {
					abs2[ai].strength = maxPower;
				} else {
					abs2[ai].strength = abs2[ai].strength_fix;
				}
			}

			Pack packNow;
			if (true) {
				StatusHolder sh = new StatusHolder(numField);
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						int type = map.getType(x, y);
						if (Constant.isAgent(type)) {
							sh.setAgent(x, y, type);
						}
					}
				}

				for (BombEEE bbb : bbbs) {
					sh.setBomb(bbb.x, bbb.y, -1, bbb.life, bbb.dir, bbb.power);
				}

				packNow = new Pack(map.board, flameLife, abs2, sh);
			}

			AgentEEE[] agents = new AgentEEE[4];
			for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
				agents[aaa.agentID - 10] = aaa;
			}

			double[][] scores = new double[4][2];
			for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
				int gg = computeKillScore(packNow, aaa.agentID - 10);
				if (gg != Integer.MAX_VALUE) {
					scores[aaa.agentID - 10][0]++;
				}
				scores[aaa.agentID - 10][1]++;
			}

			scoresList.add(scores);
		} else {
			BombTracker.Node node = nodes.get(index);
			for (int dir = 0; dir < 5; dir++) {
				if (node.dirs[dir]) {
					BombEEE bbb = new BombEEE(node.x, node.y, -1, node.life, dir, node.power);
					bbbs.addLast(bbb);
					rrr(index + 1, me, maxPower, abs, map, nodes, flameLife, bbbs, scoresList);
					bbbs.removeLast();
				}
			}
		}
	}

	public int computeKillScore(Pack pack, int ai) throws Exception {
		int index = index(pack, ai);
		if (index == -1) return Integer.MAX_VALUE;
		return phi[index];
	}
}
