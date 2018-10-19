package com.ibm.trl.BBM.mains;

import java.util.Random;

import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class WorstScoreEvaluatorSingle {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final ForwardModel fm = new ForwardModel();
	static final int numt = 15;

	// 敵エージェントが分割して増えていくときの増加率。大きくすると最悪ケース見積もりに近くなる。
	double divideRate_near = 0.99;
	double divideRate_far = 1.0;
	double decayRate = 0.9;
	int numtNear = 100;

	static class FootPrint {
		double score = 0;
		int x_pre = -1;
		int y_pre = -1;
	}

	public double Do(int me, Pack packNow, Pack packNow_nagent, Pack packNext_onlyme, Pack packNext_nagent, boolean[][] firstActionSet) throws Exception {

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// numtステップ先まで盤面をシミュレーションしておく。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		Pack[] packs_nagent = new Pack[numt];
		if (true) {
			packs_nagent[0] = packNow_nagent;
			packs_nagent[1] = packNext_nagent;
			Pack packNext = packNext_nagent;
			int[] actions = new int[4];
			for (int t = 2; t < numt; t++) {
				packNext = fm.Step(packNext.board, packNext.flameLife, packNext.abs, packNext.sh, actions);
				packs_nagent[t] = packNext;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// エージェントを動かして、存在確率を計算する。酔歩。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MyMatrix[][] agentWeight = new MyMatrix[numt][4];
		double divideRate_near_log = Math.log(divideRate_near);
		double divideRate_far_log = Math.log(divideRate_far);

		for (int t = 0; t < numt; t++) {
			for (int ai = 0; ai < 4; ai++) {
				agentWeight[t][ai] = new MyMatrix(numField, numField, Double.NEGATIVE_INFINITY);
			}
		}

		for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
			int ai = aaa.agentID - 10;
			if (ai == me - 10) continue;
			agentWeight[0][ai].data[aaa.x][aaa.y] = divideRate_near_log;
		}

		for (int ai = 0; ai < 4; ai++) {
			if (ai == me - 10) continue;
			for (int t = 0; t < numt - 1; t++) {
				Pack packNext = packs_nagent[t + 1];

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						double weight = agentWeight[t][ai].data[x][y];
						if (weight == Double.NEGATIVE_INFINITY) continue;

						// 遷移先の数を数える。
						double count = 0;
						boolean[] able = new boolean[5];
						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (t == 0) {
								if (dir == 0) {
									if (firstActionSet[ai][0] == false && firstActionSet[ai][5] == false) continue;
								} else {
									if (firstActionSet[ai][dir] == false) continue;
								}
							}
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int type = (int) packNext.board.data[x2][y2];
							if (Constant.isWall(type)) continue;
							if (dir != 0 && type == Constant.Bomb) continue;
							if (type == Constant.Flames) continue;
							able[dir] = true;
							count++;
						}

						// 1/countに分割して次ステップの存在確率に足し込む。
						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (able[dir] == false) continue;
							if (t <= numtNear) {
								double weightNext = weight + divideRate_near_log;
								if (weightNext > agentWeight[t + 1][ai].data[x2][y2]) {
									agentWeight[t + 1][ai].data[x2][y2] = weightNext;
								}
							} else {
								double weightNext = add_log(weight - Math.log(count), divideRate_far_log);
								if (weightNext > agentWeight[t + 1][ai].data[x2][y2]) {
									agentWeight[t + 1][ai].data[x2][y2] = weightNext;
								}
							}
						}
					}
				}

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						if (agentWeight[t + 1][ai].data[x][y] < agentWeight[t][ai].data[x][y]) {
							agentWeight[t + 1][ai].data[x][y] = agentWeight[t][ai].data[x][y];
						}
					}
				}

				// Flamesが存在したら、0にクリアする。
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						int type = (int) packNext.board.data[x][y];
						if (type == Constant.Flames) {
							agentWeight[t + 1][ai].data[x][y] = Double.NEGATIVE_INFINITY;
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 全行動の経路のスコアを計算してみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MyMatrix[] reachProb = new MyMatrix[numt];
		MyMatrix[] aliveProb = new MyMatrix[numt];

		if (true) {
			for (int t = 0; t < numt; t++) {
				reachProb[t] = new MyMatrix(numField, numField, Double.NEGATIVE_INFINITY);
				aliveProb[t] = new MyMatrix(numField, numField, Double.NEGATIVE_INFINITY);
			}
			for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
				if (aaa.agentID != me) continue;
				reachProb[0].data[aaa.x][aaa.y] = 0;
				aliveProb[0].data[aaa.x][aaa.y] = 0;
			}
			for (AgentEEE aaa : packNext_onlyme.sh.getAgentEntry()) {
				if (aaa.agentID != me) continue;
				reachProb[1].data[aaa.x][aaa.y] = 0;
				aliveProb[1].data[aaa.x][aaa.y] = 0;
			}

			for (int t = 1; t < numt - 1; t++) {
				Pack packNext = packs_nagent[t + 1];
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						double count = 0;
						boolean[] able = new boolean[5];
						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (t == 0) {
								if (dir == 0) {
									if (firstActionSet[me - 10][0] == false && firstActionSet[me - 10][5] == false) continue;
								} else {
									if (firstActionSet[me - 10][dir] == false) continue;
								}
							}
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int type = (int) packNext.board.data[x2][y2];
							if (Constant.isWall(type)) continue;
							if (dir != 0 && type == Constant.Bomb) continue;
							if (type == Constant.Flames) continue;
							able[dir] = true;
							count++;
						}
						if (count == 0) continue;

						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (able[dir] == false) continue;

							double nonEnemyProb = 0;
							for (int ai = 0; ai < 4; ai++) {
								if (ai == me - 10) continue;
								double temp = agentWeight[t + 1][ai].data[x2][y2];
								if (temp > 0) temp = 0;
								nonEnemyProb += sub_log(0, temp);
							}

							// リーチ確率の計算
							if (true) {
								double probNow = reachProb[t].data[x][y];
								if (probNow != Double.NEGATIVE_INFINITY) {
									double probNext = probNow + nonEnemyProb;
									if (probNext > reachProb[t + 1].data[x2][y2]) {
										reachProb[t + 1].data[x2][y2] = probNext;
									}
								}
							}

							// 生存確率の計算
							if (true) {
								double probNow = aliveProb[t].data[x][y];
								if (probNow != Double.NEGATIVE_INFINITY) {
									double moveNextProb = probNow + nonEnemyProb - Math.log(count);
									double stayProb = sub_log(probNow - Math.log(count), moveNextProb);
									aliveProb[t + 1].data[x][y] = add_log(aliveProb[t + 1].data[x][y], stayProb);
									aliveProb[t + 1].data[x2][y2] = add_log(aliveProb[t + 1].data[x2][y2], moveNextProb);
								}
							}
						}
					}
				}
			}
		}

		// 生存確率
		if (false) {
			double temp = aliveProb[numt - 1].normL1();
			return temp;
		}

		// 到達セルの前ステップ総和
		if (true) {
			double sum = 0;
			double total = 0;
			for (int t = 1; t < numt; t++) {
				double temp = total_log(reachProb[t]);
				System.out.println(t + ", " + temp);
			}
			double ave = sum / total;
			// return ave;
		}

		// 到達セル
		if (true) {
			double temp = total_log(reachProb[numt - 1]);
			return temp;
		}

		return 0;
	}

	public double Do2(int me, Pack packNow, Pack packNow_nagent, Pack packNext_onlyme, Pack packNext_nagent, boolean[][] firstActionSet) throws Exception {

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// numtステップ先まで盤面をシミュレーションしておく。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		Pack[] packs_nagent = new Pack[numt];
		if (true) {
			packs_nagent[0] = packNow_nagent;
			packs_nagent[1] = packNext_nagent;
			Pack packNext = packNext_nagent;
			int[] actions = new int[4];
			for (int t = 2; t < numt; t++) {
				packNext = fm.Step(packNext.board, packNext.flameLife, packNext.abs, packNext.sh, actions);
				packs_nagent[t] = packNext;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// エージェントを動かして、存在確率を計算する。酔歩。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MyMatrix[][] agentsStepCounter = new MyMatrix[numt][4];

		for (int t = 0; t < numt; t++) {
			for (int ai = 0; ai < 4; ai++) {
				agentsStepCounter[t][ai] = new MyMatrix(numField, numField, numt);
			}
		}

		for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
			int ai = aaa.agentID - 10;
			if (ai == me - 10) continue;
			agentsStepCounter[0][ai].data[aaa.x][aaa.y] = 0;
		}

		for (int ai = 0; ai < 4; ai++) {
			if (ai == me - 10) continue;
			for (int t = 0; t < numt - 1; t++) {
				Pack packNext = packs_nagent[t + 1];

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						double stepCount = agentsStepCounter[t][ai].data[x][y];
						if (stepCount == numt) continue;

						// 遷移先の数を数える。
						boolean[] able = new boolean[5];
						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (t == 0) {
								if (dir == 0) {
									if (firstActionSet[ai][0] == false && firstActionSet[ai][5] == false) continue;
								} else {
									if (firstActionSet[ai][dir] == false) continue;
								}
							}
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int type = (int) packNext.board.data[x2][y2];
							if (Constant.isWall(type)) continue;
							if (dir != 0 && type == Constant.Bomb) continue;
							if (type == Constant.Flames) continue;
							able[dir] = true;
						}

						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (able[dir] == false) continue;
							double stepCountNext = stepCount + 1;
							if (stepCountNext < agentsStepCounter[t + 1][ai].data[x2][y2]) {
								agentsStepCounter[t + 1][ai].data[x2][y2] = stepCountNext;
							}
						}
					}
				}

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						if (agentsStepCounter[t + 1][ai].data[x][y] > agentsStepCounter[t][ai].data[x][y]) {
							agentsStepCounter[t + 1][ai].data[x][y] = agentsStepCounter[t][ai].data[x][y];
						}
					}
				}

				// Flamesが存在したら、0にクリアする。
				if (false) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							int type = (int) packNext.board.data[x][y];
							if (type == Constant.Flames) {
								agentsStepCounter[t + 1][ai].data[x][y] = Double.NEGATIVE_INFINITY;
							}
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 全行動の経路のスコアを計算してみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MyMatrix[] hitNearestStep = new MyMatrix[numt];
		if (true) {
			for (int t = 0; t < numt; t++) {
				hitNearestStep[t] = new MyMatrix(numField, numField, Double.NEGATIVE_INFINITY);
			}
			for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
				if (aaa.agentID != me) continue;
				hitNearestStep[0].data[aaa.x][aaa.y] = numt;
			}
			for (AgentEEE aaa : packNext_onlyme.sh.getAgentEntry()) {
				if (aaa.agentID != me) continue;
				hitNearestStep[1].data[aaa.x][aaa.y] = numt;
			}

			for (int t = 1; t < numt - 1; t++) {
				Pack packNext = packs_nagent[t + 1];
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {

						double nearestStepNow = hitNearestStep[t].data[x][y];
						if (nearestStepNow == Double.NEGATIVE_INFINITY) continue;

						double count = 0;
						boolean[] able = new boolean[5];
						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (t == 0) {
								if (dir == 0) {
									if (firstActionSet[me - 10][0] == false && firstActionSet[me - 10][5] == false) continue;
								} else {
									if (firstActionSet[me - 10][dir] == false) continue;
								}
							}
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int type = (int) packNext.board.data[x2][y2];
							if (Constant.isWall(type)) continue;
							if (dir != 0 && type == Constant.Bomb) continue;
							if (type == Constant.Flames) continue;
							able[dir] = true;
							count++;
						}
						if (count == 0) continue;

						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (able[dir] == false) continue;

							double nearestStepNext = nearestStepNow;
							for (int ai = 0; ai < 4; ai++) {
								if (ai == me - 10) continue;
								double stepCount = agentsStepCounter[t + 1][ai].data[x2][y2];
								if (stepCount < nearestStepNext) {
									nearestStepNext = stepCount;
								}
							}

							if (nearestStepNext > hitNearestStep[t + 1].data[x2][y2]) {
								hitNearestStep[t + 1].data[x2][y2] = nearestStepNext;
							}
						}
					}
				}
			}
		}

		// 到達セルの前ステップ総和
		if (true) {
			double rate = 7;
			double ratelog = Math.log(rate);
			double sum = 0;
			double weightTotal = 0;
			double decay = 0.9;
			for (int t = 1; t < numt; t++) {
				MyMatrix mat = hitNearestStep[t];
				double sss = Double.NEGATIVE_INFINITY;
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						double score = mat.data[x][y];
						double temp = ratelog * (score - numt);
						sss = add_log(sss, temp);
					}
				}
				System.out.println(t + ", " + sss + ", " + Math.exp(sss));

				double weight = Math.pow(decay, t - 1);
				sum += sss * weight;
				weightTotal += weight;
			}
			double average = sum / weightTotal;
			System.out.println("重み付きスコア: " + average);
			// return average;
		}

		// 到達セル
		if (true) {
			double rate = 7;
			double ratelog = Math.log(rate);
			double sum = 0;
			double weightTotal = 0;
			double decay = 0.9;
			// for (int t = 1; t < numt; t++) {
			{
				int t = numt - 1;
				MyMatrix mat = hitNearestStep[t];
				double sss = Double.NEGATIVE_INFINITY;
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						double score = mat.data[x][y];
						double temp = ratelog * (score - numt);
						sss = add_log(sss, temp);
					}
				}
				System.out.println(t + ", " + sss + ", " + Math.exp(sss));

				double weight = Math.pow(decay, t - 1);
				sum += sss * weight;
				weightTotal += weight;
			}
			double average = sum / weightTotal;
			System.out.println("重み付きスコア: " + average);
			return average;
		}

		return 0;
	}

	static double add_log(double a, double b) {
		if (a == Double.NEGATIVE_INFINITY && b == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;
		if (a > b) {
			return Math.log(1 + Math.exp(b - a)) + a;
		} else {
			return Math.log(Math.exp(a - b) + 1) + b;
		}
	}

	static double sub_log(double a, double b) {
		if (a == Double.NEGATIVE_INFINITY && b == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;
		if (a > b) {
			return Math.log(1 - Math.exp(b - a)) + a;
		} else {
			return Math.log(Math.exp(a - b) - 1) + b;
		}
	}

	static double total_log(MyMatrix a) {

		double max = Double.NEGATIVE_INFINITY;
		for (int t = 0; t < a.numt; t++) {
			for (int d = 0; d < a.numd; d++) {
				if (a.data[t][d] > max) {
					max = a.data[t][d];
				}
			}
		}
		if (max == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;

		double total = 0;
		for (int t = 0; t < a.numt; t++) {
			for (int d = 0; d < a.numd; d++) {
				total += Math.exp(a.data[t][d] - max);
			}
		}

		return Math.log(total) + max;
	}
}
