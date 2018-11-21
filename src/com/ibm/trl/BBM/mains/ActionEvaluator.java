/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.LastAgentPosition;
import com.ibm.trl.BBM.mains.Agent.ModelParameter;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.WorstScoreEvaluator.ScoreResult;

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
	public int ComputeOptimalAction(boolean collapse, int frame, int me, int friend, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife,
			MyMatrix lastLook, LastAgentPosition[] laps, ScoreResult sr) throws Exception {

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
		MyMatrix dis = BBMUtility.ComputeOptimalDistance(board, agentMe.x, agentMe.y, Integer.MAX_VALUE, false);
		MyMatrix dis2 = BBMUtility.ComputeOptimalDistance(board, agentMe.x, agentMe.y, Integer.MAX_VALUE, true);

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

		if (false) {
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
		}

		// アクションが無限ループに入っているか検知する。
		int actionNG = -1;
		// TODO とりあえずオフっておく。
		if (false) {
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

		// 平均の平均で見積もる。
		double[][] safetyScoreAverage = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			for (int a = 0; a < 6; a++) {
				double num = sr.singleScore[a][ai].num;
				double sss = sr.singleScore[a][ai].sum / num;
				if (num == 0) {
					safetyScoreAverage[ai][a] = Double.NaN;
				} else {
					safetyScoreAverage[ai][a] = sss;
				}
			}
		}

		// 最悪ケースの最悪ケースで見積もる。
		double[][] safetyScoreWorst = new double[4][6];
		for (int ai = 0; ai < 4; ai++) {
			for (int a = 0; a < 6; a++) {
				double num = sr.singleScore[a][ai].num;
				double sss = sr.singleScore[a][ai].min;
				if (num == 0) {
					safetyScoreWorst[ai][a] = Double.NaN;
				} else {
					safetyScoreWorst[ai][a] = sss;
				}
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

				double max = 0;
				double min = Double.POSITIVE_INFINITY;
				int amin = -1;
				for (int a = 0; a < 6; a++) {
					double scoreFriend = safetyScoreWorst[friend - 10][a];
					double scoreEnemy = safetyScoreAverage[ai][a];
					if (Double.isNaN(scoreEnemy)) continue;
					if (Double.isNaN(scoreFriend) == false && scoreFriend < attackThreshold) continue;
					if (scoreEnemy < min) {
						min = scoreEnemy;
						amin = a;
					}
					if (scoreEnemy > max) {
						max = scoreEnemy;
					}
				}

				// ほっといても死ぬ。
				if (max == 0) continue;

				// 自爆できる。
				if (amin != -1 && min == 0) {
					actionFinal = amin;
					reason = "自爆します。(^o^)/";
					System.out.println(reason);
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 恐神さんの挟み撃ちロジックを真似てみる？？
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1) {
			double hasamiThreshold = 1;

			for (int pos : new int[] { 4, 5, 6 }) {
				int x2 = -1, y2 = -1, x3 = -1, y3 = -1;
				if (me == 10) {
					x2 = 1;
					y2 = pos;
					x3 = 1;
					y3 = pos - 1;
				} else if (me == 11) {
					x2 = 9;
					y2 = pos;
					x3 = 9;
					y3 = pos - 1;
				} else if (me == 12) {
					x2 = pos;
					y2 = 9;
					x3 = pos + 1;
					y3 = 9;
				} else if (me == 13) {
					x2 = pos;
					y2 = 9;
					x3 = pos - 1;
					y3 = 9;
				}

				int type = map.getType(x2, y2);
				int type2 = map.getType(x3, y3);
				int power2 = map.getPower(x3, y3);

				if (type == Constant.Wood) {
					if (type2 == Constant.Passage || type2 == Constant.Flames || type2 == Constant.Bomb) {
						// (x2, y2-1)に向かう。
						double ddd = dis2.data[x3][y3];
						if (ddd < 100) {
							int dir = BBMUtility.ComputeFirstDirection(dis2, x3, y3);
							double score = safetyScoreWorst[me - 10][dir];
							if (score > hasamiThreshold) {
								actionFinal = dir;
								reason = "挟み撃ち。移動。";
								break;
							} else {
								double scoreStop = safetyScoreWorst[me - 10][0];
								if (scoreStop > hasamiThreshold) {
									actionFinal = 0;
									reason = "挟み撃ち。停止。";
								}
							}
						}
					} else if (type2 == me) {
						// 既に目的の場所にいたら。
						if (power2 == 0 && abs[me - 10].numBombHold > 0) {
							// 爆弾が設置されてなかったら、爆弾を設置する。
							double score = safetyScoreWorst[me - 10][5];
							if (score > hasamiThreshold) {
								actionFinal = 5;
								reason = "挟み撃ち。爆弾設置";
								break;
							}
						}
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 敵がいる方へ向かう。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1 && numVisibleEnemy == 0 && numAliveEnemy == 2 && numAliveTeam == 2) {
			double dddmin = Double.POSITIVE_INFINITY;
			int dirmin = -1;
			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;
				LastAgentPosition lap = laps[ai];
				if (lap == null) continue;

				// 目的地（最後に敵を見た場所）が、既に観測範囲にある場合は、意味なし。
				// int isInsideVisibleArea = Math.abs(lap.x - agentMe.x) + Math.abs(lap.y - agentMe.y);
				int disL0 = Math.max(Math.abs(agentMe.x - lap.x), Math.abs(agentMe.y - lap.y));
				if (disL0 <= 4) continue;

				double ddd = dis.data[lap.x][lap.y];
				if (ddd > 100) continue;
				int action = BBMUtility.ComputeFirstDirection(dis, lap.x, lap.y);
				double score = safetyScoreWorst[me - 10][action];
				if (score < usualMoveThreshold) continue;
				if (ddd < dddmin) {
					dddmin = ddd;
					dirmin = action;
				}
			}

			if (dirmin != -1) {
				actionFinal = dirmin;
				reason = "敵がいる方へ向かうムーブ";
			}
		}

		// 友達がいる方へ向かう。
		if (false) {
			// if (actionFinal == -1 && numVisibleEnemy == 0 && numAliveEnemy == 2) {
			double dddmin = Double.POSITIVE_INFINITY;
			int dirmin = -1;
			for (int ai = 0; ai < 4; ai++) {
				if (ai != friend - 10) continue;
				LastAgentPosition lap = laps[ai];
				if (lap == null) continue;

				// 目的地（最後に敵を見た場所）が、既に観測範囲にある場合は、意味なし。
				int isInsideVisibleArea = Math.abs(lap.x - agentMe.x) + Math.abs(lap.y - agentMe.y);
				if (isInsideVisibleArea <= 4) continue;
				double ddd = dis.data[lap.x][lap.y];
				if (ddd > 100) continue;
				int action = BBMUtility.ComputeFirstDirection(dis, lap.x, lap.y);
				double score = safetyScoreWorst[me - 10][action];
				if (score < usualMoveThreshold) continue;
				if (ddd < dddmin) {
					dddmin = ddd;
					dirmin = action;
				}
			}

			if (dirmin != -1) {
				actionFinal = dirmin;
				reason = "味方がいる方へ向かうムーブ";
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

		if (mugenLoopRecoveryKikan > 0) {
			mugenLoopRecoveryKikan--;
			actionFinal = -1;
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 上の選択肢で危険な状態に陥るなら、もっとも安全なアクションを取る。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		double averageWeight = 1.0e-15;
		// double averageWeight = 0.001;
		// double enemyAverageScoreWeaker = 1.2;
		// double enemyAverageScoreWeaker = 0.7;
		// double enemyAverageScoreWeaker = 1.0;
		double enemyAverageScoreWeaker = 1.0;

		// ペア避け。相手との差分。
		if (actionFinal == -1 && numVisibleTeam == 2 && numVisibleEnemy > 0) {
			double min = Double.POSITIVE_INFINITY;
			int mina = -1;
			int minb = -1;

			{
				for (int a = 0; a < 6; a++) {
					for (int b = 0; b < 6; b++) {
						double totalEnemy = 1;
						double counterEnemy = 0;
						for (int ai = 0; ai < 4; ai++) {
							if (ai == me - 10) continue;
							if (ai == friend - 10) continue;
							double snum = sr.pairScore[a][b][ai].num;
							double ssum = sr.pairScore[a][b][ai].sum;
							if (snum == 0) continue;
							double sss = ssum / snum;
							totalEnemy *= sss;
							counterEnemy++;
						}
						if (counterEnemy == 0) continue;
						double scoreEnemy = Math.pow(totalEnemy, 1 / counterEnemy / enemyAverageScoreWeaker);

						double totalTeam = 1;
						double counterTeam = 0;
						for (int ai : new int[] { me - 10, friend - 10 }) {
							double smin = sr.pairScore[a][b][ai].min;
							double ssum = sr.pairScore[a][b][ai].sum;
							double snum = sr.pairScore[a][b][ai].num;
							if (snum == 0) continue;
							double save = ssum / snum;
							double sss = smin + averageWeight * save;
							if (smin == 0) sss = 0;
							totalTeam *= sss;
							counterTeam++;
						}

						double scoreTeam = Math.pow(totalTeam, 1 / counterTeam);
						if (scoreTeam == 0) continue;

						double scoreRate = scoreEnemy / scoreTeam;
						if (scoreRate < min) {
							min = scoreRate;
							mina = a;
							minb = b;
						}
					}
				}
			}
			if (mina != -1) {
				actionFinal = mina;
				reason = "もっとも安全なアクションを選ぶ。ペア避け。差分。";
			} else {
				{
					for (int a = 0; a < 6; a++) {
						for (int b = 0; b < 6; b++) {
							double totalEnemy = 1;
							double counterEnemy = 0;
							for (int ai = 0; ai < 4; ai++) {
								if (ai == me - 10) continue;
								if (ai == friend - 10) continue;
								double snum = sr.pairScore[a][b][ai].num;
								double ssum = sr.pairScore[a][b][ai].sum;
								if (snum == 0) continue;
								double sss = ssum / snum;
								totalEnemy *= sss;
								counterEnemy++;
							}
							if (counterEnemy == 0) continue;
							double scoreEnemy = Math.pow(totalEnemy, 1 / counterEnemy);

							double totalTeam = 1;
							double counterTeam = 0;
							for (int ai : new int[] { me - 10, friend - 10 }) {
								double smin = sr.pairScore[a][b][ai].min;
								double ssum = sr.pairScore[a][b][ai].sum;
								double snum = sr.pairScore[a][b][ai].num;
								if (snum == 0) continue;
								double save = ssum / snum;
								double sss = smin + averageWeight * save;
								// if (smin == 0) sss = 0;
								totalTeam *= sss;
								counterTeam++;
							}

							double scoreTeam = Math.pow(totalTeam, 1 / counterTeam);
							if (scoreTeam == 0) continue;

							double scoreRate = scoreEnemy / scoreTeam;
							if (scoreRate < min) {
								min = scoreRate;
								mina = a;
								minb = b;
							}
						}
					}
				}
				if (mina != -1) {
					actionFinal = mina;
					reason = "もっとも安全なアクションを選ぶ。ペア避け。差分。その２。";
				}
			}
		}

		// 単独避け。相手との差分。
		if (actionFinal == -1 && numVisibleTeam == 1 && numVisibleEnemy > 0) {
			double min = Double.POSITIVE_INFINITY;
			int mina = -1;
			{
				for (int a = 0; a < 6; a++) {
					double totalEnemy = 1;
					double counterEnemy = 0;
					for (int ai = 0; ai < 4; ai++) {
						if (ai == me - 10) continue;
						if (ai == friend - 10) continue;
						double sss = safetyScoreAverage[ai][a];
						if (Double.isNaN(sss)) continue;
						totalEnemy *= sss;
						counterEnemy++;
					}
					if (counterEnemy == 0) continue;
					double scoreEnemy = Math.pow(totalEnemy, 1 / counterEnemy / enemyAverageScoreWeaker);

					double totalTeam = 1;
					double counterTeam = 0;
					for (int ai : new int[] { me - 10, friend - 10 }) {
						double smin = safetyScoreWorst[ai][a];
						double save = safetyScoreAverage[ai][a];
						if (Double.isNaN(smin) || Double.isNaN(save)) continue;
						double sss = smin + averageWeight * save;
						if (smin == 0) sss = 0;
						totalTeam *= sss;
						counterTeam++;
					}
					double scoreTeam = Math.pow(totalTeam, 1 / counterTeam);
					if (scoreTeam == 0) continue;

					double scoreRate = scoreEnemy / scoreTeam;
					if (scoreRate < min) {
						min = scoreRate;
						mina = a;
					}
				}
			}
			if (mina != -1) {
				actionFinal = mina;
				reason = "もっとも安全なアクションを選ぶ。単独。差分。";
			} else {
				{
					for (int a = 0; a < 6; a++) {
						double totalEnemy = 1;
						double counterEnemy = 0;
						for (int ai = 0; ai < 4; ai++) {
							if (ai == me - 10) continue;
							if (ai == friend - 10) continue;
							double sss = safetyScoreAverage[ai][a];
							if (Double.isNaN(sss)) continue;
							totalEnemy *= sss;
							counterEnemy++;
						}
						if (counterEnemy == 0) continue;
						double scoreEnemy = Math.pow(totalEnemy, 1 / counterEnemy);

						double totalTeam = 1;
						double counterTeam = 0;
						for (int ai : new int[] { me - 10, friend - 10 }) {
							double smin = safetyScoreWorst[ai][a];
							double save = safetyScoreAverage[ai][a];
							if (Double.isNaN(smin) || Double.isNaN(save)) continue;
							double sss = smin + averageWeight * save;
							// if (smin == 0) sss = 0;
							totalTeam *= sss;
							counterTeam++;
						}
						double scoreTeam = Math.pow(totalTeam, 1 / counterTeam);
						if (scoreTeam == 0) continue;

						double scoreRate = scoreEnemy / scoreTeam;
						if (scoreRate < min) {
							min = scoreRate;
							mina = a;
						}
					}
				}
				if (mina != -1) {
					actionFinal = mina;
					reason = "もっとも安全なアクションを選ぶ。単独。差分。その２。";
				}
			}
		}

		// 単独避け
		// if(false) {
		if (actionFinal == -1) {
			int actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], safetyScoreWorst[friend - 10], actionNG);
			System.out.println("step1");
			if (actionSelected == -1) {
				actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], safetyScoreWorst[friend - 10], -1);
				System.out.println("step2");
				if (actionSelected == -1) {
					actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], safetyScoreAverage[friend - 10], -1);
					System.out.println("step3");
					if (actionSelected == -1) {
						actionSelected = findMostSafetyAction(safetyScoreWorst[me - 10], null, -1);
						System.out.println("step4");
						if (actionSelected == -1) {
							actionSelected = findMostSafetyAction(safetyScoreAverage[me - 10], safetyScoreWorst[friend - 10], -1);
							System.out.println("step5");
							if (actionSelected == -1) {
								actionSelected = findMostSafetyAction(safetyScoreAverage[me - 10], safetyScoreAverage[friend - 10], -1);
								System.out.println("step6");
								if (actionSelected == -1) {
									actionSelected = findMostSafetyAction(safetyScoreAverage[me - 10], null, -1);
									System.out.println("step7");
									if (safetyScoreAverage[me - 10][0] > 0) {
										actionFinal = rand.nextInt(6);
										reason = "全アクションで違いなし";
									} else {
										System.out.println("完全に死ぬ。");
									}
								}
							}
						}
					}
				}
			}

			if (actionSelected != -1) {
				actionFinal = actionSelected;
				reason = "もっとも安全なアクションを選ぶ。";
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 助かる道がない。敵を道連れにできるアクションがあれば、実行する。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1) {
			double min = Double.POSITIVE_INFINITY;
			int mina = -1;

			for (int ai = 0; ai < 4; ai++) {
				if (ai == me - 10) continue;
				if (ai == friend - 10) continue;

				for (int a = 0; a < 6; a++) {
					double sss = safetyScoreAverage[ai][a];
					if (Double.isNaN(sss)) continue;
					double sss2 = safetyScoreWorst[friend - 10][a];
					if (Double.isNaN(sss2) == false && sss2 == 0) continue;

					if (sss < min) {
						min = sss;
						mina = a;
					}
				}
			}

			if (mina != -1) {
				actionFinal = mina;
				reason = "助かる道がない。敵を道連れにできるアクションがあれば、実行する。";
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//
		// 最後の手段。乱択。
		//
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		if (actionFinal == -1) {
			actionFinal = rand.nextInt(6);
			reason = "最後の手段。乱択。(^o^)/";
		}

		System.out.println("ComputeOptimalAction: agentID=" + me);
		String line = String.format("ComputeOptimalAction: %s, action=%d", reason, actionFinal);
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
			double min = Double.POSITIVE_INFINITY;
			for (int action = 0; action < 6; action++) {
				if (action == actionNG) continue;
				double scoreMe = scoresMe[action];
				if (friendDependency) {
					double scoreFriend = scoresFriend[action];
					if (scoreFriend < usualMoveThreshold) continue;
				}
				if (scoreMe > max) max = scoreMe;
				if (scoreMe < min) min = scoreMe;

			}
			if (max == min) return -1;

			List<Integer> set = new ArrayList<Integer>();
			for (int action = 0; action < 6; action++) {
				if (action == actionNG) continue;
				double scoreMe = scoresMe[action];
				if (friendDependency) {
					double scoreFriend = scoresFriend[action];
					if (scoreFriend < usualMoveThreshold) continue;
				}
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
