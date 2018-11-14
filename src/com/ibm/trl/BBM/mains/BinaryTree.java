/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import ibm.ANACONDA.Core.MyMatrix;
import ibm.ANACONDA.Core.SortValue;
import ibm.ANACONDA.Core.SortValueComparator;

public class BinaryTree {
	static int numField = GlobalParameter.numField;

	int depth = 0;
	BinaryTree parent;

	double[] ysumRight;
	double[] numSampleRight;
	double ysumBoth = 0;
	double numSampleBoth = 0;

	// 共起関係チェッカー
	static int[][] smcounter = null;
	static int[] fmcounter = null;
	static int smcounterTotal = 0;

	// 分岐情報
	List<Integer> dims = new ArrayList<Integer>();
	double aveL, aveR;
	boolean[] rightFlag;
	BinaryTree[] child;

	public BinaryTree(int depth, BinaryTree parent) {
		this.depth = depth;
		this.parent = parent;
		ysumRight = new double[numd];
		numSampleRight = new double[numd];

		if (depth == 0) {
			smcounter = new int[numd][numd];
			fmcounter = new int[numd];
		}
	}

	static int numC = 21;
	static int numd = numC * numC * 36;
	static int[] indexList = new int[numC * numC * 36 + 1];
	static int frameCounter = 0;

	public void computeFeature(int me, MyMatrix board, List<MyMatrix> boardOld, List<MyMatrix> lifeOld, List<MyMatrix> powerOld) throws Exception {
		int center = numC / 2;
		int step = 1;

		long timeStart = System.currentTimeMillis();

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int count = 0;

				//////////////////////////////////////////////////////////////
				// 説明変数を作る。
				//////////////////////////////////////////////////////////////

				// Boardを展開する。
				// 14枚
				MyMatrix boardThen = boardOld.get(step - 1);
				for (int i = 0; i < numC; i++) {
					for (int j = 0; j < numC; j++) {
						int x2 = x + i - center;
						int y2 = y + j - center;
						int panel;
						if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
							panel = Constant.Rigid;
						} else {
							panel = (int) boardThen.data[x2][y2];
						}

						if (panel >= 10 && panel <= 13) {
							if (panel == me) {
								panel = Constant.Agent0;
							} else {
								panel = Constant.Agent1;
							}
						}

						int index = panel * numC * numC + j * numC + i;
						indexList[count] = index;
						count++;
						// X.data[index][0] = 1;
					}
				}

				// BombのStrengthとLineを展開する。

				// bomb_blast_strength
				// 2-11の10枚
				MyMatrix powerThen = powerOld.get(step - 1);
				for (int i = 0; i < numC; i++) {
					for (int j = 0; j < numC; j++) {
						int x2 = x + i - center;
						int y2 = y + j - center;
						if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
						int value = (int) powerThen.data[x2][y2];
						if (value == 0) continue;
						if (value == 1) throw new Exception("error");
						if (value > 11) value = 11;
						for (int si = 2; si <= value; si++) {
							int index = (si - 2 + 14) * numC * numC + j * numC + i;
							indexList[count] = index;
							count++;
							// X.data[index][0] = 1;
						}
					}
				}

				// bomb_life
				// 1-9の9枚
				MyMatrix lifeThen = lifeOld.get(step - 1);
				for (int i = 0; i < numC; i++) {
					for (int j = 0; j < numC; j++) {
						int x2 = x + i - center;
						int y2 = y + j - center;
						if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
						int value = (int) lifeThen.data[x2][y2];
						if (value == 0) continue;
						int index = (value - 1 + 24) * numC * numC + j * numC + i;
						indexList[count] = index;
						count++;
						// X.data[index][0] = 1;
					}
				}

				// Bompの過去1ステップ分を展開する。
				// 1枚
				MyMatrix boardThenPre = boardOld.get(step - 1 + 1);
				for (int i = 0; i < numC; i++) {
					for (int j = 0; j < numC; j++) {
						int x2 = x + i - center;
						int y2 = y + j - center;
						if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
						int value = (int) boardThenPre.data[x2][y2];
						if (value != Constant.Bomb) continue;
						int index = (33) * numC * numC + j * numC + i;
						indexList[count] = index;
						count++;
						// X.data[index][0] = 1;
					}
				}

