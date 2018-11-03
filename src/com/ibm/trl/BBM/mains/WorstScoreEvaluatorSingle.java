package com.ibm.trl.BBM.mains;

import java.util.Arrays;
import java.util.Random;

import com.ibm.trl.BBM.mains.Agent.ModelParameter;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class WorstScoreEvaluatorSingle {

	static final int INSTRUCTION_BOARD = 0;
	static final int INSTRUCTION_STAY = 1;
	static final int INSTRUCTION_ALLMOVE = 2;

	static final Random rand = new Random();
	static final int numField = GlobalParameter.numField;
	static final ForwardModel fm = new ForwardModel();

	ModelParameter param;

	public WorstScoreEvaluatorSingle(ModelParameter param) {
		this.param = param;
	}

	public double[][] Do3(Pack[] packsOrg, int[][] instructions) throws Exception {

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本変数の作成
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		int numt = instructions.length;
		double rateLevel = param.rateLevel;
		double gainOffset = param.gainOffset;

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
		MyMatrix[][] stepMaps_stay = new MyMatrix[numt][4];
		for (int ai = 0; ai < 4; ai++) {
			for (int t = 0; t < numt; t++) {
				stepMaps_stay[t][ai] = new MyMatrix(numField, numField, numt);
			}
		}

		for (int ai = 0; ai < 4; ai++) {
			Pack packNow = packsNA[0];
			AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
			if (agentNow == null) continue;
			stepMaps_stay[0][ai].data[agentNow.x][agentNow.y] = 0;
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
					int x = agentNow.x;
					int y = agentNow.y;
					double stepNow = stepMaps_stay[t][ai].data[x][y];
					int x2 = agentNext.x;
					int y2 = agentNext.y;

					if (true) {
						double stepNext;
						if (x == x2 && y == y2) {
							stepNext = stepNow;
						} else {
							// TODO どっちがいいのだろうか？
							// stepNext = stepNow + 1;
							stepNext = t + 1;
						}
						stepMaps_stay[t + 1][ai].data[x2][y2] = stepNext;
					}
				} else {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							double stepNow = stepMaps_stay[t][ai].data[x][y];
							if (stepNow == numt) continue;

							if (true) {
								double stepNext = stepNow;
								stepMaps_stay[t + 1][ai].data[x][y] = stepNext;
							}
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// 敵エージェントの動き
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		MyMatrix[][] stepMaps_move = new MyMatrix[numt][4];
		for (int ai = 0; ai < 4; ai++) {
			for (int t = 0; t < numt; t++) {
				stepMaps_move[t][ai] = new MyMatrix(numField, numField, numt);
			}
		}

		for (int ai = 0; ai < 4; ai++) {
			Pack packNow = packsNA[0];
			AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
			if (agentNow == null) continue;
			stepMaps_move[0][ai].data[agentNow.x][agentNow.y] = 0;
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
					int x = agentNow.x;
					int y = agentNow.y;
					double stepNow = stepMaps_move[t][ai].data[x][y];
					int x2 = agentNext.x;
					int y2 = agentNext.y;

					if (true) {
						double stepNext;
						if (x == x2 && y == y2) {
							stepNext = stepNow;
						} else {
							// TODO どっちがいいのだろうか？
							// stepNext = stepNow + 1;
							stepNext = t + 1;
						}
						stepMaps_move[t + 1][ai].data[x2][y2] = stepNext;
					}
				} else if (instruction == INSTRUCTION_STAY) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							double stepNow = stepMaps_move[t][ai].data[x][y];
							if (stepNow == numt) continue;

							if (true) {
								double stepNext = stepNow;
								stepMaps_move[t + 1][ai].data[x][y] = stepNext;
							}
						}
					}
				} else if (instruction == INSTRUCTION_ALLMOVE) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							double stepNow = stepMaps_move[t][ai].data[x][y];
							if (stepNow == numt) continue;

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

								if (true) {
									double stepNext;
									if (dir == 0) {
										stepNext = stepNow;
									} else {
										// TODO どっちがいいのだろうか？
										// stepNext = stepNow + 1;
										stepNext = t + 1;
									}
									if (stepNext < stepMaps_move[t + 1][ai].data[x2][y2]) {
										stepMaps_move[t + 1][ai].data[x2][y2] = stepNext;
									}
								}
							}
						}
					}

					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							int type = (int) packNext.board.data[x][y];
							if (type == Constant.Flames) {
								stepMaps_move[t + 1][ai].data[x][y] = numt;
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

		double[][] scores = new double[4][2];
		for (int ai = 0; ai < 4; ai++) {
			scores[ai][0] = Double.NEGATIVE_INFINITY;
		}

		for (int ai = 0; ai < 4; ai++) {

			MyMatrix[] hitNearestStep = new MyMatrix[numt];
			for (int t = 0; t < numt; t++) {
				hitNearestStep[t] = new MyMatrix(numField, numField, Double.NEGATIVE_INFINITY);
			}

			{
				Pack packNow = packsNA[0];
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) continue;
				hitNearestStep[0].data[agentNow.x][agentNow.y] = numt;
			}

			for (int t = 0; t < numt - 1; t++) {

				int instruction = instructions[t][ai];
				Pack packNow = packsNA[t];
				Pack packNext = packsNA[t + 1];

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {

						double nearestStepNow = hitNearestStep[t].data[x][y];
						if (nearestStepNow == Double.NEGATIVE_INFINITY) continue;

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

							double nearestStepNext = nearestStepNow;
							for (int ai2 = 0; ai2 < 4; ai2++) {
								if (ai2 == ai) continue;

								// TODO 友達の計算の仕方をもう少し賢くする。
								MyMatrix stepMapNext = stepMaps_move[t + 1][ai2];
								MyMatrix stepMapNow = stepMaps_move[t][ai2];
								if (ai == 0) {
									if (ai2 == 2) {
										stepMapNext = stepMaps_stay[t + 1][ai2];
										stepMapNow = stepMaps_stay[t][ai2];
									}
								} else if (ai == 1) {
									if (ai2 == 3) {
										stepMapNext = stepMaps_stay[t + 1][ai2];
										stepMapNow = stepMaps_stay[t][ai2];
									}
								} else if (ai == 2) {
									if (ai2 == 0) {
										stepMapNext = stepMaps_stay[t + 1][ai2];
										stepMapNow = stepMaps_stay[t][ai2];
									}
								} else if (ai == 3) {
									if (ai2 == 1) {
										stepMapNext = stepMaps_stay[t + 1][ai2];
										stepMapNow = stepMaps_stay[t][ai2];
									}
								}
								double stepNext = stepMapNext.data[x2][y2];
								if (stepNext != numt) {
									if (stepNext < nearestStepNext) {
										nearestStepNext = stepNext;
									}
								} else {
									double stepCross1 = stepMapNow.data[x2][y2];
									double stepCross2 = stepMapNext.data[x][y];
									if (stepCross1 != numt && stepCross2 != numt) {
										if (stepCross2 < nearestStepNext) {
											nearestStepNext = Math.min(stepCross1, stepCross2);
										}
									}
								}
							}
							if (nearestStepNext > hitNearestStep[t + 1].data[x2][y2]) {
								hitNearestStep[t + 1].data[x2][y2] = nearestStepNext;
							}
						}
					}
				}
			}

			// 縮小断面積
			if (true) {
				int t = numt - 1;
				MyMatrix mat = hitNearestStep[t];
				double[] gain = new double[numt + 1];
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						if (mat.data[x][y] < 0) continue;
						int step = (int) mat.data[x][y];
						gain[step]++;
					}
				}

				for (int s = 0; s < numt; s++) {
					gain[s] = gain[s] - gainOffset;
					if (gain[s] < 0) gain[s] = 0;
				}

				double total = 0;
				for (int s = 0; s < numt + 1; s++) {
					total += Math.pow(rateLevel, s - numt) * gain[s];
				}

				double sss = Math.log(total);
				scores[ai][0] = BBMUtility.add_log(scores[ai][0], sss);
				scores[ai][1] += 1;
			}

			// 到達セル
			if (false) {
				// double rateLevel = 7;
				double ratelog = Math.log(rateLevel);
				int t = numt - 1;
				MyMatrix mat = hitNearestStep[t];
				double sss = Double.NEGATIVE_INFINITY;
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						double score = mat.data[x][y];
						double temp = ratelog * (score - numt);
						sss = BBMUtility.add_log(sss, temp);
					}
				}
				// System.out.println(t + ", " + sss + ", " + Math.exp(sss));

				scores[ai][0] = BBMUtility.add_log(scores[ai][0], sss);
				scores[ai][1] += 1;
			}
		}

		return scores;
	}

	public double[][] Do3_HighSpeed(Pack[] packsOrg, int[][] instructions) throws Exception {

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本変数の作成
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		int numt = instructions.length;
		double rateLevel = param.rateLevel;
		double gainOffset = param.gainOffset;

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
		double[] stepMaps_stay = new double[4 * numt * numField * numField];
		Arrays.fill(stepMaps_stay, numt);

		for (int ai = 0; ai < 4; ai++) {
			Pack packNow = packsNA[0];
			AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
			if (agentNow == null) continue;
			int x = agentNow.x;
			int y = agentNow.y;
			int t = 0;
			int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
			stepMaps_stay[index] = 0;
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
					int x = agentNow.x;
					int y = agentNow.y;
					int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
					double stepNow = stepMaps_stay[index];
					int x2 = agentNext.x;
					int y2 = agentNext.y;

					if (true) {
						double stepNext;
						if (x == x2 && y == y2) {
							stepNext = stepNow;
						} else {
							// TODO どっちがいいのだろうか？
							// stepNext = stepNow + 1;
							stepNext = t + 1;
						}
						int index2 = ai * numt * numField * numField + (t + 1) * numField * numField + y2 * numField + x2;
						stepMaps_stay[index2] = stepNext;
					}
				} else {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
							double stepNow = stepMaps_stay[index];
							if (stepNow == numt) continue;

							if (true) {
								double stepNext = stepNow;
								int index2 = ai * numt * numField * numField + (t + 1) * numField * numField + y * numField + x;
								stepMaps_stay[index2] = stepNext;
							}
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// 敵エージェントの動き
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		double[] stepMaps_move = new double[4 * numt * numField * numField];
		Arrays.fill(stepMaps_move, numt);

		for (int ai = 0; ai < 4; ai++) {
			Pack packNow = packsNA[0];
			AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
			if (agentNow == null) continue;
			int x = agentNow.x;
			int y = agentNow.y;
			int t = 0;
			int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
			stepMaps_move[index] = 0;
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
					int x = agentNow.x;
					int y = agentNow.y;
					int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
					double stepNow = stepMaps_move[index];
					int x2 = agentNext.x;
					int y2 = agentNext.y;

					if (true) {
						double stepNext;
						if (x == x2 && y == y2) {
							stepNext = stepNow;
						} else {
							// TODO どっちがいいのだろうか？
							// stepNext = stepNow + 1;
							stepNext = t + 1;
						}
						int index2 = ai * numt * numField * numField + (t + 1) * numField * numField + y2 * numField + x2;
						stepMaps_move[index2] = stepNext;
					}
				} else if (instruction == INSTRUCTION_STAY) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
							double stepNow = stepMaps_move[index];
							if (stepNow == numt) continue;

							if (true) {
								double stepNext = stepNow;
								int index2 = ai * numt * numField * numField + (t + 1) * numField * numField + y * numField + x;
								stepMaps_move[index2] = stepNext;
							}
						}
					}
				} else if (instruction == INSTRUCTION_ALLMOVE) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
							double stepNow = stepMaps_move[index];
							if (stepNow == numt) continue;

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

								if (true) {
									double stepNext;
									if (dir == 0) {
										stepNext = stepNow;
									} else {
										// TODO どっちがいいのだろうか？
										// stepNext = stepNow + 1;
										stepNext = t + 1;
									}
									int index2 = ai * numt * numField * numField + (t + 1) * numField * numField + y2 * numField + x2;

									if (stepNext < stepMaps_move[index2]) {
										stepMaps_move[index2] = stepNext;
									}
								}
							}
						}
					}

					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							int type = (int) packNext.board.data[x][y];
							if (type == Constant.Flames) {
								int index2 = ai * numt * numField * numField + (t + 1) * numField * numField + y * numField + x;
								stepMaps_move[index2] = numt;
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
		double[][] scores = new double[4][2];
		for (int ai = 0; ai < 4; ai++) {
			scores[ai][0] = Double.NEGATIVE_INFINITY;
		}

		double[] hitNearestStep = new double[4 * numt * numField * numField];
		Arrays.fill(hitNearestStep, Double.NEGATIVE_INFINITY);

		for (int ai = 0; ai < 4; ai++) {

			{
				Pack packNow = packsNA[0];
				AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
				if (agentNow == null) continue;

				int t = 0;
				int x = agentNow.x;
				int y = agentNow.y;
				int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
				hitNearestStep[index] = numt;
			}

			for (int t = 0; t < numt - 1; t++) {

				int instruction = instructions[t][ai];
				Pack packNow = packsNA[t];
				Pack packNext = packsNA[t + 1];

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {

						int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
						double nearestStepNow = hitNearestStep[index];
						if (nearestStepNow == Double.NEGATIVE_INFINITY) continue;

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

							double nearestStepNext = nearestStepNow;
							for (int ai2 = 0; ai2 < 4; ai2++) {
								if (ai2 == ai) continue;

								// TODO 友達の計算の仕方をもう少し賢くする。
								int index1 = ai2 * numt * numField * numField + (t + 0) * numField * numField + y * numField + x;
								int index2 = ai2 * numt * numField * numField + (t + 0) * numField * numField + y2 * numField + x2;
								int index3 = ai2 * numt * numField * numField + (t + 1) * numField * numField + y * numField + x;
								int index4 = ai2 * numt * numField * numField + (t + 1) * numField * numField + y2 * numField + x2;

								double[] stepMap = stepMaps_move;
								if (ai == 0) {
									if (ai2 == 2) {
										stepMap = stepMaps_stay;
									}
								} else if (ai == 1) {
									if (ai2 == 3) {
										stepMap = stepMaps_stay;
									}
								} else if (ai == 2) {
									if (ai2 == 0) {
										stepMap = stepMaps_stay;
									}
								} else if (ai == 3) {
									if (ai2 == 1) {
										stepMap = stepMaps_stay;
									}
								}

								double stepNext = stepMap[index4];
								double stepCross1 = stepMap[index2];
								double stepCross2 = stepMap[index3];

								if (stepNext != numt) {
									if (stepNext < nearestStepNext) {
										nearestStepNext = stepNext;
									}
								} else {
									if (stepCross1 != numt && stepCross2 != numt) {
										if (stepCross2 < nearestStepNext) {
											nearestStepNext = Math.min(stepCross1, stepCross2);
										}
									}
								}
							}
							int index2 = ai * numt * numField * numField + (t + 1) * numField * numField + y2 * numField + x2;
							if (nearestStepNext > hitNearestStep[index2]) {
								hitNearestStep[index2] = nearestStepNext;
							}
						}
					}
				}
			}
		}

		// 縮小断面積
		for (int ai = 0; ai < 4; ai++) {
			Pack packNow = packsNA[0];
			AgentEEE agentNow = packNow.sh.getAgent(ai + 10);
			if (agentNow == null) continue;

			int t = numt - 1;
			double[] gain = new double[numt + 1];
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int index = ai * numt * numField * numField + t * numField * numField + y * numField + x;
					int step = (int) hitNearestStep[index];
					if (step < 0) continue;
					gain[step]++;
				}
			}

			for (int s = 0; s < numt; s++) {
				gain[s] = gain[s] - gainOffset;
				if (gain[s] < 0) gain[s] = 0;
			}

			double total = 0;
			for (int s = 0; s < numt + 1; s++) {
				total += Math.pow(rateLevel, s - numt) * gain[s];
			}

			double sss = Math.log(total);
			scores[ai][0] = BBMUtility.add_log(scores[ai][0], sss);
			scores[ai][1] += 1;
		}

		return scores;
	}
}
