package com.ibm.trl.BBM.mains;

import java.io.Serializable;
import java.util.LinkedList;

import com.ibm.trl.BBM.mains.BombTracker.Node;

import ibm.ANACONDA.Core.MyMatrix;

public class Agent {
	static final boolean verbose = GlobalParameter.verbose;
	static final int timeSampling = GlobalParameter.timeStampling;
	static final int numField = GlobalParameter.numField;
	static final int numPast = 20;

	int me;
	int friend;
	int frame = 0;
	Ability[] abs = new Ability[4];
	LinkedList<MapInformation> mapsOld = new LinkedList<MapInformation>();
	LinkedList<MapInformation> mapsOld_ex = new LinkedList<MapInformation>();
	MyMatrix board_memo = new MyMatrix(numField, numField, Constant.Rigid);
	int maxPower = 2;
	int numItemGet = 0;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	WorstScoreEvaluator worstScoreEvaluator = new WorstScoreEvaluator();
	ActionEvaluator actionEvaluator = new ActionEvaluator();

	static public class Ability implements Serializable {
		private static final long serialVersionUID = 372642396371084459L;
		public boolean isAlive = true;
		public int numMaxBomb = 1;
		public int strength = 2;
		public boolean kick = false;
		public int numBombHold = 1;
		public boolean justBombed = false;

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

		@Override
		public String toString() {
			String line = String.format("isAlive=%5b, hold/max=%2d/%2d, strength=%2d, kick=%5b, justBombd=%5b\n", isAlive, numBombHold, numMaxBomb, strength, kick, justBombed);
			return line;
		}

		public boolean equals(Ability a) {
			if (this.isAlive == a.isAlive) {
				if (this.numMaxBomb == a.numMaxBomb) {
					if (this.strength == a.strength) {
						if (this.kick == a.kick) {
							if (this.numBombHold == a.numBombHold) { return true; }
						}
					}
				}
			}
			return false;
		}
	}

	Agent(int me) throws Exception {
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
	}

	public void episode_end(int reward) throws Exception {
		System.out.println("episode_end, reward = " + reward + ", " + frame);

		if (true) {
			GlobalParameter.FinishOneEpisode(me, frame, reward, numItemGet);
		}

		// 殺された場合（reward==-1 && frame!=801）、最後の20ステップの盤面遷移を出力する。
		if (reward == -1 && frame != 801) {
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			for (MapInformation map : mapsOld_ex) {
				System.out.println("=========================================================================================");
				BBMUtility.printBoard2(map.board, map.board, map.life, map.power);
			}
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
			System.out.println("=========================================================================================");
		}
	}