				// Flameの過去2ステップ分を展開する。
				// 2枚
				for (int k = 0; k < 2; k++) {
					MyMatrix flameThenPre = boardOld.get(step - 1 + k + 1);
					for (int i = 0; i < numC; i++) {
						for (int j = 0; j < numC; j++) {
							int x2 = x + i - center;
							int y2 = y + j - center;
							if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;
							int value = (int) flameThenPre.data[x2][y2];
							if (value != Constant.Flames) continue;
							int index = (k + 34) * numC * numC + j * numC + i;
							indexList[count] = index;
							count++;
							// X.data[index][0] = 1;
						}
					}
				}

				indexList[count] = -1;
				count++;

				//////////////////////////////////////////////////////////////
				// 目的変数を作る。
				//////////////////////////////////////////////////////////////
				// とりあえず、敵の位置を予測してみる。
				// if (boardThen.data[x][y] != Constant.Wood) {
				// continue;
				// }

				int Y = 0;
				if (board.data[x][y] == Constant.Bomb) {
					// if (board.data[x][y] == Constant.Agent3) {
					Y = 1;
				}

				//////////////////////////////////////////////////////////////
				// 学習する。
				//////////////////////////////////////////////////////////////
				put(indexList, Y);
			}
		}

		long timeEnd = System.currentTimeMillis();
		double timeSpan = (timeEnd - timeStart) * 0.001;
		// System.out.println("time, " + timeSpan);

		if (frameCounter % 100 == 0) {
			Set<Integer> selected = new TreeSet<Integer>();
			dig(selected);
			System.out.println(print());
		}
		frameCounter++;
	}

	public void put(int[] indexList, double y) throws Exception {

		// グローバルにXの共起関係を積算しておく。
		if (true) {
			if (depth == 0) {
				smcounterTotal++;

				for (int i = 0; i < indexList.length; i++) {
					int d1 = indexList[i];
					if (d1 == -1) break;
					for (int j = 0; j < indexList.length; j++) {
						int d2 = indexList[j];
						if (d2 == -1) break;
						smcounter[d1][d2] += 1;
					}
				}

				for (int i = 0; i < indexList.length; i++) {
					int d1 = indexList[i];
					if (d1 == -1) break;
					fmcounter[d1]++;
				}
			}
		}

		ysumBoth += y;
		numSampleBoth += 1;
		for (int i = 0; i < indexList.length; i++) {
			int d1 = indexList[i];
			if (d1 == -1) break;
			ysumRight[d1] += y;
			numSampleRight[d1]++;
		}

		if (child != null) {
			boolean find = false;
			for (int i = 0; i < indexList.length; i++) {
				int d1 = indexList[i];
				if (d1 == -1) break;
				if (rightFlag[d1]) {
					find = true;
					break;
				}
			}
			if (find) {
				child[1].put(indexList, y);
			} else {
				child[0].put(indexList, y);
			}
		}
	}

	class Result {
		Set<Integer> selected;
		double score;
		double aveL;
		double aveR;

		public Result(Set<Integer> selected, double score, double aveL, double aveR) {
			this.selected = selected;
			this.score = score;
			this.aveL = aveL;
			this.aveR = aveR;
		}
	}

	private Result rrr(List<Integer> targets, Set<Integer> selected, int depth, int targetIndex, double numSampleR_pre, double ysumR_pre, Result result_pre) {

		if (depth >= 30) return result_pre;

		int dTarget = targets.get(targetIndex);

		// すでに選択されてるやつとiidじゃなかったら飛ばす。
		for (int d : selected) {
			if (smcounter[dTarget][d] > 0) return result_pre;
		}

		// System.out.println(selected.toString() + " + " + dTarget);

		double numSampleB = numSampleBoth;
		double ysumB = ysumBoth;

		double numSampleR = numSampleR_pre + numSampleRight[dTarget];
		double ysumR = ysumR_pre + ysumRight[dTarget];

		if (ysumR == 0) {
			numSampleR += 1.0e-10;
			ysumR += 1.0e-10;
			numSampleB += 1.0e-10;
			ysumB += 1.0e-10;
		}

		double numSampleL = numSampleB - numSampleR;
		double ysumL = ysumB - ysumR;

		if (ysumL == 0) {
			numSampleL += 1.0e-10;
			ysumL += 1.0e-10;
			numSampleB += 1.0e-10;
			ysumB += 1.0e-10;
		}

		double score = 0;
		double aveL = 0, aveR = 0;
		if (numSampleR <= 100) {
			score = result_pre.score;
			System.out.println("まだ右リーフのサンプルが100に満たない。次に持ち越し。" + score);
		} else if (numSampleL <= 100) {
			System.out.println("すでに左リーフのサンプルが100に満たなくなった。探索打ち切り。");
			return result_pre;
		} else {
			aveL = ysumL / numSampleL;
			aveR = ysumR / numSampleR;
			double aveBoth = ysumB / numSampleB;

			double hBoth = -aveBoth * Math.log(aveBoth) - (1 - aveBoth) * Math.log(1 - aveBoth);
			double hL = -aveL * Math.log(aveL) - (1 - aveL) * Math.log(1 - aveL);
			double hR = -aveR * Math.log(aveR) - (1 - aveR) * Math.log(1 - aveR);
			double gain = hBoth - (numSampleL * hL + numSampleR * hR) / numSampleB;

			// System.out.println(depth + ", " + gain + ", " + aveBoth + ", " + aveL + ", " + aveR);

			score = gain;

			if (score <= result_pre.score) return result_pre;
		}

		// スコアが大きくなっているなら、さらに追加してみる。
		Set<Integer> selected2 = new TreeSet<Integer>();
		selected2.addAll(selected);
		selected2.add(dTarget);
		Result resultBest = new Result(selected2, score, aveL, aveR);
		Result resultMe = new Result(selected2, score, aveL, aveR);

		// 追加していってスコアが大きくなるやつがあるかどうか試す。
		for (int targetIndex2 = targetIndex + 1; targetIndex2 < targets.size(); targetIndex2++) {
			Result resultChild = rrr(targets, selected2, depth + 1, targetIndex2, numSampleR_pre + numSampleRight[dTarget], ysumR_pre + ysumRight[dTarget], resultMe);
			if (resultChild.score > resultBest.score) {
				resultBest = resultChild;
			}
		}

		return resultBest;
	}

	public void dig(Set<Integer> selected) throws Exception {
		if (child == null) {
			double numy1 = ysumBoth;
			double numy0 = numSampleBoth - numy1;
			double numySmallerFlag = Math.min(numy0, numy1);
			if (numSampleBoth > 10000 && numySmallerFlag > 2000) {

				// 偏りベストの上位N個（ただし相互に相関が弱いやつ）を選んで、OR条件で右リーフ
				if (true) {
					ArrayList<SortValue> svlist = new ArrayList<SortValue>();
					for (int d = 0; d < numd; d++) {
						double numSampleR = numSampleRight[d];
						double numSampleL = numSampleBoth - numSampleR;
						if (numSampleR < 50) continue;
						if (numSampleL < 50) continue;

						double numSampleB = numSampleBoth;
						double ysumB = ysumBoth;

						double ysumR = ysumRight[d];

						if (ysumR == 0) {
							numSampleR += 1.0e-10;
							ysumR += 1.0e-10;
							numSampleB += 1.0e-10;
							ysumB += 1.0e-10;
						}

						double ysumL = ysumB - ysumR;

						if (ysumL == 0) {
							numSampleL += 1.0e-10;
							ysumL += 1.0e-10;
							numSampleB += 1.0e-10;
							ysumB += 1.0e-10;
						}

						double aveL = ysumL / numSampleL;
						double aveR = ysumR / numSampleR;
						double aveBoth = ysumB / numSampleB;

						double hBoth = -aveBoth * Math.log(aveBoth) - (1 - aveBoth) * Math.log(1 - aveBoth);
						double hL = -aveL * Math.log(aveL) - (1 - aveL) * Math.log(1 - aveL);
						double hR = -aveR * Math.log(aveR) - (1 - aveR) * Math.log(1 - aveR);
						double gain = hBoth - (numSampleL * hL + numSampleR * hR) / numSampleB;

						svlist.add(new SortValue(d, -gain));
					}
					Collections.sort(svlist, new SortValueComparator());

					List<Integer> targets = new ArrayList<Integer>();
					int numk = Math.min(200, svlist.size());
					for (int k = 0; k < numk; k++) {
						targets.add(svlist.get(k).index);
					}

					Result resultBest = new Result(new TreeSet<Integer>(), 0, 0, 0);
					for (int k = 0; k < numk; k++) {
						Set<Integer> selected2 = new TreeSet<Integer>();
						Result resultMe = new Result(selected2, 0, 0, 0);
						Result result = rrr(targets, selected2, 0, k, 0, 0, resultMe);
						if (result.score > resultBest.score) {
							resultBest = result;
						}
					}
					System.out.println(resultBest.score);
					System.out.println(resultBest.selected);

					if (resultBest.score == 0) return;
					if (resultBest.selected.size() == 0) return;

					System.out.println("======================================");
					System.out.println("======================================");
					System.out.println("======================================");

					this.dims.clear();
					this.dims.addAll(resultBest.selected);
					this.aveL = resultBest.aveL;
					this.aveR = resultBest.aveR;
					this.rightFlag = new boolean[numd];
					for (int d : dims) {
						rightFlag[d] = true;
					}
					child = new BinaryTree[2];
					child[0] = new BinaryTree(depth + 1, this);
					child[1] = new BinaryTree(depth + 1, this);
				}
			}
		} else {
			Set<Integer> selectedHere = new TreeSet<Integer>();
			selectedHere.addAll(selected);
			selectedHere.addAll(dims);
			child[0].dig(selectedHere);
			child[1].dig(selectedHere);
		}
	}

	public String print() throws Exception {

		String prefix = "";
		for (int i = 0; i < depth; i++) {
			if (i == depth - 1) {
				prefix += "｜---";
			} else {
				prefix += "｜   ";
			}
		}

		String prefix_air = "";
		for (int i = 0; i <= depth; i++) {
			prefix_air += "｜   ";
		}

		String prefix2 = "";
		for (int i = 0; i < depth; i++) {
			if (i == depth - 1) {
				prefix2 += "｜   ";
			} else {
				prefix2 += "｜   ";
			}
		}

		String postfix = "";
		if (child == null) {
			postfix = "(Leaf)";
		}

		int numSample = (int) (numSampleBoth);
		int numy = (int) (ysumBoth);
		double average = 1.0 * ysumBoth / numSampleBoth;

		String line;
		if (parent == null) {
			line = String.format("%s■ depth=%03d, numSample=%8d, numy=%8d, ave=%10f %s\n", prefix, depth, numSample, numy, average, postfix);
		} else {
			double averageEst = Double.NaN;
			if (parent.child[0] == this) {
				averageEst = parent.aveL;
			} else if (parent.child[1] == this) {
				averageEst = parent.aveR;
			}
			line = String.format("%s■ depth=%03d, numSample=%8d, numy=%8d, average=%10f, averageEst=%10f %s\n", prefix, depth, numSample, numy, average, averageEst, postfix);
		}

		if (dims.size() > 0) {
			line += String.format("%saveL=%f, averR=%f\n", prefix_air, aveL, aveR);
		}

		for (int dSep : dims) {
			int panel = dSep / 441;
			int x = dSep % 441 % 21 - 10;
			int y = dSep % 441 / 21 - 10;
			line += String.format("%sdSep=%5d, panel=%3d, (x,y)=(%3d,%3d)\n", prefix_air, dSep, panel, x, y);
		}

		if (child != null) {
			String c1 = child[0].print();
			String c2 = child[1].print();
			line = line + c1 + c2 + prefix2 + "\n";
		}

		return line;
	}

	public String toString() {
		try {
			return print();
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}
}
