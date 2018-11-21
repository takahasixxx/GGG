/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.ibm.trl.BBM.mains.BombTracker.Node;
import com.ibm.trl.BBM.mains.BombTracker.ResultBT;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.WorstScoreEvaluator.ScoreResult;

import ibm.ANACONDA.Core.MatrixUtility;
import ibm.ANACONDA.Core.MyMatrix;

public class Agent {
	static final boolean verbose = GlobalParameter.verbose;
	static final int numField = GlobalParameter.numField;
	static final int numPast = 20;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	ModelParameter param;
	WorstScoreEvaluator worstScoreEvaluator;
	// WorstScoreEvaluator_mt worstScoreEvaluator_mt;
	// WorstScoreEvaluator_2step_mt worstScoreEvaluator_2step_mt;
	ActionEvaluator actionEvaluator;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	int me;
	int frame = 0;
	Ability[] abs = new Ability[4];
	LinkedList<MapInformation> mapsOld = new LinkedList<MapInformation>();
	LinkedList<MapInformation> exmapsOld = new LinkedList<MapInformation>();
	MyMatrix board_memo = new MyMatrix(numField, numField, Constant.Rigid);
	int maxPower = 2;
	int numItemGet = 0;
	MyMatrix lastLook = new MyMatrix(numField, numField, 0);
	LastAgentPosition[] laps = new LastAgentPosition[4];

	static public class ModelParameter {
		int numFirstMoveStepsAsFriend = 2;
		double rateLevel = 1.25;
		double gainOffset = 1.0;
		double usualThreshold = 3.5;
		double attackThreshold = 2.5;

		@Override
		public String toString() {
			String line = String.format("numFirstMoveStepsAsFriend=%d, rateLevel=%f, gainOffsetr=%f, usualThreshold=%f, attackThreshold=%f", numFirstMoveStepsAsFriend, rateLevel, gainOffset,
					usualThreshold, attackThreshold);
			return line;
		}
	}

	static public class Ability implements Serializable {
		private static final long serialVersionUID = 372642396371084459L;
		public boolean isAlive = true;
		public int numMaxBomb = 1;
		public int strength = 2;
		public int strength_fix = -1;
		public boolean kick = false;
		public int numBombHold = 1;

		public Ability() {
		}

		public Ability(Ability a) {
			this.isAlive = a.isAlive;
			this.numMaxBomb = a.numMaxBomb;
			this.strength = a.strength;
			this.strength_fix = a.strength_fix;
			this.kick = a.kick;
			this.numBombHold = a.numBombHold;
		}

		@Override
		public String toString() {
			String line = String.format("isAlive=%5b, hold/max=%2d/%2d, strength=%2d, strength_fix=%2d, kick=%5b\n", isAlive, numBombHold, numMaxBomb, strength, strength_fix, kick);
			return line;
		}

		public boolean equals(Ability a) {
			if (this.isAlive == a.isAlive) {
				if (this.numMaxBomb == a.numMaxBomb) {
					if (this.strength == a.strength) {
						if (this.strength_fix == a.strength_fix) {
							if (this.kick == a.kick) {
								if (this.numBombHold == a.numBombHold) { return true; }
							}
						}
					}
				}
			}
			return false;
		}
	}

	static public class LastAgentPosition {
		int x;
		int y;
		int frame;

		public LastAgentPosition(int x, int y, int frame) {
			this.x = x;
			this.y = y;
			this.frame = frame;
		}
	}

