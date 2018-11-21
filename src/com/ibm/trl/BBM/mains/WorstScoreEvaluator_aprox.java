/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.LastAgentPosition;
import com.ibm.trl.BBM.mains.Agent.ModelParameter;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;

import ibm.ANACONDA.Core.MyMatrix;
import ibm.ANACONDA.Core.SortValue;
import ibm.ANACONDA.Core.SortValueComparator;

public class WorstScoreEvaluator_aprox {

	static final int LOGIC_ALL = 0;
	static final int LOGIC_MOVEONLY_IF_FAR = 1;
	static final int LOGIC_MOVEONLY = 2;
	static final int LOGIC_STOP_IF_FAR = 3;
	static final int LOGIC_STOP = 4;

	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;

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

	public WorstScoreEvaluator_aprox(ModelParameter param) {
		this.param = param;
		this.wses = new WorstScoreEvaluatorSingle(param);
	}

	static public class ScoreEntry {
		double max = Double.NEGATIVE_INFINITY;
		double min = Double.POSITIVE_INFINITY;
		double sum = 0;
		double num = 0;
	}

	static public class ScoreResult {
		ScoreEntry[][] singleScore = new ScoreEntry[6][4];
		ScoreEntry[][][] pairScore = new ScoreEntry[6][6][4];
		List<double[][]> hhh = new ArrayList<double[][]>();
		List<int[]> ggg = new ArrayList<int[]>();

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
	}

	/***********************************************************************
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * テスト用
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 ***********************************************************************/

	SortValueComparator svc = new SortValueComparator();

	class PPP {
		Pack packNow;
		List<int[]> actionsList = new ArrayList<int[]>();
		List<Pack> packNextList = new ArrayList<Pack>();

		double sum = 0;
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double num = 0;

		public PPP(Pack packNow) {
			this.packNow = packNow;
		}
	}

