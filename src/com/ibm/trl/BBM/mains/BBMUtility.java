package com.ibm.trl.BBM.mains;

import ibm.ANACONDA.Core.MyMatrix;

public class BBMUtility {

	static public void printBoard(MyMatrix board, MyMatrix life) {
		int numt = board.numt;
		int numd = board.numd;
		for (int x = 0; x < numt; x++) {
			for (int y = 0; y < numd; y++) {
				int type = (int) board.data[x][y];
				int lll = (int) life.data[x][y];
				String print = "";
				if (type == Constant.Passage) {
					print = "E";
				} else if (type == Constant.Rigid) {
					print = "¡";
				} else if (type == Constant.Wood) {
					print = " ";
				} else if (type == Constant.Bomb) {
					print = "—";
				} else if (type == Constant.Flames) {
					print = "”";
				} else if (type == Constant.Fog) {
					print = "";
				} else if (type == Constant.ExtraBomb) {
					print = "ª";
				} else if (type == Constant.IncrRange) {
					print = "Ì";
				} else if (type == Constant.Kick) {
					print = "R";
				} else if (type == Constant.AgentDummy) {
					print = "‚c";
				} else if (type == Constant.Agent0) {
					if (lll == 0) {
						print = "‚P";
					} else {
						print = "‡@";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print = "‚Q";
					} else {
						print = "‡A";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print = "‚R";
					} else {
						print = "‡B";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print = "‚S";
					} else {
						print = "‡C";
					}
				}

				System.out.print(print);
			}
			System.out.println();
		}
	}

	static String[] zenkakuNumber = { "‚O", "‚P", "‚Q", "‚R", "‚S", "‚T", "‚U", "‚V", "‚W", "‚X" };

	static public void printBoard2(MyMatrix board, MyMatrix life, MyMatrix power) {
		int numt = board.numt;
		int numd = board.numd;
		for (int x = 0; x < numt; x++) {
			String line1 = "";
			String line2 = "";
			String line3 = "";
			for (int y = 0; y < numd; y++) {
				int type = (int) board.data[x][y];
				int lll = (int) life.data[x][y];
				int ppp = (int) power.data[x][y];
				String print1 = "";
				String print2 = "";
				String print3 = "";
				if (type == Constant.Passage) {
					print1 = "@@@";
					print2 = "@E@";
					print3 = "@@@";
				} else if (type == Constant.Rigid) {
					print1 = "¡¡¡";
					print2 = "¡¡¡";
					print3 = "¡¡¡";
				} else if (type == Constant.Wood) {
					print1 = "   ";
					print2 = "   ";
					print3 = "   ";
				} else if (type == Constant.Bomb) {
					print1 = "^P_";
					print2 = "" + zenkakuNumber[lll] + "";
					print3 = "_" + zenkakuNumber[ppp] + "^";
				} else if (type == Constant.Flames) {
					print1 = "”””";
					print2 = "”””";
					print3 = "”””";
				} else if (type == Constant.Fog) {
					print1 = "";
					print2 = "";
					print3 = "";
				} else if (type == Constant.ExtraBomb) {
					print1 = "”š@’e";
					print2 = "‚P@ŒÂ";
					print3 = "‘@—Ê";
				} else if (type == Constant.IncrRange) {
					print1 = "@ª@";
					print2 = "©—¨";
					print3 = "@«@";
				} else if (type == Constant.Kick) {
					print1 = "@@@";
					print2 = "@R@";
					print3 = "@@@";
				} else if (type == Constant.AgentDummy) {
					print1 = "@@@";
					print2 = "@‚c@";
					print3 = "@@@";
				} else if (type == Constant.Agent0) {
					if (lll == 0) {
						print1 = "@‡@@";
						print2 = "^b_";
						print3 = "@ƒn@";
					} else {
						print1 = "@‡@@";
						print2 = "^b_";
						print3 = "@ƒn—";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print1 = "@‡A@";
						print2 = "^b_";
						print3 = "@ƒn@";
					} else {
						print1 = "@‡A@";
						print2 = "^b_";
						print3 = "@ƒn—";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print1 = "@‡B@";
						print2 = "^b_";
						print3 = "@ƒn@";
					} else {
						print1 = "@‡B@";
						print2 = "^b_";
						print3 = "@ƒn—";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print1 = "@‡C@";
						print2 = "^b_";
						print3 = "@ƒn@";
					} else {
						print1 = "@‡C@";
						print2 = "^b_";
						print3 = "@ƒn—";
					}
				}

				line1 += print1;
				line2 += print2;
				line3 += print3;
			}
			System.out.println(line1);
			System.out.println(line2);
			System.out.println(line3);
		}
	}
}
