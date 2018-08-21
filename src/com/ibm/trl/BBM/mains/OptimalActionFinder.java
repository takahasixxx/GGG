package com.ibm.trl.BBM.mains;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;

import ibm.ANACONDA.Core.MyMatrix;

public class OptimalActionFinder {
	static final int numField = GlobalParameter.numField;
	Parameter param = new Parameter();

	public static class Parameter {
		MyMatrix thresholdMoveToItem;
		MyMatrix thresholdMoveToWoodBrake;
		MyMatrix thresholdMoveToKill;
		MyMatrix thresholdBombToWoodBrake;
		MyMatrix thresholdAttack;

		public Parameter() {
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

		public double getThresholdMoveToItem(int item, int dis) {
			int temp1 = -1;
			if (item == Constant.ExtraBomb) {
				temp1 = 0;
			} else if (item == Constant.IncrRange) {
				temp1 = 1;
			} else if (item == Constant.Kick) {
				temp1 = 2;
			}
			int temp2 = dis - 1;
			if (temp2 > 9) temp2 = 9;
			return thresholdMoveToItem.data[temp1][temp2];

		}

		public double getThresholdMoveToWoodBrake(int num, int dis) {
			int temp2 = dis - 1;
			if (temp2 > 9) temp2 = 9;
			return thresholdMoveToWoodBrake.data[num - 1][temp2];

		}

		public double getThresholdMoveToKill(int dis) {
			int temp2 = dis - 1;
			if (temp2 > 9) temp2 = 9;
			return thresholdMoveToKill.data[0][temp2];
		}

		public double getThresholdBombToWoodBrake(int num) {
			return thresholdBombToWoodBrake.data[num - 1][0];
		}

		public double getThresholdAttack() {
			return thresholdAttack.data[0][0];
		}

		public double getThresholdAttackEffect() {
			return thresholdAttack.data[1][0];
		}
	}

	public int findOptimalAction(Pack packNow, int me, double[][] safetyScoreAll) throws Exception {

		int aiMe = me - 10;

		double[] safetyScore = safetyScoreAll[aiMe];

		// 二人以上のエージェントに有効な回数が入ってなければ、0を返す。
		{
			int num = 0;
			for (int ai = 0; ai < 4; ai++) {
				boolean ok = true;
				for (int act = 0; act < 6; act++) {
					if (Double.isNaN(safetyScoreAll[ai][act])) {
						ok = false;
						break;
					}
				}
				if (ok) num++;
			}
			if (num <= 1) return 0;
		}

		for (int i = 0; i < 6; i++) {
			System.out.println("action=" + i + ", safetyScore=" + safetyScore[i]);
		}

		MyMatrix boardNow = packNow.board;
		Ability[] absNow = packNow.abs;
		StatusHolder shNow = packNow.sh;

		AgentEEE[] agentsNow = new AgentEEE[4];
		for (AgentEEE aaa : shNow.getAgentEntry()) {
			agentsNow[aaa.agentID - 10] = aaa;
		}

		boolean[][] bombExist = new boolean[numField][numField];
		for (EEE bbb : shNow.getBombEntry()) {
			bombExist[bbb.x][bbb.y] = true;
		}

		// 各アクションの優先度を計算して、ベストを選ぶ。
		AgentEEE agentMe = agentsNow[aiMe];
		MyMatrix dis = BBMUtility.ComputeOptimalDistance(packNow.board, agentMe.x, agentMe.y, Integer.MAX_VALUE);
		Ability ab = absNow[aiMe];

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
					double threshold = param.getThresholdMoveToItem(type, d);
					dir = BBMUtility.ComputeFirstDirection(dis, x, y);
					score = safetyScore[dir] - threshold;
					reason = "Move to ExtraBomb";
				} else if (type == Constant.IncrRange) {
					double threshold = param.getThresholdMoveToItem(type, d);
					dir = BBMUtility.ComputeFirstDirection(dis, x, y);
					score = safetyScore[dir] - threshold;
					reason = "move to IncrRange";
				} else if (type == Constant.Kick) {
					double threshold = param.getThresholdMoveToItem(type, d);
					dir = BBMUtility.ComputeFirstDirection(dis, x, y);
					score = safetyScore[dir] - threshold;
					reason = "Move to Kick";
				} else {
					int numAgent = BBMUtility.numAgent(boardNow, x, y);
					if (numAgent > 0) {
						double threshold = param.getThresholdMoveToKill(d);
						dir = BBMUtility.ComputeFirstDirection(dis, x, y);
						score = safetyScore[dir] - threshold;
						reason = "Move to Kill";
					} else {
						int numWoodBrakable = BBMUtility.numWoodBrakable(numField, boardNow, x, y, ab.strength);
						if (numWoodBrakable > 0) {
							double threshold = param.getThresholdMoveToWoodBrake(numWoodBrakable, d);
							dir = BBMUtility.ComputeFirstDirection(dis, x, y);
							score = safetyScore[dir] - threshold;
							reason = "Move to Wood Brake";
						}
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
		if (ab.numBombHold > 0 && bombExist[agentMe.x][agentMe.y] == false) {
			int num = BBMUtility.numWoodBrakable(numField, boardNow, agentMe.x, agentMe.y, ab.strength);
			if (num > 0) {
				double threshold = param.getThresholdBombToWoodBrake(num);
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
		if (ab.numBombHold > 0 && bombExist[agentMe.x][agentMe.y] == false) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == aiMe) continue;

				double thresholdMe = param.getThresholdAttack();
				double thresholdEnemy = param.getThresholdAttackEffect();

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
		if (true) {
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
}
