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
import com.ibm.trl.BBM.mains.Agent.ModelParameter;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;

import ibm.ANACONDA.Core.MyMatrix;
import ibm.ANACONDA.Core.SortValue;
import ibm.ANACONDA.Core.SortValueComparator;

public class WorstScoreEvaluator_v2 {

	static final int LOGIC_ALL = 0;
	static final int LOGIC_MOVEONLY_IF_FAR = 1;
	static final int LOGIC_MOVEONLY = 2;
	static final int LOGIC_STOP_IF_FAR = 3;
	static final int LOGIC_STOP = 4;

	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;

	static final int numMaxCase = 120;
	// static final int numMaxCase = 120000000;

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

	class PPP {
		List<int[]> actionsList = new ArrayList<int[]>();
		Pack packNow;
		Pack packNext;

		double sum = 0;
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double num = 0;

		public PPP(Pack packNow, Pack packNext) {
			this.packNow = packNow;
			this.packNext = packNext;
		}
	}

	public WorstScoreEvaluator_v2(ModelParameter param) {
		this.param = param;
		this.wses = new WorstScoreEvaluatorSingle(param);
	}

	SortValueComparator svc = new SortValueComparator();

	private int computeHash(Pack pack, boolean isBombMeOnly, int me) {
		Collection<BombEEE> bombsOrg = pack.sh.getBombEntry();
		List<BombEEE> bombs = new ArrayList<BombEEE>();
		bombs.addAll(bombsOrg);

		int numb = bombs.size();
		int[] moto = new int[4 * 2 + numb * 6];

		if (isBombMeOnly) {
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE aaa = pack.sh.getAgent(ai + 10);
				if (aaa != null && ai == me - 10) {
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

		List<SortValue> svlist = new ArrayList<SortValue>();
		for (int i = 0; i < numb; i++) {
			BombEEE bbb = bombs.get(i);
			int value = bbb.y * numField + bbb.x;
			svlist.add(new SortValue(i, value));
		}
		Collections.sort(svlist, svc);

		for (int i = 0; i < numb; i++) {
			BombEEE bbb = bombs.get(svlist.get(i).index);
			moto[8 + i * 6 + 0] = bbb.x;
			moto[8 + i * 6 + 1] = bbb.y;
			moto[8 + i * 6 + 2] = bbb.owner;
			moto[8 + i * 6 + 3] = bbb.life;
			moto[8 + i * 6 + 4] = bbb.dir;
			moto[8 + i * 6 + 5] = bbb.power;
		}

		int hash = Arrays.hashCode(moto);

		return hash;
	}

	public double[][][] Do(int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife) throws Exception {

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
		// collectPackInitStates(0, me, maxPower, abs, map, nodes, flameLife, bbbs, packList);
		collectPackInitStates_ALLSTOP(0, me, maxPower, abs, map, nodes, flameLife, bbbs, packList);
		System.out.println("packList.size()=" + packList.size());

		int numVisibleAgent = 0;
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = map.getType(x, y);
				if (Constant.isAgent(type)) numVisibleAgent++;
			}
		}

		// TODO
		if (true) {
			Pack packStart = packList.get(0);

			boolean[][] actionExecuteFlag = new boolean[4][6];
			for (int ai = 0; ai < 4; ai++) {
				AgentEEE agentNow = packStart.sh.getAgent(ai + 10);
				if (agentNow == null) {
					actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false };
				} else {
					actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true };
				}
			}

			long timeStart = System.currentTimeMillis();

