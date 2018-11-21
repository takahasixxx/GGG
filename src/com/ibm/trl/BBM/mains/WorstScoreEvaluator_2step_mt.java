/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Future;

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

public class WorstScoreEvaluator_2step_mt {

	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;

	static final Random rand = GlobalParameter.rand;

	static ForwardModel fm = new ForwardModel();
	static int numt = 12;
	ModelParameter param;
	WorstScoreEvaluatorSingle wses;

	static boolean isRun = false;
	List<ScoreComputingTask> tasks = new ArrayList<ScoreComputingTask>();
	List<Future<?>> futures = new ArrayList<Future<?>>();
	TreeMap<Long, NodeWSE> nodeMap = new TreeMap<Long, NodeWSE>();
	TreeMap<Long, LeafWSE> leafMap = new TreeMap<Long, LeafWSE>();

	static public class LeafWSE {
		double[][] scores;
	}

	static public class NodeWSE {
		Pack packNext;
		int[] actionsList = new int[6 * 6 * 6 * 6];
		double[][][] mins = new double[4][6][6];

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

	public WorstScoreEvaluator_2step_mt(ModelParameter param) {
		this.param = param;
		this.wses = new WorstScoreEvaluatorSingle(param);
	}

	public void Start(boolean collapse, int frame, int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife, LastAgentPosition[] laps)
			throws Exception {

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

		isRun = true;
		tasks.clear();
		futures.clear();
		nodeMap.clear();
		leafMap.clear();

		if (false) {
			ScoreComputingTask task = new ScoreComputingTask(collapse, frame, me, friend, wses, packInit, nodeMap, leafMap);
			task.run();
		}

		for (int fff = 0; fff < GlobalParameter.numCPUCore; fff++) {
			ScoreComputingTask ggg = new ScoreComputingTask(collapse, frame, me, friend, wses, packInit, nodeMap, leafMap);
			Future future = GlobalParameter.executor.submit(ggg);
			futures.add(future);
			tasks.add(ggg);
		}
	}

	public void Stop() throws Exception {
		isRun = false;
		for (Future<?> future : futures) {
			future.get();
		}
	}

	public static class ScoreComputingTask implements Runnable {
		boolean collapse;
		int frame;
		int me;
		int friend;
		WorstScoreEvaluatorSingle wses;
		Pack packInit;
		TreeMap<Long, NodeWSE> nodeMap;
		TreeMap<Long, LeafWSE> leafMap;

		TreeMap<Long, NodeWSE> nodeMapLocal = new TreeMap<Long, NodeWSE>();
		TreeMap<Long, LeafWSE> leafMapLocal = new TreeMap<Long, LeafWSE>();

		public ScoreComputingTask(boolean collapse, int frame, int me, int friend, WorstScoreEvaluatorSingle wses, Pack packInit, TreeMap<Long, NodeWSE> nodeMap, TreeMap<Long, LeafWSE> leafMap) {
			this.collapse = collapse;
			this.frame = frame;
			this.me = me;
			this.friend = friend;
			this.wses = wses;
			this.packInit = packInit;
			this.nodeMap = nodeMap;
			this.leafMap = leafMap;
		}

