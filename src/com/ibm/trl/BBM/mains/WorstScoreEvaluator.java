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

public class WorstScoreEvaluator {

	static final int LOGIC_ALL = 0;
	static final int LOGIC_MOVEONLY = 1;
	static final int LOGIC_STOPORBOMB = 2;
	static final int LOGIC_STOP = 3;
	static final int LOGIC_STOPORBOMB_IF_NONEIGHBOR = 4;
	static final int LOGIC_STOP_IF_NONEIGHBOR = 5;

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;
	static final ForwardModel fm = new ForwardModel();

	class OperationSet {
		Pack pack;
		Operation[] ops;

		public OperationSet(Pack pack, Operation[] ops) {
			this.pack = pack;
			this.ops = ops;
		}
	}

	class Operation {
		int action;
		int instruction;

		public Operation(int action, int instruction) {
			this.action = action;
			this.instruction = instruction;
		}
	}

	public double[][][][] Do(int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife) throws Exception {
		List<BombTracker.Node> nodes = new ArrayList<BombTracker.Node>();
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				BombTracker.Node node = bombMap[x][y];
				if (node == null) continue;
				nodes.add(node);
			}
		}

		List<Pack> packList = new ArrayList<Pack>();
		LinkedList<BombEEE> bbbs = new LinkedList<BombEEE>();
		collectPackInitStates(0, me, maxPower, abs, map, nodes, flameLife, bbbs, packList);

		int numMax = 120;
		List<OperationSet> opsetList = new ArrayList<OperationSet>();
		if (true) {
			for (Pack pack : packList) {
				List<OperationSet> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_ALL, LOGIC_ALL);
				opsetList.addAll(opsetList2);
			}
			System.out.println(opsetList.size());
		}
		if (opsetList.size() > numMax) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_ALL, LOGIC_STOPORBOMB_IF_NONEIGHBOR);
				opsetList.addAll(opsetList2);
			}
			System.out.println(opsetList.size());
		}
		if (opsetList.size() > numMax) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_ALL, LOGIC_STOP_IF_NONEIGHBOR);
				opsetList.addAll(opsetList2);
			}
			System.out.println(opsetList.size());
		}
		if (opsetList.size() > numMax) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_ALL, LOGIC_STOPORBOMB);
				opsetList.addAll(opsetList2);
			}
			System.out.println(opsetList.size());
		}
		if (opsetList.size() > numMax) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_ALL, LOGIC_STOP);
				opsetList.addAll(opsetList2);
			}
			System.out.println(opsetList.size());
		}

		// リストアップした初期条件でシミュレーションを実施する。

		// action * action * ai * (ave, min, max, num)
		double[][][][] scores = new double[6][6][4][4];
		for (int a = 0; a < 6; a++) {
			for (int b = 0; b < 6; b++) {
				for (int ai = 0; ai < 4; ai++) {
					scores[a][b][ai][0] = Double.NEGATIVE_INFINITY;
					scores[a][b][ai][1] = Double.POSITIVE_INFINITY;
					scores[a][b][ai][2] = Double.NEGATIVE_INFINITY;
				}
			}
		}

		if (true) {
			WorstScoreEvaluatorSingle wses = new WorstScoreEvaluatorSingle();
			for (OperationSet opset : opsetList) {
				Pack packNow = opset.pack;
				int firstAction = opset.ops[me - 10].action;
				int firstAction2 = opset.ops[friend - 10].action;
				int[] actions = new int[] { opset.ops[0].action, opset.ops[1].action, opset.ops[2].action, opset.ops[3].action };
				int[] instructions = new int[] { opset.ops[0].instruction, opset.ops[1].instruction, opset.ops[2].instruction, opset.ops[3].instruction };
				Pack packNext = fm.Step(packNow.board, packNow.flameLife, packNow.abs, packNow.sh, actions);
				double[][] temp2 = wses.Do2(packNow, packNext, instructions);

				// System.out.println(firstAction + ", " + temp2[3][0] + ", " + temp2[3][1]);

				for (int ai = 0; ai < 4; ai++) {
					double sss = temp2[ai][0];
					double num = temp2[ai][1];
					if (num == 0) continue;
					scores[firstAction][firstAction2][ai][0] = BBMUtility.add_log(scores[firstAction][firstAction2][ai][0], sss);
					if (sss < scores[firstAction][firstAction2][ai][1]) {
						scores[firstAction][firstAction2][ai][1] = sss;
					}
					if (sss > scores[firstAction][firstAction2][ai][2]) {
						scores[firstAction][firstAction2][ai][2] = sss;
					}
					scores[firstAction][firstAction2][ai][3] += num;
				}
			}
		}

		return scores;
	}

	private void collectPackInitStates(int index, int me, int maxPower, Ability[] abs, MapInformation map, List<BombTracker.Node> nodes, MyMatrix flameLife, LinkedList<BombEEE> bbbs,
			List<Pack> packList) throws Exception {
		if (index == nodes.size()) {
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			//
			// 基本データを作る。
			//
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			Pack packNow;
			if (true) {
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

			packList.add(packNow);
		} else {
			BombTracker.Node node = nodes.get(index);
			for (int dir = 0; dir < 5; dir++) {
				if (node.dirs[dir]) {
					BombEEE bbb = new BombEEE(node.x, node.y, -1, node.life, dir, node.power);
					bbbs.addLast(bbb);
					collectPackInitStates(index + 1, me, maxPower, abs, map, nodes, flameLife, bbbs, packList);
					bbbs.removeLast();
				}
			}
		}
	}

	private List<OperationSet> collectOperationSet(int me, int friend, Pack pack, int frinedLogic, int enemyLogic) {

		AgentEEE[] agents = new AgentEEE[4];
		for (AgentEEE aaa : pack.sh.getAgentEntry()) {
			agents[aaa.agentID - 10] = aaa;
		}

		// 2hop以内に他のAgentか爆弾があるかどうか調べる。
		boolean[] existNear = new boolean[4];
		for (int ai = 0; ai < 4; ai++) {
			AgentEEE aaa = agents[ai];
			if (aaa == null) continue;
			boolean find = false;
			if (find == false) {
				for (AgentEEE aaa2 : pack.sh.getAgentEntry()) {
					if (aaa2.agentID - 10 == ai) continue;
					int dis = Math.abs(aaa.x - aaa2.x) + Math.abs(aaa.y - aaa2.y);
					if (dis <= 2) {
						find = true;
						break;
					}
				}
			}
			if (find == false) {
				for (BombEEE bbb : pack.sh.getBombEntry()) {
					int dis = Math.abs(aaa.x - bbb.x) + Math.abs(aaa.y - bbb.y);
					if (dis > 0 && dis <= 2) {
						find = true;
						break;
					}
				}
			}
			existNear[ai] = find;
		}

		List<OperationSet> opsetList = new ArrayList<OperationSet>();
		for (int firstAction = 0; firstAction < 6; firstAction++) {
			boolean[][] actionExecuteFlag = new boolean[4][6];
			int[][] instructionSet = new int[4][6];
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) {
					actionExecuteFlag[ai][firstAction] = true;
					instructionSet[ai][firstAction] = WorstScoreEvaluatorSingle.FIRSTACTION_BOARD;
				} else {
					int logic;
					if (ai == friend - 10) {
						logic = frinedLogic;
					} else {
						logic = enemyLogic;
					}

					if (agents[ai] == null) {
						actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false };
						instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_ALLMOVE, -1, -1, -1, -1, -1 };
					} else if (logic == LOGIC_ALL) {
						actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true };
						instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD,
								WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD };
					} else if (logic == LOGIC_MOVEONLY) {
						actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, false };
						instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD,
								WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, -1 };
					} else if (logic == LOGIC_STOPORBOMB) {
						actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, true };
						instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_ALLMOVE, -1, -1, -1, -1, WorstScoreEvaluatorSingle.FIRSTACTION_STOP };
					} else if (logic == LOGIC_STOP) {
						actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false };
						instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_ALLMOVE, -1, -1, -1, -1, -1 };
					} else if (logic == LOGIC_STOPORBOMB_IF_NONEIGHBOR) {
						if (existNear[ai]) {
							actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true };
							instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD,
									WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD };
						} else {
							actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, true };
							instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_ALLMOVE, -1, -1, -1, -1, WorstScoreEvaluatorSingle.FIRSTACTION_STOP };
						}
					} else if (logic == LOGIC_STOP_IF_NONEIGHBOR) {
						if (existNear[ai]) {
							actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true };
							instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD,
									WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD, WorstScoreEvaluatorSingle.FIRSTACTION_BOARD };
						} else {
							actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false };
							instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.FIRSTACTION_ALLMOVE, -1, -1, -1, -1, -1 };
						}
					}
				}
			}

			for (int a = 0; a < 6; a++) {
				if (actionExecuteFlag[0][a] == false) continue;
				for (int b = 0; b < 6; b++) {
					if (actionExecuteFlag[1][b] == false) continue;
					for (int c = 0; c < 6; c++) {
						if (actionExecuteFlag[2][c] == false) continue;
						for (int d = 0; d < 6; d++) {
							if (actionExecuteFlag[3][d] == false) continue;

							Operation[] ops = new Operation[4];
							ops[0] = new Operation(a, instructionSet[0][a]);
							ops[1] = new Operation(b, instructionSet[1][b]);
							ops[2] = new Operation(c, instructionSet[2][c]);
							ops[3] = new Operation(d, instructionSet[3][d]);

							OperationSet opset = new OperationSet(pack, ops);
							opsetList.add(opset);
						}
					}
				}
			}
		}

		// 実行できな行動を減らしてみる。
		{
			List<OperationSet> opsetList2 = new ArrayList<OperationSet>();
			for (OperationSet opset : opsetList) {
				boolean executable = true;
				for (int ai = 0; ai < 4; ai++) {
					AgentEEE aaa = agents[ai];
					Ability ab = pack.abs[ai];

					if (aaa == null) continue;

					Operation op = opset.ops[ai];
					int action = op.action;
					int x = aaa.x;
					int y = aaa.y;
					if (action == 1) {
						x = x - 1;
					} else if (action == 2) {
						x = x + 1;
					} else if (action == 3) {
						y = y - 1;
					} else if (action == 4) {
						y = y + 1;
					}

					if (x < 0 || x >= numField || y < 0 || y >= numField) {
						executable = false;
						break;
					}
					int type = (int) pack.board.data[x][y];
					if (Constant.isWall(type)) {
						executable = false;
						break;
					}

					if (action == 5) {
						if (ab.numBombHold == 0 || type == Constant.Bomb) {
							executable = false;
							break;
						}
					}
				}
				if (executable == false) continue;
				opsetList2.add(opset);
			}
			System.out.println("アクションリストの数を減らしてみる。。" + opsetList.size() + "→" + opsetList2.size());
			opsetList = opsetList2;
		}

		return opsetList;
	}
}
