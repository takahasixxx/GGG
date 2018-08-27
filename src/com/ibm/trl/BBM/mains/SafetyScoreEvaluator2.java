package com.ibm.trl.BBM.mains;

import java.util.Random;

import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;

public class SafetyScoreEvaluator2 {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;

	static final ForwardModel fm = new ForwardModel();
	static final double decayRate = 1;
	static final int numt = 12;
	static final int numTry = 5;
	static final double[] weights = new double[numt];

	static {
		for (int t = 0; t < numt; t++) {
			weights[t] = Math.pow(decayRate, t);
		}

	}

	static class Result {
		double scoreBest = Double.MAX_VALUE;
		int[] actionSeqBest = null;
	}

	private static void rrr(int numTry, Pack pack, int me, int you, int depth, int maxDepth, int[] actionSeq, Result result) throws Exception {
		if (depth == maxDepth) {
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
				rrr(numTry, pack, me, you, depth + 1, maxDepth, actionSeq, result);
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

			// int numWall = BBMUtility.numWall(pack.board, aaa.x, aaa.y);
			// if (numWall != 3) continue;

			// MyMatrix board2 = new MyMatrix(pack.board);
			// board2.data[aaa.x][aaa.y] = Constant.Passage;
			// MyMatrix dis = BBMUtility.ComputeOptimalDistance(board2, agentMe.x, agentMe.y, Integer.MAX_VALUE);
			// int d = (int) dis.data[aaa.x][aaa.y];
			// if (d > 2) continue;

			for (int numDepth = 2; numDepth < 3; numDepth++) {
				Result result = new Result();
				int[] actionSeq = new int[numDepth];
				rrr(numTry, pack, me, aaa.agentID, 0, numDepth, actionSeq, result);

				System.out.println("################, " + aaa.agentID + ", " + result.scoreBest);

				if (result.scoreBest < 0.01) {
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

}
