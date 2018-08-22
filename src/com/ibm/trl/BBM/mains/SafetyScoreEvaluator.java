package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;

public class SafetyScoreEvaluator {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;

	static final ForwardModel fm = new ForwardModel();
	static final double decayRate = 0.99;
	static final int numt = 12;
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

					double[][][] temp = SafetyScoreEvaluator.evaluateSafetyScore(packLocal, meLocal);

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

	public static double[][][] evaluateSafetyScore(Pack pack, int me) throws Exception {
		int aiMe = me - 10;
		Pack packNow = pack;
		//////////////////////////////////////////////////////////////////
		// 全アクションのSurvivableScoreを計算する。
		//////////////////////////////////////////////////////////////////
		double[][] points = new double[4][6];
		double[][] pointsTotal = new double[4][6];
		for (int targetFirstAction = 0; targetFirstAction < 6; targetFirstAction++) {
			for (int tryIndex = 0; tryIndex < numTry; tryIndex++) {
				Pack packNext = packNow;
				for (int t = 0; t < numt; t++) {
					int[] actions = { rand.nextInt(6), rand.nextInt(6), rand.nextInt(6), rand.nextInt(6) };
					if (t == 0) actions[aiMe] = targetFirstAction;

					packNext = fm.Step(packNext.board, packNext.abs, packNext.sh, actions);

					double weight = weights[t];

					AgentEEE agentsNext[] = new AgentEEE[4];
					for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
						agentsNext[aaa.agentID - 10] = aaa;
					}

					for (int ai = 0; ai < 4; ai++) {
						if (packNow.abs[ai].isAlive == false) continue;

						double good = weight;
						if (packNext.abs[ai].isAlive == false) {
							good = 0;
						} else {
							AgentEEE aaa = agentsNext[ai];
							int numSrrounded = BBMUtility.numSurrounded(packNext.board, aaa.x, aaa.y);
							if (numSrrounded == 4) {
								good = 0;
							} else if (numSrrounded == 3) {
								good = weight * 0.7;
							} else if (numSrrounded == 2) {
								good = weight * 1.0;
							}
						}

						pointsTotal[ai][targetFirstAction] += weight;
						points[ai][targetFirstAction] += good;
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