		@Override
		public void run() {
			try {

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

				///////////////////////////////////////////////////////////////////////////////////////////////////////
				//
				// リストアップした初期条件でシミュレーションを実施する。
				//
				///////////////////////////////////////////////////////////////////////////////////////////////////////

				for (int yyy = 0; yyy < 100000; yyy++) {
					for (int fff = 0; fff < 5; fff++) {

						if (isRun == false) return;

						///////////////////////////////////////////////////////////////////////////////////////////////////////
						// 一ステップ目
						///////////////////////////////////////////////////////////////////////////////////////////////////////

						// アクションを決定する。
						int[] actions1 = new int[4];
						for (int ai = 0; ai < 4; ai++) {
							if (isVisible[ai]) {
								actions1[ai] = rand.nextInt(6);
							} else {
								actions1[ai] = 0;
							}
						}
						int actionInt1 = actions1[0] * 6 * 6 * 6 + actions1[1] * 6 * 6 + actions1[2] * 6 + actions1[3];

						// 次の盤面を計算する。
						Pack packNext = fm.Step(collapse, frame, packInit, actions1);

						long hash1 = computeHash(packNext, packInit);
						NodeWSE node = nodeMapLocal.get(hash1);
						if (node == null) {
							node = new NodeWSE(packNext);
							nodeMapLocal.put(hash1, node);
						}
						node.actionsList[actionInt1] = 1;

						///////////////////////////////////////////////////////////////////////////////////////////////////////
						// 二ステップ目
						///////////////////////////////////////////////////////////////////////////////////////////////////////

						// アクションを決定する。
						int[] actions2 = new int[4];
						for (int ai = 0; ai < 4; ai++) {
							if (isVisible[ai]) {
								actions2[ai] = rand.nextInt(6);
							} else {
								actions2[ai] = 0;
							}
						}

						// 次の盤面を計算する。
						Pack packNext2 = fm.Step(collapse, frame + 1, packNext, actions2);

						long hash2 = computeHash(packNext2, packNext);
						LeafWSE leaf = leafMapLocal.get(hash2);
						if (leaf == null) {
							leaf = new LeafWSE();
							leafMapLocal.put(hash2, leaf);

							// 盤面を評価する。
							{
								Pack[] packs = new Pack[numt];
								packs[0] = packNext2;

								int[][] instructions = new int[numt][4];
								for (int ai = 0; ai < 4; ai++) {
									for (int t = 0; t < numt; t++) {
										instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
									}
								}
								double[][] score_temp = wses.Do3_HighSpeed(collapse, frame + 1, me, friend, packs, null, instructions);
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

							int ame = actions2[me2 - 10];
							int afr = actions2[friend2 - 10];

							if (sss < node.mins[ai][ame][afr]) {
								node.mins[ai][ame][afr] = sss;
							}
						}
					}

					// たまに、グローバルのnodeとleafの情報をマージする。
					synchronized (nodeMap) {

						if (isRun == false) return;

						{
							Set<Long> keys = new TreeSet<Long>();
							keys.addAll(nodeMap.keySet());
							keys.addAll(nodeMapLocal.keySet());
							for (long hash : keys) {
								NodeWSE nodeL = nodeMapLocal.get(hash);
								NodeWSE nodeG = nodeMap.get(hash);
								if (nodeG == null) {
									nodeMap.put(hash, nodeL);
								} else if (nodeL == null) {
									nodeMapLocal.put(hash, nodeG);
								} else {
									for (int i = 0; i < nodeL.actionsList.length; i++) {
										nodeG.actionsList[i] |= nodeL.actionsList[i];
									}

									for (int ai = 0; ai < 4; ai++) {
										for (int a = 0; a < 6; a++) {
											for (int b = 0; b < 6; b++) {
												double min = Math.min(nodeG.mins[ai][a][b], nodeL.mins[ai][a][b]);
												nodeG.mins[ai][a][b] = min;
												nodeL.mins[ai][a][b] = min;
											}
										}
									}
								}
							}
						}

						{
							Set<Long> keys = new TreeSet<Long>();
							keys.addAll(leafMap.keySet());
							keys.addAll(leafMapLocal.keySet());

							for (long hash : keys) {
								LeafWSE leafL = leafMapLocal.get(hash);
								LeafWSE leafG = leafMap.get(hash);
								if (leafG == null) {
									leafMap.put(hash, leafL);
								} else if (leafL == null) {
									leafMapLocal.put(hash, leafG);
								}
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public ScoreResult Get(int me, int friend) {
		for (ScoreComputingTask task : tasks) {
			TreeMap<Long, NodeWSE> nodeMapLocal = task.nodeMapLocal;
			TreeMap<Long, LeafWSE> leafMapLocal = task.leafMapLocal;

			{
				Set<Long> keys = new TreeSet<Long>();
				keys.addAll(nodeMap.keySet());
				keys.addAll(nodeMapLocal.keySet());
				for (long hash : keys) {
					NodeWSE nodeL = nodeMapLocal.get(hash);
					NodeWSE nodeG = nodeMap.get(hash);
					if (nodeG == null) {
						nodeMap.put(hash, nodeL);
					} else if (nodeL == null) {
						nodeMapLocal.put(hash, nodeG);
					} else {
						for (int i = 0; i < nodeL.actionsList.length; i++) {
							nodeG.actionsList[i] |= nodeL.actionsList[i];
						}

						for (int ai = 0; ai < 4; ai++) {
							for (int a = 0; a < 6; a++) {
								for (int b = 0; b < 6; b++) {
									double min = Math.min(nodeG.mins[ai][a][b], nodeL.mins[ai][a][b]);
									nodeG.mins[ai][a][b] = min;
									nodeL.mins[ai][a][b] = min;
								}
							}
						}
					}
				}
			}

			{
				Set<Long> keys = new TreeSet<Long>();
				keys.addAll(leafMap.keySet());
				keys.addAll(leafMapLocal.keySet());

				for (long hash : keys) {
					LeafWSE leafL = leafMapLocal.get(hash);
					LeafWSE leafG = leafMap.get(hash);
					if (leafG == null) {
						leafMap.put(hash, leafL);
					} else if (leafL == null) {
						leafMapLocal.put(hash, leafG);
					}
				}
			}
		}

		// TODO
		if (true) {
			int numNode = nodeMap.size();
			int numLeaf = leafMap.size();
			System.out.println("WWWWWWWW: " + numNode + ", " + numLeaf);
		}

		// 「敵の最小値」の「味方の平均」の「自分の最大値」をnodeのスコアとする。
		if (false) {
			for (Entry<Long, NodeWSE> entry : nodeMap.entrySet()) {
				NodeWSE node = entry.getValue();

				for (int ai = 0; ai < 4; ai++) {
					// if (isVisible[ai] == false) continue;

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
						if (num == 0) continue;
						double ave = sum / num;
						if (ave > max) {
							max = ave;
						}
					}

					node.scoreFinal[ai] = max;
				}
			}
		}

		// 「敵の最小値」の「味方の平均」の「自分の平均」をnodeのスコアとする。
		if (true) {
			for (Entry<Long, NodeWSE> entry : nodeMap.entrySet()) {
				NodeWSE node = entry.getValue();
				for (int ai = 0; ai < 4; ai++) {
					double sum = 0;
					double num = 0;
					for (int a = 0; a < 6; a++) {
						for (int b = 0; b < 6; b++) {
							double sss = node.mins[ai][a][b];
							if (sss == Double.POSITIVE_INFINITY) continue;
							sum += sss;
							num++;
						}
					}
					if (num == 0) continue;
					double ave = sum / num;
					node.scoreFinal[ai] = ave;
				}
			}
		}

		ScoreResult sr = new ScoreResult();
		for (Entry<Long, NodeWSE> entry : nodeMap.entrySet()) {
			NodeWSE node = entry.getValue();

			for (int i = 0; i < node.actionsList.length; i++) {
				if (node.actionsList[i] == 0) continue;
				int a = i / (6 * 6 * 6);
				int b = i % (6 * 6 * 6) / (6 * 6);
				int c = i % (6 * 6) / 6;
				int d = i % 6;
				int[] actions = new int[] { a, b, c, d };

				int actionMe = actions[me - 10];
				int actionFriend = actions[friend - 10];

				for (int ai = 0; ai < 4; ai++) {
					double sss = node.scoreFinal[ai];

					if (sss == Double.NEGATIVE_INFINITY) {
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
					double sss = node.scoreFinal[ai];

					if (sss == Double.NEGATIVE_INFINITY) {
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

	static SortValueComparator svc = new SortValueComparator();

	static private long computeHash(Pack pack, Pack packPre) throws Exception {

		int[] moto = new int[4 * 2 + numField * numField * 5];
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

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) pack.board.data[x][y];
				moto[8 + y * numField + x + numField * numField * 0] = type;
			}
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				BombEEE bbb = pack.sh.getBomb(x, y);
				if (bbb == null) {
					moto[8 + y * numField + x + numField * numField * 1] = -1;
					moto[8 + y * numField + x + numField * numField * 2] = -1;
					moto[8 + y * numField + x + numField * numField * 3] = -1;
				} else {
					moto[8 + y * numField + x + numField * numField * 1] = bbb.life;
					moto[8 + y * numField + x + numField * numField * 2] = bbb.power;
					moto[8 + y * numField + x + numField * numField * 3] = bbb.dir;
				}
			}
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int fff = (int) pack.flameLife.data[x][y];
				moto[8 + y * numField + x + numField * numField * 4] = fff;
			}
		}

		// int hash = Arrays.hashCode(moto);

		ByteBuffer byteBuffer = ByteBuffer.allocate(moto.length * 4);
		IntBuffer intBuffer = byteBuffer.asIntBuffer();
		intBuffer.put(moto);

		byte[] array = byteBuffer.array();

		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] thedigest = md.digest(array);
		long temp1 = thedigest[0] * 256L * 256L * 256L + thedigest[1] * 256L * 256L + thedigest[2] * 256L + thedigest[3];
		long temp2 = thedigest[4] * 256L * 256L * 256L + thedigest[5] * 256L * 256L + thedigest[6] * 256L + thedigest[7];
		long hash2 = temp1 * 256L * 256L * 256L * 256L + temp2;
		return hash2;
	}

	static private int computeHash_temp(Pack pack, Pack packPre) {
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
