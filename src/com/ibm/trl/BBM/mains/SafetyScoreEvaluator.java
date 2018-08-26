package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;

public class SafetyScoreEvaluator {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;

	static final ForwardModel fm = new ForwardModel();
	static final double decayRate = 0.99;
	static final int numt = 14;
	static final int numTry = 5;
	static final double[] weights = new double[numt];
	static List<Task_ComputeSafetyScore> tasks = new ArrayList<Task_ComputeSafetyScore>();

	static {
		for (int t = 0; t < numt; t++) {
			weights[t] = Math.pow(decayRate, t);
		}

		for (int i = 0; i < numThread; i++) {
			Task_ComputeSafetyScore task = new Task_ComputeSafetyScore();
			task.start();
			tasks.add(task);
		}
	}

	static public class Task_ComputeSafetyScore extends Thread {

		int me = -1;
		Pack pack = null;
		double[][][] result = new double[2][4][6];
		int tryCounter = 0;

		@Override
		public void run() {
			try {
				Pack packLast = null;
				while (true) {
					Pack packLocal;
					int meLocal;
					synchronized (this) {
						packLocal = this.pack;
						meLocal = this.me;
					}
					if (packLocal == null) {
						continue;
					}

					if (packLocal != packLast) {
						packLast = packLocal;
						synchronized (result) {
							for (int i = 0; i < 2; i++) {
								for (int ai = 0; ai < 4; ai++) {
									for (int act = 0; act < 6; act++) {
										result[i][ai][act] = 0;
									}
								}
							}
							tryCounter = 0;
						}
					}

					double[][][] temp = SafetyScoreEvaluator.evaluateSafetyScore(numTry, packLocal, meLocal);

					synchronized (result) {
						for (int i = 0; i < 2; i++) {
							for (int ai = 0; ai < 4; ai++) {
								for (int act = 0; act < 6; act++) {
									result[i][ai][act] += temp[i][ai][act];
								}
							}
						}
						tryCounter += 1;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public SafetyScoreEvaluator() {

	}

	public static double[][][] evaluateSafetyScore(int numTry, Pack pack, int me) throws Exception {
		Pack packNow = pack;
		//////////////////////////////////////////////////////////////////
		// 全アクションのSurvivableScoreを計算する。
		//////////////////////////////////////////////////////////////////
		double[][] points = new double[4][6];
		double[][] pointsTotal = new double[4][6];
		int[] actions = new int[4];
		int[] temp = new int[6];
		for (int targetFirstAction = 0; targetFirstAction < 6; targetFirstAction++) {
			for (int tryIndex = 0; tryIndex < numTry; tryIndex++) {
				Pack packNext = packNow;
				for (int t = 0; t < numt; t++) {

					boolean[][] bombExist = new boolean[numField][numField];
					for (EEE bbb : packNext.sh.getBombEntry()) {
						bombExist[bbb.x][bbb.y] = true;
					}

					// 取りうるアクションを列挙して、乱択する。
					for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
						int ai = aaa.agentID - 10;
						if (t == 0 && aaa.agentID == me) {
							actions[ai] = targetFirstAction;
						} else {
							int num = 0;

							temp[num] = 0;
							num++;

							int x = aaa.x;
							int y = aaa.y;

							if (x > 0) {
								int type = (int) packNext.board.data[x - 1][y];
								if (Constant.isWall(type) == false) {
									temp[num] = 1;
									num++;
								}
							}
							if (x < numField - 1) {
								int type = (int) packNext.board.data[x + 1][y];
								if (Constant.isWall(type) == false) {
									temp[num] = 2;
									num++;
								}
							}
							if (y > 0) {
								int type = (int) packNext.board.data[x][y - 1];
								if (Constant.isWall(type) == false) {
									temp[num] = 3;
									num++;
								}
							}
							if (y < numField - 1) {
								int type = (int) packNext.board.data[x][y + 1];
								if (Constant.isWall(type) == false) {
									temp[num] = 4;
									num++;
								}
							}

							if (packNext.abs[ai].numBombHold > 0 && bombExist[x][y] == false) {
								temp[num] = 5;
								num++;
							}

							int act = temp[rand.nextInt(num)];
							actions[ai] = act;
						}
					}

					packNext = fm.Step(packNext.board, packNext.abs, packNext.sh, actions);

					double weight = weights[t];

					AgentEEE agentsNext[] = new AgentEEE[4];
					for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
						agentsNext[aaa.agentID - 10] = aaa;
					}

					for (int ai = 0; ai < 4; ai++) {
						if (packNow.abs[ai].isAlive == false) continue;

						double ppp = 1;
						if (packNext.abs[ai].isAlive == false) {
							ppp = 0;
						} else {
							AgentEEE aaa = agentsNext[ai];
							int numSrrounded = BBMUtility.numSurrounded(packNext.board, aaa.x, aaa.y);
							if (numSrrounded == 4) {
								ppp = -1;
							} else {
								int numSrrounded2 = BBMUtility.numSurrounded2(packNext.board, aaa.x, aaa.y, aaa.agentID);
								if (numSrrounded2 == 4) {
									ppp = 0;
								}
							}
						}

						pointsTotal[ai][targetFirstAction] += weight;
						points[ai][targetFirstAction] += weight * ppp;
					}
				}
			}
		}

		double[][][] ret = new double[2][][];
		ret[0] = points;
		ret[1] = pointsTotal;
		return ret;
	}

	static public void set(Pack pack, int me) throws Exception {
		for (Task_ComputeSafetyScore task : tasks) {
			synchronized (task) {
				task.me = me;
				task.pack = pack;
			}
		}
	}

	static public double[][] getLatestSafetyScore() {
		double[][][] temp = new double[2][4][6];
		for (Task_ComputeSafetyScore task : tasks) {
			synchronized (task.result) {
				for (int i = 0; i < 2; i++) {
					for (int ai = 0; ai < 4; ai++) {
						for (int act = 0; act < 6; act++) {
							temp[i][ai][act] += task.result[i][ai][act];
						}
					}
				}
			}
		}

		double[][] safetyScore = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			for (int act = 0; act < 6; act++) {
				safetyScore[ai][act] = temp[0][ai][act] / temp[1][ai][act];
			}
		}

		return safetyScore;
	}

	static public double[][] computeSafetyScore(Pack pack, int me) throws Exception {
		double[][][] temp = SafetyScoreEvaluator.evaluateSafetyScore(500, pack, me);

		double[][] safetyScore = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			for (int act = 0; act < 6; act++) {
				safetyScore[ai][act] = temp[0][ai][act] / temp[1][ai][act];
			}
		}

		return safetyScore;
	}

	static public int getTryCounter() {
		int tryCounter = 0;
		for (Task_ComputeSafetyScore task : tasks) {
			synchronized (task.result) {
				tryCounter += task.tryCounter;
			}
		}
		return tryCounter;
	}

}
