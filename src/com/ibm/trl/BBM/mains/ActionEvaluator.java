package com.ibm.trl.BBM.mains;

import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;

import ibm.ANACONDA.Core.MatrixUtility;
import ibm.ANACONDA.Core.MyMatrix;

public class ActionEvaluator {
	static Random rand = new Random();
	static NormalDistribution nd = new NormalDistribution();
	static final int numField = GlobalParameter.numField;
	static final boolean verbose = GlobalParameter.verbose;
	double worstScoreThreshold = Math.log(4.5);
	double attackThreshold = -5;

	/**
	 * アクションを決定する。
	 */
	public int ComputeOptimalAction(int me, int friend, MapInformation map, Ability abs[], double[][][][] worstScores) throws Exception {
		worstScoreThreshold = 1.3;

		MyMatrix board = map.board;

		AgentEEE[] agentsNow = new AgentEEE[4];
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = map.getType(x, y);
				if (Constant.isAgent(type)) {
					AgentEEE aaa = new AgentEEE(x, y, type);
					agentsNow[aaa.agentID - 10] = aaa;
				}
			}
		}

		AgentEEE agentMe = agentsNow[me - 10];

		Ability ab = abs[me - 10];

		// 自分の位置から、各セルへの移動距離を計算しておく。
		MyMatrix dis = BBMUtility.ComputeOptimalDistance(board, agentMe.x, agentMe.y, Integer.MAX_VALUE);

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// safetyScoreを計算する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		double[][] safetyScore = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			if (ai == me - 10) {

				int numzero = 6;

				// 自分のスコアは最悪ケースで見積もる。
				if (numzero == 6) {
					for (int a = 0; a < 6; a++) {
						double min = Double.POSITIVE_INFINITY;
						for (int b = 0; b < 6; b++) {
							double num = worstScores[a][b][ai][3];
							if (num == 0) continue;
							double temp = worstScores[a][b][ai][1];
							if (temp < min) {
								min = temp;
							}
						}
						if (min == Double.POSITIVE_INFINITY) min = Double.NEGATIVE_INFINITY;
						safetyScore[ai][a] = min;
					}
				}

				numzero = 0;
				for (int a = 0; a < 6; a++) {
					if (safetyScore[ai][a] == Double.NEGATIVE_INFINITY) numzero++;
				}

				// 最悪ケースが全部0なら、しょうがないので平均で見積もる。
				if (numzero == 6) {
					for (int a = 0; a < 6; a++) {
						double sum = Double.NEGATIVE_INFINITY;
						double count = 0;
						for (int b = 0; b < 6; b++) {
							double num = worstScores[a][b][ai][3];
							if (num == 0) continue;
							sum = BBMUtility.add_log(sum, worstScores[a][b][ai][0]);
							count += num;
						}
						double ave;
						if (count == 0) {
							ave = Double.NEGATIVE_INFINITY;
						} else {
							ave = sum - Math.log(count);
						}
						safetyScore[ai][a] = ave;
					}
				}

				numzero = 0;
				for (int a = 0; a < 6; a++) {
					if (safetyScore[ai][a] == Double.NEGATIVE_INFINITY) numzero++;
				}

				if (numzero == 6) {
					System.out.println("次で死ぬね");
				}
			} else {
				// 自分以外のスコアは、平均で見積もる。
				for (int a = 0; a < 6; a++) {
					double sum = Double.NEGATIVE_INFINITY;
					double count = 0;
					for (int b = 0; b < 6; b++) {
						double num = worstScores[a][b][ai][3];
						if (num == 0) continue;
						sum = BBMUtility.add_log(sum, worstScores[a][b][ai][0]);
						count += num;
					}
					double ave;
					if (count == 0) {
						ave = Double.POSITIVE_INFINITY;
					} else {
						ave = sum - Math.log(count);
					}
					safetyScore[ai][a] = ave;
				}
			}
		}

		if (verbose) {
			System.out.println("==============================");
			System.out.println("==============================");
			MatrixUtility.OutputMatrix(new MyMatrix(safetyScore));
			System.out.println("==============================");
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 詰められる状態であれば、詰める。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (true) {

		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 自分の安全を確保した状態で、相手を危険にできるケースを探す。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		int action_final = -1;
		String reason = "なし";

		if (true) {
			int action_attack = -1;
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;
				double min = Double.POSITIVE_INFINITY;
				int mina = -1;
				for (int a = 0; a < 6; a++) {
					double sss = safetyScore[ai][a];
					double sssme = safetyScore[me - 10][a];
					if (sssme < attackThreshold) continue;
					if (Double.isNaN(sss)) continue;
					if (sss < min) {
						min = sss;
						mina = a;
					}
				}

				if (min < -22) {
					action_attack = mina;
				}
			}
			if (action_attack != -1) {
				System.out.println("追い詰め可能？a=" + action_attack);
				action_final = action_attack;
				reason = "攻撃するべし。";
			}
		}

		// ペア攻撃
		if (true) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;

				double min = Double.POSITIVE_INFINITY;
				int mina = -1;
				int minb = -1;
				for (int a = 0; a < 6; a++) {
					for (int b = 0; b < 6; b++) {
						double num = worstScores[a][b][ai][3];
						if (num == 0) continue;
						double ave = worstScores[a][b][ai][0] - Math.log(num);

						if (ave < min) {
							min = ave;
							mina = a;
							minb = b;
						}
					}
				}

				if (min < -22) {
					System.out.println("ペアで追い詰め可能？ai=" + ai + ", amin=" + mina + ", bmin=" + minb);
					System.out.println("ペアで追い詰め可能？ai=" + ai + ", amin=" + mina + ", bmin=" + minb);
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// TODO アイテムがあるなら、アイテムを取りに行く。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (action_final == -1) {
			int minx = -1, miny = -1, mindis = Integer.MAX_VALUE;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int type = map.getType(x, y);
					if (Constant.isItem(type)) {
						int ddd = (int) dis.data[x][y];
						if (ddd < mindis) {
							mindis = ddd;
							minx = x;
							miny = y;
						}
					}
				}
			}

			if (mindis < Integer.MAX_VALUE) {
				int action = BBMUtility.ComputeFirstDirection(dis, minx, miny);
				double score = safetyScore[me - 10][action];
				if (score > worstScoreThreshold) {
					action_final = action;
					reason = "新規。アイテムを取るために移動する。";
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// TODO 木があるなら、木を壊しに行く。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (action_final == -1) {
			int minx = -1, miny = -1, mindis = Integer.MAX_VALUE;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int power = map.getPower(x, y);
					if (power > 0) continue;
					int ddd = (int) dis.data[x][y];
					if (ddd < mindis) {
						int num = BBMUtility.numWoodBrakable(board, x, y, ab.strength);
						if (num > 0) {
							mindis = ddd;
							minx = x;
							miny = y;
						}
					}
				}
			}

			if (action_final == -1 && mindis == 0) {
				// Woodが壊せる位置にいたら、爆弾を置く。
				if (ab.numBombHold > 0) {
					int action = 5;
					double score = safetyScore[me - 10][action];
					if (score > worstScoreThreshold) {
						action_final = action;
						reason = "新規。木を壊すために爆弾を設置する。";
					}
				}
			}

			if (action_final == -1 && mindis < Integer.MAX_VALUE && mindis > 0) {
				// Woodを壊せる場所がみつかったら、そっちに移動する。
				int action = BBMUtility.ComputeFirstDirection(dis, minx, miny);
				double score = safetyScore[me - 10][action];
				if (score > worstScoreThreshold) {
					action_final = action;
					reason = "新規。木を壊すために移動する。";
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// TODO 安全な選択肢からランダムに選ぶ。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// TODO 上の選択肢で危険な状態に陥るなら、もっとも安全なアクションを取る。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (action_final == -1) {
			double scoreMax = Double.NEGATIVE_INFINITY;
			for (int action = 0; action < 6; action++) {
				double score = safetyScore[me - 10][action];
				if (score > scoreMax) {
					scoreMax = score;
					action_final = action;
					reason = "もっとも安全な選択肢を選ぶ。";
				}
			}

			if (verbose) {
				if (scoreMax <= 2) {
					System.out.println("やばい状況！！");
				}
			}
		}

		String line = String.format("%s, action=%d", reason, action_final);
		System.out.println(line);

		return action_final;
	}
}
