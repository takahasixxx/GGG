package obsolete;

import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.ibm.trl.BBM.mains.Agent;
import com.ibm.trl.BBM.mains.BBMUtility;
import com.ibm.trl.BBM.mains.Constant;
import com.ibm.trl.BBM.mains.GlobalParameter;
import com.ibm.trl.BBM.mains.MapInformation;
import com.ibm.trl.BBM.mains.StatusHolder;
import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;

import ibm.ANACONDA.Core.MatrixUtility;
import ibm.ANACONDA.Core.MyMatrix;

public class ActionEvaluator_Obsolete {
	static Random rand = new Random();
	static NormalDistribution nd = new NormalDistribution();

	int numField = GlobalParameter.numField;
	boolean verbose = GlobalParameter.verbose;

	// int currentAction = -1;
	// int xTarget, yTarget;
	double safetyThreshold = 0.2;
	boolean clock = true;
	int clockCounter = 0;

	/**
	 * アクションを決定する。
	 */
	public int ComputeOptimalAction(int me, int friend, MapInformation map, Ability abs[], double[][][][] points) throws Exception {
		// TODO
		safetyThreshold = 0.15;

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

		double[][][] points2 = new double[6][4][2];
		for (int ai = 0; ai < 4; ai++) {
			if (false) {
				// if (ai == me - 10) {
				for (int a = 0; a < 6; a++) {
					double max = 0;
					for (int b = 0; b < 6; b++) {
						double temp = points[a][b][ai][0];
						max = Math.max(max, temp);
					}
					points2[a][ai][0] = max;
					points2[a][ai][1] = points[a][0][ai][1];
				}
			} else {
				for (int a = 0; a < 6; a++) {
					for (int b = 0; b < 6; b++) {
						for (int i = 0; i < 2; i++) {
							points2[a][ai][i] += points[a][b][ai][i];
						}
					}
				}
			}
		}

		double[][] safetyScore = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			for (int a = 0; a < 6; a++) {
				safetyScore[ai][a] = points2[a][ai][0] / points2[a][ai][1];
			}
		}

		// TODO
		MatrixUtility.OutputMatrix(new MyMatrix(safetyScore));
		// MatrixUtility.OutputMatrix(new MyMatrix(safetyScore));

