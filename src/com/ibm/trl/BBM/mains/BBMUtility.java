package com.ibm.trl.BBM.mains;

import com.ibm.trl.BBM.mains.Agent.Node;

import ibm.ANACONDA.Core.MyMatrix;

public class BBMUtility {

	static public int numWoodBrakable(int numField, MyMatrix board, int x, int y, int strength) {
		int num = 0;
		for (int dir = 1; dir <= 4; dir++) {
			for (int w = 1; w <= strength; w++) {
				int x2 = x, y2 = y;
				if (dir == 1) {
					x2 = x - w;
					if (x2 < 0) break;
					y2 = y;
				} else if (dir == 2) {
					x2 = x + w;
					if (x2 >= numField) break;
					y2 = y;
				} else if (dir == 3) {
					y2 = y - w;
					if (y2 < 0) break;
					x2 = x;
				} else if (dir == 4) {
					y2 = y + w;
					if (y2 >= numField) break;
					x2 = x;
				}

				int type = (int) board.data[x2][y2];
				if (type == Constant.Rigid) break;

				if (type == Constant.Wood) {
					num++;
					break;
				}
			}
		}
		return num;
	}
	
	static public boolean isSurrounded(int numField, MyMatrix board, int x, int y) {
		int num = 0;
		if (x > 0) {
			int type = (int) board.data[x - 1][y];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || type == Constant.Flames || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		if (x < numField - 1) {
			int type = (int) board.data[x + 1][y];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || type == Constant.Flames || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		if (y > 0) {
			int type = (int) board.data[x][y - 1];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || type == Constant.Flames || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		if (y < numField - 1) {
			int type = (int) board.data[x][y + 1];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || type == Constant.Flames || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		if (num == 4) return true;
		else return false;
	}

	
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
					print1 = "      ";
					print2 = "  --  ";
					print3 = "      ";
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
					print1 = "”š  ’e";
					print2 = "‚P  ŒÂ";
					print3 = "‘  —Ê";
				} else if (type == Constant.IncrRange) {
					print1 = "  ª  ";
					print2 = "©—¨";
					print3 = "  «  ";
				} else if (type == Constant.Kick) {
					print1 = "      ";
					print2 = "  R  ";
					print3 = "      ";
				} else if (type == Constant.AgentDummy) {
					print1 = "      ";
					print2 = "  ‚c  ";
					print3 = "      ";
				} else if (type == Constant.Agent0) {
					if (lll == 0) {
						print1 = "  ‡@  ";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "  ‡@  ";
						print2 = "^b_";
						print3 = "  ƒn—";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print1 = "  ‡A  ";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "  ‡A  ";
						print2 = "^b_";
						print3 = "  ƒn—";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print1 = "  ‡B  ";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "  ‡B  ";
						print2 = "^b_";
						print3 = "  ƒn—";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print1 = "  ‡C  ";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "  ‡C  ";
						print2 = "^b_";
						print3 = "  ƒn—";
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

				// ‘OƒXƒeƒbƒv‚ªRidge‚¾‚Á‚½‚çA‚±‚Ìè‘O‚ÅFlame‚ª~‚Ü‚é‚Ì‚Åƒ‹[ƒvI—¹
				if (typePre == Constant.Rigid) break;

				myFlame.data[xSeek][ySeek] = value;

				// ‘OƒXƒeƒbƒv‚ªWood‚¾‚Á‚½‚çA‚±‚ÌƒZƒ‹‚ğI’[‚Æ‚µ‚ÄFlame‚ª~‚Ü‚é‚Ì‚Åƒ‹[ƒvI—¹
				if (typePre == Constant.Wood) break;
			}
		}
	}

}
