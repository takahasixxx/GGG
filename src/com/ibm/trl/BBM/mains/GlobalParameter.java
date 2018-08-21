package com.ibm.trl.BBM.mains;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class GlobalParameter {
	static public String PID;
	static public int numThread = 2;
	static public int numField = 11;

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
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