			// 一周目
			TreeMap<Integer, PPP> packMap = new TreeMap<Integer, PPP>();
			if (true) {
				Pack packNow = packStart;
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
								int hash = computeHash(packNext, false, me);
								PPP ppp = packMap.get(hash);
								if (ppp == null) {
									ppp = new PPP(packNow, packNext);
									packMap.put(hash, ppp);
								}
								ppp.actionsList.add(actions);

								////////////////////////////////////////
								if (false) {
									int numt = 13;
									Pack[] packs = new Pack[numt];
									packs[0] = packNow;
									packs[1] = packNext;

									int[][] instructions = new int[numt][4];
									for (int ai = 0; ai < 4; ai++) {
										for (int t = 0; t < numt; t++) {
											if (t == 0) {
												instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_BOARD;
											} else {
												instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
											}
										}
									}

									double[][] score_temp = wses.Do3_HighSpeed(packs, instructions);
									////////////////////////////////////////
								}
							}
						}
					}
				}
			}

			TreeMap<Integer, PPP> packMap2 = new TreeMap<Integer, PPP>();
			TreeMap<Integer, PPP> packMapBO2 = new TreeMap<Integer, PPP>();
			for (Entry<Integer, PPP> entry : packMap.entrySet()) {
				Pack packNow = entry.getValue().packNext;
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
								int hash = computeHash(packNext, false, me);
								PPP ppp = packMap2.get(hash);
								if (ppp == null) {
									ppp = new PPP(packNow, packNext);
									packMap2.put(hash, ppp);
								}
								ppp.actionsList.add(actions);

								int hashBO = computeHash(packNext, true, me);
								PPP pppBO = packMapBO2.get(hashBO);
								if (pppBO == null) {
									pppBO = new PPP(packNow, packNext);
									packMapBO2.put(hashBO, pppBO);

									////////////////////////////////////////
									if (true) {
										int numt = 13;
										Pack[] packs = new Pack[numt];
										packs[0] = packNow;
										packs[1] = packNext;

										int[][] instructions = new int[numt][4];
										for (int ai = 0; ai < 4; ai++) {
											for (int t = 0; t < numt; t++) {
												if (t == 0) {
													instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_BOARD;
												} else {
													instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
												}
											}
										}

										double[][] score_temp = wses.Do3_HighSpeed(packs, instructions);
									}
									////////////////////////////////////////
								}
								pppBO.actionsList.add(actions);
							}
						}
					}
				}
			}

			long timeEnd = System.currentTimeMillis();

			long timeDel = timeEnd - timeStart;

			System.out.println("===================================");

			String line = String.format("PPP, numVisibleAgent=%d, packMap=%d, packMap2=%d, packMapBO2=%d, timeDel=%d", numVisibleAgent, packMap.size(), packMap2.size(), packMapBO2.size(), timeDel);
			System.out.println(line);
			// System.out.println("numVisibleAgent = " + numVisibleAgent);
			// System.out.println("packMap = " + packMap.size());
			// System.out.println("packMapBO = " + packMapBO.size());
			// System.out.println("packMap2 = " + packMap2.size());
			// System.out.println("packMapBO2 = " + packMapBO2.size());
			// System.out.println("timeDelDel = " + timeDel);
			System.out.println("===================================");

			if (numVisibleAgent == 4) {
				System.out.println("busy");
			}
		}

		if (numVisibleAgent < 10000) { return null; }

		List<OperationSet[]> opsetList = new ArrayList<OperationSet[]>();
		// if (true) {
		if (numVisibleAgent <= 3) {
			if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
				opsetList.clear();
				for (Pack pack : packList) {
					List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_ALL);
					opsetList.addAll(opsetList2);
				}
				System.out.println("opsetList.size()=" + opsetList.size());
			}

			if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
				opsetList.clear();
				for (Pack pack : packList) {
					List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_MOVEONLY);
					opsetList.addAll(opsetList2);
				}
				System.out.println("opsetList.size()=" + opsetList.size());
			}

			if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
				opsetList.clear();
				for (Pack pack : packList) {
					List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_STOP_IF_FAR);
					opsetList.addAll(opsetList2);
				}
				System.out.println("opsetList.size()=" + opsetList.size());
			}

			if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
				opsetList.clear();
				for (Pack pack : packList) {
					List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_STOP);
					opsetList.addAll(opsetList2);
				}
				System.out.println("opsetList.size()=" + opsetList.size());
			}

		} else {

			if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
				opsetList.clear();
				for (Pack pack : packList) {
					List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_MOVEONLY);
					opsetList.addAll(opsetList2);
				}
				System.out.println("opsetList.size()=" + opsetList.size());
			}

			if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
				opsetList.clear();
				for (Pack pack : packList) {
					List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_STOP_IF_FAR);
					opsetList.addAll(opsetList2);
				}
				System.out.println("opsetList.size()=" + opsetList.size());
			}

			if (opsetList.size() == 0 || opsetList.size() > numMaxCase) {
				opsetList.clear();
				for (Pack pack : packList) {
					List<OperationSet[]> opsetList2 = collectOperationSet(me, friend, pack, LOGIC_STOP);
					opsetList.addAll(opsetList2);
				}
				System.out.println("opsetList.size()=" + opsetList.size());
			}
		}

		if (false) {
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
		if (true) {
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

				double[][] score_temp = wses.Do3_HighSpeed(packs, instructions);

				// TODO 通常版とハイスピード版の突合デバッグ用。
				if (false) {
					double[][] temp1 = wses.Do3(packs, instructions);
					double[][] temp2 = wses.Do3_HighSpeed(packs, instructions);
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

		return null;
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

	private List<OperationSet[]> collectOperationSet(int me, int friend, Pack packNow, int logic) throws Exception {

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
						Pack packNext = fm.Step(packNow, actions);

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
