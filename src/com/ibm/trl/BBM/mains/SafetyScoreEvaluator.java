package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ibm.trl.BBM.mains.BBMUtility.SurroundedInformation;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;

import ibm.ANACONDA.Core.MyMatrix;

public class SafetyScoreEvaluator {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;

	static final ForwardModel fm = new ForwardModel();
	static final double decayRate = 1;
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

	static class Result {
		double scoreBest = Double.MAX_VALUE;
		int[] actionSeqBest = null;
	}

	private static void rrr(int numTry, Pack pack, int me, int you, int depth, int[] actionSeq, Result result) throws Exception {
		if (depth == actionSeq.length) {
			double[][] temp = evaluateSafetyScore_temp_r(numTry, pack, me, actionSeq);

			double sfMe = temp[0][me - 10] / temp[1][me - 10];
			double sfYou = temp[0][you - 10] / temp[1][you - 10];

			if (sfMe > 0.1 && sfYou < result.scoreBest) {
				result.scoreBest = sfYou;
				result.actionSeqBest = new int[actionSeq.length];
				for (int i = 0; i < actionSeq.length; i++) {
					result.actionSeqBest[i] = actionSeq[i];
				}
			}
		} else {
			for (int act = 0; act < 6; act++) {
				actionSeq[depth] = act;
				rrr(numTry, pack, me, you, depth + 1, actionSeq, result);
			}
		}
	}

	public static double[][][] evaluateSafetyScore_temp(int numTry, Pack pack, int me) throws Exception {
		numTry = 300;

		AgentEEE agentMe = null;
		for (AgentEEE aaa : pack.sh.getAgentEntry()) {
			if (aaa.agentID == me) {
				agentMe = aaa;
			}
		}
		if (agentMe == null) return null;

		// 注目しているエージェントを追い詰めるパスがあるかどうか調べる。
		for (AgentEEE aaa : pack.sh.getAgentEntry()) {
			if (aaa.agentID == me) continue;

			int numWall = BBMUtility.numWall(pack.board, aaa.x, aaa.y);
			if (numWall != 3) continue;

			MyMatrix board2 = new MyMatrix(pack.board);
			board2.data[aaa.x][aaa.y] = Constant.Passage;
			MyMatrix dis = BBMUtility.ComputeOptimalDistance(board2, agentMe.x, agentMe.y, Integer.MAX_VALUE);
			int d = (int) dis.data[aaa.x][aaa.y];
			if (d > 2) continue;

			for (int numDepth = 4; numDepth < 5; numDepth++) {
				Result result = new Result();
				int[] actionSeq = new int[numDepth];
				rrr(numTry, pack, me, aaa.agentID, 0, actionSeq, result);

				if (result.scoreBest < 0.05) {
					System.out.print(numDepth + "ステップで追い詰め可能。");
					for (int a : result.actionSeqBest) {
						System.out.print(", " + a);
					}
					System.out.println();
					System.out.println();
				}
			}
		}
		return null;
	}

	public static double[][] evaluateSafetyScore_temp_r(int numTry, Pack pack, int me, int[] actionTargetSeq) throws Exception {
		Pack packNow = pack;

		//////////////////////////////////////////////////////////////////
		// 全アクションのSurvivableScoreを計算する。
		//////////////////////////////////////////////////////////////////
		double[] points = new double[4];
		double[] pointsTotal = new double[4];

		for (int tryIndex = 0; tryIndex < numTry; tryIndex++) {
			Pack packNext = packNow;
			for (int t = 0; t < numt; t++) {
				boolean[][] bombExist = new boolean[numField][numField];
				for (EEE bbb : packNext.sh.getBombEntry()) {
					bombExist[bbb.x][bbb.y] = true;
				}

				// 取りうるアクションを列挙して、乱択する。
				int[] actions = new int[4];
				for (int ai = 0; ai < 4; ai++) {
					if (ai + 10 == me) {
						if (t < actionTargetSeq.length) {
							// if (actionTargetSeq[t] == -2) {
							// actions[ai] = targetFirstAction;
							// } else
							if (actionTargetSeq[t] == -1) {
								actions[ai] = rand.nextInt(6);
							} else {
								actions[ai] = actionTargetSeq[t];
							}
						} else {
							actions[ai] = rand.nextInt(6);
						}
					} else {
						actions[ai] = rand.nextInt(6);
					}
				}

				packNext = fm.Step(packNext.board, packNext.abs, packNext.sh, actions);
			}

			for (int ai = 0; ai < 4; ai++) {
				if (packNow.abs[ai].isAlive == false) continue;

				double ppp;
				if (packNext.abs[ai].isAlive == false) {
					ppp = 0;
				} else {
					ppp = 1;
				}
				pointsTotal[ai] += 1;
				points[ai] += 1 * ppp;
			}
		}

		double[][] ret = new double[2][];
		ret[0] = points;
		ret[1] = pointsTotal;
		return ret;
	}

	// static private int[] sum(int depth, int[] actionTarget, double[][][][][] points, double[][][][][] pointsTotal) {
	//
	// int[] ret = ret[2][6];
	// return null;
	// }

