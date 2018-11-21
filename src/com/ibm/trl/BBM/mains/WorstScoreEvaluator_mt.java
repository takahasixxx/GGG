/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
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

public class WorstScoreEvaluator_mt {

	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;

	static final Random rand = GlobalParameter.rand;

	static ForwardModel fm = new ForwardModel();
	static int numt = 11;
	ModelParameter param;
	WorstScoreEvaluatorSingle wses;

	static boolean isRun = false;
	List<ScoreComputingTask> tasks = new ArrayList<ScoreComputingTask>();
	List<Future<?>> futures = new ArrayList<Future<?>>();
	TreeMap<Long, LeafWSE> leafMap = new TreeMap<Long, LeafWSE>();

	static public class LeafWSE {
		double[][] scores;
		int[] actionsList = new int[6 * 6 * 6 * 6];
	}

	public WorstScoreEvaluator_mt(ModelParameter param) {
		this.param = param;
		this.wses = new WorstScoreEvaluatorSingle(param);
	}

	public void Start(boolean collapse, int frame, int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife, LastAgentPosition[] laps)
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

		///////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// リストアップした初期条件でシミュレーションを実施する。
		//
		///////////////////////////////////////////////////////////////////////////////////////////////////////
		isRun = true;
		tasks.clear();
		futures.clear();
		leafMap.clear();

		for (int fff = 0; fff < GlobalParameter.numCPUCore; fff++) {
			for (Pack packInit : initialPackList) {
				ScoreComputingTask ggg = new ScoreComputingTask(collapse, frame, me, friend, abs, wses, packInit, leafMap);
				Future future = GlobalParameter.executor.submit(ggg);
				futures.add(future);
				tasks.add(ggg);
			}
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
		Ability[] abs;
		WorstScoreEvaluatorSingle wses;
		Pack packInit;
		TreeMap<Long, LeafWSE> leafMap;

		TreeMap<Long, LeafWSE> leafMapLocal = new TreeMap<Long, LeafWSE>();

		public ScoreComputingTask(boolean collapse, int frame, int me, int friend, Ability abs[], WorstScoreEvaluatorSingle wses, Pack packInit, TreeMap<Long, LeafWSE> leafMap) {
			this.collapse = collapse;
			this.frame = frame;
			this.me = me;
			this.friend = friend;
			this.abs = abs;
			this.wses = wses;
			this.packInit = packInit;
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
				// 繰り返し１ステップ後のスコアを計算する。
				//
				///////////////////////////////////////////////////////////////////////////////////////////////////////

				boolean[][] actionExecuteFlag = new boolean[4][6];
				for (int ai = 0; ai < 4; ai++) {
					if (isVisible[ai]) {
						actionExecuteFlag[ai] = new boolean[] { true, true, true, true, true, true };
					} else {
						actionExecuteFlag[ai] = new boolean[] { true, false, false, false, false, false };
					}
				}

				for (int yyy = 0; yyy < 100000; yyy++) {
					int ae1 = -1;
					if (isVisible[e1 - 10]) {
						ae1 = rand.nextInt(6);
					} else {
						ae1 = 0;
					}

					int ae2 = -1;
					if (isVisible[e2 - 10]) {
						ae2 = rand.nextInt(6);
					} else {
						ae2 = 0;
					}

					for (int a = 0; a < 6; a++) {
						if (actionExecuteFlag[me - 10][a] == false) continue;
						for (int b = 0; b < 6; b++) {
							if (actionExecuteFlag[friend - 10][b] == false) continue;
							if (isRun == false) return;

							int[] actions = new int[4];
							actions[me - 10] = a;
							actions[friend - 10] = b;
							actions[e1 - 10] = ae1;
							actions[e2 - 10] = ae2;

							Pack packNext = fm.Step(collapse, frame, packInit, actions);

							long hash = computeHash(packNext);
							LeafWSE leaf = leafMapLocal.get(hash);

							if (leaf == null) {
								leaf = new LeafWSE();
								leafMapLocal.put(hash, leaf);

								Pack[] packs = new Pack[numt];
								packs[0] = packNext;

								int[][] instructions = new int[numt][4];
								for (int ai = 0; ai < 4; ai++) {
									for (int t = 0; t < numt; t++) {
										instructions[t][ai] = WorstScoreEvaluatorSingle.INSTRUCTION_ALLMOVE;
									}
								}

								double[][] score_temp = wses.Do3_HighSpeed(collapse, frame + 1, me, friend, abs, packs, null, instructions);
								for (int ai = 0; ai < 4; ai++) {
									if (isVisible[ai]) {
										score_temp[ai][1] = 1;
									}
								}

								leaf.scores = score_temp;
							}

							int actionInt = actions[0] * 6 * 6 * 6 + actions[1] * 6 * 6 + actions[2] * 6 + actions[3];
							leaf.actionsList[actionInt] = 1;
						}
					}

					synchronized (leafMap) {
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
							} else {
								for (int i = 0; i < leafL.actionsList.length; i++) {
									int temp = leafG.actionsList[i] | leafL.actionsList[i];
									leafG.actionsList[i] = temp;
									leafL.actionsList[i] = temp;
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
		ScoreResult sr = new ScoreResult();

		for (ScoreComputingTask task : tasks) {
			TreeMap<Long, LeafWSE> leafMapLocal = task.leafMapLocal;

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
				} else {
					for (int i = 0; i < leafL.actionsList.length; i++) {
						int temp = leafG.actionsList[i] | leafL.actionsList[i];
						leafG.actionsList[i] = temp;
						leafL.actionsList[i] = temp;
					}
				}
			}
		}

		int numLeaf = leafMap.size();
		System.out.println("WorstScoreEvaluator_mt: numLeaf = " + numLeaf);

		for (Entry<Long, LeafWSE> entry : leafMap.entrySet()) {
			LeafWSE leaf = entry.getValue();

			double[][] score_temp = leaf.scores;

			for (int i = 0; i < leaf.actionsList.length; i++) {
				if (leaf.actionsList[i] == 0) continue;
				int a = i / (6 * 6 * 6);
				int b = i % (6 * 6 * 6) / (6 * 6);
				int c = i % (6 * 6) / 6;
				int d = i % 6;
				int[] actions = new int[] { a, b, c, d };

				int actionMe = actions[me - 10];
				int actionFriend = actions[friend - 10];

				for (int ai = 0; ai < 4; ai++) {
					double sss = score_temp[ai][0];
					double nnn = score_temp[ai][1];
					if (nnn == 0) continue;

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
					double sss = score_temp[ai][0];
					double nnn = score_temp[ai][1];
					if (nnn == 0) continue;

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

	static private long computeHash(Pack pack) throws Exception {

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

}