	public int act(int xMe, int yMe, int ammo, int blast_strength, boolean can_kick, MyMatrix board, MyMatrix bomb_blast_strength, MyMatrix bomb_life, MyMatrix alive, MyMatrix enemies)
			throws Exception {

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 盤面をログに出力してみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (verbose) {
			// Thread.sleep(1000);
			// System.out.println("=========================================================================================");
			// System.out.println("=========================================================================================");
			// System.out.println("=========================================================================================");
			// System.out.println("=========================================================================================");
			// System.out.println("=========================================================================================");
			// System.out.println("board picture");
			// BBMUtility.printBoard2(board, board, bomb_life, bomb_blast_strength);
			// System.out.println("=========================================================================================");
			// System.out.println("=========================================================================================");
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本情報のアップデート
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		MapInformation map = new MapInformation(board, bomb_blast_strength, bomb_life);

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

		// 友達を探す
		// TODO ハードコードしなくてもどこかに情報あるはず。
		if (true) {
			if (me == 10) {
				friend = 12;
			} else if (me == 11) {
				friend = 13;
			} else if (me == 12) {
				friend = 10;
			} else if (me == 13) {
				friend = 11;
			}
		}

		// エージェントのアイテム取得状況を観測し、Abilityをトラッキングする。
		// Fogの中でアイテム取得されるケースがあるので、自分以外はトラック漏れが多々ある。
		{
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

		// エージェントが爆弾を置く瞬間を発見できたら、Abilityのstrengthを更新する。
		// TODO Woodが全て破壊されている状況であれば、Strength確定。
		if (true) {
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int type = map.getType(x, y);
					int life = map.getLife(x, y);
					int power = map.getPower(x, y);
					if (Constant.isAgent(type) && life == 9) {
						abs[type - 10].numBombHold--;
						abs[type - 10].strength = power;
					}
				}
			}
		}

		// 自分が最後に爆弾を設置した時刻から9フレーム以上経過していたら、所有爆弾数はかならずMAXになっている。
		abs[me - 10].numBombHold = ammo;

		// TODO Abilityを出力してみる。
		if (false) {
			for (int ai = 0; ai < 4; ai++) {
				System.out.print(ai + ", " + abs[ai]);
			}
		}

		// 爆弾のmaxPowerを保存する。自分以外のエージェントのアイテム取得状況が完全観測できないので、maxPowerで最悪ケースを押さえるため。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int power = (int) bomb_blast_strength.data[x][y];
				if (power == 0) continue;
				if (power > maxPower) maxPower = power;
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
					board_memo.data[y][x] = Constant.Rigid;
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
		MapInformation map_ex;
		{
			MyMatrix board_ex = new MyMatrix(board);
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (board_ex.data[x][y] == Constant.Fog) {
						board_ex.data[x][y] = board_memo.data[x][y];
					}
				}
			}
			map_ex = new MapInformation(board_ex, map.power, map.life);

			// TODO 出力してみる。
			if (verbose) {
				System.out.println("board_ex picture");
				BBMUtility.printBoard2(board_ex, board, bomb_life, bomb_blast_strength);
				System.out.println("=========================================================================================");
				System.out.println("=========================================================================================");
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 爆弾の動きをトラッキングする。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		Node[][] bombMap = BombTracker.computeBombMap(map, mapsOld);

		// TODO 爆弾の移動方向で複数候補があるときは、とりあえず停止してることにする。
		if (false) {
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					BombTracker.Node node = bombMap[x][y];
					if (node == null) continue;
					if (node.dirs[0]) {
						for (int i = 1; i < 5; i++) {
							node.dirs[i] = false;
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// FlameのLifeを計算する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		MyMatrix flameLife = new MyMatrix(numField, numField);
		{
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (board.data[x][y] == Constant.Flames) {
						// 今のBoardがFlamesだった場合、Flameが出現する瞬間を見つけて、残りLifeを計算する。
						int life = 3;
						for (int t = 0; t < 3; t++) {
							MapInformation mapPre = mapsOld.get(t);
							int type2 = mapPre.getType(x, y);
							if (type2 != Constant.Flames) {
								life = 3 - t;
								break;
							}
						}
						// TODO とりあえず炎は全部Life3にする。
						// life = 3;
						flameLife.data[x][y] = life;
					}
				}
			}
		}

		// TODO 保守的にやるなら、Fogで見積もれないFlameはLife=3にすると良いかもしれない。

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 最適アクションを選択する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		int action;
		if (true) {
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// worstScoreを計算する。
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			double[] worstScores = worstScoreEvaluator.Do(me, maxPower, abs, map_ex, bombMap, flameLife);

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			// スコアに基づいてアクションを求める。
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			Ability[] abs2 = new Ability[4];
			for (int ai = 0; ai < 4; ai++) {
				abs2[ai] = new Ability(abs[ai]);
				if (ai + 10 == me) continue;
				abs2[ai].kick = true;
				abs2[ai].numMaxBomb = 4;
				abs2[ai].numBombHold = 3;
				abs2[ai].strength = maxPower;
			}
			action = actionEvaluator.ComputeOptimalAction(me, friend, map_ex, abs2, worstScores);
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 過去フレーム情報を保存する。その他の後処理。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		mapsOld.addFirst(map);
		mapsOld.removeLast();

		mapsOld_ex.addLast(map_ex);
		if (mapsOld_ex.size() > 20) {
			mapsOld_ex.removeFirst();
		}

		frame++;

		return action;
	}
}