	public static double[][][] evaluateSafetyScore(int numTry, Pack pack, int me) throws Exception {
		// TODO
		// evaluateSafetyScore_temp(numTry, pack, me);

		Pack packNow = pack;

		// TODO
		if (false) {
			boolean[][] bombExist = new boolean[numField][numField];
			for (EEE bbb : pack.sh.getBombEntry()) {
				bombExist[bbb.x][bbb.y] = true;
			}

			for (AgentEEE aaa : pack.sh.getAgentEntry()) {
				BBMUtility.numSurrounded_Rich(pack.board, bombExist, aaa.x, aaa.y);
			}
		}

		//////////////////////////////////////////////////////////////////
		// 全アクションのSurvivableScoreを計算する。
		//////////////////////////////////////////////////////////////////
		double[][] points = new double[4][6];
		double[][] pointsTotal = new double[4][6];
		for (int targetFirstAction = 0; targetFirstAction < 6; targetFirstAction++) {
			for (int tryIndex = 0; tryIndex < numTry; tryIndex++) {
				boolean[] lock = new boolean[4];
				Pack packNext = packNow;
				for (int t = 0; t < numt; t++) {

					AgentEEE agentsNow[] = new AgentEEE[4];
					for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
						agentsNow[aaa.agentID - 10] = aaa;
					}

					boolean[][] bombExist = new boolean[numField][numField];
					for (EEE bbb : packNext.sh.getBombEntry()) {
						bombExist[bbb.x][bbb.y] = true;
					}

					// 取りうるアクションを列挙して、乱択する。
					int[] actions = new int[4];
					for (int ai = 0; ai < 4; ai++) {
						if (t == 0 && ai == me - 10) {
							actions[ai] = targetFirstAction;
						} else if (lock[ai]) {
							actions[ai] = 0;
						} else {
							actions[ai] = rand.nextInt(6);
						}
					}

					packNext = fm.Step(packNext.board, packNext.abs, packNext.sh, actions);

					double weight = weights[t];

					AgentEEE agentsNext[] = new AgentEEE[4];
					for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
						agentsNext[aaa.agentID - 10] = aaa;
					}

					boolean[][] bombExistNext = new boolean[numField][numField];
					for (EEE bbb : packNext.sh.getBombEntry()) {
						bombExistNext[bbb.x][bbb.y] = true;
					}

					// 移動できるはずなのに、移動できていない場合は、一定確率でLockをかける。
					{
						for (int ai = 0; ai < 4; ai++) {
							if (packNext.abs[ai].isAlive == false) continue;
							EEE aaaNow = agentsNow[ai];
							EEE aaaNext = agentsNext[ai];
							if (aaaNow.x != aaaNext.x || aaaNow.y != aaaNext.y) continue;

							boolean movable = false;
							if (actions[ai] == 1) {
								int x = aaaNow.x - 1;
								int y = aaaNow.y;
								if (x >= 0) {
									int type = (int) packNext.board.data[x][y];
									if (type == Constant.Passage || Constant.isItem(type)) {
										movable = true;
									}
								}
							}

							if (actions[ai] == 2) {
								int x = aaaNow.x + 1;
								int y = aaaNow.y;
								if (x < numField) {
									int type = (int) packNext.board.data[x][y];
									if (type == Constant.Passage || Constant.isItem(type)) {
										movable = true;
									}
								}
							}

							if (actions[ai] == 3) {
								int x = aaaNow.x;
								int y = aaaNow.y - 1;
								if (y >= 0) {
									int type = (int) packNext.board.data[x][y];
									if (type == Constant.Passage || Constant.isItem(type)) {
										movable = true;
									}
								}
							}

							if (actions[ai] == 4) {
								int x = aaaNow.x;
								int y = aaaNow.y + 1;
								if (y < numField) {
									int type = (int) packNext.board.data[x][y];
									if (type == Constant.Passage || Constant.isItem(type)) {
										movable = true;
									}
								}
							}

							if (movable == true) {
								if (rand.nextDouble() < 0.5) {
									lock[ai] = true;
								}
							}
						}
					}

					for (int ai = 0; ai < 4; ai++) {
						if (packNow.abs[ai].isAlive == false) continue;

						double ppp = 1;
						if (packNext.abs[ai].isAlive == false) {
							ppp = 0;
						} else {
							AgentEEE aaa = agentsNext[ai];

							SurroundedInformation si = BBMUtility.numSurrounded_Rich(packNext.board, bombExistNext, aaa.x, aaa.y);
							if (si.numWall + si.numBombFixed + si.numBombFixedByAgent + si.numBombKickable + si.numAgent == 4 && si.numWall == 3) {
								if (si.numBombFixed == 1) {
									ppp = -12;
								} else if (si.numBombFixedByAgent == 1) {
									ppp = -9;
								} else if (si.numBombKickable == 1) {
									ppp = -6;
								} else if (si.numAgent == 1) {
									ppp = -3;
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
