package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class WorstScoreEvaluator {

	static final int LOGIC_ALL = 0;
	static final int LOGIC_MOVEONLY_IF_FAR = 1;
	static final int LOGIC_MOVEONLY = 2;
	static final int LOGIC_STOP_IF_FAR = 3;
	static final int LOGIC_STOP = 4;

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;
	static final ForwardModel fm = new ForwardModel();

	static final int numMaxCase = 200000000;

	class OperationSet {
		Pack packNow;
		Pack packNext;
		List<int[]> actionsList = new ArrayList<int[]>();
		int[] instructions;

		public OperationSet(Pack packNow, Pack packNext, int[] instructions, int[] actions) {
			this.packNow = packNow;
			this.packNext = packNext;
			this.instructions = instructions;
			actionsList.add(actions);
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

		List<OperationSet[]> opsetList = new ArrayList<OperationSet[]>();
		if (true) {
			for (Pack pack : packList) {
				List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_ALL);
				opsetList.addAll(opsetList2);
			}
			System.out.println("opsetList.size()=" + opsetList.size());
		}

		if (opsetList.size() > numMaxCase) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_MOVEONLY_IF_FAR);
				opsetList.addAll(opsetList2);
			}
			System.out.println("opsetList.size()=" + opsetList.size());
		}

		if (opsetList.size() > numMaxCase) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_MOVEONLY);
				opsetList.addAll(opsetList2);
			}
			System.out.println("opsetList.size()=" + opsetList.size());
		}

		if (opsetList.size() > numMaxCase) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_STOP_IF_FAR);
				opsetList.addAll(opsetList2);
			}
			System.out.println("opsetList.size()=" + opsetList.size());
		}

		if (opsetList.size() > numMaxCase) {
			opsetList.clear();
			for (Pack pack : packList) {
				List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_STOP);
				opsetList.addAll(opsetList2);
			}
			System.out.println("opsetList.size()=" + opsetList.size());
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

		// シングルスレッドバージョン
		if (false) {
			WorstScoreEvaluatorSingle wses = new WorstScoreEvaluatorSingle();
			for (OperationSet[] opset : opsetList) {

				int numt = 13;

				Pack[] packs = new Pack[numt];
				int[][] instructions = new int[numt][4];
				if (true) {
					for (int t = 0; t < numt; t++) {
						for (int ai = 0; ai < 4; ai++) {
							instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
						}
					}

					for (int i = 0; i < opset.length; i++) {
						packs[i] = opset[i].packNow;
						instructions[i] = opset[i].instructions;
					}

					packs[opset.length] = opset[opset.length - 1].packNext;
				}

				double[][] score_temp = wses.Do3(packs, instructions);
				for (int[] actions : opset[0].actionsList) {
					int firstAction = actions[me - 10];
					int secondAction = 0;
					for (int ai = 0; ai < 4; ai++) {
						double sss = score_temp[ai][0];
						double num = score_temp[ai][1];
						if (num == 0) continue;

						// スコア平均値
						scores[firstAction][secondAction][ai][0] = BBMUtility.add_log(scores[firstAction][secondAction][ai][0], sss);

						// スコア最小値
						if (sss < scores[firstAction][secondAction][ai][1]) {
							scores[firstAction][secondAction][ai][1] = sss;
						}

						// スコア最大値
						if (sss > scores[firstAction][secondAction][ai][2]) {
							scores[firstAction][secondAction][ai][2] = sss;
						}

						// 計測回数
						scores[firstAction][secondAction][ai][3] += num;
					}
				}
			}
		}

		// マルチスレッドバージョン
		if (true) {
			List<Future<?>> list = new ArrayList<Future<?>>();
			for (OperationSet[] opset : opsetList) {
				ScoreComputingTask ggg = new ScoreComputingTask(me, friend, opset, scores);
				Future<?> future = GlobalParameter.executor.submit(ggg);
				list.add(future);
			}
			for (Future<?> future : list) {
				future.get();
			}
		}

		return scores;
	}

	public static class ScoreComputingTask implements Runnable {
		int me;
		int friend;
		OperationSet opset[];
		double[][][][] scores;

		public ScoreComputingTask(int me, int friend, OperationSet[] opset, double[][][][] scores) {
			this.me = me;
			this.friend = friend;
			this.opset = opset;
			this.scores = scores;
		}

		@Override
		public void run() {
			try {
				WorstScoreEvaluatorSingle wses = new WorstScoreEvaluatorSingle();

				int numt = 13;

				Pack[] packs = new Pack[numt];
				int[][] instructions = new int[numt][4];
				if (true) {
					for (int t = 0; t < numt; t++) {
						for (int ai = 0; ai < 4; ai++) {
							instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
						}
					}

					for (int i = 0; i < opset.length; i++) {
						packs[i] = opset[i].packNow;
						instructions[i] = opset[i].instructions;
					}

					packs[opset.length] = opset[opset.length - 1].packNext;
				}

				double[][] score_temp = wses.Do3(packs, instructions);
				synchronized (scores) {
					for (int[] actions : opset[0].actionsList) {
						int firstAction = actions[me - 10];
						int secondAction = 0;
						for (int ai = 0; ai < 4; ai++) {
							double sss = score_temp[ai][0];
							double num = score_temp[ai][1];
							if (num == 0) continue;

							// スコア平均値
							scores[firstAction][secondAction][ai][0] = BBMUtility.add_log(scores[firstAction][secondAction][ai][0], sss);

							// スコア最小値
							if (sss < scores[firstAction][secondAction][ai][1]) {
								scores[firstAction][secondAction][ai][1] = sss;
							}

							// スコア最大値
							if (sss > scores[firstAction][secondAction][ai][2]) {
								scores[firstAction][secondAction][ai][2] = sss;
							}

							// 計測回数
							scores[firstAction][secondAction][ai][3] += num;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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

				StatusHolder sh = new StatusHolder();
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

	private List<OperationSet[]> collectOperationSet(int me, int friend, Pack packNow, int logic) throws Exception {

		double disFar = 3;

		boolean[][] actionExecuteFlag = new boolean[4][7];
		int[][] instructionSet = new int[4][7];
		for (int ai = 0; ai < 4; ai++) {
			for (int i = 0; i < 7; i++) {
				instructionSet[ai][i] = WorstScoreEvaluatorSingle.INSTRUCTION_BOARD;
			}
		}
		if (logic == LOGIC_ALL) {
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) {
					actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false, false };
				} else if (ai == me - 10) {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true, false };
				} else {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true, false };
				}
			}
		} else if (logic == LOGIC_MOVEONLY_IF_FAR) {
			boolean[] far = new boolean[4];
			AgentEEE agentMe = packNow.sh.getAgent(me);
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				AgentEEE agent = packNow.sh.getAgent(ai + 10);
				if (agent == null) continue;
				double dis = Math.abs(agentMe.x - agent.x) + Math.abs(agentMe.y - agent.y);
				if (dis > disFar) {
					far[ai] = true;
				} else {
					far[ai] = false;
				}
			}

			for (int ai = 0; ai < 4; ai++) {
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) {
					actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false, false };
				} else if (ai == me - 10) {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true, false };
				} else {
					if (far[ai]) {
						actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, false, false };
					} else {
						actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true, false };
					}
				}
			}
		} else if (logic == LOGIC_MOVEONLY) {
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) {
					actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false, false };
				} else if (ai == me - 10) {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true, false };
				} else {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, false, false };
				}
			}
		} else if (logic == LOGIC_STOP_IF_FAR) {
			boolean[] far = new boolean[4];
			AgentEEE agentMe = packNow.sh.getAgent(me);
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				AgentEEE agent = packNow.sh.getAgent(ai + 10);
				if (agent == null) continue;
				double dis = Math.abs(agentMe.x - agent.x) + Math.abs(agentMe.y - agent.y);
				if (dis > disFar) {
					far[ai] = true;
				} else {
					far[ai] = false;
				}
			}

			for (int ai = 0; ai < 4; ai++) {
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) {
					actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false, false };
				} else if (ai == me - 10) {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true, false };
				} else {
					if (far[ai]) {
						actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false, false };
						instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE, -1, -1, -1, -1, -1, -1 };
					} else {
						actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, false, false };
					}
				}
			}
		} else if (logic == LOGIC_STOP) {
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) {
					actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false, false };
				} else if (ai == me - 10) {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true, false };
				} else {
					actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false, false };
					instructionSet[ai] = new int[] { WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE, -1, -1, -1, -1, -1, -1 };
				}
			}
		}

		List<OperationSet[]> opsetList = new ArrayList<OperationSet[]>();
		for (int a = 0; a < 6; a++) {
			if (actionExecuteFlag[0][a] == false) continue;
			for (int b = 0; b < 6; b++) {
				if (actionExecuteFlag[1][b] == false) continue;
				for (int c = 0; c < 6; c++) {
					if (actionExecuteFlag[2][c] == false) continue;
					for (int d = 0; d < 6; d++) {
						if (actionExecuteFlag[3][d] == false) continue;

						int[] actions = new int[] { a, b, c, d };
						Pack packNext = fm.Step(packNow, actions);

						if (true) {
							OperationSet[] find = null;
							for (OperationSet[] opsets : opsetList) {
								Pack packTarget = opsets[0].packNext;
								if (packNext.equals(packTarget) == true) {
									find = opsets;
									break;
								}
							}
							if (find != null) {
								find[0].actionsList.add(actions);
								continue;
							}
						}

						int[] instructions = new int[4];
						for (int ai = 0; ai < 4; ai++) {
							instructions[ai] = instructionSet[ai][actions[ai]];
						}

						OperationSet opset = new OperationSet(packNow, packNext, instructions, actions);

						OperationSet[] opsets = new OperationSet[1];
						opsets[0] = opset;
						opsetList.add(opsets);
					}
				}
			}
		}

		return opsetList;
	}
}
