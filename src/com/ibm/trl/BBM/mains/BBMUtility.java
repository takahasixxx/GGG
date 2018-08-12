package com.ibm.trl.BBM.mains;

import com.ibm.trl.BBM.mains.Agent.Node;

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
					print = "・";
				} else if (type == Constant.Rigid) {
					print = "■";
				} else if (type == Constant.Wood) {
					print = "□";
				} else if (type == Constant.Bomb) {
					print = "＠";
				} else if (type == Constant.Flames) {
					print = "＃";
				} else if (type == Constant.Fog) {
					print = "￥";
				} else if (type == Constant.ExtraBomb) {
					print = "↑";
				} else if (type == Constant.IncrRange) {
					print = "⇔";
				} else if (type == Constant.Kick) {
					print = "蹴";
				} else if (type == Constant.AgentDummy) {
					print = "Ｄ";
				} else if (type == Constant.Agent0) {
					if (lll == 0) {
						print = "１";
					} else {
						print = "�@";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print = "２";
					} else {
						print = "�A";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print = "３";
					} else {
						print = "�B";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print = "４";
					} else {
						print = "�C";
					}
				}

				System.out.print(print);
			}
			System.out.println();
		}
	}

	static String[] zenkakuNumber = { "０", "１", "２", "３", "４", "５", "６", "７", "８", "９" };

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
					print1 = "      ";
					print2 = "  --  ";
					print3 = "      ";
				} else if (type == Constant.Rigid) {
					print1 = "■■■";
					print2 = "■■■";
					print3 = "■■■";
				} else if (type == Constant.Wood) {
					print1 = "□□□";
					print2 = "□□□";
					print3 = "□□□";
				} else if (type == Constant.Bomb) {
					print1 = "／￣＼";
					print2 = "＝" + zenkakuNumber[lll] + "＝";
					print3 = "＼" + zenkakuNumber[ppp] + "／";
				} else if (type == Constant.Flames) {
					print1 = "＃＃＃";
					print2 = "＃＃＃";
					print3 = "＃＃＃";
				} else if (type == Constant.Fog) {
					print1 = "￥￥￥";
					print2 = "￥￥￥";
					print3 = "￥￥￥";
				} else if (type == Constant.ExtraBomb) {
					print1 = "爆  弾";
					print2 = "１  個";
					print3 = "増  量";
				} else if (type == Constant.IncrRange) {
					print1 = "  ↑  ";
					print2 = "←＠→";
					print3 = "  ↓  ";
				} else if (type == Constant.Kick) {
					print1 = "      ";
					print2 = "  蹴  ";
					print3 = "      ";
				} else if (type == Constant.AgentDummy) {
					print1 = "      ";
					print2 = "  Ｄ  ";
					print3 = "      ";
				} else if (type == Constant.Agent0) {
					if (lll == 0) {
						print1 = "  �@  ";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "  �@  ";
						print2 = "／｜＼";
						print3 = "  ハ＠";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print1 = "  �A  ";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "  �A  ";
						print2 = "／｜＼";
						print3 = "  ハ＠";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print1 = "  �B  ";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "  �B  ";
						print2 = "／｜＼";
						print3 = "  ハ＠";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print1 = "  �C  ";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "  �C  ";
						print2 = "／｜＼";
						print3 = "  ハ＠";
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

	static public void printBombMap(MyMatrix board, Node[][] bombMap) {
		int numt = board.numt;
		int numd = board.numd;
		for (int x = 0; x < numt; x++) {
			String line1 = "";
			String line2 = "";
			String line3 = "";
			for (int y = 0; y < numd; y++) {
				Node node = bombMap[x][y];
				String print1 = "";
				String print2 = "";
				String print3 = "";
				if (node == null) {
					print1 = "      ";
					print2 = "  --  ";
					print3 = "      ";
				} else if (node.type == Constant.Bomb) {
					int id = node.owner - 10 + 1;
					print1 = String.format("@%d pw%d", id, node.power);
					print2 = String.format("   lf%d", node.lifeBomb);
					print3 = String.format("   mv%d", node.moveDirection);
				} else {
					int id = node.owner - 10 + 1;
					print1 = String.format("#%d pw%d", id, node.power);
					print2 = String.format("   lf%d", node.lifeFlameCenter);
					print3 = String.format("   mv%d", node.moveDirection);
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

	static public void PrintFlame(MyMatrix boardPre, MyMatrix myFlame, int x, int y, int power, int value) {
		int numField = boardPre.numd;

		myFlame.data[x][y] = value;

		for (int dir = 0; dir < 4; dir++) {
			for (int w = 1; w < power; w++) {
				int xSeek = x;
				int ySeek = y;
				if (dir == 0) {
					xSeek = x - w;
				} else if (dir == 1) {
					xSeek = x + w;
				} else if (dir == 2) {
					ySeek = y - w;
				} else if (dir == 3) {
					ySeek = y + w;
				}
				if (xSeek < 0 || xSeek >= numField) break;
				if (ySeek < 0 || ySeek >= numField) break;

				int typePre = (int) boardPre.data[xSeek][ySeek];

				// 前ステップがRidgeだったら、この手前でFlameが止まるのでループ終了
				if (typePre == Constant.Rigid) break;

				myFlame.data[xSeek][ySeek] = value;

				// 前ステップがWoodだったら、このセルを終端としてFlameが止まるのでループ終了
				if (typePre == Constant.Wood) break;
			}
		}
	}

}
