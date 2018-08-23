package com.ibm.trl.BBM.mains;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Properties;
import java.util.Random;

import com.ibm.trl.BBM.mains.OptimalActionFinder.OAFParameter;

import ibm.ANACONDA.Core.MyMatrix;

public class GlobalParameter {
	static Random rand = new Random();
	static final public boolean verbose = false;
	static public String PID;
	static public int numThread = 1;
	static final public int numField = 11;

	static public OAFParameter[] oafparameters = new OAFParameter[4];
	static public OAFParameter oafparamCenter;
	static MyMatrix KeisuGlobal = null;
	static int numKeisuGlobal = 0;

	static {
		try {
			// PIDを獲得しておく。
			{
				PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
				System.out.println("PID = " + PID);
			}

			// スレッド数を設定する。
			{
				if (new File("data/parameters.txt").exists()) {
					Properties p = new Properties();
					p.load(new FileInputStream(new File("data/parameters.txt")));
					numThread = Integer.parseInt(p.getProperty("numThread"));
				} else {
					numThread = 1;
				}
				System.out.println("numThread = " + numThread);
			}

			{
				File file = new File("data/oafparameter_average.dat");
				if (file.exists()) {
					ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
					KeisuGlobal = (MyMatrix) ois.readObject();
					numKeisuGlobal = (int) ois.readObject();
					ois.close();
				}

				if (numKeisuGlobal > 0) {
					oafparamCenter = new OAFParameter(KeisuGlobal.times(1.0 / numKeisuGlobal));
				} else {
					oafparamCenter = new OAFParameter();
				}
				oafparamCenter.numEpisode = 1;
				oafparamCenter.numFrame = 1;

				for (int ai = 0; ai < 4; ai++) {
					oafparameters[ai] = new OAFParameter(oafparamCenter.Keisu);
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	/**
	 * Episode終了時に呼ばれる。 KPIを集計する。 ある程度Episodeがたまったら、以下をやる。
	 * 
	 * １．GlobalParameterのOAPParameterのKPIと比較して、良ければGlobalParamterに登録する。ついでにファイルにも保存する。
	 * ２．GlobalParameterのOAFParameterの設定をランダムに動かして、新規トライOAFParameterを設定する。
	 */

	static public void FinishOneEpisode(int me, double numFrame, double reward, double numItemGet) throws Exception {
		OAFParameter oafparam = oafparameters[me - 10];
		oafparam.numEpisode++;
		oafparam.numFrame += numFrame;
		oafparam.numItemGet += numItemGet;
		if (reward == 1) oafparam.numWin++;

		if (oafparam.numEpisode >= 10) {
			double stepSize = 0.01;

			///////////////////////////////////////////////////////////
			// KPIがItem取得率の場合
			double score = oafparam.numWin + oafparam.numItemGet * 0.1;
			double scoreBest = oafparamCenter.numWin + oafparamCenter.numItemGet * 0.1;
			score = score * score;
			scoreBest = scoreBest * scoreBest;

			System.out.println("今の起点OAFParameter");
			System.out.println(oafparamCenter.Keisu);
			System.out.println("試したOAFParameter");
			System.out.println(oafparam.Keisu);
			System.out.println(oafparam.Keisu.minus(oafparamCenter.Keisu));
			System.out.println("結果は、");
			System.out.println(score + " vs " + scoreBest + "(best)");
			System.out.println(String.format("score=%f, numEpisode=%f, numFrame=%f, numItemGet=%f, numWin=%f", score, oafparam.numEpisode, oafparam.numFrame, oafparam.numItemGet, oafparam.numWin));

			if (rand.nextDouble() * scoreBest < score) {
				oafparamCenter = oafparam;
				System.out.println("パラメータをアップデートした。");
			}

			if (numKeisuGlobal == 0) {
				KeisuGlobal = new MyMatrix(oafparamCenter.Keisu);
				numKeisuGlobal++;
			} else {
				double rate = 0.99;
				KeisuGlobal = KeisuGlobal.times(rate).plus(oafparamCenter.Keisu.times(1 - rate));
				numKeisuGlobal = 1;
			}

			// 保存する。
			{
				File file = new File("data/oafparameter_average.dat");
				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
				oos.writeObject(KeisuGlobal);
				oos.writeObject(numKeisuGlobal);
				oos.flush();
				oos.close();
			}

			// パラメータを散らす。
			MyMatrix Keisu = new MyMatrix(oafparamCenter.Keisu);
			for (int ii = 0; ii < 5; ii++) {
				int[] targetIndexSet = { 0, 1, 2, 3, 4, 5, 6, 7 };
				int index = -1;
				int dim = -1;
				boolean increment;
				while (true) {
					int i = rand.nextInt(targetIndexSet.length);
					index = targetIndexSet[i];
					dim = rand.nextInt(3);
					increment = rand.nextBoolean();
					if (oafparam.KeisuUsed[index][dim]) {
						if (increment) {
							break;
						} else {
							if (Keisu.data[index][dim] != 0) {
								break;
							}
						}
					}
				}

				if (increment) {
					Keisu.data[index][dim] += stepSize;
				} else {
					Keisu.data[index][dim] -= stepSize;
					if (Keisu.data[index][dim] < 0) Keisu.data[index][dim] = 0;
				}
			}

			oafparam = new OAFParameter(Keisu);

			System.out.println("今の起点OAFParameter");
			System.out.println(oafparamCenter.Keisu);
			System.out.println("次に試すOAFParameter");
			System.out.println(oafparam.Keisu);
			System.out.println(oafparam.Keisu.minus(oafparamCenter.Keisu));
			System.out.println("平均のOAFParameter");
			System.out.println(KeisuGlobal.times(1.0 / numKeisuGlobal));

			oafparameters[me - 10] = oafparam;
		}
	}
}
