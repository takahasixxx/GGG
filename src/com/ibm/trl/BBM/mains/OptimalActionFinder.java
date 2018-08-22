package com.ibm.trl.BBM.mains;

import java.io.Serializable;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;

import ibm.ANACONDA.Core.MyMatrix;

public class OptimalActionFinder {
	static final boolean verbose = GlobalParameter.verbose;
	static final int numField = GlobalParameter.numField;
	OAFParameter param = new OAFParameter();

	public static class OAFParameter implements Serializable {
		private static final long serialVersionUID = 2378492149979768600L;

		MyMatrix Keisu;
		boolean[][] KeisuUsed;

		double numEpisode = 0;
		double numFrame = 0;
		double numItemGet = 0;
		double numWin = 0;

		public OAFParameter() {
			// 切片、距離増加分、個数増加分の順番に係数を格納する。
			double[][] temp = new double[8][];
			temp[0] = new double[] { 0.50, 0.03, 0.00 };// Move to ExtraBomb
			temp[1] = new double[] { 0.50, 0.03, 0.00 };// Move to IncrRange
			temp[2] = new double[] { 0.50, 0.03, 0.00 };// Move to Kick
			temp[3] = new double[] { 0.70, 0.03, 0.03 };// Move to WoodBrake
			temp[4] = new double[] { 0.80, 0.00, 0.00 };// Move to Kill
			temp[5] = new double[] { 0.50, 0.00, 0.03 };// Bomb to WoodBrake
			temp[6] = new double[] { 0.50, 0.00, 0.00 };// Attack
			temp[7] = new double[] { 0.10, 0.00, 0.00 };// Attack Efficiency
			Keisu = new MyMatrix(temp);

			KeisuUsed = new boolean[8][];
			KeisuUsed[0] = new boolean[] { true, true, false };
			KeisuUsed[1] = new boolean[] { true, true, false };
			KeisuUsed[2] = new boolean[] { true, true, false };
			KeisuUsed[3] = new boolean[] { true, true, true };
			KeisuUsed[4] = new boolean[] { true, true, false };
			KeisuUsed[5] = new boolean[] { true, false, true };
			KeisuUsed[6] = new boolean[] { true, false, false };
			KeisuUsed[7] = new boolean[] { true, false, false };
		}

		public OAFParameter(MyMatrix Keisu) {
			super();
			this.Keisu = new MyMatrix(Keisu);
		}

		public OAFParameter(OAFParameter p) {
			super();
			this.Keisu = new MyMatrix(p.Keisu);
			this.numEpisode = p.numEpisode;
			this.numFrame = p.numFrame;
			this.numItemGet = p.numItemGet;
			this.numWin = p.numWin;
		}

		public double getThresholdMoveToItem(int item, int dis) {
			int index = -1;
			if (item == Constant.ExtraBomb) {
				index = 0;
			} else if (item == Constant.IncrRange) {
				index = 1;
			} else if (item == Constant.Kick) {
				index = 2;
			}
			double threshold = Keisu.data[index][0] + Keisu.data[index][1] * dis - Keisu.data[index][2] * 0;
			return threshold;
		}

		public double getThresholdMoveToWoodBrake(int num, int dis) {
			double threshold = Keisu.data[3][0] + Keisu.data[3][1] * dis - Keisu.data[3][2] * num;
			return threshold;
		}

		public double getThresholdMoveToKill(int dis) {
			if (dis <= 3) return 1;
			double threshold = Keisu.data[4][0] + Keisu.data[4][1] * dis - Keisu.data[4][2] * 0;
			return threshold;
		}

		public double getThresholdBombToWoodBrake(int num) {
			double threshold = Keisu.data[5][0] + Keisu.data[5][1] * 0 - Keisu.data[5][2] * num;
			return threshold;
		}

		public double getThresholdAttack() {
			return Keisu.data[6][0];
		}

		public double getThresholdAttackEffect() {
			return Keisu.data[7][0];
		}
	}

	public OptimalActionFinder(OAFParameter param) {
		this.param = param;
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

		if (verbose) {
			for (int i = 0; i < 6; i++) {
				System.out.println("action=" + i + ", safetyScore=" + safetyScore[i]);
			}
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

}
