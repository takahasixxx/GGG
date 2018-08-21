package com.ibm.trl.BBM.mains;

import java.io.Serializable;
import java.util.LinkedList;

import com.ibm.trl.BBM.mains.BombTracker.Node;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;

import ibm.ANACONDA.Core.MyMatrix;

public class Agent {
	static int numField = GlobalParameter.numField;
	static int numPast = 20;
	int me;

	LinkedList<MyMatrix> boardOld = new LinkedList<MyMatrix>();
	LinkedList<MyMatrix> powerOld = new LinkedList<MyMatrix>();
	LinkedList<MyMatrix> lifeOld = new LinkedList<MyMatrix>();
	LinkedList<Node[][]> bombMapOld = new LinkedList<Node[][]>();
	Ability[] abs = new Ability[4];

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	static BinaryTree bt = new BinaryTree(0, null);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	static ActionEvaluator actionEvaluator = new ActionEvaluator(numField);

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	OptimalActionFinder oaf = new OptimalActionFinder();

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

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

	public void episode_end(int reward) throws Exception {
		System.out.println("episode_end, reward = " + reward);
		actionEvaluator.FinishOneEpoch(me, reward);
	}

	public int act(int xMe, int yMe, int ammo, int blast_strength, boolean can_kick, MyMatrix board, MyMatrix bomb_blast_strength, MyMatrix bomb_life, MyMatrix alive, MyMatrix enemies)
			throws Exception {
		if (true) {
			// Thread.sleep(1000);
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("==========================================");
			System.out.println("board picture");
			BBMUtility.printBoard2(board, bomb_life, bomb_blast_strength);
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
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (board_pre.data[x][y] == 6 && board.data[x][y] >= 10 && board.data[x][y] <= 13) {
						int id = (int) (board.data[x][y] - 10);
						abs[id].numMaxBomb++;
						abs[id].numBombHold++;
					} else if (board_pre.data[x][y] == 7 && board.data[x][y] >= 10 && board.data[x][y] <= 13) {
						int id = (int) (board.data[x][y] - 10);
						abs[id].strength++;
					} else if (board_pre.data[x][y] == 8 && board.data[x][y] >= 10 && board.data[x][y] <= 13) {
						int id = (int) (board.data[x][y] - 10);
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
		Node[][] bombMap = BombTracker.computeBombMap(abs, board, bomb_life, bomb_blast_strength, boardOld, lifeOld, powerOld, bombMapOld);

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// うまく分岐させながら、BombやAgentが存在しうる状態を列挙して、行動選択に活用する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// TODO
		int action = -1;
		if (false) {
			// ForwardModel ft = new ForwardModel(numField);
			// int action = ft.Compute(board, bombMap, abs);
			// if (action != -1) return action;

			// actionEvaluator.Sample(board, bombMap, abs);

			action = actionEvaluator.ComputeOptimalAction(me, board, bombMap, abs);
			MyMatrix board_pre = boardOld.get(0);
			actionEvaluator.RecordKPI(me, board, board_pre);
		}

		if (true) {
			StatusHolder sh = new StatusHolder(numField);
			{
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						int type = (int) board.data[x][y];
						if (Constant.isAgent(type)) {
							sh.setAgent(x, y, type);
						}
					}
				}

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						Node node = bombMap[x][y];
						if (node == null) continue;
						if (node.type == Constant.Bomb) {
							sh.setBomb(x, y, node.owner, node.lifeBomb, node.moveDirection, node.power);
						} else if (node.type == Constant.Flames) {
							sh.setFlameCenter(x, y, node.lifeFlameCenter, node.power);
						}
					}
				}
			}

			Pack pack = new Pack(board, abs, sh);

			SafetyScoreEvaluator.set(pack, me);
			Thread.sleep(90);
			double[][] safetyScore = SafetyScoreEvaluator.getLatestSafetyScore();
			int tryCounter = SafetyScoreEvaluator.getTryCounter();
			action = oaf.findOptimalAction(pack, me, safetyScore);
			SafetyScoreEvaluator.set(null, -1);
			System.out.println("tryCounter = " + tryCounter);
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// とりあえず予測モデルを作ってみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (false) {
			bt.computeFeature(yMe, board, boardOld, lifeOld, powerOld);
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

		return action;
	}
}
