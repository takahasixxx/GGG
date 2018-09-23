package com.ibm.trl.BBM.mains;

import java.util.Random;

import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;

public class SafetyScoreEvaluator {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final ForwardModel fm = new ForwardModel();

	public static double[][] evaluateSafetyScore(int numTry, Pack pack, int[][] actionsSeq, boolean avoidContact) throws Exception {
		Pack packNow = pack;

		AgentEEE agentsNow[] = new AgentEEE[4];
		for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
			agentsNow[aaa.agentID - 10] = aaa;
		}

		int numt = actionsSeq.length;

		double[] points = new double[4];
		double[] pointsTotal = new double[4];
		for (int tryIndex = 0; tryIndex < numTry; tryIndex++) {
			Pack packNext = packNow;
			double[] contact = new double[4];
			boolean[] tsume = new boolean[4];
			for (int t = 0; t < numt; t++) {
				// 取りうるアクションを列挙して、乱択する。
				int[] actions = new int[4];
				for (int ai = 0; ai < 4; ai++) {
					int temp = actionsSeq[t][ai];
					if (temp == -1) {
						actions[ai] = rand.nextInt(6);
					} else if (temp == -2) {
						actions[ai] = rand.nextInt(5);
					} else {
						actions[ai] = temp;
					}
				}
				packNext = fm.Step(packNext.board, packNext.flameLife, packNext.abs, packNext.sh, actions);

				// TODO 詰められる状況か判断する。
				if (t < 2) {
					for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
						int ks = KillScoreEvaluator.computeKillScore(packNext, aaa.agentID - 10);
						if (ks != Integer.MAX_VALUE) {
							tsume[aaa.agentID - 10] = true;
						}
					}
				}

				// TODO コンタクト回数を数える。
				if (avoidContact && t < 1) {
					for (AgentEEE aaa1 : packNext.sh.getAgentEntry()) {
						if (packNext.abs[aaa1.agentID - 10].isAlive == false) continue;
						for (AgentEEE aaa2 : packNext.sh.getAgentEntry()) {
							if (packNext.abs[aaa2.agentID - 10].isAlive == false) continue;
							if (aaa1 == aaa2) continue;
							int dis = Math.abs(aaa1.x - aaa2.x) + Math.abs(aaa1.y - aaa2.y);
							if (dis <= 2) {
								contact[aaa1.agentID - 10]++;
							}
						}
					}
				}
			}

			AgentEEE agentsNext[] = new AgentEEE[4];
			for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
				agentsNext[aaa.agentID - 10] = aaa;
			}

			boolean[][] bombExistNext = new boolean[numField][numField];
			for (EEE bbb : packNext.sh.getBombEntry()) {
				bombExistNext[bbb.x][bbb.y] = true;
			}

			// TODO 死亡カウント、追い込まれカウントとかを計算して、評価する。評価の仕方をいろいろ試したい。
			for (int ai = 0; ai < 4; ai++) {
				if (packNow.abs[ai].isAlive == false) continue;
				if (agentsNow[ai] == null) continue;

				double ppp = 1;
				if (packNext.abs[ai].isAlive == false) {
					ppp = 0;
				} else if (tsume[ai]) {
					ppp = 0;
				} else {
					ppp = 1 - contact[ai] / 1 * 0.3;
				}

				pointsTotal[ai] += 1;
				points[ai] += ppp;
			}
		}
		double[][] ret = new double[2][];
		ret[0] = points;
		ret[1] = pointsTotal;
		return ret;
	}

	static public double[][][][] computeSafetyScore(Pack pack, int me, int friend) throws Exception {
		int numt = 13;

		// Nステップ後の各エージェントの位置を計算しておく。ここに入ったら痛い。
		int[][][][] posWorst = new int[numt][4][numField][numField];
		for (AgentEEE aaa : pack.sh.getAgentEntry()) {
			for (int t = 0; t < numt; t++) {

			}
		}

		// コンタクトを避けるかどうか。盤面に爆弾がなかったら接近戦する。
		boolean avoidContact = true;
		// if (pack.sh.getBombEntry().size() == 0) {
		// avoidContact = false;
		// }

		int[][] actionsSeq = new int[numt][4];
		for (int t = 0; t < numt; t++) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) {
					actionsSeq[t][ai] = -2;
				} else {
					actionsSeq[t][ai] = -2;
				}
			}
		}

		if (false) {
			for (int t = 5; t < numt; t++) {
				for (int ai = 0; ai < 4; ai++) {
					actionsSeq[t][ai] = 0;
				}
			}
		}

		double[][][][] points = new double[6][6][4][2];
		for (int a = 0; a < 6; a++) {
			for (int b = 0; b < 6; b++) {
				actionsSeq[0][me - 10] = a;
				// actionsSeq[0][friend - 10] = b;
				actionsSeq[1][me - 10] = b;
				double[][] temp = SafetyScoreEvaluator.evaluateSafetyScore(100, pack, actionsSeq, avoidContact);
				for (int ai = 0; ai < 4; ai++) {
					points[a][b][ai][0] += temp[0][ai];
					points[a][b][ai][1] += temp[1][ai];
				}
				// TODO
				if (false) {
					for (int ai = 0; ai < 4; ai++) {
						String line = String.format("a=%d, b=%d, ai=%d, num=%f, live=%f", a, b, ai, temp[0][ai], temp[1][ai]);
						System.out.println(line);
					}
				}
			}
		}

		return points;
	}

}
