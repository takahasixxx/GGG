package ibm.ANACONDA.Core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 
 * This class provides data matrix operations (Read, Write, Connect, Divide, etc.) with variable names for every columns and timestamps for every
 * rows.
 * 
 * @author Toshihiro Takahashi (e30137@jp.ibm.com)
 * 
 */
public class Dataset implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6802174089086757152L;

	/** Variable name list */
	public List<String> names;

	/** Properties list of each column */
	public List<Object> properties;

	/** Array of timestamp */
	public long[] times;

	/** Data matrix */
	public MyMatrix mat;

	public Dataset(Dataset ds) {
		names = new ArrayList<String>();
		names.addAll(ds.names);

		properties = new ArrayList<Object>();
		properties.addAll(ds.properties);

		int numt = ds.GetNumSample();
		times = new long[numt];
		System.arraycopy(ds.times, 0, times, 0, numt);

		mat = new MyMatrix(ds.mat);
	}

	public Dataset(List<Dataset> dss, boolean connectRow) throws Exception {
		if (connectRow) {
			if (dss.size() == 0) throw new Exception("Dataset(ArrayList<Dataset>) failed. ds.size() should be 1 or more.");

			names = new ArrayList<String>();
			names.addAll(dss.get(0).names);

			properties = new ArrayList<Object>();
			properties.addAll(dss.get(0).properties);

			int length = 0;
			for (Dataset ds : dss) {
				length += ds.times.length;
			}

			int numd = names.size();

			times = new long[length];
			double[][] data = new double[length][numd];
			{
				int offset = 0;
				for (Dataset ds : dss) {
					int numt = ds.GetNumSample();
					for (int t = 0; t < numt; t++) {
						for (int d = 0; d < numd; d++) {
							data[t + offset][d] = ds.mat.get(t, d);
						}
						times[t + offset] = ds.times[t];
					}
					offset += numt;
				}
			}

			mat = new MyMatrix(data);
		} else {
			names = new ArrayList<String>();
			for (Dataset ds : dss) {
				names.addAll(ds.names);
			}

			properties = new ArrayList<Object>();
			for (Dataset ds : dss) {
				properties.addAll(ds.properties);
			}

			int numt = -1;
			int numd = 0;
			for (Dataset ds : dss) {
				if (numt == -1) {
					numt = ds.GetNumSample();
					times = new long[numt];
					System.arraycopy(ds.times, 0, times, 0, numt);
				} else {
					if (numt != ds.GetNumSample()) throw new Exception("Error");
				}
				numd += ds.GetNumDimension();
			}

			double[][] temp = new double[numt][numd];
			int offset = 0;
			for (Dataset ds : dss) {
				int numdLocal = ds.GetNumDimension();
				for (int d = 0; d < numdLocal; d++) {
					for (int t = 0; t < numt; t++) {
						temp[t][offset + d] = ds.mat.data[t][d];
					}
				}
				offset += numdLocal;
			}

			this.mat = new MyMatrix(temp);
		}
	}

	public Dataset(List<String> names, List<Object> properties, long[] times, MyMatrix mat) throws Exception {
		if (names.size() != mat.getColumnDimension()) throw new Exception("Invalid data : num of names and num of column of matrix are invalid");
		if (properties.size() != mat.getColumnDimension()) throw new Exception("Invalid data : num of names and num of column of matrix are invalid");
		if (times.length != mat.getRowDimension()) throw new Exception("Invalida data : num of times and num of row of matrix are invalid");

		this.names = new ArrayList<String>();
		this.names.addAll(names);

		this.properties = new ArrayList<Object>();
		this.properties.addAll(properties);

		int numt = times.length;
		this.times = new long[numt];
		System.arraycopy(times, 0, this.times, 0, numt);

		this.mat = new MyMatrix(mat);
	}

	public Dataset(List<String> names, long[] times, MyMatrix mat) throws Exception {
		if (names.size() != mat.getColumnDimension()) throw new Exception("Invalid data : num of names and num of column of matrix are invalid");
		if (times.length != mat.getRowDimension()) throw new Exception("Invalida data : num of times and num of row of matrix are invalid");

		this.names = new ArrayList<String>();
		this.names.addAll(names);

		properties = new ArrayList<Object>();
		for (String name : names) {
			properties.add(null);
		}

		int numt = times.length;
		this.times = new long[numt];
		System.arraycopy(times, 0, this.times, 0, numt);

		this.mat = new MyMatrix(mat);
	}

	public Dataset(String[] names, long[] times, MyMatrix mat) throws Exception {
		if (names.length != mat.getColumnDimension()) throw new Exception("Invalid data : num of names and num of column of matrix are invalid");
		if (times.length != mat.getRowDimension()) throw new Exception("Invalida data : num of times and num of row of matrix are invalid");

		this.names = new ArrayList<String>();
		for (String name : names) {
			this.names.add(name);
		}

		properties = new ArrayList<Object>();
		for (String name : names) {
			properties.add(null);
		}

		int numt = times.length;
		this.times = new long[numt];
		System.arraycopy(times, 0, this.times, 0, numt);

		this.mat = new MyMatrix(mat);
	}

	public Dataset(String name, Object property, long[] times, MyMatrix mat) throws Exception {
		if (mat.getColumnDimension() != 1) throw new Exception("Invalid data");

		names = new ArrayList<String>();
		this.names.add(name);

		properties = new ArrayList<Object>();
		this.properties.add(property);

		int numt = times.length;
		this.times = new long[numt];
		System.arraycopy(times, 0, this.times, 0, numt);

		this.mat = new MyMatrix(mat);
	}

	public Dataset(String name, long[] times, MyMatrix mat) throws Exception {
		if (mat.getColumnDimension() != 1) throw new Exception("Invalid data");

		names = new ArrayList<String>();
		this.names.add(name);

		properties = new ArrayList<Object>();
		this.properties.add(null);

		int numt = times.length;
		this.times = new long[numt];
		System.arraycopy(times, 0, this.times, 0, numt);

		this.mat = new MyMatrix(mat);
	}

	public Dataset(DateFormat sdf, File file, boolean titleFlag, boolean timeFlag) throws Exception {
		this.Read(sdf, file, titleFlag, timeFlag);
	}

	public Dataset(DateFormat sdf, InputStream is, boolean titleFlag, boolean timeFlag) throws Exception {
		this.Read(sdf, is, titleFlag, timeFlag);
	}

	public Dataset(File file) throws Exception {
		this.ReadBinary(file);
	}

	private void Read(DateFormat sdf, InputStream is, boolean titleFlag, boolean timeFlag) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		Read(sdf, br, timeFlag, timeFlag);
	}

	private void Read(DateFormat sdf, File file, boolean titleFlag, boolean timeFlag) throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(file));
		Read(sdf, br, timeFlag, timeFlag);
	}

	private void Read(DateFormat sdf, BufferedReader br, boolean titleFlag, boolean timeFlag) throws Exception {
		int numd = -1;

		names = new ArrayList<String>();
		if (titleFlag) {
			String line = br.readLine();
			String[] part = line.split(",");

			if (timeFlag) {
				numd = part.length - 1;
				for (int d = 0; d < numd; d++) {
					names.add(part[d + 1]);
				}
			} else {
				numd = part.length;
				for (int d = 0; d < numd; d++) {
					names.add(part[d]);
				}
			}
		}

		properties = new ArrayList<Object>();
		for (String name : names) {
			properties.add(null);
		}

		ArrayList<double[]> vecs = new ArrayList<double[]>();
		ArrayList<Long> times = new ArrayList<Long>();
		{
			while (true) {
				String line = br.readLine();
				if (line == null) break;

				if (line.endsWith(",")) {
					line = line + " ";
				}

				String[] part = line.split(",");

				if (timeFlag) {
					if (numd == -1) {
						numd = part.length - 1;
					}
					Long t = sdf.parse(part[0]).getTime();
					double[] vec = new double[numd];
					for (int d = 0; d < numd; d++) {
						if (part[d + 1].equals("") || part[d + 1].equals(" ")) {
							vec[d] = Double.NaN;
						} else if (part[d + 1].equals("NaN")) {
							vec[d] = Double.NaN;
						} else {
							vec[d] = Double.parseDouble(part[d + 1]);
						}
					}
					vecs.add(vec);
					times.add(t);
				} else {
					if (numd == -1) {
						numd = part.length;
					}

					double[] vec = new double[numd];
					for (int d = 0; d < numd; d++) {
						if (part[d + 1].equals("") || part[d + 1].equals(" ")) {
							vec[d] = Double.NaN;
						} else if (part[d].equals("NaN")) {
							vec[d] = Double.NaN;
						} else {
							vec[d] = Double.parseDouble(part[d]);
						}
					}
					vecs.add(vec);
				}
			}
		}
		assert (vecs.size() == times.size());

		int numt = vecs.size();
		double[][] value = new double[numt][numd];
		this.times = new long[numt];
		for (int t = 0; t < numt; t++) {
			if (timeFlag) {
				this.times[t] = times.get(t);
			} else {
				this.times[t] = t;
			}

			double[] vec = vecs.get(t);
			for (int d = 0; d < numd; d++) {
				value[t][d] = vec[d];
			}
		}

		this.mat = new MyMatrix(value);

		if (titleFlag == false) {
			this.names.clear();
			for (int d = 0; d < numd; d++) {
				String name = "name_" + d;
				this.names.add(name);
			}
		}

		br.close();
	}

	public void Write(DateFormat sdf, File file) throws Exception {
		PrintWriter pr = new PrintWriter(new BufferedWriter(new FileWriter(file)));

		{
			pr.print("time");
			for (String name : names) {
				pr.print("," + name);
			}
			pr.println();
		}

		{
			int numd = mat.getColumnDimension();
			int numt = mat.getRowDimension();

			for (int t = 0; t < numt; t++) {
				pr.print(sdf.format(new Date(times[t])));
				for (int d = 0; d < numd; d++) {
					double v = mat.data[t][d];
					if (Double.isNaN(v)) {
						pr.print(",NaN");
					} else if (Double.isInfinite(v)) {
						pr.print(",");
					} else {
						pr.print("," + mat.get(t, d));
					}
				}
				pr.println();
			}
		}

		pr.flush();
		pr.close();
	}

	public void ReadBinary(File file) throws Exception {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
		this.ReadBinary(ois);
		ois.close();
	}

	public void WriteBinary(File file) throws Exception {
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
		this.WriteBinary(oos);
		oos.flush();
		oos.close();
	}

	@SuppressWarnings("unchecked")
	public void ReadBinary(ObjectInputStream ois) throws Exception {
		names = (ArrayList<String>) ois.readObject();
		properties = (ArrayList<Object>) ois.readObject();
		times = (long[]) ois.readObject();
		mat = (MyMatrix) ois.readObject();
	}

	public void WriteBinary(ObjectOutputStream oos) throws Exception {
		oos.writeObject(names);
		oos.writeObject(properties);
		oos.writeObject(times);
		oos.writeObject(mat);
	}

	public int GetNumDimension() {
		return mat.getColumnDimension();
	}

	public int GetNumSample() {
		return mat.getRowDimension();
	}

	public void DumpNames() {
		System.out.println("==============================================");
		System.out.println("==============================================");
		for (int d = 0; d < names.size(); d++) {
			System.out.println(d + ", " + names.get(d));
		}
		System.out.println("==============================================");
		System.out.println("==============================================");
	}

	public Dataset getMatrix(List<Integer> tIndex, List<Integer> dIndex) throws Exception {
		int tlength = tIndex.size();
		int dlength = dIndex.size();

		List<String> names = new ArrayList<String>();
		for (int d = 0; d < dlength; d++) {
			names.add(this.names.get(dIndex.get(d)));
		}

		List<Object> properties = new ArrayList<Object>();
		for (int d = 0; d < dlength; d++) {
			properties.add(this.properties.get(dIndex.get(d)));
		}

		long[] times = new long[tlength];
		for (int t = 0; t < tlength; t++) {
			times[t] = this.times[tIndex.get(t)];
		}

		MyMatrix mat = this.mat.getMatrix(tIndex, dIndex);

		return new Dataset(names, properties, times, mat);
	}

	public Dataset getMatrix(List<String> names) throws Exception {
		TreeMap<String, Integer> map = new TreeMap<String, Integer>();
		{
			int index = 0;
			for (String name : this.names) {
				map.put(name, index);
				index++;
			}
		}

		ArrayList<Integer> dList = new ArrayList<Integer>();
		for (String name : names) {
			System.out.println(name);
			int d = map.get(name);
			dList.add(d);
		}

		return this.getMatrix(0, mat.getRowDimension() - 1, dList);
	}

	public Dataset getMatrix(Set<String> names) throws Exception {
		TreeMap<String, Integer> map = new TreeMap<String, Integer>();
		{
			int index = 0;
			for (String name : this.names) {
				map.put(name, index);
				index++;
			}
		}

		ArrayList<Integer> dList = new ArrayList<Integer>();
		for (String name : names) {
			int d = map.get(name);
			dList.add(d);
		}

		return this.getMatrix(0, mat.getRowDimension() - 1, dList);
	}

	public Dataset getMatrix(String[] names) throws Exception {
		ArrayList<String> ns = new ArrayList<String>();
		for (String name : names) {
			ns.add(name);
		}
		return getMatrix(ns);
	}

	public Dataset getMatrix(String name) throws Exception {
		ArrayList<String> temp = new ArrayList<String>();
		temp.add(name);
		return this.getMatrix(temp);
	}

	public Dataset getMatrix(int st, int et, int sd, int ed) throws Exception {

		List<String> names = new ArrayList<String>();
		for (int d = sd; d <= ed; d++) {
			names.add(this.names.get(d));
		}

		List<Object> properties = new ArrayList<Object>();
		for (int d = sd; d <= ed; d++) {
			properties.add(this.properties.get(d));
		}

		long[] times = new long[et - st + 1];
		for (int t = st; t <= et; t++) {
			times[t - st] = this.times[t];
		}

		MyMatrix mat = this.mat.getMatrix(st, et, sd, ed);

		return new Dataset(names, properties, times, mat);
		//
		// ArrayList<Integer> tIndex = new ArrayList<Integer>();
		// for (int t = st; t <= et; t++) {
		// tIndex.add(t);
		// }
		//
		// ArrayList<Integer> dIndex = new ArrayList<Integer>();
		// for (int d = sd; d <= ed; d++) {
		// dIndex.add(d);
		// }
		//
		// return this.getMatrix(tIndex, dIndex);
	}

	public Dataset getMatrix(int[] tIndex, int sd, int ed) throws Exception {
		ArrayList<Integer> tIndex2 = new ArrayList<Integer>();
		for (int t : tIndex) {
			tIndex2.add(t);
		}

		ArrayList<Integer> dIndex = new ArrayList<Integer>();
		for (int d = sd; d <= ed; d++) {
			dIndex.add(d);
		}

		return this.getMatrix(tIndex2, dIndex);
	}

	public Dataset getMatrix(List<Integer> tIndex, int sd, int ed) throws Exception {
		ArrayList<Integer> dIndex = new ArrayList<Integer>();
		for (int d = sd; d <= ed; d++) {
			dIndex.add(d);
		}

		return this.getMatrix(tIndex, dIndex);
	}

	public Dataset getMatrix(int st, int et, int[] dIndex) throws Exception {
		ArrayList<Integer> tIndex = new ArrayList<Integer>();
		for (int t = st; t <= et; t++) {
			tIndex.add(t);
		}

		ArrayList<Integer> dIndex2 = new ArrayList<Integer>();
		for (int d : dIndex) {
			dIndex2.add(d);
		}

		return this.getMatrix(tIndex, dIndex2);
	}

	public Dataset getMatrix(int st, int et, List<Integer> dIndex) throws Exception {
		ArrayList<Integer> tIndex = new ArrayList<Integer>();
		for (int t = st; t <= et; t++) {
			tIndex.add(t);
		}

		return this.getMatrix(tIndex, dIndex);
	}

	public Dataset getMatrix(int[] tIndex, int[] dIndex) throws Exception {
		ArrayList<Integer> tIndex2 = new ArrayList<Integer>();
		for (int t : tIndex) {
			tIndex2.add(t);
		}

		ArrayList<Integer> dIndex2 = new ArrayList<Integer>();
		for (int d : dIndex) {
			dIndex2.add(d);
		}

		return this.getMatrix(tIndex2, dIndex2);
	}

	public Dataset AddRightColumn(List<String> names, List<Object> properties, MyMatrix mat) throws Exception {
		MyMatrix mat2 = MatrixUtility.ConnectColumn(this.mat, mat);

		ArrayList<String> names2 = new ArrayList<String>();
		names2.addAll(this.names);
		names2.addAll(names);

		List<Object> properties2 = new ArrayList<Object>();
		properties2.addAll(this.properties);
		properties2.addAll(properties);

		Dataset ret = new Dataset(names2, properties2, this.times, mat2);
		return ret;
	}

	public Dataset AddRightColumn(Dataset ds) throws Exception {
		return this.AddRightColumn(ds.names, ds.properties, ds.mat);
	}

	public Dataset AddRightColumn(String name, Object propertie, MyMatrix mat) throws Exception {
		if (mat.getColumnDimension() != 1) throw new Exception("fatal");

		MyMatrix mat2 = MatrixUtility.ConnectColumn(this.mat, mat);

		ArrayList<String> names2 = new ArrayList<String>();
		names2.addAll(this.names);
		names2.add(name);

		List<Object> properties2 = new ArrayList<Object>();
		properties2.addAll(this.properties);
		properties2.add(propertie);

		Dataset ret = new Dataset(names2, properties2, this.times, mat2);
		return ret;
	}

	public Dataset AddBottomRow(long[] times, MyMatrix mat) throws Exception {
		MyMatrix mat2 = MatrixUtility.ConnectRow(this.mat, mat);

		long[] times2 = new long[this.times.length + times.length];
		for (int t = 0; t < this.times.length; t++) {
			times2[t] = this.times[t];
		}

		for (int t = 0; t < times.length; t++) {
			times2[t + this.times.length] = times[t];
		}

		return new Dataset(this.names, this.properties, times2, mat2);
	}

	public Dataset AddBottomRow(Dataset ds) throws Exception {
		return this.AddBottomRow(ds.times, ds.mat);
	}

	public Dataset ShiftTime(int width) throws Exception {
		return ShiftTime(width, Double.NaN);
	}

	public Dataset ShiftTime(int width, double fillNumber) throws Exception {
		MyMatrix temp = new MyMatrix(mat.numt, mat.numd, fillNumber);
		for (int t = 0; t < mat.numt; t++) {
			int t2 = t + width;
			if (t2 < 0) continue;
			if (t2 >= mat.numt) continue;
			for (int d = 0; d < this.mat.numd; d++) {
				temp.data[t][d] = mat.data[t2][d];
			}
		}
		Dataset ret = new Dataset(names, properties, times, temp);
		return ret;
	}

	public Dataset DownSampling(int numDownSample) throws Exception {
		int numt = mat.getRowDimension();
		int numd = mat.getColumnDimension();

		ArrayList<Integer> index = new ArrayList<Integer>();
		if (numt > numDownSample) {
			double rate = 1.0 * numt / numDownSample;
			for (int i = 0; i < numDownSample; i++) {
				int temp = (int) (i * rate);
				index.add(temp);
			}
		} else {
			return new Dataset(this);
		}

		return this.getMatrix(index, 0, numd - 1);
	}

	public class CrossValidationDataset {
		public Dataset Learn;
		public Dataset Test;

		public CrossValidationDataset(Dataset Learn, Dataset Test) {
			this.Learn = Learn;
			this.Test = Test;
		}
	}

	// クロスバリデーション用にデータを分ける。
	public CrossValidationDataset ComputeCrossValicationData(int numCV, int cvi, int type) throws Exception {
		if (type == 0) {// numCV飛びにデータを分ける。
			int numt = mat.getRowDimension();
			int numd = mat.getColumnDimension();

			ArrayList<Integer> indexL = new ArrayList<Integer>();
			ArrayList<Integer> indexT = new ArrayList<Integer>();

			for (int t = 0; t < numt; t++) {
				if (t % numCV == cvi) {
					indexT.add(t);
				} else {
					indexL.add(t);
				}
			}

			Dataset L = getMatrix(indexL, 0, numd - 1);
			Dataset T = getMatrix(indexT, 0, numd - 1);
			return new CrossValidationDataset(L, T);

		} else if (type == 1) {// 途中の1/numCVをまとめて分ける。
			int numt = mat.getRowDimension();
			int numd = mat.getColumnDimension();

			ArrayList<Integer> indexL = new ArrayList<Integer>();
			ArrayList<Integer> indexT = new ArrayList<Integer>();

			int start = numt * cvi / numCV;
			int end = numt * (cvi + 1) / numCV;

			for (int t = 0; t < numt; t++) {
				if (t >= start && t < end) {
					indexT.add(t);
				} else {
					indexL.add(t);
				}
			}

			Dataset L = getMatrix(indexL, 0, numd - 1);
			Dataset T = getMatrix(indexT, 0, numd - 1);
			return new CrossValidationDataset(L, T);
		} else {
			return null;
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// バリエーションが中途半端なやつは除く。
	public Dataset RemoveLowVariationSensors(double threshold) throws Exception {
		int numd = this.mat.getColumnDimension();
		int numt = this.mat.getRowDimension();

		MyMatrix numVariation = MatrixUtility.CountNumericalVariation(this.mat);
		ArrayList<Integer> indexNumerical = new ArrayList<Integer>();
		for (int d = 0; d < numd; d++) {
			double numV = numVariation.get(d, 0);
			if (numV < threshold) continue;
			indexNumerical.add(d);
		}
		Dataset dsRet = this.getMatrix(0, numt - 1, indexNumerical);

		return dsRet;
	}

	public ArrayList<String> PickupLowVariationSensors(double threshold) throws Exception {
		int numd = this.mat.getColumnDimension();

		MyMatrix numVariation = MatrixUtility.CountNumericalVariation(this.mat);
		ArrayList<Integer> indexD = new ArrayList<Integer>();
		for (int d = 0; d < numd; d++) {
			double numV = numVariation.get(d, 0);
			if (numV < threshold) {
				indexD.add(d);
			}
		}

		ArrayList<String> ret = new ArrayList<String>();
		for (int d : indexD) {
			ret.add(this.names.get(d));
		}
		return ret;
	}

	// カテゴリ変数だけ取り出す。
	public Dataset GetCategoricalSensors() throws Exception {
		ArrayList<String> names = new ArrayList<String>();
		for (String n : this.names) {
			if (n.indexOf("Categorical") == -1) continue;
			names.add(n);
		}
		return this.getMatrix(names);
	}

	// 数値変数だけ取り出す。
	public Dataset GetNumericalSensors() throws Exception {
		ArrayList<String> names = new ArrayList<String>();
		for (String n : this.names) {
			if (n.indexOf("Categorical") != -1) continue;
			names.add(n);
		}
		return this.getMatrix(names);
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// 特定センサーを除く。
	public Dataset RemoveSensor(String name) throws Exception {
		ArrayList<String> names = new ArrayList<String>();
		for (String n : this.names) {
			if (n.equals(name)) continue;
			names.add(n);
		}
		return this.getMatrix(names);
	}

	public Dataset RemoveSensors(ArrayList<String> namesRemove) throws Exception {
		ArrayList<String> namesNew = new ArrayList<String>();
		for (String n : this.names) {
			if (namesRemove.contains(n)) continue;
			namesNew.add(n);
		}
		return this.getMatrix(namesNew);
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	public Dataset ExpandTimeseries(int[] widthList, long baseSamplingRate, boolean[] defFlag, int numDownSampling) throws Exception {
		// 有効なIndexを集める。
		ArrayList<Integer> indexList = new ArrayList<Integer>();
		{
			int numt = mat.numt;

			for (int t = 0; t < numt; t += 1) {
				// 過去の値がデータ範囲内か調べる。
				boolean flag = true;
				for (int w : widthList) {
					int t2 = t - w;
					if (t2 < 0 || t2 >= numt) {
						flag = false;
						break;
					}
					long timeDef = times[t] - times[t2];
					if (timeDef != baseSamplingRate * w) {
						flag = false;
						break;
					}
				}
				if (flag == false) continue;
				indexList.add(t);
			}
		}

		if (numDownSampling != -1 && indexList.size() > numDownSampling) {
			ArrayList<Integer> indexList2 = new ArrayList<Integer>();
			double rate = 1.0 * indexList.size() / numDownSampling;
			for (int i = 0; i < numDownSampling; i++) {
				int j = (int) (rate * i);
				int index = indexList.get(j);
				indexList2.add(index);
			}
			indexList = indexList2;
		}

		{
			int numIndex = indexList.size();
			int numd = mat.numd;
			double[][] XData = new double[numIndex][numd * widthList.length];

			for (int i = 0; i < numIndex; i++) {
				int t = indexList.get(i);

				for (int wi = 0; wi < widthList.length; wi++) {
					int w = widthList[wi];

					for (int d = 0; d < numd; d++) {

						if (defFlag[wi] == false) {
							XData[i][numd * wi + d] = mat.get(t - w, d);
						} else {
							XData[i][numd * wi + d] = mat.get(t - w, d) - mat.get(t, d);
						}
					}
				}
			}

			List<String> names = new ArrayList<String>();
			for (int wi = 0; wi < widthList.length; wi++) {
				for (int d = 0; d < numd; d++) {
					String n = this.names.get(d) + "_w" + widthList[wi];
					names.add(n);
				}
			}

			List<Object> properties = new ArrayList<Object>();
			for (int wi = 0; wi < widthList.length; wi++) {
				for (int d = 0; d < numd; d++) {
					properties.add(null);
				}
			}

			long[] times = new long[numIndex];
			for (int i = 0; i < numIndex; i++) {
				int t = indexList.get(i);
				times[i] = this.times[t];
			}

			Dataset ret = new Dataset(names, properties, times, new MyMatrix(XData));
			return ret;
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	public Dataset PickupTimes(Dataset ds) throws Exception {

		HashMap<Long, Integer> map = new HashMap<Long, Integer>();
		for (int t = 0; t < mat.numt; t++) {
			long time = times[t];
			map.put(time, t);
		}

		ArrayList<Integer> indexList = new ArrayList<Integer>();
		for (int t = 0; t < ds.mat.numt; t++) {
			int index = map.get(ds.times[t]);
			indexList.add(index);
		}

		return this.getMatrix(indexList, 0, mat.numd - 1);
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	public Dataset MedianFilter(long maxTime, int minSample) throws Exception {
		double[][] data = new double[mat.numt][mat.numd];

		for (int d = 0; d < mat.numd; d++) {
			for (int t = 0; t < mat.numt; t++) {
				ArrayList<Double> valueList = new ArrayList<Double>();
				for (int w = 0; w < mat.numt; w++) {
					int t2 = t - w;
					if (t2 < 0 || t2 >= mat.numt) continue;
					long time1 = times[t];
					long time2 = times[t2];
					long timeDef = time1 - time2;
					if (timeDef < 0 || timeDef > maxTime) {
						break;
					}
					double value = mat.get(t2, d);
					if (Double.isNaN(value)) continue;
					valueList.add(value);
				}
				if (valueList.size() < minSample) {
					data[t][d] = Double.NaN;
				} else {
					Collections.sort(valueList);
					double median = valueList.get(valueList.size() / 2);
					data[t][d] = median;
				}
			}
		}

		Dataset ret = new Dataset(names, properties, times, new MyMatrix(data));
		return ret;
	}

	public Dataset MovingAverage(long maxTime, int minSample) throws Exception {
		double[][] data = new double[mat.numt][mat.numd];

		for (int d = 0; d < mat.numd; d++) {
			for (int t = 0; t < mat.numt; t++) {
				double total = 0;
				int num = 0;
				for (int w = 0; w < mat.numt; w++) {
					int t2 = t - w;
					if (t2 < 0 || t2 >= mat.numt) continue;
					long time1 = times[t];
					long time2 = times[t2];
					long timeDef = time1 - time2;
					if (timeDef < 0 || timeDef > maxTime) {
						break;
					}
					double value = mat.get(t2, d);
					if (Double.isNaN(value)) continue;
					total += value;
					num++;
				}
				if (num < minSample) {
					data[t][d] = Double.NaN;
				} else {
					data[t][d] = total / num;
				}
			}
		}

		Dataset ret = new Dataset(names, properties, times, new MyMatrix(data));
		return ret;

	}

	// //////////////////////////////////////////////////////////////////////////////////////
	public ArrayList<String> ComputeUniqueSensorList(int numtDS, int numThread, double threshold) throws Exception {
		return ComputeUniqueSensorList(numtDS, numThread, threshold, null);
	}

	public ArrayList<String> ComputeUniqueSensorList(int numtDS, int numThread, double threshold, ArrayList<String> must) throws Exception {
		Dataset dsDS;
		if (numtDS == 0) {
			dsDS = new Dataset(this);
		} else {
			dsDS = this.DownSampling(numtDS);
		}

		MyMatrix W = MatrixUtility.ComputeSecondMomentSum(dsDS.mat, numThread);
		MyMatrix S = MatrixUtility.ComputeCorrelationFromVariance(W);

		int numd = this.GetNumDimension();

		boolean[] remove = new boolean[numd];

		for (int d = 0; d < numd; d++) {
			if (must != null && must.contains(names.get(d)) == true) continue;

			boolean find = false;
			for (int d2 = 0; d2 < d; d2++) {
				if (remove[d2] == true) continue;
				double cor = S.get(d, d2);
				if (cor > threshold || cor < -threshold) {
					find = true;
					break;
				}
			}
			if (find == true) {
				remove[d] = true;
			}
		}

		ArrayList<Integer> indexD = new ArrayList<Integer>();
		for (int d = 0; d < numd; d++) {
			if (remove[d] == false) {
				indexD.add(d);
			}
		}

		ArrayList<String> ret = new ArrayList<String>();
		for (int d : indexD) {
			ret.add(this.names.get(d));
		}
		return ret;
	}

	public Dataset MaxForEachRow(String name) throws Exception {
		int numt = this.GetNumSample();
		int numd = this.GetNumDimension();

		double[][] ret = new double[numt][1];

		for (int t = 0; t < numt; t++) {
			ret[t][0] = -Double.MAX_VALUE;
		}

		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				double s = this.mat.get(t, d);
				if (s > ret[t][0]) {
					ret[t][0] = s;
				}
			}
		}

		Dataset ds = new Dataset(name, null, this.times, new MyMatrix(ret));
		return ds;
	}

	// public Dataset ComputeMedian(int width) throws Exception {
	// int numt = this.GetNumSample();
	// int numd = this.GetNumDimension();
	// double[][] ret = new double[numt][numd];
	// for (int t = 0; t < numt; t++) {
	// for (int d = 0; d < numd; d++) {
	// ArrayList<Double> values = new ArrayList<Double>();
	// for (int w = 0; w < width; w++) {
	// int t2 = t - w;
	// if (t2 < 0) continue;
	// values.add(mat.get(t2, d));
	// }
	// if (values.size() == 0) continue;
	// Collections.sort(values);
	// double med = values.get(values.size() / 2);
	// ret[t][d] = med;
	// }
	// }
	// return new Dataset(names, times, new MyMatrix(ret));
	// }

	public Dataset SubmatrixExceptingD(int dTarget) throws Exception {
		ArrayList<String> names = new ArrayList<String>();
		int numd = this.GetNumDimension();
		for (int d = 0; d < numd; d++) {
			if (d == dTarget) continue;
			names.add(this.names.get(d));
		}
		return this.getMatrix(names);
	}

	public Dataset SubmatrixIncludingD(int dTarget) throws Exception {
		int numt = this.GetNumSample();
		return this.getMatrix(0, numt - 1, dTarget, dTarget);
	}

	public void setWithDifferentTimes(Dataset ds) throws Exception {
		if (this.GetNumDimension() != ds.GetNumDimension()) throw new Exception("Error");
		int numd = this.GetNumDimension();
		int numt1 = this.GetNumSample();
		int numt2 = ds.GetNumSample();
		Map<Long, Integer> time2index = new HashMap<Long, Integer>();
		for (int t1 = 0; t1 < numt1; t1++) {
			time2index.put(times[t1], t1);
		}

		for (int t2 = 0; t2 < numt2; t2++) {
			Integer t1 = time2index.get(ds.times[t2]);
			if (t1 == null) continue;
			for (int d = 0; d < numd; d++) {
				mat.data[t1][d] = ds.mat.data[t2][d];
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// dateを超えない最大のインデックスを探す。
	public int findMaxIndexBeforeDate(String date, SimpleDateFormat sdf) throws Exception {
		long time = sdf.parse(date).getTime();
		return findMaxIndexBeforeDate(time);
	}

	public int findMaxIndexBeforeDate(long time) {
		int numt = mat.getRowDimension();
		int max = -1;
		for (int t = 0; t < numt; t++) {
			if (times[t] < time) {
				max = t;
			}
		}
		return max;
	}

	// dateを超える最小のインデックスを探す。
	public int findMinIndexAfterDate(String date, SimpleDateFormat sdf) throws Exception {
		long time = sdf.parse(date).getTime();
		return findMinIndexAfterDate(time);
	}

	public int findMinIndexAfterDate(long time) {
		int numt = mat.getRowDimension();
		int min = numt;
		for (int t = numt - 1; t >= 0; t--) {
			if (times[t] >= time) {
				min = t;
			}
		}
		return min;
	}

	@Override
	public String toString() {
		return mat.toString();
	}

}
