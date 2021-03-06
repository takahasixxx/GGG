/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GlobalParameter {
	static final Random rand = new Random();
	// static final public boolean verbose = true;
	static final public boolean verbose = false;

	static public String PID;
	static public int numThread = 1;
	static public int numCPUCore = 1;
	static final public int numField = 11;

	static public int[][] onehopList = new int[][] { { 0, 0, 0 }, { 1, -1, 0 }, { 2, 1, 0 }, { 3, 0, -1 }, { 4, 0, 1 } };

	static public ExecutorService executor;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
			if (new File("data/parameters.txt").exists()) {
				Properties p = new Properties();
				p.load(new FileInputStream(new File("data/parameters.txt")));
				numThread = Integer.parseInt(p.getProperty("numThread"));
			} else {
				numThread = 1;
			}
			System.out.println("numThread = " + numThread);

			// コア数を取得しておく。
			numCPUCore = Runtime.getRuntime().availableProcessors();
			System.out.println("num of CPU core = " + numCPUCore);

			// スレッドプールを初期化しておく。
			// TODO
			// int numExecutorsThread = numCPUCore / 2 - 1;
			int numExecutorsThread = numCPUCore - 1;
			if (numExecutorsThread < 1) numExecutorsThread = 1;
			executor = Executors.newFixedThreadPool(numExecutorsThread);
			// executor = Executors.newFixedThreadPool(16);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