		int action_final = -1;
		String reason = "なし";

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// TODO 詰めれるなら詰める。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (false) {
			if (action_final == -1) {
				for (int ai = 0; ai < 4; ai++) {
					if (ai == me - 10) continue;
					if (ai == friend - 10) continue;

					double sum = 0;
					double num = 0;
					for (int a = 0; a < 6; a++) {
						for (int b = 0; b < 6; b++) {
							sum += points[a][b][ai][0];
							num += points[a][b][ai][1];
						}
					}
					if (num == 0) continue;
					double ave = sum / num;

					double min = Double.MAX_VALUE;
					int aMin = -1;
					for (int a = 0; a < 6; a++) {
						double scoreMe = safetyScore[me - 10][a];
						if (scoreMe < safetyThreshold) continue;
						for (int b = 0; b < 6; b++) {
							if (points[a][b][ai][1] == 0) continue;
							double scoreTarget = points[a][b][ai][0] / points[a][b][ai][1];
							if (scoreTarget < min) {
								min = scoreTarget;
								aMin = a;
							}
						}
					}

					// TODO 判断基準をよく調べる
					if (ave - min > 0.15 && min < 0.1) {
						action_final = aMin;
						reason = "詰める。";
						break;
					}
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
				if (score > safetyThreshold) {
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
						// int num = BBMUtility.numWoodBrakable(board, x, y, ab.strength);
						int num = BBMUtility.numWoodBrakable(board, x, y, 2);
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
					if (score > safetyThreshold) {
						action_final = action;
						reason = "新規。木を壊すために爆弾を設置する。";
					}
				}
			}

			if (action_final == -1 && mindis < Integer.MAX_VALUE && mindis > 0) {
				// Woodを壊せる場所がみつかったら、そっちに移動する。
				int action = BBMUtility.ComputeFirstDirection(dis, minx, miny);
				double score = safetyScore[me - 10][action];
				if (score > safetyThreshold) {
					action_final = action;
					reason = "新規。木を壊すために移動する。";
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// TODO ぐるぐる回る？回る方向に爆弾をキックする？
		// TODO とりあえず、敵の方に動く。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		// 仲間と距離が2以下になったら、clockを反転させる。
		if (true) {
			AgentEEE agentFriend = agentsNow[friend - 10];
			if (agentMe != null && agentFriend != null) {
				int ddd = Math.abs(agentMe.x - agentFriend.x) + Math.abs(agentMe.y - agentFriend.y);
				if (ddd <= 2) {
					clockCounter++;
					if (clockCounter > 5) {
						if (clock) {
							clock = false;
						} else {
							clock = true;
						}
						clockCounter = 0;
					}
				} else {
					clockCounter = 0;
				}
			}
		}

		if (action_final == -1) {
			int x = agentMe.x;
			int y = agentMe.y;
			int dir = -1;
			if ((clock == true && me < friend) || (clock == false && me > friend)) {
				if (x == 1 && y >= 1 && y < 9) {
					dir = 4;
				} else if (x == 9 && y > 1 && y <= 9) {
					dir = 3;
				} else if (y == 1 && x > 1 && x <= 9) {
					dir = 1;
				} else if (y == 9 && x >= 1 && x < 9) {
					dir = 2;
				}
			} else {
				if (x == 1 && y > 1 && y <= 9) {
					dir = 3;
				} else if (x == 9 && y >= 1 && y < 9) {
					dir = 4;
				} else if (y == 1 && x >= 1 && x < 9) {
					dir = 2;
				} else if (y == 9 && x > 1 && x <= 9) {
					dir = 1;
				}
			}

			if (dir == -1) {
				// トラック上にいないときは、トラック上で一番近いセルを探して、そっちに移動する。
				int minDis = Integer.MAX_VALUE;
				int xTarget = -1;
				int yTarget = -1;
				for (int i = 0; i < 4; i++) {
					for (int p = 1; p <= 9; p++) {
						int x2 = 0;
						int y2 = 0;
						if (i == 0) {
							x2 = 1;
							y2 = p;
						} else if (i == 1) {
							x2 = 9;
							y2 = p;
						} else if (i == 2) {
							x2 = p;
							y2 = 1;
						} else if (i == 3) {
							x2 = p;
							y2 = 9;
						}
						int ddd = (int) dis.data[x2][y2];
						if (ddd < minDis) {
							minDis = ddd;
							xTarget = x2;
							yTarget = y2;
						}
					}
				}
				if (minDis < Integer.MAX_VALUE) {
					int action = BBMUtility.ComputeFirstDirection(dis, xTarget, yTarget);
					double score = safetyScore[me - 10][action];
					if (score > safetyThreshold) {
						action_final = action;
						reason = "トラックに移動する。";
					}
				}
			} else {
				// トラック上にいるときは、ぐるぐる回る。
				int action = dir;
				double score = safetyScore[me - 10][action];
				if (score > safetyThreshold) {
					action_final = action;
					reason = "トラック上をぐるぐる回る。";
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
			double scoreMax = -Double.MAX_VALUE;
			for (int action = 0; action < 6; action++) {
				double score = safetyScore[me - 10][action];
				if (score > scoreMax) {
					scoreMax = score;
					action_final = action;
					reason = "もっとも安全な選択肢を選ぶ。";
				}
			}

			if (scoreMax < 0.1) {
				System.out.println("error?");
			}
		}

		String line = String.format("%s, action=%d", reason, action_final);
		System.out.println(line);

		return action_final;
	}
}
