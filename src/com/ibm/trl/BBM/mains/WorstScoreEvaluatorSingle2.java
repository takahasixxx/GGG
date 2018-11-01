package com.ibm.trl.BBM.mains;

import java.util.Random;

import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class WorstScoreEvaluatorSingle2 {

	static final int INSTRUCTION_BOARD = 0;
	static final int INSTRUCTION_STAY = 1;
	static final int INSTRUCTION_ALLMOVE = 2;

	static final Random rand = new Random();
	static final int numField = GlobalParameter.numField;
	static final ForwardModel fm = new ForwardModel();

	// double rateLevel = 3.0;
	double rateLevel = GlobalParameter.rateLevel;

	public double[][] Do3(int me, Pack[] packsOrg, int[][] instructions) throws Exception {

		int num = 20;

		double rrr = Math.pow(1.0 / 3.0, 5.0 / num);

		double[][] temp = new double[4][num];

		for (int i = 0; i < num; i++) {
			double divisionRate = 1.0 - i * 0.01;
			double[][] sss = Do4(packsOrg, instructions, divisionRate);
			for (int ai = 0; ai < 4; ai++) {
				double score = sss[ai][0] / sss[ai][1];
				temp[ai][i] = score;
			}
		}

		double[][] temp2 = new double[4][num];
		for (int ai = 0; ai < 4; ai++) {
			temp2[ai][0] = temp[ai][0];
		}

		for (int ai = 0; ai < 4; ai++) {
			for (int i = 1; i < num; i++) {
				temp2[ai][i] = temp[ai][i] - temp[ai][i - 1];
			}
		}

		double[][] ret = new double[4][2];
		for (int ai = 0; ai < 4; ai++) {
			double total = 0;
			for (int i = 0; i < num; i++) {
				double weight = Math.pow(rrr, i);
				total += temp2[ai][i] * weight;
			}
			if (Double.isNaN(total)) {
				ret[ai][0] = Double.NEGATIVE_INFINITY;
				ret[ai][1] = 0;
			} else {
				ret[ai][0] = Math.log(total);
				ret[ai][1] = 1;
			}
		}

		return ret;
	}

	public double[][] Do4(Pack[] packsOrg, int[][] instructions, double divisionRate) throws Exception {

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本変数の作成
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		int numt = instructions.length;

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// numtステップ先まで盤面をシミュレーションしておく。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		Pack[] packs = new Pack[numt];
		for (int t = 0; t < packsOrg.length; t++) {
			packs[t] = packsOrg[t];
		}

		Pack[] packsNA = new Pack[numt];
		{
			packsNA[0] = packs[0];
			for (int t = 1; t < numt; t++) {
				if (packs[t] != null) {
					// 元系列が存在するときは、そのままコピーする。
					packsNA[t] = packs[t];
				} else {
					// 元系列が存在しないときは、ステップ計算する。
					Pack packPreNA;
					if (packs[t - 1] == null) {
						packPreNA = packsNA[t - 1];
					} else {
						Pack packPre = packs[t - 1];
						packPreNA = new Pack(packPre);
						for (int ai = 0; ai < 4; ai++) {
							packPreNA.removeAgent(ai + 10);
						}
					}
					int[] actions = new int[4];
					Pack packNextNA = fm.Step(packPreNA, actions);
					packsNA[t] = packNextNA;
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// エージェントを動かして、存在確率を計算する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// 仲間エージェントの動き。最初の位置から動かない。
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MyMatrix[][] prob_stay = new MyMatrix[numt][4];
		for (int ai = 0; ai < 4; ai++) {
			for (int t = 0; t < numt; t++) {
				prob_stay[t][ai] = new MyMatrix(numField, numField);
			}
		}

		for (int ai = 0; ai < 4; ai++) {
			Pack packNow = packsNA[0];
			AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
			if (agentNow == null) continue;
			prob_stay[0][ai].data[agentNow.x][agentNow.y] = 1;
		}

		for (int ai = 0; ai < 4; ai++) {
			for (int t = 0; t < numt - 1; t++) {
				int instruction = instructions[t][ai];
				Pack packNow = packsNA[t];
				Pack packNext = packsNA[t + 1];

				if (instruction == INSTRUCTION_BOARD) {
					AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
					AgentEEE agentNext = packNext.sh.getAgent(ai + 10);
					if (agentNow == null || agentNext == null) continue;

					int x2 = agentNext.x;
					int y2 = agentNext.y;
					prob_stay[t + 1][ai].data[x2][y2] = 1;
				} else {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							double probNow = prob_stay[t][ai].data[x][y];
							if (probNow == 0) continue;
							prob_stay[t + 1][ai].data[x][y] = probNow;
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// 敵エージェントの動き
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MyMatrix[][] prob_move = new MyMatrix[numt][4];
		for (int ai = 0; ai < 4; ai++) {
			for (int t = 0; t < numt; t++) {
				prob_move[t][ai] = new MyMatrix(numField, numField);
			}
		}

		MyMatrix one = new MyMatrix(numField, numField, 1);

		for (int ai = 0; ai < 4; ai++) {
			Pack packNow = packsNA[0];
			AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
			if (agentNow == null) continue;
			prob_move[0][ai].data[agentNow.x][agentNow.y] = 1;
		}

		for (int ai = 0; ai < 4; ai++) {
			for (int t = 0; t < numt - 1; t++) {
				int instruction = instructions[t][ai];
				Pack packNow = packsNA[t];
				Pack packNext = packsNA[t + 1];
				MyMatrix openProbNext = new MyMatrix(numField, numField, 1);

				if (instruction == INSTRUCTION_BOARD) {
					AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
					AgentEEE agentNext = packNext.sh.getAgent(ai + 10);
					if (agentNow == null || agentNext == null) continue;
					int x = agentNow.x;
					int y = agentNow.y;
					double probNow = prob_move[t][ai].data[x][y];
					int x2 = agentNext.x;
					int y2 = agentNext.y;
					openProbNext.data[x2][y2] = 1 - probNow;
				} else if (instruction == INSTRUCTION_STAY) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							double probNow = prob_move[t][ai].data[x][y];
							if (probNow == 0) continue;
							openProbNext.data[x][y] = 1 - probNow;
						}
					}
				} else if (instruction == INSTRUCTION_ALLMOVE) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							double probNow = prob_move[t][ai].data[x][y];
							if (probNow == 0) continue;

							// 遷移先の数を数える。
							boolean[] able = new boolean[5];
							for (int[] vec : GlobalParameter.onehopList) {
								int dir = vec[0];
								int dx = vec[1];
								int dy = vec[2];
								int x2 = x + dx;
								int y2 = y + dy;
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

								double openProb = openProbNext.data[x2][y2];
								openProb = openProb * (1 - probNow * divisionRate);
								openProbNext.data[x2][y2] = openProb;
							}
						}
					}
				}

				prob_move[t + 1][ai] = one.minus(openProbNext);
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 全行動の経路のスコアを計算してみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		double[][] scores = new double[4][2];

		for (int ai = 0; ai < 4; ai++) {

			MyMatrix[] arriveProb = new MyMatrix[numt];
			for (int t = 0; t < numt; t++) {
				arriveProb[t] = new MyMatrix(numField, numField);
			}

			{
				Pack packNow = packsNA[0];
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) continue;
				arriveProb[0].data[agentNow.x][agentNow.y] = 1;
			}

			for (int t = 0; t < numt - 1; t++) {

				int instruction = instructions[t][ai];
				Pack packNow = packsNA[t];
				Pack packNext = packsNA[t + 1];

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {

						double arriveProbNow = arriveProb[t].data[x][y];
						if (arriveProbNow == 0) continue;

						boolean[] able = new boolean[5];
						for (int[] vec : GlobalParameter.onehopList) {
							int dir = vec[0];
							int dx = vec[1];
							int dy = vec[2];
							int x2 = x + dx;
							int y2 = y + dy;
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							if (instruction == INSTRUCTION_BOARD) {
								AgentEEE agentNext = packNext.sh.getAgent(ai + 10);
								if (agentNext == null) continue;
								if (x2 != agentNext.x || y2 != agentNext.y) continue;
							} else if (instruction == INSTRUCTION_STAY) {
								if (dx != 0 || dy != 0) continue;
							} else if (instruction == INSTRUCTION_ALLMOVE) {
							}
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

							// double nearestStepNext = arriveProbNow;
							double open = 1;
							for (int ai2 = 0; ai2 < 4; ai2++) {
								if (ai2 == ai) continue;

								// TODO 友達の計算の仕方をもう少し賢くする。
								MyMatrix probNext = prob_move[t + 1][ai2];
								MyMatrix probNow = prob_move[t][ai2];
								if (ai == 0) {
									if (ai2 == 2) {
										probNext = prob_stay[t + 1][ai2];
										probNow = prob_stay[t][ai2];
									}
								} else if (ai == 1) {
									if (ai2 == 3) {
										probNext = prob_stay[t + 1][ai2];
										probNow = prob_stay[t][ai2];
									}
								} else if (ai == 2) {
									if (ai2 == 0) {
										probNext = prob_stay[t + 1][ai2];
										probNow = prob_stay[t][ai2];
									}
								} else if (ai == 3) {
									if (ai2 == 1) {
										probNext = prob_stay[t + 1][ai2];
										probNow = prob_stay[t][ai2];
									}
								}

								double pNext = probNext.data[x2][y2];
								double stepCross1 = probNow.data[x2][y2];
								double stepCross2 = probNext.data[x][y];

								if (stepCross1 == 1 && stepCross2 == 1) {
									open = 0;
								} else {
									open = open * (1 - pNext);
								}
							}

							double arrivePorbNext = arriveProbNow * open;

							if (arrivePorbNext > arriveProb[t + 1].data[x2][y2]) {
								arriveProb[t + 1].data[x2][y2] = arrivePorbNext;
							}
						}
					}
				}
			}

			// 到達セル
			if (true) {
				double sss = arriveProb[numt - 1].normL1();
				scores[ai][0] += sss;
				scores[ai][1] += 1;
			}
		}

		return scores;
	}
}