	Agent(int me, ModelParameter param) throws Exception {
		this.param = param;
		worstScoreEvaluator = new WorstScoreEvaluator(param);
		// worstScoreEvaluator_mt = new WorstScoreEvaluator_mt(param);
		actionEvaluator = new ActionEvaluator(param);

		this.me = me;

		for (int ai = 0; ai < 4; ai++) {
			abs[ai] = new Ability();
		}

		// board_memoを初期化する。
		{
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					board_memo.data[x][y] = Constant.Wood;
				}
			}
			for (int x = 1; x < numField - 1; x++) {
				board_memo.data[x][1] = Constant.Passage;
				board_memo.data[x][numField - 2] = Constant.Passage;
				board_memo.data[1][x] = Constant.Passage;
				board_memo.data[numField - 2][x] = Constant.Passage;
			}
			for (int x = 4; x < numField - 4; x++) {
				board_memo.data[x][1] = Constant.Wood;
				board_memo.data[x][numField - 2] = Constant.Wood;
				board_memo.data[1][x] = Constant.Wood;
				board_memo.data[numField - 2][x] = Constant.Wood;
			}
		}

		// lastAgentPositionを初期化する。
		if (true) {
			laps[0] = new LastAgentPosition(1, 1, 0);
			laps[1] = new LastAgentPosition(9, 1, 0);
			laps[2] = new LastAgentPosition(9, 9, 0);
			laps[3] = new LastAgentPosition(1, 9, 0);
		}

	}

	public void episode_end(int reward) throws Exception {
		System.out.println("episode_end, reward = " + reward + ", " + frame);

		// 殺された場合、最後の20ステップの盤面遷移を出力する。
		if (false) {
			// if (reward == -1 && frame != 801) {
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("");
			System.out.println("LOSELOSELOSE");
			List<MapInformation> temp = new ArrayList<MapInformation>();
			temp.addAll(exmapsOld);
			Collections.reverse(temp);
			for (MapInformation map : temp) {
				System.out.println("=========================================================================================");
				BBMUtility.printBoard2(map.board, map.board, map.life, map.power);
			}
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
		}
	}

	public int act(int xMe, int yMe, int ammo, int blast_strength, boolean can_kick, MyMatrix board, MyMatrix bomb_blast_strength, MyMatrix bomb_life, MyMatrix alive, MyMatrix enemies, int friend,
			boolean isCollapse) throws Exception {

		long timeStartFunction = System.currentTimeMillis();

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 盤面をログに出力してみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (verbose) {
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("frame = " + frame);
			System.out.println("me = " + me);
			// System.out.println("=========================================================================================");
			// System.out.println("board picture");
			// BBMUtility.printBoard2(board, board, bomb_life, bomb_blast_strength);
			System.out.println("=========================================================================================");
		}

		// TODO 崩壊前後で止める。
		if (false) {
			int delta = 10;

			for (int ttt : new int[] { 500, 575, 650, 725 }) {
				int def = frame - ttt;
				if (def >= -2 && def <= delta) {
					System.out.println("aaa");
					break;
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本情報のアップデート
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		if (mapsOld.size() == 0) {
			for (int i = 0; i < numPast; i++) {
				MapInformation mi = new MapInformation(board, bomb_blast_strength, bomb_life);
				mapsOld.add(mi);
			}
		}

		// 生きてるかどうかのフラグをアップデートする。
		if (true) {
			for (int i = 0; i < 4; i++) {
				abs[i].isAlive = false;
			}
			int numAlive = alive.numt;
			for (int i = 0; i < numAlive; i++) {
				int index = (int) (alive.data[i][0] - 10);
				abs[index].isAlive = true;
			}
		}

		// Collapseさせる。
		if (isCollapse) {
			int delta = 0;
			int[][] ggg = new int[][] { { 0, 500 }, { 1, 575 }, { 2, 650 }, { 3, 725 } };
			for (int[] g : ggg) {
				int targetFrame = g[1];
				if (frame == targetFrame + delta) {
					int dis = g[0];
					for (int p = 0; p < numField; p++) {
						for (int hen = 0; hen < 4; hen++) {
							int x = -1, y = -1;
							if (hen == 0) {
								x = dis;
								y = p;
							} else if (hen == 1) {
								x = numField - 1 - dis;
								y = p;
							} else if (hen == 2) {
								x = p;
								y = dis;
							} else if (hen == 3) {
								x = p;
								y = numField - 1 - dis;
							}

							int type = (int) board.data[x][y];
							if (type == Constant.Flames) continue;
							board.data[x][y] = Constant.Rigid;
							if (Constant.isAgent(type)) {
								abs[type - 10].isAlive = false;
							}
							bomb_blast_strength.data[x][y] = 0;
							bomb_life.data[x][y] = 0;
						}
					}
				}
			}
		}

		// 情報をまとめたMapInformationオブジェクトを作る。
		MapInformation map = new MapInformation(board, bomb_blast_strength, bomb_life);

		// エージェントのアイテム取得状況を観測し、Abilityをトラッキングする。
		// Fogの中でアイテム取得されるケースがあるので、自分以外はトラック漏れが多々ある。
		if (true) {
			MapInformation mapPre = mapsOld.get(0);
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (mapPre.getType(x, y) == 6 && Constant.isAgent(map.getType(x, y))) {
						int id = (int) (board.data[x][y] - 10);
						abs[id].numMaxBomb++;
						abs[id].numBombHold++;
						if (id + 10 == me) numItemGet++;
					} else if (mapPre.getType(x, y) == 7 && Constant.isAgent(map.getType(x, y))) {
						int id = (int) (board.data[x][y] - 10);
						abs[id].strength++;
						if (id + 10 == me) numItemGet++;
					} else if (mapPre.getType(x, y) == 8 && Constant.isAgent(map.getType(x, y))) {
						int id = (int) (board.data[x][y] - 10);
						abs[id].kick = true;
						if (id + 10 == me) numItemGet++;
					}
				}
			}
		}

		// 自分だけは爆弾保有数が観測できる。
		abs[me - 10].numBombHold = ammo;

		// 爆弾のmaxPowerを保存する。自分以外のエージェントのアイテム取得状況が完全観測できないので、maxPowerで最悪ケースを押さえるため。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int power = (int) bomb_blast_strength.data[x][y];
				if (power == 0) continue;
				if (power > maxPower) maxPower = power;
			}
		}

		// エージェントが観測されている場合、最後に見たエージェントの位置を記憶する。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = map.getType(x, y);
				if (Constant.isAgent(type)) {
					laps[type - 10].x = x;
					laps[type - 10].y = y;
					laps[type - 10].frame = frame;
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 最新の観測結果からboard_memoを更新する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (type == Constant.Fog) continue;
				if (type == Constant.Passage) {
					board_memo.data[x][y] = Constant.Passage;
					if (frame < 10) {
						board_memo.data[x][y] = Constant.Passage;
					}
				} else if (type == Constant.Rigid) {
					board_memo.data[x][y] = Constant.Rigid;
					if (frame < 500) {
						board_memo.data[y][x] = Constant.Rigid;
					}
				} else if (type == Constant.Wood) {
					board_memo.data[x][y] = Constant.Wood;
					if (frame < 10) {
						board_memo.data[y][x] = Constant.Wood;
					}
				} else if (type == Constant.Bomb) {
					board_memo.data[x][y] = Constant.Passage;
				} else if (type == Constant.Flames) {
					board_memo.data[x][y] = Constant.Passage;
				} else if (type == Constant.ExtraBomb) {
					board_memo.data[x][y] = Constant.ExtraBomb;
				} else if (type == Constant.IncrRange) {
					board_memo.data[x][y] = Constant.IncrRange;
				} else if (type == Constant.Kick) {
					board_memo.data[x][y] = Constant.Kick;
				} else if (type == Constant.Agent0) {
					board_memo.data[x][y] = Constant.Passage;
				} else if (type == Constant.Agent1) {
					board_memo.data[x][y] = Constant.Passage;
				} else if (type == Constant.Agent2) {
					board_memo.data[x][y] = Constant.Passage;
				} else if (type == Constant.Agent3) {
					board_memo.data[x][y] = Constant.Passage;
				}
			}
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (type == Constant.Fog) continue;
				lastLook.data[x][y] = frame;
			}
		}

		// TODO 出力してみる。
		if (false) {
			System.out.println("board_memo picture");
			BBMUtility.printBoard2(board_memo, board, bomb_life, bomb_blast_strength);
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// Fogの部分を埋めたBoardを作る。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MapInformation exmap;
		{
			MyMatrix board_ex = new MyMatrix(board);
			MyMatrix power_ex = new MyMatrix(map.power);
			MyMatrix life_ex = new MyMatrix(map.life);
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (board_ex.data[x][y] == Constant.Fog) {
						board_ex.data[x][y] = board_memo.data[x][y];
					}
				}
			}
			exmap = new MapInformation(board_ex, power_ex, life_ex);

			// TODO 出力してみる。
			if (false) {
				System.out.println("board_ex picture");
				// BBMUtility.printBoard2(board_ex, board, bomb_life, bomb_blast_strength);
				BBMUtility.printBoard2(board_ex, board_ex, bomb_life, bomb_blast_strength);
				System.out.println("=========================================================================================");
				System.out.println("=========================================================================================");
			}
		}

		if (exmapsOld.size() == 0) {
			for (int i = 0; i < numPast; i++) {
				exmapsOld.add(exmap);
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// エージェントが爆弾を置く瞬間を発見できたら、Abilityのstrengthを更新する。
		// Woodが全て破壊されている状況であれば、Strength確定。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (true) {
			boolean complete = true;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int type = map.getType(x, y);
					if (type == Constant.IncrRange || type == Constant.Wood || type == Constant.Flames) {
						complete = false;
						break;
					}
				}
				if (complete == false) break;
			}

			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int type = map.getType(x, y);
					int life = map.getLife(x, y);
					int power = map.getPower(x, y);
					if (Constant.isAgent(type) && life == 9) {
						abs[type - 10].numBombHold--;
						abs[type - 10].strength = power;
						if (complete) {
							abs[type - 10].strength_fix = power;
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 爆弾の動きをトラッキングする。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		Node[][] bombMap;
		MyMatrix flameLife;
		if (true) {
			List<MapInformation> maps = new ArrayList<MapInformation>();
			maps.add(map);
			maps.addAll(mapsOld);

			List<MapInformation> exmaps = new ArrayList<MapInformation>();
			exmaps.add(exmap);
			exmaps.addAll(exmapsOld);

			ResultBT res = BombTracker.computeBombMap(maxPower, maps, exmaps);
			bombMap = res.bombMap;
			flameLife = res.flames;
		}

		// exmapのboardのBombを書き換える。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				Node node = bombMap[x][y];
				if (node != null) {
					int type = (int) exmap.board.data[x][y];
					if (Constant.isAgent(type) == false) {
						exmap.board.data[x][y] = Constant.Bomb;
						exmap.life.data[x][y] = node.life;
						exmap.power.data[x][y] = node.power;
					}
				}
			}
		}

		// exmapのboardのFlameを書き換える。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				if (flameLife.data[x][y] > 0) {
					exmap.board.data[x][y] = Constant.Flames;
				}
			}
		}

		if (verbose) {
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("life");
			MatrixUtility.OutputMatrix(exmap.life);
			System.out.println("=========================================================================================");
			System.out.println("power");
			MatrixUtility.OutputMatrix(exmap.power);
			System.out.println("=========================================================================================");
			System.out.println("bomb direction");
			for (int x = 0; x < numField; x++) {
				String a1 = "";
				String a2 = "";
				String a3 = "";
				for (int y = 0; y < numField; y++) {
					Node node = bombMap[x][y];
					if (node == null) {
						a1 += " /   \\ ";
						a2 += " | + | ";
						a3 += " \\   / ";
					} else {
						String line1 = "   ";
						String line2 = "";
						String line3 = "   ";
						if (node.dirs[1]) {
							line1 = " O ";
						}
						if (node.dirs[2]) {
							line3 = " O ";
						}
						if (node.dirs[3]) {
							line2 += "O";
						} else {
							line2 += " ";
						}
						if (node.dirs[0]) {
							line2 += "O";
						} else {
							line2 += "+";
						}
						if (node.dirs[4]) {
							line2 += "O";
						} else {
							line2 += " ";
						}

						a1 += " /" + line1 + "\\ ";
						a2 += " |" + line2 + "| ";
						a3 += " \\" + line3 + "/ ";
					}
				}
				System.out.println(a1);
				System.out.println(a2);
				System.out.println(a3);
			}
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("flame life");
			MatrixUtility.OutputMatrix(flameLife);
			System.out.println("=========================================================================================");
			// System.out.println("=========================================================================================");
			System.out.println("board_ex & bomb_ex & flame_ex picture");
			BBMUtility.printBoard2(exmap.board, exmap.board, exmap.life, exmap.power);
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 最適アクションを選択する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		int action = 0;
		if (true) {
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// worstScoreを計算する。
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

			AgentEEE[] agentsNow = new AgentEEE[4];
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int type = map.getType(x, y);
					if (Constant.isAgent(type)) {
						AgentEEE aaa = new AgentEEE(x, y, type);
						agentsNow[aaa.agentID - 10] = aaa;
					}
				}
			}

			int numVisibleTeam = 0;
			int numVisibleEnemy = 0;
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE aaa = agentsNow[ai];
				if (aaa == null) continue;
				if (ai == me - 10) {
					numVisibleTeam++;
				} else if (ai == friend - 10) {
					numVisibleTeam++;
				} else {
					numVisibleEnemy++;
				}
			}

			int numAliveTeam = 0;
			int numAliveEnemy = 0;
			for (int ai = 0; ai < 4; ai++) {
				if (abs[ai].isAlive == false) continue;
				if (ai == me - 10) {
					numAliveTeam++;
				} else if (ai == friend - 10) {
					numAliveTeam++;
				} else {
					numAliveEnemy++;
				}
			}

			ScoreResult sr;
			// if (true) {
			if (numVisibleTeam + numVisibleEnemy >= 3) {
				WorstScoreEvaluator_mt worstScoreEvaluator_mt = new WorstScoreEvaluator_mt(param);
				worstScoreEvaluator_mt.Start(isCollapse, frame, me, friend, maxPower, abs, exmap, bombMap, flameLife, laps);
				while (true) {
					long timeNow = System.currentTimeMillis();
					long timeDel = timeNow - timeStartFunction;
					if (timeDel > 390) {
						break;
					}
					Thread.sleep(3);
				}
				worstScoreEvaluator_mt.Stop();
				sr = worstScoreEvaluator_mt.Get(me, friend);
			} else {
				WorstScoreEvaluator_2step_mt worstScoreEvaluator_2step_mt = new WorstScoreEvaluator_2step_mt(param);
				worstScoreEvaluator_2step_mt.Start(isCollapse, frame, me, friend, maxPower, abs, exmap, bombMap, flameLife, laps);
				while (true) {
					long timeNow = System.currentTimeMillis();
					long timeDel = timeNow - timeStartFunction;
					if (timeDel > 390) {
						break;
					}
					Thread.sleep(3);
				}
				worstScoreEvaluator_2step_mt.Stop();
				sr = worstScoreEvaluator_2step_mt.Get(me, friend);
			}

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// スコアに基づいてアクションを求める。
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

			action = actionEvaluator.ComputeOptimalAction(isCollapse, frame, me, friend, maxPower, abs2, exmap, bombMap, flameLife, lastLook, laps, sr);
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 過去フレーム情報を保存する。その他の後処理。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		mapsOld.addFirst(map);
		mapsOld.removeLast();

		exmapsOld.addFirst(exmap);
		exmapsOld.removeLast();

		frame++;

		if (false) {
			System.out.println("board_ex & bomb_ex & flame_ex picture");
			// BBMUtility.printBoard2(exmap.board, map.board, bomb_life, exmap.power);
			BBMUtility.printBoard2(exmap.board, exmap.board, exmap.life, exmap.power);
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			MatrixUtility.OutputMatrix(flameLife);
		}

		// if (GlobalParameter.randomness) {
		// if (GlobalParameter.rand.nextInt(5) == 0) {
		// action = GlobalParameter.rand.nextInt(6);
		// }
		// }

		if (me == 13) {
			int temp = 0;
			// Thread.sleep(1000);
		}

		return action;
	}
}
