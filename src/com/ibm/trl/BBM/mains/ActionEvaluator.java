package com.ibm.trl.BBM.mains;

import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.BombTracker.Node;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
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
	public int ComputeOptimalAction(int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife, double[][][][] worstScores)
			throws Exception {

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本変数の計算
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		// TODO
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

		int numVisibleTeam = 0;
		int numVisibleEnemy = 0;
		for (int ai = 0; ai < 4; ai++) {
			AgentEEE aaa = agentsNow[ai];
			if (aaa == null) continue;
			if (ai == me - 10) {
				numVisibleTeam++;
			} else if (ai == friend - 10) {
				numVisibleTeam++;
			} else {
				numVisibleEnemy++;
			}
		}

		int numAliveTeam = 0;
		int numAliveEnemy = 0;
		for (int ai = 0; ai < 4; ai++) {
			if (abs[ai].isAlive == false) continue;
			if (ai == me - 10) {
				numAliveTeam++;
			} else if (ai == friend - 10) {
				numAliveTeam++;
			} else {
				numAliveEnemy++;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// safetyScoreを計算する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		double[][] safetyScore = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			if (ai == me - 10 || ai == friend - 10) {

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
					System.out.println("最悪ケースでは必ず死ぬので平均を使う。");
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

				if (ai == me && numzero == 6) {
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
						ave = Double.NaN;
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
		// アクション決定ルーチン
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		int action_final = -1;
		String reason = "なし";

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// ２：１のときに、自爆巻き込みできる場合は、実行する。(^o^)/
		// 単独
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (action_final == -1 && numAliveTeam == 2 && numAliveEnemy == 1) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;

				double max = Double.NEGATIVE_INFINITY;
				double min = Double.POSITIVE_INFINITY;
				int amin = -1;
				for (int a = 0; a < 6; a++) {
					double sen = safetyScore[ai][a];
					if (Double.isNaN(sen)) continue;
					if (sen < min) {
						min = sen;
						amin = a;
					}
					if (sen > max) {
						max = sen;
					}
				}

				if (amin != -1 && min == Double.NEGATIVE_INFINITY && max != Double.NEGATIVE_INFINITY) {
					action_final = amin;
					reason = "自爆する。(^o^)/";
					System.out.println("自爆する。(^o^)/" + action_final);
					System.out.println("！！！");
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 全行動ペアで見て、片方（または両方）が瀕死になる場合、二人揃って最善策を取る。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		if (action_final == -1 && numVisibleTeam == 2) {
			double max = Double.NEGATIVE_INFINITY;
			int amax = -1;
			int bmax = -1;
			double aaa = 0;
			double bbb = 0;
			int count = 0;
			for (int a = 0; a < 6; a++) {
				for (int b = 0; b < 6; b++) {
					double sme = worstScores[a][b][me - 10][1];
					double sfr = worstScores[a][b][friend - 10][1];
					double nme = worstScores[a][b][me - 10][3];
					double nfr = worstScores[a][b][friend - 10][3];
					if (nme == 0) continue;
					if (nfr == 0) continue;
					count++;
					double temp = Math.min(sme, sfr);
					if (temp > max) {
						max = temp;
						amax = a;
						bmax = b;
						aaa = sme;
						bbb = sfr;
					}
				}
			}

			if (count > 0 && max == Double.NEGATIVE_INFINITY) {
				System.out.println("！！！");
			}

			if (amax != -1 && bmax != -1 && max < -20) {
				action_final = amax;
				reason = "ふたりともやばい！";
				System.out.println("ふたりともやばい？a=" + amax + ", b=" + bmax);
				System.out.println("！！！");
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 詰められる状態であれば、詰める。
		// TODO 詰める要員が誰なのかを返すようにしたい。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		if (action_final == -1) {
			Pack packNow;
			if (true) {
				Ability[] abs2 = new Ability[4];
				for (int ai = 0; ai < 4; ai++) {
					abs2[ai] = new Ability(abs[ai]);
					if (ai + 10 == me) continue;
					abs2[ai].kick = true;
					abs2[ai].numMaxBomb = 3;
					abs2[ai].numBombHold = 3;
					if (abs2[ai].strength_fix == -1) {
						abs2[ai].strength = maxPower;
					} else {
						abs2[ai].strength = abs2[ai].strength_fix;
					}
				}

				StatusHolder sh = new StatusHolder(numField);
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						int type = map.getType(x, y);
						if (Constant.isAgent(type)) {
							sh.setAgent(x, y, type);
						}
					}
				}

				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						Node node = bombMap[x][y];
						if (node == null) continue;
						// TODO とりあえず停止してる爆弾だけ考慮する。
						if (node.dirs[0] == false) continue;
						sh.setBomb(x, y, -1, node.life, 0, node.power);
					}
				}

				packNow = new Pack(map.board, flameLife, abs2, sh);
			}

			int killScoreMin = Integer.MAX_VALUE;
			int aiTarget = -1;
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;

				int killScore = KillScoreEvaluator.computeKillScore(packNow, ai);
				if (killScore < Integer.MAX_VALUE) {
					killScoreMin = killScore;
					aiTarget = ai;
					System.out.println("詰める！！");
					System.out.println("！！！");
					break;
				}
			}

			if (aiTarget != -1) {
				ForwardModel fm = new ForwardModel();
				if (numVisibleTeam == 2) {
					int amin = -1;
					int bmin = -1;
					for (int a = 0; a < 6; a++) {
						for (int b = 0; b < 6; b++) {
							double sme = worstScores[a][b][me - 10][1];
							double sfr = worstScores[a][b][friend - 10][1];
							double nme = worstScores[a][b][me - 10][3];
							double nfr = worstScores[a][b][friend - 10][3];
							if (nme == 0) continue;
							if (nfr == 0) continue;
							if (sme < attackThreshold) continue;
							if (sfr < attackThreshold) continue;
							int[] actions = new int[4];
							actions[me - 10] = a;
							actions[friend - 10] = b;
							Pack packNext = fm.Step(packNow.board, packNow.flameLife, packNow.abs, packNow.sh, actions);
							int temp = KillScoreEvaluator.computeKillScore(packNext, aiTarget);
							if (temp < killScoreMin) {
								killScoreMin = temp;
								amin = a;
								bmin = b;
							}
						}
					}
					if (amin != -1 && bmin != -1) {
						action_final = amin;
						reason = "より詰める。";
						System.out.println("より詰める。！！！" + action_final);
					}
				} else if (numVisibleTeam == 1) {
					int amin = -1;
					for (int a = 0; a < 6; a++) {
						double sme = safetyScore[me - 10][a];
						if (sme < attackThreshold) continue;
						int[] actions = new int[4];
						actions[me - 10] = a;
						Pack packNext = fm.Step(packNow.board, packNow.flameLife, packNow.abs, packNow.sh, actions);
						int temp = KillScoreEvaluator.computeKillScore(packNext, aiTarget);
						if (temp < killScoreMin) {
							killScoreMin = temp;
							amin = a;
						}
					}
					if (amin != -1) {
						action_final = amin;
						reason = "より詰める。";
						System.out.println("より詰める。！！！" + action_final);
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 自分の安全を確保した状態で、相手を危険にできるケースを探す。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		// ペア攻撃
		if (action_final == -1 && numVisibleTeam == 2) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;

				double min = Double.POSITIVE_INFINITY;
				int mina = -1;
				int minb = -1;
				for (int a = 0; a < 6; a++) {
					for (int b = 0; b < 6; b++) {
						double numMe = worstScores[a][b][me - 10][3];
						double numFriend = worstScores[a][b][friend - 10][3];
						double numEnemy = worstScores[a][b][ai][3];
						if (numMe == 0) continue;
						if (numFriend == 0) continue;
						if (numEnemy == 0) continue;
						double scoreMe = worstScores[a][b][me - 10][1];
						double scoreFriend = worstScores[a][b][friend - 10][1];
						double scoreEnemy = worstScores[a][b][ai][0] - Math.log(numEnemy);
						if (scoreMe < attackThreshold || scoreFriend < attackThreshold) continue;
						if (scoreEnemy < min) {
							min = scoreEnemy;
							mina = a;
							minb = b;
						}
					}
				}

				if (min < -25) {
					System.out.println("ペアで追い詰め可能？ai=" + ai + ", amin=" + mina + ", bmin=" + minb);
					System.out.println("！！！");
				}
			}
		}

		// 単独
		if (action_final == -1) {
			int action_attack = -1;
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;
				double min = Double.POSITIVE_INFINITY;
				int mina = -1;
				for (int a = 0; a < 6; a++) {
					double scoreMe = safetyScore[me - 10][a];
					double scoreEnemy = safetyScore[ai][a];
					if (Double.isNaN(scoreMe)) continue;
					if (Double.isNaN(scoreEnemy)) continue;
					if (scoreMe < attackThreshold) continue;
					if (scoreEnemy < min) {
						min = scoreEnemy;
						mina = a;
					}
				}

				if (min < -22) {
					action_attack = mina;
				}
			}
			if (action_attack != -1) {
				action_final = action_attack;
				reason = "攻撃するべし。";
				System.out.println("追い詰め可能？a=" + action_attack);
				System.out.println("！！！");
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
