package ibm.ANACONDA.Core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MatrixUtility {
	static public MyMatrix ReadMatrixFromFile(File file) throws Exception {
		return ReadMatrixFromFile(file, false, false);
	}

	static public MyMatrix ReadMatrixFromFile(File file, boolean titleFlag) throws Exception {
		return ReadMatrixFromFile(file, titleFlag, false);
	}

	static public MyMatrix ReadMatrixFromFile(File file, boolean titleFlagColumn, boolean titleFlagRow) throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(file));
		int numrow = 0;
		int numcol = -1;
		// 一行目が変数名だったら飛ばす。
		if (titleFlagColumn == true) {
			String line = br.readLine();
			if (titleFlagRow == true) {
				numcol = line.split(",").length - 1;
			} else {
				numcol = line.split(",").length;
			}
		}
		while (true) {
			String line = br.readLine();
			if (line == null) break;

			String[] terms = line.split(",");
			int numcol2;
			if (titleFlagRow == true) {
				numcol2 = terms.length - 1;
			} else {
				numcol2 = terms.length;
			}
			if (numcol2 <= 0) { throw new Exception("invalid csv file"); }
			if (numcol == -1) {
				numcol = numcol2;
			} else if (numcol != numcol2) { throw new Exception("invalid csv file"); }
			numrow++;
		}
		br.close();

		if (numcol == -1) throw new Exception("invalid csv file");
		MyMatrix ret = new MyMatrix(numrow, numcol);

		br = new BufferedReader(new FileReader(file));
		// 一行目が変数名だったら飛ばす。
		if (titleFlagColumn == true) {
			br.readLine();
		}

		int rowIndex = 0;
		while (true) {
			String line = br.readLine();
			if (line == null) break;

			String[] terms = line.split(",");
			for (int colIndex = 0; colIndex < numcol; colIndex++) {
				double value;
				// TODO
				try {
					if (titleFlagRow == true) {
						value = Double.parseDouble(terms[colIndex + 1]);
					} else {
						value = Double.parseDouble(terms[colIndex]);
					}
				} catch (NumberFormatException e) {
					value = 0;
				}
				ret.set(rowIndex, colIndex, value);
			}
			rowIndex++;
		}
		br.close();
		return ret;
	}

	static public void WriteMatrixToFile(File file, MyMatrix mat) throws Exception {
		WriteMatrixToFile(file, mat, null, null);
	}

	static public void WriteMatrixToFile(File file, MyMatrix mat, String title) throws Exception {
		WriteMatrixToFile(file, mat, title, null);
	}

	static public void WriteMatrixToFile(File file, MyMatrix mat, ArrayList<String> titleColumn, ArrayList<String> titleRow, boolean dummy) throws Exception {
		String cstr = null;
		if (titleColumn != null) {
			for (String temp : titleColumn) {
				if (cstr == null) {
					cstr = temp;
				} else {
					cstr += "," + temp;
				}
			}
		}

		String rstr = null;
		if (titleRow != null) {
			for (String temp : titleRow) {
				if (rstr == null) {
					rstr = temp;
				} else {
					rstr += "," + temp;
				}
			}
		}

		WriteMatrixToFile(file, mat, cstr, rstr);
	}

	static public void WriteMatrixToFile(File file, MyMatrix mat, String titleColumn, String titleRow) throws Exception {
		String[] titleRowPart = null;
		if (titleRow != null) {
			titleRowPart = titleRow.split(",");
		}

		PrintWriter pr = new PrintWriter(new BufferedWriter(new FileWriter(file)));
		if (titleColumn != null) {
			pr.write(titleColumn);
			pr.println();
		}
		int numrow = mat.getRowDimension();
		int numcol = mat.getColumnDimension();
		for (int i = 0; i < numrow; i++) {
			boolean pre = false;
			if (titleRow != null) {
				pr.write(titleRowPart[i]);
				pre = true;
			}
			for (int j = 0; j < numcol; j++) {
				if (pre == false) {
					pre = true;
				} else {
					pr.print(",");
				}
				double value = mat.get(i, j);
				pr.print(value);
			}
			pr.println();
		}
		pr.flush();
		pr.close();
	}

	static public MyMatrix ReadMatrixFromBinaryFile(File file) throws Exception {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
		MyMatrix ret = (MyMatrix) ois.readObject();
		ois.close();
		return ret;
	}

	static public void WriteMatrixToBinaryFile(File file, MyMatrix mat) throws Exception {
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
		oos.writeObject(mat);
		oos.flush();
		oos.close();
	}

	static public MyMatrix ReadMatrixFromCompressedBinaryFile(File file) throws Exception {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
		MyMatrix ret = (MyMatrix) ois.readObject();
		ois.close();
		return ret;
	}

	static public void WriteMatrixToCompressedBinaryFile(File file, MyMatrix mat) throws Exception {
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file));

		ZipEntry entry = new ZipEntry(file.getName());
		zos.putNextEntry(entry);

		ObjectOutputStream oos = new ObjectOutputStream(zos);
		oos.writeObject(mat);
		oos.flush();
		zos.closeEntry();
		oos.close();
	}

	static public void OutputMatrix(MyMatrix mat) throws Exception {

		int numrow = mat.getRowDimension();
		int numcol = mat.getColumnDimension();

		for (int i = 0; i < numrow; i++) {
			for (int j = 0; j < numcol; j++) {
				double v = mat.get(i, j);
				if (j > 0) System.out.print(", ");
				System.out.print(String.format("%.3f", v));
			}
			System.out.println();
		}
	}

	static public MyMatrix ComputeAverage(MyMatrix mat) throws Exception {
		double[][] data = mat.getArray();

		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();

		double[][] averages = new double[numd][1];
		for (int d = 0; d < numd; d++) {
			double total = 0;
			for (int t = 0; t < numt; t++) {
				total += data[t][d];
			}
			double average = total / numt;
			averages[d][0] = average;
		}
		return new MyMatrix(averages);
	}

	static public MyMatrix ComputeAverage(MyMatrix mat, MyMatrix weight) throws Exception {
		double[][] data = mat.getArray();
		double[][] wdata = weight.getArray();

		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();

		double wtotal = 0;
		{
			for (int t = 0; t < numt; t++) {
				wtotal += wdata[t][0];
			}
			if (wtotal == 0) throw new Exception("total weight equals to zero");
		}

		double[][] averages = new double[numd][1];
		for (int d = 0; d < numd; d++) {
			double total = 0;
			for (int t = 0; t < numt; t++) {
				total += data[t][d] * wdata[t][0];
			}
			double average = total / wtotal;
			averages[d][0] = average;
		}
		return new MyMatrix(averages);
	}

	static public MyMatrix CenteringAverage(MyMatrix mat) throws Exception {
		double[][] data = mat.getArray();

		int numd = mat.getColumnDimension();
		int numt = mat.getRowDimension();

		double[][] ret = new double[numt][numd];

		for (int d = 0; d < numd; d++) {
			double total = 0;
			for (int t = 0; t < numt; t++) {
				total += data[t][d];
			}
			double average = total / numt;
			for (int t = 0; t < numt; t++) {
				ret[t][d] = data[t][d] - average;
			}
		}

		return new MyMatrix(ret);
	}

	static public MyMatrix CenteringAverage(MyMatrix mat, MyMatrix averages) throws Exception {
		if (mat.getColumnDimension() != averages.getRowDimension()) throw new Exception("MatrixUtility.CenteringAverage : invalid dimension");

		double[][] data = mat.getArray();

		int numd = mat.getColumnDimension();
		int numt = mat.getRowDimension();

		double[][] ret = new double[numt][numd];
		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt; t++) {
				ret[t][d] = data[t][d] - averages.data[d][0];
			}
		}

		return new MyMatrix(ret);
	}

	static public MyMatrix CenteringAverageBack(MyMatrix mat, MyMatrix averages) throws Exception {
		if (mat.getColumnDimension() != averages.getRowDimension()) throw new Exception("MatrixUtility.CenteringAverage : invalid dimension");

		double[][] data = mat.getArray();

		int numd = mat.getColumnDimension();
		int numt = mat.getRowDimension();

		double[][] ret = new double[numt][numd];
		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt; t++) {
				ret[t][d] = data[t][d] + averages.data[d][0];
			}
		}

		return new MyMatrix(ret);
	}

	static public MyMatrix ComputeSigma(MyMatrix mat) throws Exception {
		double[][] data = mat.getArray();

		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();

		double[][] sigmas = new double[numd][1];
		for (int d = 0; d < numd; d++) {
			double total = 0;
			for (int t = 0; t < numt; t++) {
				total += data[t][d] * data[t][d];
			}
			double sigma = total / numt;
			sigmas[d][0] = Math.sqrt(sigma);
		}
		return new MyMatrix(sigmas);
	}

	static public MyMatrix ComputeSigma(MyMatrix mat, MyMatrix weight) throws Exception {
		double[][] data = mat.getArray();
		double[][] wdata = weight.getArray();

		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();

		double wtotal = 0;
		{
			for (int t = 0; t < numt; t++) {
				wtotal += wdata[t][0];
			}
			if (wtotal == 0) throw new Exception("total weight equals to zero");
		}

		double[][] sigmas = new double[numd][1];
		for (int d = 0; d < numd; d++) {
			double total = 0;
			for (int t = 0; t < numt; t++) {
				total += data[t][d] * data[t][d] * wdata[t][0];
			}
			double sigma = total / wtotal;
			sigmas[d][0] = Math.sqrt(sigma);
		}
		return new MyMatrix(sigmas);
	}

	static public MyMatrix Rescale(MyMatrix mat, MyMatrix sigmas) throws Exception {
		if (mat.getColumnDimension() != sigmas.getRowDimension()) throw new Exception("MatrixUtility.Rescale : invalid dimension");

		double[][] data = mat.getArray();

		int numd = mat.getColumnDimension();
		int numt = mat.getRowDimension();

		double[][] ret = new double[numt][numd];
		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt; t++) {
				if (sigmas.get(d, 0) == 0) {
					ret[t][d] = 0;
				} else {
					ret[t][d] = data[t][d] / sigmas.data[d][0];
				}
			}
		}

		return new MyMatrix(ret);
	}

	static public MyMatrix RescaleBack(MyMatrix mat, MyMatrix sigmas) throws Exception {
		if (mat.getColumnDimension() != sigmas.getRowDimension()) throw new Exception("MatrixUtility.Rescale : invalid dimension");

		double[][] data = mat.getArray();

		int numd = mat.getColumnDimension();
		int numt = mat.getRowDimension();

		double[][] ret = new double[numt][numd];
		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt; t++) {
				ret[t][d] = data[t][d] * sigmas.data[d][0];
			}
		}

		return new MyMatrix(ret);
	}

	static public MyMatrix Whitening(MyMatrix mat) throws Exception {
		return Whitening(mat, true);
	}

	static public MyMatrix Whitening(MyMatrix mat, boolean isCentering) throws Exception {
		if (isCentering) {
			MyMatrix averages = ComputeAverage(mat);
			mat = CenteringAverage(mat, averages);
		}
		MyMatrix sigmas = ComputeSigma(mat);
		mat = Rescale(mat, sigmas);
		return mat;
	}

	static public class WhiteningParameter implements Serializable {
		private static final long serialVersionUID = -5406390244041857186L;
		public MyMatrix average;
		public MyMatrix sigma;
		public MyMatrix mat;

		public WhiteningParameter(MyMatrix average, MyMatrix sigma, MyMatrix mat) {
			this.average = average;
			this.sigma = sigma;
			this.mat = mat;
		}
	}

	static public WhiteningParameter Whitening2(MyMatrix mat) throws Exception {
		MyMatrix weight = new MyMatrix(mat.getRowDimension(), 1, 1);
		return Whitening2(mat, weight, true);
	}

	static public WhiteningParameter Whitening2(MyMatrix mat, boolean isCentering) throws Exception {
		MyMatrix weight = new MyMatrix(mat.getRowDimension(), 1, 1);
		return Whitening2(mat, weight, isCentering);
	}

	static public WhiteningParameter Whitening2(MyMatrix mat, MyMatrix weight, boolean isCentering) throws Exception {
		MyMatrix averages;
		if (isCentering) {
			averages = ComputeAverage(mat, weight);
			mat = CenteringAverage(mat, averages);
		} else {
			averages = new MyMatrix(mat.getColumnDimension(), 1);
		}
		MyMatrix sigmas = ComputeSigma(mat, weight);
		mat = Rescale(mat, sigmas);
		return new WhiteningParameter(averages, sigmas, mat);
	}

	static public MyMatrix Whitening2(MyMatrix mat, WhiteningParameter wp) throws Exception {
		mat = CenteringAverage(mat, wp.average);
		mat = Rescale(mat, wp.sigma);
		return mat;
	}

	static public MyMatrix WhiteningBack2(MyMatrix mat, WhiteningParameter param) throws Exception {
		mat = RescaleBack(mat, param.sigma);
		mat = CenteringAverageBack(mat, param.average);
		return mat;
	}

	static public MyMatrix ComputeFirstMomentSum(MyMatrix mat) throws Exception {
		double[][] data = mat.getArray();

		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();

		double[][] averages = new double[numd][1];
		for (int d = 0; d < numd; d++) {
			double total = 0;
			for (int t = 0; t < numt; t++) {
				total += data[t][d];
			}
			averages[d][0] = total;
		}
		return new MyMatrix(averages);
	}

	static public MyMatrix ComputeSecondMomentSum(MyMatrix value) throws Exception {
		return ComputeSecondMomentSum(value, 1, false);
	}

	static public MyMatrix ComputeSecondMomentSum(MyMatrix value, int numThread) throws Exception {
		return ComputeSecondMomentSum(value, numThread, false);
	}

	static public MyMatrix ComputeSecondMomentSum(MyMatrix value, int numThread, final boolean verbose) throws Exception {
		return ComputeSecondMomentSum(value, null, numThread, verbose);
	}

	static public MyMatrix ComputeSecondMomentSum(MyMatrix value, boolean[] useFlag, int numThread, final boolean verbose) throws Exception {
		class RunnableComputeSecondMomentSum extends MyRunnable {
			int d1 = 0;
			int d2 = 0;
			MyMatrix value;
			double cor;

			public RunnableComputeSecondMomentSum(int d1, int d2, MyMatrix value) {
				this.d1 = d1;
				this.d2 = d2;
				this.value = value;
			}

			@Override
			public void run() throws Exception {
				if (verbose == true) {
					if (d1 == d2) System.out.println(this.getClass().getName() + ": " + d1 + ", " + d2);
				}

				int numt = value.getRowDimension();
				double[][] valueData = value.getArray();
				double total = 0;
				for (int t = 0; t < numt; t++) {
					total += valueData[t][d1] * valueData[t][d2];
				}
				cor = total;
			}
		}

		int numd = value.getColumnDimension();

		LinkedList<MyRunnable> rs = new LinkedList<MyRunnable>();
		ArrayList<RunnableComputeSecondMomentSum> list = new ArrayList<RunnableComputeSecondMomentSum>();
		for (int d1 = 0; d1 < numd; d1++) {
			for (int d2 = d1; d2 < numd; d2++) {
				if (useFlag != null) {
					if (useFlag[d1] == false || useFlag[d2] == false) continue;
				}
				RunnableComputeSecondMomentSum r = new RunnableComputeSecondMomentSum(d1, d2, value);
				rs.add(r);
				list.add(r);
			}
		}

		if (numThread == 1) {
			for (RunnableComputeSecondMomentSum r : list) {
				if (Thread.currentThread().isInterrupted() == true) { throw new InterruptedException("Interruped"); }
				r.run();
			}
		} else {
			ThreadPoolFramework tpf = new ThreadPoolFramework(numThread, rs);
			tpf.Join();
			if (tpf.isAllSucceeded() == false) throw new Exception("Error on child thread");
		}

		double[][] ret = new double[numd][numd];
		for (RunnableComputeSecondMomentSum r : list) {
			ret[r.d1][r.d2] = r.cor;
			ret[r.d2][r.d1] = r.cor;
		}

		return new MyMatrix(ret);
	}

	static public MyMatrix ComputeQuantileValue(MyMatrix mat, double quantile) throws Exception {
		return ComputeQuantileValue(mat, quantile, true);
	}

	static public MyMatrix ComputeQuantileValue(MyMatrix mat, double quantile, boolean isSmall2Large) throws Exception {
		double[] quantiles = { quantile };
		return ComputeQuantileValue(mat, quantiles, isSmall2Large);
	}

	static public MyMatrix ComputeQuantileValue(MyMatrix mat, double[] quantiles) throws Exception {
		return ComputeQuantileValue(mat, quantiles, true);
	}

	static public MyMatrix ComputeQuantileValue(MyMatrix mat, double[] quantiles, boolean isSmall2Large) throws Exception {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		int numq = quantiles.length;

		MyMatrix ret = new MyMatrix(numd, numq);
		for (int d = 0; d < numd; d++) {
			ArrayList<Double> values = new ArrayList<Double>();
			for (int t = 0; t < numt; t++) {
				double v = mat.data[t][d];
				values.add(v);
			}
			Collections.sort(values);
			if (isSmall2Large == false) {
				Collections.reverse(values);
			}

			for (int qi = 0; qi < numq; qi++) {
				double quantile = quantiles[qi];
				int index = (int) (numt * quantile);
				if (index < 0) index = 0;
				if (index >= numt) index = numt - 1;
				ret.data[d][qi] = values.get(index);
			}
		}
		return ret;
	}

	static public MyMatrix binarizeWithThreshold(MyMatrix mat, double threshold, boolean isUpperOne) throws Exception {
		if (mat.getColumnDimension() != 1) throw new Exception("Error");
		MyMatrix th = new MyMatrix(1, 1, threshold);
		return binarizeWithThreshold(mat, th, isUpperOne);
	}

	static public MyMatrix binarizeWithThreshold(MyMatrix mat, MyMatrix threshold) throws Exception {
		return binarizeWithThreshold(mat, threshold, true);
	}

	static public MyMatrix binarizeWithThreshold(MyMatrix mat, MyMatrix threshold, boolean isUpperOne) throws Exception {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();

		MyMatrix ret = new MyMatrix(numt, numd);
		for (int d = 0; d < numd; d++) {
			double th = threshold.data[d][0];
			for (int t = 0; t < numt; t++) {
				double v = mat.data[t][d];
				if (isUpperOne) {
					if (v >= th) {
						ret.data[t][d] = 1;
					} else {
						ret.data[t][d] = 0;
					}
				} else {
					if (v >= th) {
						ret.data[t][d] = 0;
					} else {
						ret.data[t][d] = 1;
					}
				}
			}
		}

		return ret;
	}

	static public MyMatrix ReadStockFile(File file) throws Exception {
		MyMatrix stock = MatrixUtility.ReadMatrixFromFile(file, false);
		if (stock == null) return null;

		// 株式分割を補正する。
		if (true) {
			int numtStock = stock.getRowDimension();
			double rate = 1;
			for (int t = 0; t < numtStock; t++) {
				for (int d = 0; d < 4; d++) {
					double v = stock.get(t, d + 2);
					stock.set(t, d + 2, v * rate);
				}
				if (true) {
					double deki = stock.get(t, 6);
					stock.set(t, 6, deki / rate);
				}
				double unitPre = stock.get(t, 7);
				double unitAfter = stock.get(t, 8);
				rate *= unitPre / unitAfter;
			}
		}
		return stock;
	}

	static public MyMatrix ConnectColumn(MyMatrix A, MyMatrix B) {
		int numt = A.getRowDimension();
		int numd1 = A.getColumnDimension();
		int numd2 = B.getColumnDimension();

		MyMatrix ret = new MyMatrix(numt, numd1 + numd2);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd1; d++) {
				ret.set(t, d, A.get(t, d));
			}
		}
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd2; d++) {
				ret.set(t, numd1 + d, B.get(t, d));
			}
		}
		return ret;
	}

	static public MyMatrix ConnectColumn(List<MyMatrix> ms) throws Exception {
		int numt = -1;
		int numd = 0;
		for (MyMatrix m : ms) {
			if (numt == -1) {
				numt = m.getRowDimension();
			} else {
				if (numt != m.getRowDimension()) throw new Exception("Error");
			}
			numd += m.getColumnDimension();
		}

		double[][] ret = new double[numt][numd];
		int offset = 0;
		for (MyMatrix m : ms) {
			int numdLocal = m.getColumnDimension();

			for (int d = 0; d < numdLocal; d++) {
				for (int t = 0; t < numt; t++) {
					ret[t][d + offset] = m.get(t, d);
				}
			}

			offset += numdLocal;
		}

		return new MyMatrix(ret);
	}

	static public MyMatrix ConnectRow(MyMatrix A, MyMatrix B) {
		int numt1 = A.getRowDimension();
		int numt2 = B.getRowDimension();
		int numd = A.getColumnDimension();

		double[][] AData = A.getArray();
		double[][] BData = B.getArray();

		double[][] ret = new double[numt1 + numt2][numd];
		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt1; t++) {
				ret[t][d] = AData[t][d];
			}
			for (int t = 0; t < numt2; t++) {
				ret[numt1 + t][d] = BData[t][d];
			}
		}
		return new MyMatrix(ret);
	}

	static public MyMatrix ConnectRow(MyMatrix[] Xs) {
		ArrayList<MyMatrix> list = new ArrayList<MyMatrix>();
		for (MyMatrix X : Xs) {
			list.add(X);
		}
		return ConnectRow(list);
	}

	static public MyMatrix ConnectRow(ArrayList<MyMatrix> Xs) {

		int total = 0;
		int numd = 0;
		for (MyMatrix XL : Xs) {
			total += XL.getRowDimension();
			numd = XL.getColumnDimension();
		}

		MyMatrix ret = new MyMatrix(total, numd);
		int offset = 0;
		for (MyMatrix XL : Xs) {
			int numt = XL.getRowDimension();
			for (int t = 0; t < numt; t++) {
				for (int d = 0; d < numd; d++) {
					double v = XL.get(t, d);
					ret.set(t + offset, d, v);
				}
			}
			offset += numt;
		}

		return ret;
	}

	static public long[] ConnectRow(long[] A, long[] B) {
		int numt1 = A.length;
		int numt2 = B.length;

		long[] ret = new long[numt1 + numt2];
		for (int t = 0; t < numt1; t++) {
			ret[t] = A[t];
		}
		for (int t = 0; t < numt2; t++) {
			ret[t + numt1] = B[t];
		}
		return ret;
	}

	static public long[] GetSubArray(long[] A, ArrayList<Integer> index) {
		int numt = index.size();
		long[] ret = new long[numt];

		for (int t = 0; t < numt; t++) {
			ret[t] = A[index.get(t)];
		}
		return ret;
	}

	static public long[] GetSubArray(long[] A, int s, int e) {
		int numt = e - s + 1;
		long[] ret = new long[numt];

		for (int t = 0; t < numt; t++) {
			ret[t] = A[t + s];
		}
		return ret;
	}

	static public int[] GetSubArray(int[] A, ArrayList<Integer> index) {
		int numt = index.size();
		int[] ret = new int[numt];

		for (int t = 0; t < numt; t++) {
			ret[t] = A[index.get(t)];
		}
		return ret;
	}

	static public int[] GetSubArray(int[] A, int s, int e) {
		int numt = e - s + 1;
		int[] ret = new int[numt];

		for (int t = 0; t < numt; t++) {
			ret[t] = A[t + s];
		}
		return ret;
	}

	static public void SetAll(MyMatrix A, double v) {
		int numt = A.getRowDimension();
		int numd = A.getColumnDimension();

		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt; t++) {
				A.set(t, d, v);
			}
		}
	}

	static public class CrossValidationData {
		public MyMatrix Learn, Test;

		public CrossValidationData(MyMatrix AL, MyMatrix AT) {
			this.Learn = AL;
			this.Test = AT;
		}
	}

	static public CrossValidationData ComputeCrossValicationData(MyMatrix A, int numCV, int cvi, int type) {
		if (type == 0) {// numCV飛びにデータを分ける。
			int numt = A.getRowDimension();
			int numd = A.getColumnDimension();

			int numT = 0;
			int numL = 0;
			for (int t = 0; t < numt; t++) {
				if (t % numCV == cvi) {
					numT++;
				} else {
					numL++;
				}
			}

			MyMatrix AT, AL;
			if (true) {
				int countT = 0;
				int countL = 0;
				int[] indexT = new int[numT];
				int[] indexL = new int[numL];
				for (int t = 0; t < numt; t++) {
					if (t % numCV == cvi) {
						indexT[countT] = t;
						countT++;
					} else {
						indexL[countL] = t;
						countL++;
					}
				}
				AT = A.getMatrix(indexT, 0, numd - 1);
				AL = A.getMatrix(indexL, 0, numd - 1);
			}

			return new CrossValidationData(AL, AT);
		} else if (type == 1) {// 途中の1/numCVをまとめて分ける。
			int numt = A.getRowDimension();
			int numd = A.getColumnDimension();

			int start = numt * cvi / numCV;
			int end = numt * (cvi + 1) / numCV;

			int numtT = end - start;
			int numtL = numt - numtT;
			int[] indexT = new int[numtT];
			int[] indexL = new int[numtL];
			int countT = 0;
			int countL = 0;
			for (int t = 0; t < numt; t++) {
				if (t >= start && t < end) {
					indexT[countT] = t;
					countT++;
				} else {
					indexL[countL] = t;
					countL++;
				}
			}

			MyMatrix AT = A.getMatrix(indexT, 0, numd - 1);
			MyMatrix AL = A.getMatrix(indexL, 0, numd - 1);

			return new CrossValidationData(AL, AT);
		} else {
			return null;
		}
	}

	static public MyMatrix SubsamplingMatrix(MyMatrix A, int numt2) {
		int numd = A.getColumnDimension();
		int numt = A.getRowDimension();

		double[][] ret = new double[numt2][numd];
		double rate = 1.0 * numt / numt2;

		for (int i = 0; i < numt2; i++) {
			int j = (int) (rate * i);
			for (int d = 0; d < numd; d++) {
				ret[i][d] = A.get(j, d);
			}
		}
		return new MyMatrix(ret);
	}

	static public long[] SubsamplingMatrix(long[] A, int numt2) {
		int numt = A.length;

		long[] ret = new long[numt2];
		double rate = 1.0 * numt / numt2;

		for (int i = 0; i < numt2; i++) {
			int j = (int) (rate * i);
			ret[i] = A[j];
		}
		return ret;
	}

	static public MyMatrix SubmatrixExceptingD(MyMatrix value, int d) {
		int numd = value.getColumnDimension();
		int numt = value.getRowDimension();
		int[] index = new int[numd - 1];
		for (int d2 = 0; d2 < numd; d2++) {
			if (d2 == d) {
				continue;
			} else if (d2 < d) {
				index[d2] = d2;
			} else {
				index[d2 - 1] = d2;
			}
		}
		MyMatrix ret = value.getMatrix(0, numt - 1, index);
		return ret;
	}

	static public MyMatrix SubmatrixIncludingD(MyMatrix value, int d) {
		MyMatrix ret = value.getMatrix(0, value.getRowDimension() - 1, d, d);
		return ret;
	}

	static public MyMatrix SubmatrixSquareExceptingD(MyMatrix W, int d) {
		int numd = W.getColumnDimension();
		int[] index = new int[numd - 1];
		for (int d2 = 0; d2 < numd; d2++) {
			if (d2 == d) {
				continue;
			} else if (d2 < d) {
				index[d2] = d2;
			} else {
				index[d2 - 1] = d2;
			}
		}
		MyMatrix ret = W.getMatrix(index, index);
		return ret;
	}

	static public MyMatrix SubmatrixSquareIncludingD(MyMatrix W, int d) {
		int numd = W.getColumnDimension();
		int[] index = new int[numd - 1];
		for (int d2 = 0; d2 < numd; d2++) {
			if (d2 == d) {
				continue;
			} else if (d2 < d) {
				index[d2] = d2;
			} else {
				index[d2 - 1] = d2;
			}
		}
		MyMatrix ret = W.getMatrix(index, d, d);
		return ret;
	}

	static public MyMatrix GetMatrix(long[] a) {
		int numt = a.length;
		MyMatrix ret = new MyMatrix(numt, 1);
		for (int t = 0; t < numt; t++) {
			ret.set(t, 0, a[t]);
		}
		return ret;
	}

	static public MyMatrix ComputeScalingVectorFromCovarianceMatrix(MyMatrix W) throws Exception {
		int numt = W.getRowDimension();
		int numd = W.getColumnDimension();
		if (numt != numd) throw new Exception("non square matrix");

		MyMatrix sigma = new MyMatrix(numd, 1);
		for (int d = 0; d < numd; d++) {
			double value = W.get(d, d);
			if (value < 1.0e-10) {
				sigma.set(d, 0, 1);
			} else {
				double sig = Math.sqrt(W.get(d, d));
				sigma.set(d, 0, sig);
			}
		}
		return sigma;
	}

	static public MyMatrix ConstructDiagonalMatrix(double a, int numd) {
		MyMatrix diagonal = new MyMatrix(numd, numd);
		for (int d = 0; d < numd; d++) {
			diagonal.set(d, d, a);
		}
		return diagonal;
	}

	static public MyMatrix ConstructDiagonalMatrix(double[] a) {
		int numd = a.length;
		MyMatrix diagonal = new MyMatrix(numd, numd);
		for (int d = 0; d < numd; d++) {
			diagonal.set(d, d, a[d]);
		}
		return diagonal;
	}

	static public MyMatrix ConstructDiagonalMatrix(MyMatrix A) {
		int numd = A.getRowDimension();

		MyMatrix diagonal = new MyMatrix(numd, numd);
		for (int d = 0; d < numd; d++) {
			diagonal.set(d, d, A.get(d, 0));
		}
		return diagonal;
	}

	static public MyMatrix SigmoidFunction(MyMatrix kata) {
		int numt = kata.getRowDimension();
		MyMatrix ret = new MyMatrix(numt, 1);
		for (int t = 0; t < numt; t++) {
			double temp = Math.exp(-kata.get(t, 0));
			ret.set(t, 0, 1 / (1 + temp));
		}
		return ret;
	}

	static public MyMatrix StepFunction(MyMatrix kata) {
		int numt = kata.getRowDimension();
		MyMatrix ret = new MyMatrix(numt, 1);
		for (int t = 0; t < numt; t++) {
			if (ret.get(t, 0) > 0) {
				ret.set(t, 0, 1);
			} else {
				ret.set(t, 0, 0);
			}
		}
		return ret;
	}

	static public int NumNonzero(MyMatrix mat) {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		int count = 0;
		double[][] data = mat.getArray();
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				if (data[t][d] != 0) {
					count++;
				}
			}
		}
		return count;
	}

	static public double normL1(MyMatrix mat) {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		double ret = 0;
		double[][] data = mat.getArray();
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret += Math.abs(data[t][d]);
			}
		}
		return ret;
	}

	static public MyMatrix NormalizeRowVector(MyMatrix mat) {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		double[][] ret = new double[numt][numd];
		double[][] data = mat.getArray();
		for (int d = 0; d < numd; d++) {
			double total = 0;
			for (int t = 0; t < numt; t++) {
				double value = data[t][d];
				total += value * value;
			}
			double len = Math.sqrt(total);
			if (len == 0) continue;
			for (int t = 0; t < numt; t++) {
				ret[t][d] = data[t][d] / len;
			}
		}
		return new MyMatrix(ret);
	}

	static public MyMatrix NormalizeColumnVector(MyMatrix mat) {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		double[][] ret = new double[numt][numd];
		double[][] data = mat.getArray();
		for (int t = 0; t < numt; t++) {
			double total = 0;
			for (int d = 0; d < numd; d++) {
				double value = data[t][d];
				total += value * value;
			}
			double len = Math.sqrt(total);
			if (len == 0) continue;
			for (int d = 0; d < numd; d++) {
				ret[t][d] = data[t][d] / len;
			}
		}
		return new MyMatrix(ret);
	}

	static public MyMatrix ComputeCorrelationFromVariance(MyMatrix mat) {
		int numd = mat.getRowDimension();
		double[][] data = mat.getArray();
		double[][] ret = new double[numd][numd];
		for (int d1 = 0; d1 < numd; d1++) {
			ret[d1][d1] = 1;
			for (int d2 = d1 + 1; d2 < numd; d2++) {
				double a = data[d1][d2];
				double b = data[d1][d1];
				double c = data[d2][d2];
				if (b * c == 0) continue;
				double d = a / Math.sqrt(b * c);
				ret[d1][d2] = ret[d2][d1] = d;
			}
		}
		return new MyMatrix(ret);
	}

	static public MyMatrix ComputeAbsoluteValue(MyMatrix mat) {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		double[][] data = mat.getArray();
		double[][] ret = new double[numt][numd];
		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt; t++) {
				ret[t][d] = Math.abs(data[t][d]);
			}
		}
		return new MyMatrix(ret);
	}

	static public MyMatrix ComputeMax(MyMatrix A, MyMatrix B) {
		double[][] data1 = A.getArray();
		double[][] data2 = B.getArray();
		int numd = A.getColumnDimension();
		int numt = A.getRowDimension();

		double[][] ret = new double[numt][numd];
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				if (data1[t][d] > data2[t][d]) {
					ret[t][d] = data1[t][d];
				} else {
					ret[t][d] = data2[t][d];
				}
			}
		}
		return new MyMatrix(ret);
	}

	static public double DotProduct(MyMatrix a, MyMatrix b) throws Exception {
		return a.transpose().times(b).get(0, 0);
	}

	static public MyMatrix CountNumericalVariation(MyMatrix mat) throws Exception {
		int numd = mat.getColumnDimension();
		int numt = mat.getRowDimension();

		MyMatrix numVariation = new MyMatrix(numd, 1);
		for (int d = 0; d < numd; d++) {
			HashMap<Double, Integer> map = new HashMap<Double, Integer>();
			for (int t = 0; t < numt; t++) {
				double v = mat.get(t, d);
				map.put(v, 1);
			}
			numVariation.set(d, 0, map.size());
		}

		return numVariation;
	}

	static public boolean FindNaN(MyMatrix mat) {
		return FindNaN(mat, false);
	}

	static public boolean FindNaN(MyMatrix mat, boolean isPrint) {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		boolean find = false;
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				double v = mat.get(t, d);
				if (Double.isNaN(v)) {
					if (isPrint) {
						System.out.println("[" + t + "," + d + "] = NaN");
					}
					find = true;
				}

				if (Double.isInfinite(v)) {
					if (isPrint) {
						System.out.println("[" + t + "," + d + "] = Infinite");
					}
					find = true;
				}
			}
		}

		return find;
	}

	static public MyMatrix ComputeJijou(MyMatrix mat) {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();
		double[][] ret = new double[numt][numd];
		for (int d = 0; d < numd; d++) {
			for (int t = 0; t < numt; t++) {
				double v = mat.get(t, d);
				ret[t][d] = v * v;
			}
		}
		return new MyMatrix(ret);
	}
}