	private int computeHash(Pack pack, Pack packPre, boolean isBombMeOnly, int me, int friend) {
		Collection<BombEEE> bombsOrg = pack.sh.getBombEntry();
		List<BombEEE> bombs = new ArrayList<BombEEE>();
		bombs.addAll(bombsOrg);

		int numb = bombs.size();
		int[] moto = new int[4 * 2 + 4 * 4 + numb * 6];

		if (isBombMeOnly) {
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE aaa = pack.sh.getAgent(ai + 10);
				if (aaa != null && (ai == me - 10 || ai == friend - 10)) {
					moto[ai * 2 + 0] = aaa.x;
					moto[ai * 2 + 1] = aaa.y;
				} else {
					moto[ai * 2 + 0] = -1;
					moto[ai * 2 + 1] = -1;
				}
			}
		} else {
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE aaa = pack.sh.getAgent(ai + 10);
				if (aaa != null) {
					moto[ai * 2 + 0] = aaa.x;
					moto[ai * 2 + 1] = aaa.y;
				} else {
					moto[ai * 2 + 0] = -1;
					moto[ai * 2 + 1] = -1;
				}
			}
		}

		for (int ai = 0; ai < 4; ai++) {
			AgentEEE aaa = packPre.sh.getAgent(ai + 10);
			if (aaa == null) {
				moto[8 + ai * 4 + 0] = -1;
				moto[8 + ai * 4 + 1] = -1;
				moto[8 + ai * 4 + 2] = -1;
				moto[8 + ai * 4 + 3] = -1;
			} else {
				if (aaa.x - 1 >= 0) {
					moto[8 + ai * 4 + 0] = (int) pack.board.data[aaa.x - 1][aaa.y];
				} else {
					moto[8 + ai * 4 + 0] = -1;
				}
				if (aaa.x + 1 < numField) {
					moto[8 + ai * 4 + 1] = (int) pack.board.data[aaa.x + 1][aaa.y];
				} else {
					moto[8 + ai * 4 + 1] = -1;
				}
				if (aaa.y - 1 >= 0) {
					moto[8 + ai * 4 + 2] = (int) pack.board.data[aaa.x][aaa.y - 1];
				} else {
					moto[8 + ai * 4 + 2] = -1;
				}
				if (aaa.y + 1 < numField) {
					moto[8 + ai * 4 + 3] = (int) pack.board.data[aaa.x][aaa.y + 1];
				} else {
					moto[8 + ai * 4 + 3] = -1;
				}
			}
		}

		List<SortValue> svlist = new ArrayList<SortValue>();
		for (int i = 0; i < numb; i++) {
			BombEEE bbb = bombs.get(i);
			int value = bbb.y * numField + bbb.x;
			svlist.add(new SortValue(i, value));
		}
		Collections.sort(svlist, svc);

		for (int i = 0; i < numb; i++) {
			BombEEE bbb = bombs.get(svlist.get(i).index);
			moto[8 + 16 + i * 6 + 0] = bbb.x;
			moto[8 + 16 + i * 6 + 1] = bbb.y;
			moto[8 + 16 + i * 6 + 2] = bbb.owner;
			moto[8 + 16 + i * 6 + 3] = bbb.life;
			moto[8 + 16 + i * 6 + 4] = bbb.dir;
			moto[8 + 16 + i * 6 + 5] = bbb.power;
		}

		int hash = Arrays.hashCode(moto);

		return hash;
	}

	public ScoreResult Do___GGG(boolean collapse, int frame, int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife,
			LastAgentPosition[] laps) throws Exception {

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
		System.out.println("initialPackList.size()=" + initialPackList.size());

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

		if (numVisibleAgent == 3) {
			System.out.println("heavy");
		}

		// TODO
		// if (true) {
		Pack packInit = initialPackList.get(0);

		boolean[][] actionExecuteFlag = new boolean[4][6];
		for (int ai = 0; ai < 4; ai++) {
			AgentEEE agentNow = packInit.sh.getAgent(ai + 10);
			if (agentNow == null) {
				actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false };
			} else {
				actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true };
			}
		}

		long timeStart = System.currentTimeMillis();

		// 一周目
		TreeMap<Integer, PPP> transitionMap = new TreeMap<Integer, PPP>();
		if (true) {
			Pack packNow = packInit;
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
							int hash = computeHash(packNow, packNext, true, me, friend);
							PPP ppp = transitionMap.get(hash);
							if (ppp == null) {
								ppp = new PPP(packNow);
								transitionMap.put(hash, ppp);
							}
							ppp.actionsList.add(actions);
							ppp.packNextList.add(packNext);
						}
					}
				}
			}
		}

		// 各トランジション毎に盤面評価する。
		int numt = 13;

		ScoreResult sr = new ScoreResult();

		for (Entry<Integer, PPP> entry : transitionMap.entrySet()) {
			PPP ppp = entry.getValue();

			Pack[] packs = new Pack[numt];
			packs[0] = ppp.packNow;

			Pack packNext = new Pack(ppp.packNextList.get(0));
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10 || ai == friend - 10) continue;
				packNext.removeAgent(ai + 10);
			}
			packs[1] = packNext;

			int[][] instructions = new int[numt][4];
			for (int ai = 0; ai < 4; ai++) {
				instructions[0][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_EXMAP;
				for (int t = 1; t < numt; t++) {
					instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
				}
			}

			MyMatrix[][] exmaps = new MyMatrix[numt][4];
			for (int ai = 0; ai < 4; ai++) {
				exmaps[1][ai] = new MyMatrix(numField, numField);
			}
			for (Pack pack : ppp.packNextList) {
				for (int ai = 0; ai < 4; ai++) {
					AgentEEE aaa = pack.sh.getAgent(ai + 10);
					if (aaa == null) continue;
					exmaps[1][ai].data[aaa.x][aaa.y] = 1;
				}
			}

			// TODO
			if (ppp.packNextList.get(0).sh.getAgent(me) == null && numVisibleAgent >= 3) {
				System.out.println("次の一手で死亡");
			}

			double[][] score_temp = wses.Do3(collapse, frame, me, friend, packs, exmaps, instructions);

			for (int[] actions : ppp.actionsList) {

				// TODO
				sr.ggg.add(actions);
				sr.hhh.add(score_temp);

				int actionMe = actions[me - 10];
				int actionFriend = actions[friend - 10];

				for (int ai = 0; ai < 4; ai++) {
					if (isVisible[ai] == false) continue;
					double sss = score_temp[ai][0];

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

		// TODO 整理して出力してみる。
		if (false) {
			int num = sr.hhh.size();
			for (int a = 0; a < 6; a++) {
				for (int i = 0; i < num; i++) {
					int[] actions = sr.ggg.get(i);
					double[][] sss = sr.hhh.get(i);
					if (actions[me - 10] != a) continue;
					String line = String.format("%d,%d,%d,%d = %f, %f, %f, %f", actions[0], actions[1], actions[2], actions[3], sss[0][0], sss[1][0], sss[2][0], sss[3][0]);
					System.out.println(line);
				}
			}
		}

		long timeEnd = System.currentTimeMillis();
		long timeDel = timeEnd - timeStart;
		// System.out.println("===================================");
		System.out.println("===================================");
		String line = String.format("PPP, numVisibleAgent=%d, packMap=%d, timeDel=%d", numVisibleAgent, transitionMap.size(), timeDel);
		System.out.println(line);
		System.out.println("===================================");
		// }

		// ================================================================================

		// 味方チームのスコア計算をやってみる。
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
