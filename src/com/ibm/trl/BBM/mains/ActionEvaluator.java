package com.ibm.trl.BBM.mains;

import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.Node;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class ActionEvaluator {
	static Random rand = new Random();
	static NormalDistribution nd = new NormalDistribution();

	int numField;
	boolean verbose = true;

	MyMatrix thresholdMoveToItem;
	MyMatrix thresholdMoveToWoodBrake;
	MyMatrix thresholdMoveToKill;
	MyMatrix thresholdBombToWoodBrake;
	MyMatrix thresholdAttack;

	public ActionEvaluator(int numField) {
		this.numField = numField;

		thresholdMoveToItem = new MyMatrix(3, 10, 0.5);
		thresholdMoveToWoodBrake = new MyMatrix(4, 10, 0.7);
		thresholdMoveToKill = new MyMatrix(1, 10, 0.8);
		thresholdBombToWoodBrake = new MyMatrix(4, 1, 0.6 - 0.1);
		thresholdAttack = new MyMatrix(new double[][] { { 0.6 - 0.1 }, { 0.1 } });

		for (int i = 0; i < 10; i++) {
			for (int d = 0; d < 3; d++) {
				thresholdMoveToItem.data[d][i] += i * 0.03;
			}
			for (int d = 0; d < 4; d++) {
				thresholdMoveToWoodBrake.data[d][i] += i * 0.03;
			}
		}
		for (int i = 0; i < 3; i++) {
			thresholdMoveToKill.data[0][i] = 1;
		}
	}

	/**
	 * アクションを決定する。
	 */
	public int ComputeOptimalAction(int me, MyMatrix board, Node[][] bombMap, Ability abs[]) throws Exception {

		int aiMe = me - 10;

		//////////////////////////////////////////////////////////////////
		// 初期状態を作る。
		//////////////////////////////////////////////////////////////////
		MyMatrix boardNow = new MyMatrix(board);

		Ability[] absNow = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNow[i] = new Ability(abs[i]);
		}

		StatusHolder shNow = new StatusHolder(numField);
		{
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int type = (int) board.data[x][y];
					if (Constant.isAgent(type)) {
						shNow.setAgent(x, y, type);
					}
				}
			}

			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					Node node = bombMap[x][y];
					if (node == null) continue;
					if (node.type == Constant.Bomb) {
						shNow.setBomb(x, y, node.owner, node.lifeBomb, node.moveDirection, node.power);
					} else if (node.type == Constant.Flames) {
						shNow.setFlameCenter(x, y, node.lifeFlameCenter, node.power);
					}
				}
			}
		}

		AgentEEE[] agentsNow = new AgentEEE[4];
		for (AgentEEE aaa : shNow.getAgentEntry()) {
			agentsNow[aaa.agentID - 10] = aaa;
		}

		Pack packNow = new Pack(boardNow, absNow, shNow);

		//////////////////////////////////////////////////////////////////
		// 全アクションのSurvivableScoreを計算する。
		//////////////////////////////////////////////////////////////////

		double[] safetyScore = new double[6];
		double[] safetyDefScore = new double[6];
		double[][] safetyScoreAll = new double[4][6];
		{
			ForwardModel fm = new ForwardModel(numField);
			double decayRate = 0.99;
			int numt = 12;
			int numTry = 500;

			double[] weights = new double[numt];
			for (int t = 0; t < numt; t++) {
				weights[t] = Math.pow(decayRate, t);
			}

			long timeDelta = 0;
			int timeCounter = 0;

			for (int targetAction = 0; targetAction < 6; targetAction++) {
				double[] points = new double[4];
				double[] pointsTotal = new double[4];
				for (int tryIndex = 0; tryIndex < numTry; tryIndex++) {
					Pack packNext = packNow;
					for (int t = 0; t < numt; t++) {
						int[] actions = { rand.nextInt(6), rand.nextInt(6), rand.nextInt(6), rand.nextInt(6) };
						if (t == 0) {
							actions[aiMe] = targetAction;
						}

						packNext = fm.Step(packNext.board, packNext.abs, packNext.sh, actions);

						double weight = weights[t];

						AgentEEE agentsNext[] = new AgentEEE[4];
						for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
							agentsNext[aaa.agentID - 10] = aaa;
						}

						AgentEEE agentNextMe = agentsNext[aiMe];

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

							pointsTotal[ai] += weight;
							points[ai] += good;
						}
					}
				}

				double[] scores = new double[4];
				for (int ai = 0; ai < 4; ai++) {
					scores[ai] = points[ai] / pointsTotal[ai];
				}

				double scoreMe = 0;
				double scoreSumOther = 0;
				double numOther = 0;
				for (int ai = 0; ai < 4; ai++) {
					if (abs[ai].isAlive == false) continue;
					if (ai == aiMe) {
						scoreMe = scores[ai];
					} else {
						scoreSumOther += scores[ai];
						numOther++;
					}
				}
				double scoreOther = scoreSumOther / numOther;

				double scoreDef = scoreMe - scoreOther;

				safetyScore[targetAction] = scoreMe;
				safetyDefScore[targetAction] = scoreDef;
				for (int ai = 0; ai < 4; ai++) {
					safetyScoreAll[ai][targetAction] = scores[ai];
				}
			}

			for (int i = 0; i < 6; i++) {
				System.out.println("action=" + i + ", safetyScore=" + safetyScore[i]);
			}

			double time = timeDelta * 0.001 / timeCounter;
			System.out.println(time);
		}

		// 各アクションの優先度を計算して、ベストを選ぶ。
		AgentEEE agentMe = agentsNow[aiMe];
		MyMatrix dis = BBMUtility.ComputeOptimalDistance(packNow.board, agentMe.x, agentMe.y, Integer.MAX_VALUE);
		Ability ab = abs[aiMe];

		double scoreBest = 0;
		int actionBest = -1;
		String reasonBest = "";

		// 移動系でいいやつ探す。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) packNow.board.data[x][y];
				int d = (int) dis.data[x][y];
				if (d > 1000) continue;
				if (d == 0) continue;

				double score = -Double.MAX_VALUE;
				int dir = -1;
				String reason = "";
				if (type == Constant.ExtraBomb) {
					int d2 = d - 1;
					if (d2 > 9) d2 = 9;
					double threshold = thresholdMoveToItem.data[0][d2];
					dir = BBMUtility.ComputeFirstDirection(dis, x, y);
					score = safetyScore[dir] - threshold;
					reason = "Move to ExtraBomb";
				} else if (type == Constant.IncrRange) {
					int d2 = d - 1;
					if (d2 > 9) d2 = 9;
					double threshold = thresholdMoveToItem.data[1][d2];
					dir = BBMUtility.ComputeFirstDirection(dis, x, y);
					score = safetyScore[dir] - threshold;
					reason = "move to IncrRange";
				} else if (type == Constant.Kick) {
					int d2 = d - 1;
					if (d2 > 9) d2 = 9;
					double threshold = thresholdMoveToItem.data[2][d2];
					dir = BBMUtility.ComputeFirstDirection(dis, x, y);
					score = safetyScore[dir] - threshold;
					reason = "Move to Kick";
				} else if (Constant.isAgent(type) && type != me) {
					int d2 = d - 1;
					if (d2 > 9) d2 = 9;
					double threshold = thresholdMoveToKill.data[0][d2];
					dir = BBMUtility.ComputeFirstDirection(dis, x, y);
					score = safetyScore[dir] - threshold;
					reason = "Move to Kill";
				} else {
					int num = BBMUtility.numWoodBrakable(numField, boardNow, x, y, ab.strength);
					if (num > 0) {
						int d2 = d - 1;
						if (d2 > 9) d2 = 9;
						double threshold = thresholdMoveToWoodBrake.data[num - 1][d2];
						dir = BBMUtility.ComputeFirstDirection(dis, x, y);
						score = safetyScore[dir] - threshold;
						reason = "Move to Wood Brake";
					}
				}

				if (score > scoreBest) {
					scoreBest = score;
					actionBest = dir;
					reasonBest = reason;
				}
			}
		}

		// 木破壊のための爆弾設置でいいやつ探す。
		if (ab.numBombHold > 0) {
			int num = BBMUtility.numWoodBrakable(numField, boardNow, agentMe.x, agentMe.y, ab.strength);
			if (num > 0) {
				double threshold = thresholdBombToWoodBrake.data[num - 1][0];
				double score = safetyScore[5] - threshold;
				int action = 5;
				if (score > scoreBest) {
					scoreBest = score;
					actionBest = action;
					reasonBest = "Bomb for Wood Brake";
				}
			}
		}

		// 敵を殺すための爆弾設置でいいヤツ探す。
		if (ab.numBombHold > 0) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == aiMe) continue;

				double thresholdMe = thresholdAttack.data[0][0];
				double thresholdEnemy = thresholdAttack.data[1][0];

				// 自分のアクションで死にかけに成るのか、何もしなくても死にかけなのか調べる。
				double best = -Double.MAX_VALUE;
				double bad = Double.MAX_VALUE;
				int actBad = -1;
				for (int act = 0; act < 6; act++) {
					if (safetyScoreAll[ai][act] > best) {
						best = safetyScoreAll[ai][act];
					}
					if (safetyScoreAll[ai][act] < bad) {
						bad = safetyScoreAll[ai][act];
						actBad = act;
					}
				}
				if (best - bad < thresholdEnemy) continue;

				// 敵が閾値を下回るアクションで、自分が安全なヤツを探す。
				{
					double score = safetyScore[actBad] - thresholdMe;
					if (score > scoreBest) {
						scoreBest = score;
						actionBest = actBad;
						reasonBest = "Atack";
					}
				}
			}
		}

		// 全ての行動が閾値を超えていない場合は、危険な状態なので、もっとも安全になる行動を選ぶ。
		if (actionBest == -1) {
			for (int act = 0; act < 6; act++) {
				double score = safetyScore[act];
				if (score > scoreBest) {
					scoreBest = score;
					actionBest = act;
					reasonBest = "Most safety";
				}
			}
		}

		// TODO 出力
		if (verbose) {
			String actionStr = "";
			if (actionBest == 0) {
				actionStr = "・";
			} else if (actionBest == 1) {
				actionStr = "↑";
			} else if (actionBest == 2) {
				actionStr = "↓";
			} else if (actionBest == 3) {
				actionStr = "←";
			} else if (actionBest == 4) {
				actionStr = "→";
			} else if (actionBest == 5) {
				actionStr = "＠";
			}
			System.out.println(reasonBest + ", " + actionBest + ", " + actionStr);
		}

		return actionBest;
	}

	/**
	 * KPIを記録する。
	 */
	int numExtraBomb = 0;
	int numIncrRange = 0;
	int numKick;
	int numFrame = 0;

	public void RecordKPI(int me, MyMatrix board, MyMatrix board_pre) throws Exception {
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				if (board_pre.data[x][y] == Constant.ExtraBomb && board.data[x][y] == me) {
					numExtraBomb++;
				} else if (board_pre.data[x][y] == Constant.IncrRange && board.data[x][y] == me) {
					numIncrRange++;
				} else if (board_pre.data[x][y] == Constant.Kick && board.data[x][y] == me) {
					numKick++;
				}
			}
		}
		numFrame++;
	}

	/**
	 * エポックが終了して、報酬が確定したときに呼び出される。
	 */
	int numEpoch = 0;
	int numWin = 0;

	double scoreBest = 0;
	MyMatrix thresholdMoveToItemBest = new MyMatrix(3, 10, 0.65);
	MyMatrix thresholdMoveToWoodBrakeBest = new MyMatrix(3, 10, 0.85);
	MyMatrix thresholdMoveToKillBest = new MyMatrix(1, 10, 0.90);
	MyMatrix thresholdBombToWoodBrakeBest = new MyMatrix(3, 1, 0.70);
	MyMatrix thresholdAttackBest = new MyMatrix(new double[][] { { 0.6 }, { 0.1 } });

	public void FinishOneEpoch(int me, double reword) throws Exception {
		System.out.println(reword);
		numEpoch++;
		if (reword == 1) numWin++;
		System.out.println(String.format("numEpoch=%d, numWin=%d", numEpoch, numWin));
		if (numEpoch >= 200) {
			double numItem = numExtraBomb + numIncrRange + numKick;
			double rateItem = numItem / numEpoch;
			System.out.println(rateItem);
			double score = rateItem;

			if (score > scoreBest) {
				// 今の設定のほうが良ければ、記録する。
				scoreBest = score;
				thresholdMoveToItemBest = new MyMatrix(thresholdMoveToItem);
				thresholdMoveToWoodBrakeBest = new MyMatrix(thresholdBombToWoodBrake);
				thresholdMoveToKillBest = new MyMatrix(thresholdMoveToKill);
				thresholdBombToWoodBrakeBest = new MyMatrix(thresholdBombToWoodBrake);
				thresholdAttackBest = new MyMatrix(thresholdAttack);
			} else {
				// 今の設定よりベスト設定のほうが良ければ、書き戻す。
				thresholdMoveToItem = thresholdMoveToItemBest;
				thresholdMoveToWoodBrake = thresholdBombToWoodBrakeBest;
				thresholdMoveToKill = thresholdMoveToKillBest;
				thresholdBombToWoodBrake = thresholdBombToWoodBrakeBest;
				thresholdAttack = thresholdAttackBest;
			}

			// Bestを動かして、新しい設定を試す。
			int type = rand.nextInt(4);
			double move = nd.sample() * 0.01;

			if (type == 0) {
				int item = rand.nextInt(3);
				int bin = rand.nextInt(10);
				if (move > 0) {
					for (int i = bin; i < 10; i++) {
						thresholdMoveToItem.data[item][bin] += move;
					}
				} else {
					for (int i = 0; i <= bin; i++) {
						thresholdMoveToItem.data[item][bin] += move;
					}
				}
			} else if (type == 1) {
				int item = rand.nextInt(4);
				int bin = rand.nextInt(10);
				if (move > 0) {
					for (int i = bin; i < 10; i++) {
						thresholdMoveToWoodBrake.data[item][bin] += move;
					}
				} else {
					for (int i = 0; i <= bin; i++) {
						thresholdMoveToWoodBrake.data[item][bin] += move;
					}
				}
			} else if (type == 2) {
				int item = 0;
				int bin = rand.nextInt(10);
				if (move > 0) {
					for (int i = bin; i < 10; i++) {
						thresholdMoveToKillBest.data[item][bin] += move;
					}
				} else {
					for (int i = 0; i <= bin; i++) {
						thresholdMoveToKillBest.data[item][bin] += move;
					}
				}
			} else if (type == 3) {
				int bin = rand.nextInt(4);
				if (move > 0) {
					for (int i = 0; i <= bin; i++) {
						thresholdBombToWoodBrake.data[i][0] += move;
					}
				} else {
					for (int i = bin; i < 10; i++) {
						thresholdBombToWoodBrake.data[i][0] += move;
					}
				}
			}

			numEpoch = 0;
			numFrame = 0;
			numExtraBomb = 0;
			numIncrRange = 0;
			numKick = 0;
		}
	}

}
