/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.LastAgentPosition;
import com.ibm.trl.BBM.mains.Agent.ModelParameter;
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

	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;

	// static final int numMaxCase = 150;
	static final int numMaxCase = 1500000;

	ForwardModel fm = new ForwardModel();
	ModelParameter param;
	WorstScoreEvaluatorSingle wses;

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

	public WorstScoreEvaluator(ModelParameter param) {
		this.param = param;
		this.wses = new WorstScoreEvaluatorSingle(param);
	}

	static public class ScoreEntry {
		double max = Double.NEGATIVE_INFINITY;
		double min = Double.POSITIVE_INFINITY;
		double sum = 0;
		double num = 0;

		public ScoreEntry() {

		}

		public ScoreEntry(ScoreEntry s) {
			this.max = s.max;
			this.min = s.min;
			this.sum = s.sum;
			this.num = s.num;
		}
	}

	static public class ScoreResult {
		ScoreEntry[][] singleScore = new ScoreEntry[6][4];
		ScoreEntry[][][] pairScore = new ScoreEntry[6][6][4];

		public ScoreResult() {
			for (int a = 0; a < 6; a++) {
				for (int ai = 0; ai < 4; ai++) {
					singleScore[a][ai] = new ScoreEntry();
				}
			}
			for (int a = 0; a < 6; a++) {
				for (int b = 0; b < 6; b++) {
					for (int ai = 0; ai < 4; ai++) {
						pairScore[a][b][ai] = new ScoreEntry();
					}
				}
			}
		}

		public ScoreResult(ScoreResult sr) {
			for (int a = 0; a < 6; a++) {
				for (int ai = 0; ai < 4; ai++) {
					singleScore[a][ai] = new ScoreEntry(sr.singleScore[a][ai]);
				}
			}
			for (int a = 0; a < 6; a++) {
				for (int b = 0; b < 6; b++) {
					for (int ai = 0; ai < 4; ai++) {
						pairScore[a][b][ai] = new ScoreEntry(sr.pairScore[a][b][ai]);
					}
				}
			}
		}
	}

	public ScoreResult Do(boolean collapse, int frame, int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife, LastAgentPosition[] laps)
			throws Exception {

		List<BombTracker.Node> nodes = new ArrayList<BombTracker.Node>();
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				BombTracker.Node node = bombMap[x][y];
				if (node == null) continue;
				nodes.add(node);
			}
		}

		List<Pack> initialPackList = new ArrayList<Pack>();
		LinkedList<BombEEE> bbbs = new LinkedList<BombEEE>();
		// collectPackInitStates(0, me, maxPower, abs, map, nodes, flameLife, bbbs, packList);
		collectPackInitStates_ALLSTOP(0, me, maxPower, abs, map, nodes, flameLife, bbbs, initialPackList);
		System.out.println("packList.size()=" + initialPackList.size());

		boolean[] isVisible = new boolean[4];
		int numVisibleAgent = 0;
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = map.getType(x, y);
				if (Constant.isAgent(type)) {
					isVisible[type - 10] = true;
					numVisibleAgent++;
				}
			}
		}

		List<OperationSet[]> opsetList = new ArrayList<OperationSet[]>();

		// 全サンプル
		if (true) {
			for (Pack packInit : initialPackList) {
				List<OperationSet[]> opsetList2 = collectOperationSet(collapse, frame, me, friend, packInit, LOGIC_ALL);
				opsetList.addAll(opsetList2);
			}
			System.out.println("opsetList.size()=" + opsetList.size());
		}

		// 観測しているエージェントの数によって、サンプル数を調整するバージョン。
		if (false) {
			if (numVisibleAgent <= 3) {
				if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
					opsetList.clear();
					for (Pack packInit : initialPackList) {
						List<OperationSet[]> opsetList2 = collectOperationSet(collapse, frame, me, friend, packInit, LOGIC_ALL);
						opsetList.addAll(opsetList2);
					}
					System.out.println("opsetList.size()=" + opsetList.size());
				}

				if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
					opsetList.clear();
					for (Pack pack : initialPackList) {
						List<OperationSet[]> opsetList2 = collectOperationSet(collapse, frame, me, friend, pack, LOGIC_MOVEONLY);
						opsetList.addAll(opsetList2);
					}
					System.out.println("opsetList.size()=" + opsetList.size());
				}

				if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
					opsetList.clear();
					for (Pack pack : initialPackList) {
						List<OperationSet[]> opsetList2 = collectOperationSet(collapse, frame, me, friend, pack, LOGIC_STOP_IF_FAR);
						opsetList.addAll(opsetList2);
					}
					System.out.println("opsetList.size()=" + opsetList.size());
				}

			} else {
				if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
					opsetList.clear();
					for (Pack pack : initialPackList) {
						List<OperationSet[]> opsetList2 = collectOperationSet(collapse, frame, me, friend, pack, LOGIC_MOVEONLY);
						opsetList.addAll(opsetList2);
					}
					System.out.println("opsetList.size()=" + opsetList.size());
				}

				if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
					opsetList.clear();
					for (Pack pack : initialPackList) {
						List<OperationSet[]> opsetList2 = collectOperationSet(collapse, frame, me, friend, pack, LOGIC_STOP_IF_FAR);
						opsetList.addAll(opsetList2);
					}
					System.out.println("opsetList.size()=" + opsetList.size());
				}
			}
		}

		// リストアップした初期条件でシミュレーションを実施する。
		ScoreResult sr = new ScoreResult();

		// シングルスレッドバージョン
		if (true) {
			int numt = 11;

			for (OperationSet[] opset : opsetList) {

				int[][] instructions = new int[numt][4];
				for (int ai = 0; ai < 4; ai++) {
					for (int t = 0; t < numt; t++) {
						instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
					}
				}

				Pack[] packs = new Pack[numt];
				packs[0] = opset[opset.length - 1].packNext;

				double[][] score_temp = wses.Do3_HighSpeed(collapse, frame + 1, me, friend, abs, packs, null, instructions);

				// TODO 通常版とハイスピード版の突合デバッグ用。
				if (false) {
					double[][] temp1 = wses.Do3(collapse, frame, me, friend, abs, packs, null, instructions);
					double[][] temp2 = wses.Do3_HighSpeed(collapse, frame, me, friend, abs, packs, null, instructions);
					for (int ai = 0; ai < 4; ai++) {
						for (int i = 0; i < 2; i++) {
							if (temp1[ai][i] != temp2[ai][i]) {
								System.out.println("ERRORRRRRRRRRRRRRRR!!!!!!!!!!!!!!!!!!!!!");
								System.exit(0);
							}
						}
					}
				}

				for (int[] actions : opset[0].actionsList) {

					int actionMe = actions[me - 10];
					int actionFriend = actions[friend - 10];

					for (int ai = 0; ai < 4; ai++) {
						if (isVisible[ai] == false) continue;
						double sss = score_temp[ai][0];
						double num = score_temp[ai][1];

						// スコア平均値
						sr.singleScore[actionMe][ai].sum += sss;

						// スコア最小値
						if (sss < sr.singleScore[actionMe][ai].min) {
							sr.singleScore[actionMe][ai].min = sss;
						}

						// スコア最大値
						if (sss > sr.singleScore[actionMe][ai].max) {
							sr.singleScore[actionMe][ai].max = sss;
						}

						// 計測回数
						sr.singleScore[actionMe][ai].num++;
					}

					for (int ai = 0; ai < 4; ai++) {
						if (isVisible[ai] == false) continue;
						double sss = score_temp[ai][0];
						double num = score_temp[ai][1];

						// スコア平均値
						sr.pairScore[actionMe][actionFriend][ai].sum += sss;

						// スコア最小値
						if (sss < sr.pairScore[actionMe][actionFriend][ai].min) {
							sr.pairScore[actionMe][actionFriend][ai].min = sss;
						}

						// スコア最大値
						if (sss > sr.pairScore[actionMe][actionFriend][ai].max) {
							sr.pairScore[actionMe][actionFriend][ai].max = sss;
						}

						// 計測回数
						sr.pairScore[actionMe][actionFriend][ai].num++;
					}
				}
			}
		}

		return sr;
	}

	/***********************************************************************
	 * 
	 * 
	 * 
	 * 初期盤面の複数パターンを作る。爆弾の移動が読みきれないので複数パターン出てくる。
	 * 
	 * 
	 * 
	 ***********************************************************************/
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

	private void collectPackInitStates_ALLSTOP(int index, int me, int maxPower, Ability[] abs, MapInformation map, List<BombTracker.Node> nodes, MyMatrix flameLife, LinkedList<BombEEE> bbbs,
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
			// 停止状態があったら、移動状態は考慮しない。
			if (node.dirs[0]) {
				for (int i = 1; i < 5; i++) {
					node.dirs[i] = false;
				}
			}
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

	/***********************************************************************
	 * 
	 * 
	 * 
	 * 初期盤面から、2ステップ目の盤面を生成する。
	 * 
	 * 
	 * 
	 ***********************************************************************/

	private List<OperationSet[]> collectOperationSet(boolean collapse, int frame, int me, int friend, Pack packNow, int logic) throws Exception {

		double disFar = 2;

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
						Pack packNext = fm.Step(collapse, frame, packNow, actions);

						if (true) {
							OperationSet[] find = null;
							for (OperationSet[] opsets : opsetList) {
								Pack packTarget = opsets[0].packNext;
								// TODO 同じ盤面かどうかのチェックを簡略化する。
								if (packNext.sh.equals(packTarget.sh) == true) {
									// if (packNext.equals(packTarget) == true) {
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
