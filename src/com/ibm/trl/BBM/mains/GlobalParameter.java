package com.ibm.trl.BBM.mains;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.math3.distribution.NormalDistribution;

public class GlobalParameter {
	static Random rand = new Random();
	static NormalDistribution nd = new NormalDistribution();
	static final public boolean verbose = true;
	// static final public boolean verbose = false;
	static final int timeStampling = 200;
	static final boolean isOptimizeParameter = false;

	static public String PID;
	static public int numThread = 1;
	static final public int numField = 11;

	static public int[][] onehopList = new int[][] { { 0, 0, 0 }, { 1, -1, 0 }, { 2, 1, 0 }, { 3, 0, -1 }, { 4, 0, 1 } };

	static public ExecutorService executor;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	static int numGame = 0;
	static double numWin = 0;
	static double numLose = 0;
	static double numTie = 0;
	static int lastFrame = -1;

	static public double rateLevel;
	static public double usualMoveThreshold;
	static public double attackThreshold;
	static public double[] param = new double[4];
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

			executor = Executors.newFixedThreadPool(numThread);

			{
				FinishOneEpisode(11, 0, 0, 0);
				FinishOneEpisode(13, 0, 0, 0);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	/**
	 * 
	 * 
	 */

	static public void FinishOneEpisode(int me, int numFrame, int reward, double numItemGet) throws Exception {
		if (me == 11) {
			lastFrame = numField;
			return;
		}

		int frameMax = Math.max(lastFrame, numFrame);
		if (reward == 1) {
			numWin++;
		} else if (reward == -1) {
			if (frameMax == 801) {
				numTie++;
			} else {
				numLose++;
			}
		}

		int onePack = 400;

		if (numGame % onePack == 0) {
			// 試したいパラメータ設定を作る
			List<double[]> params = new ArrayList<double[]>();
			if (true) {
				if (true) {
					double usualCell = 3.5;
					double attackCell = 2.5;
					// for (double rateLevel : new double[] { 1, 1.1, 1.2, 1.3, 1.4 }) {
					// for (double rateLevel : new double[] { 1.6, 1.8, 2.0, 2.2, 2.4, 2.6, 2.8, 3 }) {
					for (double rateLevel : new double[] { 7, 8, 9, 10 }) {
						double[] param = new double[] { rateLevel, usualCell, attackCell, 1000 };
						params.add(param);
					}
				}
			}

			// 結果を出力
			double numTotal = numWin + numTie + numLose;
			String line = String.format("GlobalParameter: Statistics, params=(%f, %f, %f, %f), (total/win/lose/tie)=(%f/%f/%f/%f)", param[0], param[1], param[2], param[3], numTotal, numWin, numLose,
					numTie);
			System.out.println(line);

			// 次のパラメータを設定する。
			int index = numGame / onePack;
			double[] param = params.get(index % params.size());

			// パラメータ設定
			GlobalParameter.param = param;
			GlobalParameter.rateLevel = param[0];
			GlobalParameter.usualMoveThreshold = Math.log(param[1]);
			GlobalParameter.attackThreshold = Math.log(param[2]);

			// 戦績リセット
			numWin = 0;
			numLose = 0;
			numTie = 0;
		}

		numGame++;
	}
}
