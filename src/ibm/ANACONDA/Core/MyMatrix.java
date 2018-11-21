/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package ibm.ANACONDA.Core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public class MyMatrix implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6189673021909105525L;
	public int numt;
	public int numd;
	public double[][] data;

	public MyMatrix(int numt, int numd) {
		this.numt = numt;
		this.numd = numd;
		data = new double[numt][numd];
	}

	public MyMatrix(int numt, int numd, double v) {
		this.numt = numt;
		this.numd = numd;
		data = new double[numt][numd];
		setAll(v);
	}

	public MyMatrix(double[][] data) {
		numt = data.length;
		if (numt > 0) {
			numd = data[0].length;
		} else {
			numd = 0;
		}
		this.data = data;
	}

	public MyMatrix(MyMatrix m) {
		numt = m.numt;
		numd = m.numd;
		data = new double[numt][numd];
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				data[t][d] = m.data[t][d];
			}
		}
	}

	public MyMatrix copy() {
		return new MyMatrix(this);
	}

	public static MyMatrix identity(int numt, int numd) {
		double[][] data = new double[numt][numd];
		for (int i = 0; i < numt; i++) {
			data[i][i] = 1;
		}
		return new MyMatrix(data);
	}

	public String toString() {
		int max = 20;
		String ret = "===================\n";
		ret += String.format("[%d, %d]\n", numt, numd);
		for (int t = 0; t < numt; t++) {
			if (t > max) break;
			for (int d = 0; d < numd; d++) {
				if (d > max) break;
				if (d > 0) {
					ret += ", ";
				}
				if (data[t][d] < 0) {
					ret += String.format("%4.1f", data[t][d]);
				} else {
					ret += String.format("%4.1f", data[t][d]);
				}
			}
			ret += "\n";
		}

		return ret;
	}

	public int getRowDimension() {
		return numt;
	}

	public int getColumnDimension() {
		return numd;
	}

	public double[][] getArray() {
		return data;
	}

	public double get(int t, int d) {
		return data[t][d];
	}

	public void set(int t, int d, double v) {
		data[t][d] = v;
	}

	public void setAll(double v) {
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				data[t][d] = v;
			}
		}
	}

	public MyMatrix getMatrix(int st, int et, int sd, int ed) {
		MyMatrix ret = new MyMatrix(et - st + 1, ed - sd + 1);
		for (int t = st; t <= et; t++) {
			for (int d = sd; d <= ed; d++) {
				ret.data[t - st][d - sd] = data[t][d];
			}
		}
		return ret;
	}

	public MyMatrix getMatrix(int[] tIndex, int sd, int ed) {
		MyMatrix ret = new MyMatrix(tIndex.length, ed - sd + 1);
		for (int t = 0; t < tIndex.length; t++) {
			for (int d = sd; d <= ed; d++) {
				ret.data[t][d - sd] = data[tIndex[t]][d];
			}
		}
		return ret;
	}

	public MyMatrix getMatrix(ArrayList<Integer> tIndex, int sd, int ed) {
		int tlength = tIndex.size();
		MyMatrix ret = new MyMatrix(tlength, ed - sd + 1);
		for (int t = 0; t < tlength; t++) {
			for (int d = sd; d <= ed; d++) {
				ret.data[t][d - sd] = data[tIndex.get(t)][d];
			}
		}
		return ret;
	}

	public MyMatrix getMatrix(int st, int et, int[] dIndex) {
		MyMatrix ret = new MyMatrix(et - st + 1, dIndex.length);
		for (int t = st; t <= et; t++) {
			for (int d = 0; d < dIndex.length; d++) {
				ret.data[t - st][d] = data[t][dIndex[d]];
			}
		}
		return ret;
	}

	public MyMatrix getMatrix(int st, int et, ArrayList<Integer> dIndex) {
		int dlength = dIndex.size();
		MyMatrix ret = new MyMatrix(et - st + 1, dlength);
		for (int t = st; t <= et; t++) {
			for (int d = 0; d < dlength; d++) {
				ret.data[t - st][d] = data[t][dIndex.get(d)];
			}
		}
		return ret;
	}

	public MyMatrix getMatrix(int[] tIndex, int[] dIndex) {
		MyMatrix ret = new MyMatrix(tIndex.length, dIndex.length);
		for (int t = 0; t < tIndex.length; t++) {
			for (int d = 0; d < dIndex.length; d++) {
				ret.data[t][d] = data[tIndex[t]][dIndex[d]];
			}
		}
		return ret;
	}

	public MyMatrix getMatrix(List<Integer> tIndex, List<Integer> dIndex) {
		if (false) {
			int tlength = tIndex.size();
			int dlength = dIndex.size();
			MyMatrix ret = new MyMatrix(tlength, dlength);
			for (int t = 0; t < tlength; t++) {
				for (int d = 0; d < dlength; d++) {
					ret.data[t][d] = data[tIndex.get(t)][dIndex.get(d)];
				}
			}
			return ret;
		} else {
			int tlength = tIndex.size();
			int dlength = dIndex.size();

			Integer[] ts = (Integer[]) tIndex.toArray(new Integer[0]);
			Integer[] ds = (Integer[]) dIndex.toArray(new Integer[0]);

			double[][] data = new double[tlength][dlength];
			for (int t = 0; t < tlength; t++) {
				for (int d = 0; d < dlength; d++) {
					data[t][d] = this.data[ts[t]][ds[d]];
				}
			}
			return new MyMatrix(data);
		}
	}

	public double det() throws Exception {
		if (numd != numt) throw new Exception("Matrix Error");

		MyMatrix L = new MyMatrix(numt, numd);
		MyMatrix U = new MyMatrix(numt, numd);

		LU_D(data, L.data, U.data, numt);

		double ret = 1;
		for (int i = 0; i < numt; i++) {
			ret *= L.data[i][i] * U.data[i][i];
		}
		return ret;
	}

	public double logdet() throws Exception {
		if (numd != numt) throw new Exception("Matrix Error");

		MyMatrix L = new MyMatrix(numt, numd);
		MyMatrix U = new MyMatrix(numt, numd);

		LU_D(data, L.data, U.data, numt);

		double ret = 0;
		for (int i = 0; i < numt; i++) {
			double temp = Math.log(L.data[i][i] * U.data[i][i]);
			ret += temp;
		}
		return ret;
	}

	public void LU_D(double[][] A, double[][] L, double[][] U, int N) {
		int i, j, k;
		double T;
		for (i = 0; i < N; i++)
			for (j = 0; j < N; j++)
				L[i][j] = U[i][j] = 0;
		L[0][0] = 1;
		for (j = 0; j < N; j++)
			U[0][j] = A[0][j];
		for (i = 1; i < N; i++) {
			U[i][0] = L[0][i] = 0;
			L[i][0] = A[i][0] / U[0][0];
		}
		for (i = 1; i < N; i++) {
			L[i][i] = 1;
			T = A[i][i];
			for (k = 0; k <= i; k++)
				T -= L[i][k] * U[k][i];
			U[i][i] = T;
			for (j = i + 1; j < N; j++) {
				U[j][i] = L[i][j] = 0;
				T = A[j][i];
				for (k = 0; k <= i; k++)
					T -= L[j][k] * U[k][i];
				L[j][i] = T / U[i][i];
				T = A[i][j];
				for (k = 0; k <= i; k++)
					T -= L[i][k] * U[k][j];
				U[i][j] = T;
			}
		}
	}

	public double trace() throws Exception {
		if (numd != numt) throw new Exception("Matrix Error");

		double total = 0;
		for (int i = 0; i < numt; i++) {
			total += data[i][i];
		}
		return total;
	}

	public MyMatrix transpose() {
		MyMatrix ret = new MyMatrix(numd, numt);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret.data[d][t] = data[t][d];
			}
		}
		return ret;
	}

	public double normF() {
		double total = 0;
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				total += data[t][d] * data[t][d];
			}
		}
		return Math.sqrt(total);
	}

	public double normL1() {
		double total = 0;
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				total += Math.abs(data[t][d]);
			}
		}
		return total;
	}

	public MyMatrix plus(MyMatrix m) throws Exception {
		if (numt != m.numt || numd != m.numd) throw new Exception("Matrix Error");
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret.data[t][d] = data[t][d] + m.data[t][d];
			}
		}
		return ret;
	}

	public void plusEquals(MyMatrix m) throws Exception {
		if (numt != m.numt || numd != m.numd) throw new Exception("Matrix Error");
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				data[t][d] += m.data[t][d];
			}
		}
	}

	public MyMatrix minus(MyMatrix m) throws Exception {
		if (numt != m.numt || numd != m.numd) throw new Exception("Matrix Error");
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret.data[t][d] = data[t][d] - m.data[t][d];
			}
		}
		return ret;
	}

	public void minusEquals(MyMatrix m) throws Exception {
		if (numt != m.numt || numd != m.numd) throw new Exception("Matrix Error");
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				data[t][d] -= m.data[t][d];
			}
		}
	}

	public MyMatrix times(MyMatrix m) throws Exception {
		if (numd != m.numt) throw new Exception("Matrix Error");
		MyMatrix ret = new MyMatrix(numt, m.numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < m.numd; d++) {
				double total = 0;
				for (int i = 0; i < numd; i++) {
					total += data[t][i] * m.data[i][d];
				}
				ret.data[t][d] = total;
			}
		}
		return ret;
	}

	public MyMatrix times(final MyMatrix m, int numThread) throws Exception {
		if (numd != m.numt) throw new Exception("Matrix Error");
		final MyMatrix ret = new MyMatrix(numt, m.numd);
		class GGG extends MyRunnable {
			boolean seekt = true;
			int t;
			int d;

			public GGG(boolean seekt, int t, int d) {
				this.seekt = seekt;
				this.t = t;
				this.d = d;
			}

			@Override
			public void run() {
				if (seekt == true) {
					for (int t = 0; t < numt; t++) {
						double total = 0;
						for (int i = 0; i < numd; i++) {
							total += data[t][i] * m.data[i][d];
						}
						ret.data[t][d] = total;
					}
				} else {
					for (int d = 0; d < m.numd; d++) {
						double total = 0;
						for (int i = 0; i < numd; i++) {
							total += data[t][i] * m.data[i][d];
						}
						ret.data[t][d] = total;
					}
				}
			}
		}
		LinkedList<MyRunnable> queue = new LinkedList<MyRunnable>();

		if (numt > m.numd) {
			for (int t = 0; t < numt; t++) {
				queue.add(new GGG(false, t, -1));
			}
		} else {
			for (int d = 0; d < m.numd; d++) {
				queue.add(new GGG(true, -1, d));
			}
		}

		ThreadPoolFramework tpf = new ThreadPoolFramework(numThread, queue);
		tpf.Join();
		if (tpf.isAllSucceeded() == false) throw new Exception("Error on child thread");
		return ret;
	}

	public MyMatrix times(double v) throws Exception {
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret.data[t][d] = data[t][d] * v;
			}
		}
		return ret;
	}

	public void timesEquals(double v) throws Exception {
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				data[t][d] *= v;
			}
		}
	}

	public double dot(MyMatrix m) throws Exception {
		if (numt != m.numt || numd != m.numd) throw new Exception("Matrix Error");
		double ret = 0;
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret += data[t][d] * m.data[t][d];
			}
		}
		return ret;
	}

	public MyMatrix timesByElement(MyMatrix m) throws Exception {
		if (numt != m.numt || numd != m.numd) throw new Exception("Matrix Error");
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret.data[t][d] = data[t][d] * m.data[t][d];
			}
		}
		return ret;
	}

	public MyMatrix divideByElement(MyMatrix m) throws Exception {
		return divideByElement(m, false);
	}

	public MyMatrix divideByElement(MyMatrix m, boolean divideByZeroSetZero) throws Exception {
		if (numt != m.numt || numd != m.numd) throw new Exception("Matrix Error");
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				if (Double.isNaN(m.data[t][d]) || Double.isNaN(data[t][d])) {
					ret.data[t][d] = Double.NaN;
				} else if (divideByZeroSetZero && m.data[t][d] == 0) {
					ret.data[t][d] = 0;
				} else {
					ret.data[t][d] = data[t][d] / m.data[t][d];
				}
			}
		}
		return ret;
	}

	public MyMatrix sqrtByElement() throws Exception {
		return sqrtByElement(false);
	}

	public MyMatrix sqrtByElement(boolean sqrtNegativeSetZero) throws Exception {
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				if (data[t][d] <= 0) {
					ret.data[t][d] = 0;
				} else {
					ret.data[t][d] = Math.sqrt(data[t][d]);
				}
			}
		}
		return ret;
	}

	public MyMatrix logByElement() throws Exception {
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret.data[t][d] = Math.log(data[t][d]);
			}
		}
		return ret;
	}

	public MyMatrix absByElement() throws Exception {
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				ret.data[t][d] = Math.abs(data[t][d]);
			}
		}
		return ret;
	}

	private int GaussSeidelSimEq(double[][] A, double[][] B, double[][] X, int iterMax, double Eps) {
		double[] Y = new double[numt];
		int i, j;
		for (i = 0; i < numt; i++)
			X[i][0] = B[i][0] / A[i][i];
		double E = Eps * 100;
		int R = 0;
		while (E > Eps && R < iterMax) {
			R++;
			double dev = 0;
			for (i = 0; i < numt; i++) {
				Y[i] = B[i][0];
				for (j = 0; j < numt; j++)
					if (i != j) Y[i] -= A[i][j] * X[j][0];
				Y[i] /= A[i][i];
				double EI = X[i][0] - Y[i];
				dev += EI * EI;
				X[i][0] = Y[i];
			}
			E = Math.sqrt(dev);
		}
		return R;
	}

	public MyMatrix solve_EVD(MyMatrix b) throws Exception {
		int numd = b.getColumnDimension();
		int numt = b.getRowDimension();
		double[][] retData = new double[numt][numd];

		RealMatrix A = new Array2DRowRealMatrix(this.getArray(), false);
		DecompositionSolver solver = new EigenDecomposition(A).getSolver();

		for (int d = 0; d < numd; d++) {
			// System.out.println(d);

			RealVector brm;
			{
				double[] brmData = new double[numt];
				for (int t = 0; t < numt; t++) {
					brmData[t] = b.data[t][d];
				}
				brm = new ArrayRealVector(brmData, false);
			}

			RealVector solution = solver.solve(brm);

			{
				for (int t = 0; t < numt; t++) {
					retData[t][d] = solution.getEntry(t);
				}
			}
		}
		MyMatrix ret = new MyMatrix(retData);
		return ret;
	}

	public MyMatrix solve_Cholesky(MyMatrix b) throws Exception {
		int numd = b.getColumnDimension();
		int numt = b.getRowDimension();
		double[][] retData = new double[numt][numd];

		RealMatrix A = new Array2DRowRealMatrix(this.getArray(), false);
		DecompositionSolver solver = new CholeskyDecomposition(A).getSolver();

		for (int d = 0; d < numd; d++) {
			// System.out.println(d);

			RealVector brm;
			{
				double[] brmData = new double[numt];
				for (int t = 0; t < numt; t++) {
					brmData[t] = b.data[t][d];
				}
				brm = new ArrayRealVector(brmData, false);
			}

			RealVector solution = solver.solve(brm);

			{
				for (int t = 0; t < numt; t++) {
					retData[t][d] = solution.getEntry(t);
				}
			}
		}
		MyMatrix ret = new MyMatrix(retData);
		return ret;
	}

	public MyMatrix solve(MyMatrix b) throws Exception {
		int numd = b.getColumnDimension();
		int numt = b.getRowDimension();
		double[][] retData = new double[numt][numd];

		RealMatrix A = new Array2DRowRealMatrix(this.getArray(), false);
		DecompositionSolver solver = new LUDecomposition(A).getSolver();

		for (int d = 0; d < numd; d++) {
			// System.out.println(d);

			RealVector brm;
			{
				double[] brmData = new double[numt];
				for (int t = 0; t < numt; t++) {
					brmData[t] = b.data[t][d];
				}
				brm = new ArrayRealVector(brmData, false);
			}

			RealVector solution = solver.solve(brm);

			{
				for (int t = 0; t < numt; t++) {
					retData[t][d] = solution.getEntry(t);
				}
			}
		}
		MyMatrix ret = new MyMatrix(retData);
		return ret;
	}

	public MyMatrix solve(final MyMatrix b, int numThread) throws Exception {
		final int numd = b.getColumnDimension();
		final int numt = b.getRowDimension();
		final RealMatrix A = new Array2DRowRealMatrix(this.getArray(), false);
		final double[][] retData = new double[numt][numd];

		class GGG extends MyRunnable {
			int d;

			public GGG(int d) {
				this.d = d;
			}

			@Override
			public void run() throws Exception {
				RealVector brm;
				{
					double[] brmData = new double[numt];
					for (int t = 0; t < numt; t++) {
						brmData[t] = b.data[t][d];
					}
					brm = new ArrayRealVector(brmData, false);
				}

				final DecompositionSolver solver = new LUDecomposition(A).getSolver();
				RealVector solution = solver.solve(brm);

				{
					for (int t = 0; t < numt; t++) {
						retData[t][d] = solution.getEntry(t);
					}
				}
			}
		}

		LinkedList<MyRunnable> queue = new LinkedList<MyRunnable>();
		for (int d = 0; d < numd; d++) {
			queue.add(new GGG(d));
		}
		ThreadPoolFramework tpf = new ThreadPoolFramework(numThread, queue);
		tpf.Join();
		if (tpf.isAllSucceeded() == false) throw new Exception("Error on child thread");

		MyMatrix ret = new MyMatrix(retData);
		return ret;
	}

	public MyMatrix solve_Obsolete(MyMatrix b) throws Exception {
		if (numd != numt) throw new Exception("Matrix Error");

		int numd2 = b.getColumnDimension();
		int numt2 = b.getRowDimension();

		MyMatrix ret = new MyMatrix(numt2, numd2);

		for (int d = 0; d < numd2; d++) {
			MyMatrix temp = b.getMatrix(0, numt2 - 1, d, d);
			MyMatrix res = solve2(temp);
			for (int t = 0; t < numt2; t++) {
				ret.set(t, d, res.get(t, 0));
			}
		}
		return ret;
	}

	public MyMatrix solve2(MyMatrix b) throws Exception {
		if (numd != numt) throw new Exception("Matrix Error");

		if (numt == 1) {
			MyMatrix ret = b.times(1 / data[0][0]);
			return ret;
		} else if (numt == 2) {
			double am = data[0][0];
			double bm = data[0][1];
			double cm = data[1][0];
			double dm = data[1][1];
			double det = am * dm - bm * cm;
			double[][] temp = new double[2][2];
			temp[0][0] = dm / det;
			temp[0][1] = -bm / det;
			temp[1][0] = -cm / det;
			temp[1][1] = am / det;
			MyMatrix Ai = new MyMatrix(temp);
			MyMatrix ret = Ai.times(b);
			return ret;
		} else {
			// double error = trace() * 1.0e-10;
			MyMatrix ret = new MyMatrix(numt, 1);
			GaussSeidelSimEq(data, b.data, ret.data, 1000, 1.0e-10);
			// JacobiIterationSimEq(data, b.data, ret.data, 3000, numt, 0.0001);
			// SORSimEq(data, b.data, ret.data, 3000, numt, 1.5, 0.0001);
			return ret;
		}
	}

	public class GyakuGyouretu {

		// pivotは、消去演算を行う前に、対象となる行を基準とし、それ以降の
		// 行の中から枢軸要素の絶対値が最大となる行を見つけ出し、対象の行と
		// その行とを入れ替えることを行う関数である。
		void pivot(int k, double a[][], int N) {
			double max, copy;
			// ipは絶対値最大となるk列の要素の存在する行を示す変数で、
			// とりあえずk行とする
			int ip = k;
			// k列の要素のうち絶対値最大のものを示す変数maxの値をとりあえず
			// max=|a[k][k]|とする
			max = Math.abs(a[k][k]);
			// k+1行以降、最後の行まで、|a[i][k]|の最大値とそれが存在する行を
			// 調べる
			for (int i = k + 1; i < N; i++) {
				if (max < Math.abs(a[i][k])) {
					ip = i;
					max = Math.abs(a[i][k]);
				}
			}
			if (ip != k) {
				for (int j = 0; j < 2 * N; j++) {
					// 入れ替え作業
					copy = a[ip][j];
					a[ip][j] = a[k][j];
					a[k][j] = copy;
				}
			}
		}

		// ガウス・ジョルダン法により、消去演算を行う
		void sweep(int k, double a[][], int N) {
			double piv, mmm;
			// 枢軸要素をpivとおく
			piv = a[k][k];
			// k行の要素をすべてpivで割る a[k][k]=1となる
			for (int j = 0; j < 2 * N; j++)
				a[k][j] = a[k][j] / piv;
			//
			for (int i = 0; i < N; i++) {
				mmm = a[i][k];
				// a[k][k]=1で、それ以外のk列要素は0となる
				// k行以外
				if (i != k) {
					// i行において、k列から2N-1列まで行う
					for (int j = k; j < 2 * N; j++)
						a[i][j] = a[i][j] - mmm * a[k][j];
				}
			}
		}

		// 逆行列を求める演算
		void gyaku(double a[][], int N) {
			for (int k = 0; k < N; k++) {
				pivot(k, a, N);
				sweep(k, a, N);
			}
		}
	}

	public MyMatrix inverse_Obsolete() throws Exception {
		if (numt != numd) throw new Exception("Error");

		double[][] temp = new double[numt][numt * 2];
		for (int i = 0; i < numt; i++) {
			for (int j = 0; j < numt; j++) {
				temp[i][j] = data[i][j];
			}
		}
		for (int i = 0; i < numt; i++) {
			temp[i][numt + i] = 1;
		}
		GyakuGyouretu g = new GyakuGyouretu();
		g.gyaku(temp, numt);

		double[][] temp2 = new double[numt][numt];
		for (int i = 0; i < numt; i++) {
			for (int j = 0; j < numt; j++) {
				temp2[i][j] = temp[i][numt + j];
			}
		}
		return new MyMatrix(temp2);
	}

	public MyMatrix inverse() throws Exception {
		if (numt != numd) throw new Exception("Error");

		RealMatrix rm = MatrixUtils.createRealMatrix(this.getArray());
		RealMatrix rmi = new LUDecomposition(rm).getSolver().getInverse();

		MyMatrix ret = new MyMatrix(rmi.getData());
		return ret;
	}

	public MyMatrix inverseByElement() throws Exception {
		double[][] data2 = new double[numt][numd];
		for (int i = 0; i < numt; i++) {
			for (int j = 0; j < numd; j++) {
				data2[i][j] = 1.0 / data[i][j];
			}
		}
		return new MyMatrix(data2);
	}

	public MyEigenvalueDecomposition eig() {
		RealMatrix rm = MatrixUtils.createRealMatrix(this.getArray());
		EigenDecomposition ed = new EigenDecomposition(rm);
		MyMatrix D = new MyMatrix(ed.getD().getData());
		MyMatrix V = new MyMatrix(ed.getV().getData());
		MyEigenvalueDecomposition ret = new MyEigenvalueDecomposition(D, V);
		return ret;
	}

	public MySingularValueDecomposition svd() throws Exception {
		RealMatrix rm = MatrixUtils.createRealMatrix(this.getArray());

		SingularValueDecomposition svd = new SingularValueDecomposition(rm);

		MyMatrix S = new MyMatrix(svd.getS().getData());
		MyMatrix V = new MyMatrix(svd.getV().getData());
		MyMatrix U = new MyMatrix(svd.getU().getData());

		MySingularValueDecomposition ret = new MySingularValueDecomposition(S, V, U);
		return ret;
	}

	public MyQRDecomposition QRDecomposition() {
		RealMatrix rm = MatrixUtils.createRealMatrix(this.getArray());
		QRDecomposition qrd = new QRDecomposition(rm);
		MyMatrix Q = new MyMatrix(qrd.getQ().getData());
		MyMatrix R = new MyMatrix(qrd.getR().getData());
		MyMatrix H = new MyMatrix(qrd.getH().getData());
		return new MyQRDecomposition(Q, R, H);
	}

}
