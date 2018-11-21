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
import java.util.Random;
import java.util.TreeMap;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.LastAgentPosition;
import com.ibm.trl.BBM.mains.Agent.ModelParameter;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;
import com.ibm.trl.BBM.mains.WorstScoreEvaluator.ScoreResult;

import ibm.ANACONDA.Core.MyMatrix;
import ibm.ANACONDA.Core.SortValue;
import ibm.ANACONDA.Core.SortValueComparator;

public class WorstScoreEvaluator_2step_st {

	static final int LOGIC_ALL = 0;
	static final int LOGIC_MOVEONLY_IF_FAR = 1;
	static final int LOGIC_MOVEONLY = 2;
	static final int LOGIC_STOP_IF_FAR = 3;
	static final int LOGIC_STOP = 4;

	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;

	static final Random rand = GlobalParameter.rand;

	static ForwardModel fm = new ForwardModel();
	static int numt = 13;
	ModelParameter param;
	WorstScoreEvaluatorSingle wses;

	static boolean isRun = false;
	ScoreResult sr;

	static public class LeafWSE {
		double[][] scores;
	}

	static public class NodeWSE {
		List<int[]> actionsList = new ArrayList<int[]>();
		Pack packNext;
		double[][][] mins = new double[4][6][6];
		double[][] scores;

		double[] scoreFinal = new double[4];

		public NodeWSE(Pack packNext) {
			this.packNext = packNext;

			for (int ai = 0; ai < 4; ai++) {
				for (int a = 0; a < 6; a++) {
					for (int b = 0; b < 6; b++) {
						mins[ai][a][b] = Double.POSITIVE_INFINITY;
					}
				}
			}

			for (int ai = 0; ai < 4; ai++) {
				scoreFinal[ai] = Double.NEGATIVE_INFINITY;
			}
		}
	}

	public WorstScoreEvaluator_2step_st(ModelParameter param) {
		this.param = param;
		this.wses = new WorstScoreEvaluatorSingle(param);
	}

