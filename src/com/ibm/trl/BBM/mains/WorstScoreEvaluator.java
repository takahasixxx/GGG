package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class WorstScoreEvaluator {

	static final Random rand = new Random();
	static final int numThread = GlobalParameter.numThread;
	static final int numField = GlobalParameter.numField;
	static final ForwardModel fm = new ForwardModel();

	public double[] Do(int me, int maxPower, Ability[] abs, MapInformation map, BombTracker.Node[][] bombMap, MyMatrix flameLife) throws Exception {
		List<BombTracker.Node> nodes = new ArrayList<BombTracker.Node>();
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				BombTracker.Node node = bombMap[x][y];
				if (node == null) continue;
				nodes.add(node);
			}
		}

		List<double[][]> scoresList = new ArrayList<double[][]>();
		LinkedList<BombEEE> bbbs = new LinkedList<BombEEE>();
		rrr(0, me, maxPower, abs, map, nodes, flameLife, bbbs, scoresList);

		double[][] scores = new double[2][];
		scores[0] = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
		scores[1] = new double[6];
		for (double[][] temp : scoresList) {
			for (int a = 0; a < 6; a++) {
				if (temp[0][a] < scores[0][a]) {
					scores[0][a] = temp[0][a];
				}
				scores[1][a] += temp[1][a] / scoresList.size();
			}
		}

		boolean allzero = true;
		for (int a = 0; a < 6; a++) {
			if (scores[0][a] > 0) {
				allzero = false;
				break;
			}
		}

		if (allzero == false) {
			return scores[0];
		} else {
			System.out.println("最悪ケースが全部0なので平均を使う。");
			return scores[1];
		}
	}

	/**
	 * 
	 * 爆弾の移動方向が未確定の場合、1ステップ目でキックできる場合、条件を増やす。
	 * 
	 */
	private void rrr(int index, int me, int maxPower, Ability[] abs, MapInformation map, List<BombTracker.Node> nodes, MyMatrix flameLife, LinkedList<BombEEE> bbbs, List<double[][]> scoresList)
			throws Exception {
		if (index == nodes.size()) {
			// System.out.println("start");

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			//
			// 基本データを作る。
			//
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			Ability[] abs2 = new Ability[4];
			for (int ai = 0; ai < 4; ai++) {
				abs2[ai] = new Ability(abs[ai]);
				if (ai + 10 == me) continue;
				abs2[ai].kick = true;
				abs2[ai].numMaxBomb = 3;
				abs2[ai].numBombHold = 3;
				abs2[ai].strength = maxPower;
			}

			Pack packNow;
			if (true) {
				StatusHolder sh = new StatusHolder(numField);
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						int type = map.getType(x, y);
						if (Constant.isAgent(type)) {
							sh.setAgent(x, y, type);
						}
					}
				}

				for (BombEEE bbb : bbbs) {
					sh.setBomb(bbb.x, bbb.y, -1, bbb.life, bbb.dir, bbb.power);
				}

				packNow = new Pack(map.board, flameLife, abs2, sh);
			}

			AgentEEE[] agents = new AgentEEE[4];
			for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
				agents[aaa.agentID - 10] = aaa;
			}

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			//
			// エージェント消した現状盤面を用意しておく。
			//
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			Pack packNow_nagent;
			if (true) {
				StatusHolder sh_nagent = new StatusHolder(numField);
				for (BombEEE bbb : packNow.sh.getBombEntry()) {
					sh_nagent.setBomb(bbb.x, bbb.y, -1, bbb.life, bbb.dir, bbb.power);
				}

				MyMatrix board_nagent = new MyMatrix(packNow.board);
				if (true) {
					BombEEE[][] bombMap = new BombEEE[numField][numField];
					for (BombEEE bbb : packNow.sh.getBombEntry()) {
						bombMap[bbb.x][bbb.y] = bbb;
					}

					for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
						if (bombMap[aaa.x][aaa.y] == null) {
							board_nagent.data[aaa.x][aaa.y] = Constant.Passage;
						} else {
							board_nagent.data[aaa.x][aaa.y] = Constant.Bomb;
						}
					}

					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							int type = (int) board_nagent.data[x][y];
							if (Constant.isItem(type)) {
								board_nagent.data[x][y] = Constant.Passage;
							}
						}
					}
				}

				packNow_nagent = new Pack(board_nagent, flameLife, abs2, sh_nagent);
			}

			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			//
			// １ステップ目の状態を作る。
			//
			/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
			double[][] scores = new double[2][6];
			for (int firstAction = 0; firstAction < 6; firstAction++) {
				boolean[][] temp = new boolean[4][6];
				for (int ai = 0; ai < 4; ai++) {
					if (ai == me - 10) {
						temp[ai][firstAction] = true;
					} else {
						if (agents[ai] == null) {
							temp[ai][0] = true;
						} else {
							temp[ai] = new boolean[] { true, true, true, true, true, true };
						}
					}
				}

				List<int[]> actionsList = new ArrayList<int[]>();
				for (int a = 0; a < 6; a++) {
					if (temp[0][a] == false) continue;
					for (int b = 0; b < 6; b++) {
						if (temp[1][b] == false) continue;
						for (int c = 0; c < 6; c++) {
							if (temp[2][c] == false) continue;
							for (int d = 0; d < 6; d++) {
								if (temp[3][d] == false) continue;

								int[] actions = new int[] { a, b, c, d };
								actionsList.add(actions);
							}
						}
					}
				}

				List<Pack> packs_nagent = new ArrayList<Pack>();
				List<Pack> packs_onlyme = new ArrayList<Pack>();
				List<int[]> actionsList2 = new ArrayList<int[]>();
				for (int[] actions : actionsList) {
					Pack packNext = fm.Step(packNow.board, packNow.flameLife, packNow.abs, packNow.sh, actions);

					// Agentは消してしまう。
					Pack packNext_nagent;
					Pack packNext_onlyme;
					if (true) {
						BombEEE[][] bombMap = new BombEEE[numField][numField];
						for (BombEEE bbb : packNext.sh.getBombEntry()) {
							bombMap[bbb.x][bbb.y] = bbb;
						}

						StatusHolder sh_nagent = new StatusHolder(numField);
						StatusHolder sh_onlyme = new StatusHolder(numField);
						MyMatrix board_nagent = new MyMatrix(packNext.board);
						MyMatrix board_onlyme = new MyMatrix(packNext.board);
						for (AgentEEE aaa : packNext.sh.getAgentEntry()) {
							if (aaa.agentID != me) {
								if (bombMap[aaa.x][aaa.y] == null) {
									board_onlyme.data[aaa.x][aaa.y] = Constant.Passage;
								} else {
									board_onlyme.data[aaa.x][aaa.y] = Constant.Bomb;
								}
							} else {
								sh_onlyme.setAgent(aaa.x, aaa.y, aaa.agentID);
							}
							if (bombMap[aaa.x][aaa.y] == null) {
								board_nagent.data[aaa.x][aaa.y] = Constant.Passage;
							} else {
								board_nagent.data[aaa.x][aaa.y] = Constant.Bomb;
							}
						}

						for (BombEEE bbb : packNext.sh.getBombEntry()) {
							sh_nagent.setBomb(bbb.x, bbb.y, bbb.owner, bbb.life, bbb.dir, bbb.power);
							sh_onlyme.setBomb(bbb.x, bbb.y, bbb.owner, bbb.life, bbb.dir, bbb.power);
						}

						packNext_nagent = new Pack(board_nagent, packNext.flameLife, packNext.abs, sh_nagent);
						packNext_onlyme = new Pack(board_onlyme, packNext.flameLife, packNext.abs, sh_onlyme);
					}

					// 同様の盤面があるかどうか調べる。
					boolean find = false;
					for (Pack pack : packs_onlyme) {
						if (pack.sh.getBombEntry().size() == packNext_onlyme.sh.getBombEntry().size()) {
							double def = pack.board.minus(packNext_onlyme.board).normL1();
							if (def == 0) {
								double def2 = pack.flameLife.minus(packNext_onlyme.flameLife).normL1();
								if (def2 == 0) {
									boolean allsame = true;
									for (BombEEE bbb1 : pack.sh.getBombEntry()) {
										boolean same = false;
										for (BombEEE bbb2 : packNext_onlyme.sh.getBombEntry()) {
											if (bbb1.equals(bbb2)) {
												same = true;
												break;
											}
										}
										if (same == false) {
											allsame = false;
											break;
										}
									}
									if (allsame) {
										find = true;
									}
								}
							}
						}
						if (find) break;
					}
					if (find) continue;
					packs_nagent.add(packNext_nagent);
					packs_onlyme.add(packNext_onlyme);
					actionsList2.add(actions);
				}

				if (false) {
					System.out.println("================================");
					System.out.println("================================");
					for (int[] act : actionsList2) {
						int a = act[0];
						int b = act[1];
						int c = act[2];
						int d = act[3];
						System.out.println("代表されるアクション, " + a + ", " + b + ", " + c + ", " + d);
					}
					System.out.println("================================");
				}

				/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
				//
				// 全行動の経路のスコアを計算してみる。
				//
				/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
				double worst = Double.MAX_VALUE;
				double sum = 0;
				for (int i = 0; i < packs_onlyme.size(); i++) {
					Pack pack_onlyme = packs_onlyme.get(i);
					Pack pack_nagent = packs_nagent.get(i);
					int[] actions = actionsList2.get(i);

					// 自分エージェントが死んでいたら終了
					double s;
					if (pack_onlyme.abs[me - 10].isAlive == false) {
						s = 0;
					} else {
						WorstScoreEvaluatorSingle wses = new WorstScoreEvaluatorSingle();
						s = wses.Do(me, firstAction, packNow, packNow_nagent, pack_onlyme, pack_nagent);
					}

					System.out.println(String.format("actions=(%d, %d, %d, %d), log(s)=%f", actions[0], actions[1], actions[2], actions[3], Math.log(s)));
					if (s < worst) {
						worst = s;
					}
					sum += s;
				}

				scores[0][firstAction] = worst;

				double ave;
				if (packs_onlyme.size() == 0) {
					ave = 0;
				} else {
					ave = sum / packs_onlyme.size();
				}
				scores[1][firstAction] = ave;
			}

			scoresList.add(scores);
		} else {
			BombTracker.Node node = nodes.get(index);
			for (int dir = 0; dir < 5; dir++) {
				if (node.dirs[dir]) {
					BombEEE bbb = new BombEEE(node.x, node.y, -1, node.life, dir, node.power);
					bbbs.addLast(bbb);
					rrr(index + 1, me, maxPower, abs, map, nodes, flameLife, bbbs, scoresList);
					bbbs.removeLast();
				}
			}
		}
	}
}
