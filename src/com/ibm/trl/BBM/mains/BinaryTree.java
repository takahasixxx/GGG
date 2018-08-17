package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import ibm.ANACONDA.Core.SortValue;
import ibm.ANACONDA.Core.SortValueComparator;

public class BinaryTree {

	int depth = 0;
	int numd;
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

	public BinaryTree(int depth, int numd, BinaryTree parent) {
		this.depth = depth;
		this.numd = numd;
		this.parent = parent;
		ysumRight = new double[numd];
		numSampleRight = new double[numd];

		if (depth == 0) {
			smcounter = new int[numd][numd];
			fmcounter = new int[numd];
		}
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
					child[0] = new BinaryTree(depth + 1, numd, this);
					child[1] = new BinaryTree(depth + 1, numd, this);
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