	public void Start(boolean collapse, int frame, int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife, LastAgentPosition[] laps)
			throws Exception {

		///////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本変数を定義する。
		//
		///////////////////////////////////////////////////////////////////////////////////////////////////////

		int e1 = -1, e2 = -1;
		for (int ai = 0; ai < 4; ai++) {
			if (ai == me - 10) continue;
			if (ai == friend - 10) continue;
			if (e1 == -1) {
				e1 = ai + 10;
			} else {
				e2 = ai + 10;
			}
		}

		int[] teamNumber = new int[4];
		teamNumber[e1 - 10] = 1;
		teamNumber[e2 - 10] = 1;

		///////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 初期盤面を生成する。
		//
		///////////////////////////////////////////////////////////////////////////////////////////////////////

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

		///////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// リストアップした初期条件でシミュレーションを実施する。
		//
		///////////////////////////////////////////////////////////////////////////////////////////////////////

		Pack packInit = initialPackList.get(0);

		boolean[] isVisible = new boolean[4];
		for (int ai = 0; ai < 4; ai++) {
			AgentEEE agentNow = packInit.sh.getAgent(ai + 10);
			if (agentNow == null) {
				isVisible[ai] = false;
			} else {
				isVisible[ai] = true;
			}
		}

		int numVisibleTeam = 0;
		int numVisibleEnemy = 0;
		for (int ai = 0; ai < 4; ai++) {
			AgentEEE agentNow = packInit.sh.getAgent(ai + 10);
			if (agentNow != null) {
				if (ai == me - 10 || ai == friend - 10) {
					numVisibleTeam++;
				} else {
					numVisibleEnemy++;
				}
			}
		}

		boolean[][] actionExecuteFlag = new boolean[4][6];
		for (int ai = 0; ai < 4; ai++) {
			AgentEEE agentNow = packInit.sh.getAgent(ai + 10);
			if (agentNow == null) {
				actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false };
			} else {
				actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true };
			}
		}

		// 1周目
		TreeMap<Integer, NodeWSE> nodeMap = new TreeMap<Integer, NodeWSE>();
		for (int a = 0; a < 6; a++) {
			if (actionExecuteFlag[0][a] == false) continue;
			for (int b = 0; b < 6; b++) {
				if (actionExecuteFlag[1][b] == false) continue;
				for (int c = 0; c < 6; c++) {
					if (actionExecuteFlag[2][c] == false) continue;
					for (int d = 0; d < 6; d++) {
						if (actionExecuteFlag[3][d] == false) continue;

						int[] actions = new int[] { a, b, c, d };
						Pack packNext = fm.Step(collapse, frame, packInit, actions);
						int hash = computeHash(packNext, packInit);
						NodeWSE node = nodeMap.get(hash);
						if (node == null) {
							node = new NodeWSE(packNext);
							nodeMap.put(hash, node);

							// TODO nodeそのものの盤面評価は不要である。
							if (false) {
								Pack[] packs = new Pack[numt];
								packs[0] = packInit;
								packs[1] = packNext;

								int[][] instructions = new int[numt][4];
								for (int ai = 0; ai < 4; ai++) {
									instructions[0][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_BOARD;
									for (int t = 1; t < numt; t++) {
										instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
									}
								}

								double[][] score_temp = wses.Do3_HighSpeed(collapse, frame, me, friend, abs, packs, null, instructions);

								node.scores = score_temp;
							}
						}
						node.actionsList.add(actions);
					}
				}
			}
		}

		// 2周目
		TreeMap<Integer, LeafWSE> leafMap = new TreeMap<Integer, LeafWSE>();
		for (Entry<Integer, NodeWSE> entry : nodeMap.entrySet()) {
			NodeWSE node = entry.getValue();

			Pack packNext = node.packNext;
			for (int a = 0; a < 6; a++) {
				if (actionExecuteFlag[0][a] == false) continue;
				for (int b = 0; b < 6; b++) {
					if (actionExecuteFlag[1][b] == false) continue;
					for (int c = 0; c < 6; c++) {
						if (actionExecuteFlag[2][c] == false) continue;
						for (int d = 0; d < 6; d++) {
							if (actionExecuteFlag[3][d] == false) continue;

							int[] actions = new int[] { a, b, c, d };
							Pack packNext2 = fm.Step(collapse, frame + 1, packNext, actions);
							int hash = computeHash(packNext2, packNext);
							LeafWSE leaf = leafMap.get(hash);
							if (leaf == null) {
								leaf = new LeafWSE();
								leafMap.put(hash, leaf);

								// 盤面を評価する。
								if (true) {
									Pack[] packs = new Pack[numt];
									packs[0] = packNext;
									packs[1] = packNext2;

									int[][] instructions = new int[numt][4];
									for (int ai = 0; ai < 4; ai++) {
										instructions[0][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_BOARD;
										for (int t = 1; t < numt; t++) {
											instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
										}
									}

									double[][] score_temp = wses.Do3_HighSpeed(collapse, frame, me, friend, abs, packs, null, instructions);

									leaf.scores = score_temp;
								}
							}

							// 自分と友達の行動ペアをキーにして、敵の行動に対して最小値を計算する。
							for (int ai = 0; ai < 4; ai++) {
								if (isVisible[ai] == false) continue;

								double sss = leaf.scores[ai][0];

								int me2 = ai + 10, friend2 = -1;
								for (int ai2 = 0; ai2 < 4; ai2++) {
									if (ai2 == ai) continue;
									if (teamNumber[ai] == teamNumber[ai2]) friend2 = ai2 + 10;
								}

								int ame = actions[me2 - 10];
								int afr = actions[friend2 - 10];

								if (sss < node.mins[ai][ame][afr]) {
									node.mins[ai][ame][afr] = sss;
								}
							}
						}
					}
				}
			}
		}

		// 「敵の最小値」の「味方の平均」の「自分の最大値」をnodeのスコアとする。
		for (Entry<Integer, NodeWSE> entry : nodeMap.entrySet()) {
			NodeWSE node = entry.getValue();

			for (int ai = 0; ai < 4; ai++) {
				if (isVisible[ai] == false) continue;

				double max = Double.NEGATIVE_INFINITY;
				for (int a = 0; a < 6; a++) {
					double sum = 0;
					double num = 0;
					for (int b = 0; b < 6; b++) {
						double sss = node.mins[ai][a][b];
						if (sss == Double.POSITIVE_INFINITY) continue;
						sum += sss;
						num++;
					}

					// TODO
					if (num == 0) {
						System.out.println("okasii");
						System.exit(0);
						continue;
					}

					double ave = sum / num;
					if (ave > max) {
						max = ave;
					}
				}

				node.scoreFinal[ai] = max;
			}
		}

		// TODO
		if (true) {
			int numVisible = numVisibleTeam + numVisibleEnemy;
			String line = String.format("RRRRRRRRRRRR, %05d, %05d, %05d", numVisible, nodeMap.size(), leafMap.size());
			System.out.println(line);
		}

		ScoreResult sr = new ScoreResult();
		for (Entry<Integer, NodeWSE> entry : nodeMap.entrySet()) {
			NodeWSE node = entry.getValue();

			for (int[] actions : node.actionsList) {
				int actionMe = actions[me - 10];
				int actionFriend = actions[friend - 10];

				for (int ai = 0; ai < 4; ai++) {
					if (isVisible[ai] == false) continue;
					double sss = node.scoreFinal[ai];

					// TODO
					if (sss == Double.NEGATIVE_INFINITY) {
						System.out.println("okasii");
						System.exit(0);
						continue;
					}

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
					double sss = node.scoreFinal[ai];

					// TODO
					if (sss == Double.NEGATIVE_INFINITY) {
						System.out.println("okasii");
						System.exit(0);
						continue;
					}

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

		this.sr = sr;
	}

	public void Stop() {
		isRun = false;
	}

	public ScoreResult Get() {
		synchronized (sr) {
			ScoreResult ret = new ScoreResult(sr);
			return ret;
		}
	}

	public static class ScoreComputingTask implements Runnable {
		boolean collapse;
		int frame;
		int me;
		int friend;
		WorstScoreEvaluatorSingle wses;
		Pack packInit;
		ScoreResult sr;

		public ScoreComputingTask(boolean collapse, int frame, int me, int friend, WorstScoreEvaluatorSingle wses, Pack packInit, ScoreResult sr) {
			this.collapse = collapse;
			this.frame = frame;
			this.me = me;
			this.friend = friend;
			this.wses = wses;
			this.packInit = packInit;
			this.sr = sr;
		}

		@Override
		public void run() {
			try {
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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

	SortValueComparator svc = new SortValueComparator();

	private int computeHash(Pack pack, Pack packPre) {
		Collection<BombEEE> bombsOrg = pack.sh.getBombEntry();
		List<BombEEE> bombs = new ArrayList<BombEEE>();
		bombs.addAll(bombsOrg);

		int numb = bombs.size();
		int[] moto = new int[4 * 2 + 4 * 4 + numb * 6];

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
}
