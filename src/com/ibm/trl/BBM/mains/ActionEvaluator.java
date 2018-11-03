package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.ModelParameter;
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

	ModelParameter param;

	int[] actionLog = new int[1000];
	int actionLogIndex = 0;
	int mugenLoopRecoveryKikan = 0;

	public ActionEvaluator(ModelParameter param) {
		this.param = param;

		for (int i = 0; i < actionLog.length; i++) {
			actionLog[i] = rand.nextInt();
		}
	}

	/**
	 * アクションを決定する。
	 */
	public int ComputeOptimalAction(int frame, int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife, MyMatrix lastLook,
			double[][][][] worstScores) throws Exception {

		double usualMoveThreshold = param.usualThreshold;
		double attackThreshold = param.attackThreshold;

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 基本変数の計算
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

		int numBrakableWood = 0;
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (type == Constant.Wood) {
					boolean find = false;
					for (int[] vec : GlobalParameter.onehopList) {
						if (vec[0] == 0) continue;
						int dx = vec[1];
						int dy = vec[2];
						int x2 = x + dx;
						int y2 = y + dy;
						if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
						double ddd = dis.data[x2][y2];
						if (ddd > 1000) continue;
						find = true;
						break;
					}

					if (find) {
						numBrakableWood++;
					}
				}
			}
		}

		// TODO worstScore出力
		if (false) {
			MyMatrix temp = new MyMatrix(6, 6, Double.NaN);

			if (true) {
				System.out.println("====================================");
				System.out.println("最悪ケース");
				System.out.println("====================================");
				for (int ai = 0; ai < 4; ai++) {
					for (int a = 0; a < 6; a++) {
						for (int b = 0; b < 6; b++) {
							if (worstScores[a][b][ai][3] == 0) {
								temp.data[a][b] = Double.NaN;
							} else {
								temp.data[a][b] = worstScores[a][b][ai][1];
							}
						}
					}
					System.out.println("====================================");
					System.out.println("ai=" + ai);
					MatrixUtility.OutputMatrix(temp);
				}
			}

			if (true) {
				System.out.println("====================================");
				System.out.println("平均");
				System.out.println("====================================");
				for (int ai = 0; ai < 4; ai++) {
					for (int a = 0; a < 6; a++) {
						for (int b = 0; b < 6; b++) {
							if (worstScores[a][b][ai][3] == 0) {
								temp.data[a][b] = Double.NaN;
							} else {
								temp.data[a][b] = worstScores[a][b][ai][0] - Math.log(worstScores[a][b][ai][3]);
							}
						}
					}
					System.out.println("====================================");
					System.out.println("ai=" + ai);
					MatrixUtility.OutputMatrix(temp);
				}
			}
		}

		// アクションが無限ループに入っているか検知する。
		int actionNG = -1;
		if (true) {
			int numTry = 30;
			for (int delay = 1; delay <= 4; delay++) {
				int num = 0;
				for (int i = 0; i < numTry; i++) {
					int index1 = actionLogIndex - i - 1;
					if (index1 < 0) index1 += actionLog.length;
					int index2 = actionLogIndex - i - 1 - delay;
					if (index2 < 0) index2 += actionLog.length;
					int a1 = actionLog[index1];
					int a2 = actionLog[index2];
					if (a1 == a2) {
						num++;
					}
				}
				if (num == numTry) {
					int index = actionLogIndex - delay;
					if (index < 0) index += actionLog.length;
					actionNG = actionLog[index];
					mugenLoopRecoveryKikan = 10;
					break;
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// safetyScoreを計算する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		double[][] safetyScoreWorst = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			// 最悪ケースの最悪ケースで見積もる。
			for (int a = 0; a < 6; a++) {
				double min = Double.POSITIVE_INFINITY;
				double count = 0;
				for (int b = 0; b < 6; b++) {
					double num = worstScores[a][b][ai][3];
					if (num == 0) continue;
					min = Math.min(min, worstScores[a][b][ai][1]);
					count += 1;
				}
				if (count == 0) {
					min = Double.NEGATIVE_INFINITY;
				}
				safetyScoreWorst[ai][a] = min;
			}
		}

		double[][] safetyScoreAverage = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			// 平均の平均で見積もる。
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
				safetyScoreAverage[ai][a] = ave;
			}
		}

		if (verbose) {
			System.out.println("==============================");
			MatrixUtility.OutputMatrix(new MyMatrix(safetyScoreWorst));
			System.out.println("==============================");
		}

		if (verbose) {
			System.out.println("==============================");
			MatrixUtility.OutputMatrix(new MyMatrix(safetyScoreAverage));
			System.out.println("==============================");
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// アクション決定ルーチン
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		int actionFinal = -1;
		String reason = "なし";

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// ２：１のときに、自爆できる場合は、実行する。(^o^)/
		// 単独
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1 && numAliveTeam == 2 && numAliveEnemy == 1) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;

				double max = Double.NEGATIVE_INFINITY;
				double min = Double.POSITIVE_INFINITY;
				int amin = -1;
				for (int a = 0; a < 6; a++) {
					double scoreFriend = safetyScoreWorst[friend - 10][a];
					double scoreEnemy = safetyScoreAverage[ai][a];
					if (Double.isNaN(scoreEnemy)) continue;
					if (scoreFriend < attackThreshold) continue;
					if (scoreEnemy < min) {
						min = scoreEnemy;
						amin = a;
					}
					if (scoreEnemy > max) {
						max = scoreEnemy;
					}
				}

				// ほっといても死ぬ。
				if (max == Double.NEGATIVE_INFINITY) continue;

				// 自爆できる。
				if (amin != -1 && min == Double.NEGATIVE_INFINITY) {
					actionFinal = amin;
					reason = "自爆します。(^o^)/";
					System.out.println(reason);
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 詰められる状態であれば、詰める。
		// TODO 詰める要員が誰なのかを返すようにしたい。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		if (false) {
			// if (actionFinal == -1) {
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

				StatusHolder sh = new StatusHolder();
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
					break;
				}
			}

			if (aiTarget != -1) {
				ForwardModel fm = new ForwardModel();
				int amin = -1;
				for (int a = 0; a < 6; a++) {
					double scoreMe = safetyScoreWorst[me - 10][a];
					if (scoreMe < attackThreshold) continue;
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
					actionFinal = amin;
					reason = "より詰める。";
					System.out.println("より詰める。！！！" + actionFinal);
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 自分の安全を確保した状態で、相手を危険にできるケースを探す。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		// 単独一手先
		if (numBrakableWood == 0 && actionFinal == -1) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;
				double min = Double.POSITIVE_INFINITY;
				double max = Double.NEGATIVE_INFINITY;
				for (int a = 0; a < 6; a++) {
					double scoreMe = safetyScoreWorst[me - 10][a];
					double scoreEnemy = safetyScoreAverage[ai][a];
					if (Double.isNaN(scoreEnemy)) continue;
					if (scoreMe < attackThreshold) continue;
					if (scoreMe < scoreEnemy) continue;
					if (scoreEnemy < min) {
						min = scoreEnemy;
					}
					if (scoreEnemy > max) {
						max = scoreEnemy;
					}
				}

				// 自分のアクションが何ら影響を与えなければ飛ばす。
				if (min == max) continue;

				if (min != Double.POSITIVE_INFINITY) {
					ArrayList<Integer> set = new ArrayList<Integer>();
					for (int a = 0; a < 6; a++) {
						double scoreMe = safetyScoreWorst[me - 10][a];
						double scoreEnemy = safetyScoreAverage[ai][a];
						if (Double.isNaN(scoreEnemy)) continue;
						if (scoreMe < attackThreshold) continue;
						if (scoreMe < scoreEnemy) continue;
						if (scoreEnemy == min) {
							set.add(a);
						}
					}
					int index = rand.nextInt(set.size());
					actionFinal = set.get(index);
					reason = "攻撃する。1手先。";
				}
			}
		}

		// 単独二手先
		// if (false) {
		if (numBrakableWood == 0 && actionFinal == -1) {
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;
				double min = Double.POSITIVE_INFINITY;
				double max = Double.NEGATIVE_INFINITY;
				for (int a = 0; a < 6; a++) {
					for (int b = 0; b < 6; b++) {
						double numMe = worstScores[a][b][me - 10][3];
						double numEnemy = worstScores[a][b][ai][3];
						if (numMe == 0) continue;
						if (numEnemy == 0) continue;
						double scoreMe = worstScores[a][b][me - 10][1];
						double scoreEnemy = worstScores[a][b][ai][0] - Math.log(numEnemy);
						if (scoreMe < attackThreshold) continue;
						if (scoreMe < scoreEnemy) continue;
						if (scoreEnemy < min) {
							min = scoreEnemy;
						}
						if (scoreEnemy > max) {
							max = scoreEnemy;
						}
					}
				}

				// 自分のアクションが何ら影響を与えなければ飛ばす。
				if (min == max) continue;

				if (min != Double.POSITIVE_INFINITY) {
					ArrayList<Integer> set = new ArrayList<Integer>();
					for (int a = 0; a < 6; a++) {
						for (int b = 0; b < 6; b++) {
							double numMe = worstScores[a][b][me - 10][3];
							double numEnemy = worstScores[a][b][ai][3];
							if (numMe == 0) continue;
							if (numEnemy == 0) continue;
							double scoreMe = worstScores[a][b][me - 10][1];
							double scoreEnemy = worstScores[a][b][ai][0] - Math.log(numEnemy);
							if (scoreMe < attackThreshold) continue;
							if (scoreMe < scoreEnemy) continue;
							if (scoreEnemy == min) {
								set.add(a);
							}
						}
					}
					int index = rand.nextInt(set.size());
					actionFinal = set.get(index);
					reason = "攻撃する。二手先。";
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// アイテムがあるなら、アイテムを取りに行く。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1) {
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
				double score = safetyScoreWorst[me - 10][action];
				if (score > usualMoveThreshold) {
					actionFinal = action;
					reason = "新規。アイテムを取るために移動する。";
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 木があるなら、木を壊しに行く。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1) {
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

			if (actionFinal == -1 && mindis == 0) {
				// Woodが壊せる位置にいたら、爆弾を置く。
				if (ab.numBombHold > 0) {
					int action = 5;
					double score = safetyScoreWorst[me - 10][action];
					if (score > usualMoveThreshold) {
						actionFinal = action;
						reason = "新規。木を壊すために爆弾を設置する。";
					}
				}
			}

			if (actionFinal == -1 && mindis < Integer.MAX_VALUE && mindis > 0) {
				// Woodを壊せる場所がみつかったら、そっちに移動する。
				int action = BBMUtility.ComputeFirstDirection(dis, minx, miny);
				double score = safetyScoreWorst[me - 10][action];
				if (score > usualMoveThreshold) {
					actionFinal = action;
					reason = "新規。木を壊すために移動する。";
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 最近見てない場所に移動してみる。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1 && numVisibleEnemy == 0) {

			double frameOld = Double.POSITIVE_INFINITY;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					double ddd = dis.data[x][y];
					if (ddd == Double.MAX_VALUE) continue;
					double fff = lastLook.data[x][y];
					if (fff < frameOld) {
						frameOld = fff;
					}
				}
			}

			double frameDelta = frame - frameOld;

			if (frameDelta > 10) {
				List<int[]> posList = new ArrayList<int[]>();
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						double ddd = dis.data[x][y];
						if (ddd == Double.MAX_VALUE) continue;
						double fff = lastLook.data[x][y];
						if (fff == frameOld) {
							int[] pos = new int[] { x, y };
							posList.add(pos);
						}
					}
				}

				for (int i = 0; i < 100; i++) {
					int[] pos = posList.get(rand.nextInt(posList.size()));
					int x = pos[0];
					int y = pos[1];
					int action = BBMUtility.ComputeFirstDirection(dis, x, y);
					double score = safetyScoreWorst[me - 10][action];
					if (score < usualMoveThreshold) continue;
					actionFinal = action;
					reason = "最近見ていない場所を見に行く。";
					break;
				}
			}
		}

		if (false) {
			double frameOld = Double.POSITIVE_INFINITY;
			int xOld = -1;
			int yOld = -1;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					double ddd = dis.data[x][y];
					if (ddd == Double.MAX_VALUE) continue;
					double fff = lastLook.data[x][y];
					if (fff < frameOld) {
						frameOld = fff;
						xOld = x;
						yOld = y;
					}
				}
			}

			double frameDelta = frame - frameOld;

			if (frameDelta > 100 && xOld != -1 && yOld != -1) {
				mugenLoopRecoveryKikan = 10;
				actionNG = -1;
			}
		}

		if (mugenLoopRecoveryKikan > 0) {
			mugenLoopRecoveryKikan--;
			actionFinal = -1;
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 上の選択肢で危険な状態に陥るなら、もっとも安全なアクションを取る。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		if (actionFinal == -1) {
			int actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], safetyScoreWorst[friend - 10], actionNG);
			if (actionSelected == -1) {
				actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], safetyScoreWorst[friend - 10], -1);
				if (actionSelected == -1) {
					actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], safetyScoreAverage[friend - 10], -1);
					if (actionSelected == -1) {
						actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], null, -1);
						if (actionSelected == -1) {
							actionSelected = findMostSafetyAction(safetyScoreAverage[me - 10], safetyScoreWorst[friend - 10], -1);
							if (actionSelected == -1) {
								actionSelected = findMostSafetyAction(safetyScoreAverage[me - 10], safetyScoreAverage[friend - 10], -1);
								if (actionSelected == -1) {
									actionSelected = findMostSafetyAction(safetyScoreAverage[me - 10], null, -1);
								}
							}
						}
					}
				}
			}
			actionFinal = actionSelected;
			reason = "もっとも安全なアクションを選ぶ";
		}

		String line = String.format("%s, action=%d", reason, actionFinal);
		System.out.println(line);

		actionLog[actionLogIndex] = actionFinal;
		actionLogIndex = (actionLogIndex + 1) % actionLog.length;

		return actionFinal;
	}

	private int findMostSafetyAction(double[] scoresMe, double[] scoresFriend, int actionNG) {

		double usualMoveThreshold = param.usualThreshold;

		boolean friendDependency = false;
		if (scoresFriend != null) {
			double max = Double.NEGATIVE_INFINITY;
			double min = Double.POSITIVE_INFINITY;
			for (int action = 0; action < 6; action++) {
				double s = scoresFriend[action];
				if (s > max) max = s;
				if (s < min) min = s;
			}
			if (min == max) friendDependency = false;
			else friendDependency = true;
		}

		int actionSelected = -1;
		{
			double max = Double.NEGATIVE_INFINITY;
			for (int action = 0; action < 6; action++) {
				if (action == actionNG) continue;
				double scoreMe = scoresMe[action];
				if (friendDependency) {
					double scoreFriend = scoresFriend[action];
					if (scoreFriend == Double.NEGATIVE_INFINITY) continue;
				}
				// if (scoreMe > usualMoveThreshold) scoreMe = usualMoveThreshold;
				if (scoreMe > max) {
					max = scoreMe;
				}
			}
			if (max == Double.NEGATIVE_INFINITY) return -1;

			List<Integer> set = new ArrayList<Integer>();
			for (int action = 0; action < 6; action++) {
				if (action == actionNG) continue;
				double scoreMe = scoresMe[action];
				if (friendDependency) {
					double scoreFriend = scoresFriend[action];
					if (scoreFriend == Double.NEGATIVE_INFINITY) continue;
				}
				// if (scoreMe > usualMoveThreshold) scoreMe = usualMoveThreshold;
				if (scoreMe == max) {
					set.add(action);
				}
			}
			if (set.size() == 0) return -1;
			actionSelected = set.get(rand.nextInt(set.size()));
		}
		return actionSelected;
	}

}
