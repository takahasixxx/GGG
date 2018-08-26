package com.ibm.trl.BBM.mains;

import com.ibm.trl.BBM.mains.BombTracker.Node;

import ibm.ANACONDA.Core.MyMatrix;

public class BBMUtility {

	static public int numWoodBrakable(int numField, MyMatrix board, int x, int y, int strength) {
		int num = 0;
		for (int dir = 1; dir <= 4; dir++) {
			for (int w = 1; w < strength; w++) {
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

	static public int numAgent(MyMatrix board, int x, int y, int exceptAgent) {
		int numField = board.numd;

		int num = 0;
		if (x > 0) {
			int type = (int) board.data[x - 1][y];
			if (type != exceptAgent && Constant.isAgent(type)) num++;
		}

		if (x < numField - 1) {
			int type = (int) board.data[x + 1][y];
			if (type != exceptAgent && Constant.isAgent(type)) num++;
		}

		if (y > 0) {
			int type = (int) board.data[x][y - 1];
			if (type != exceptAgent && Constant.isAgent(type)) num++;
		}

		if (y < numField - 1) {
			int type = (int) board.data[x][y + 1];
			if (type != exceptAgent && Constant.isAgent(type)) num++;
		}

		return num;
	}

	
	static public class SurroundedInformation {
		int numWall = 0;
		int numAgent = 0;
		int numBombKickable = 0;
		int numBombFixedByAgent = 0;
		int numBombFixed = 0;
	}
	

	static public SurroundedInformation numSurrounded_Rich(MyMatrix board, boolean[][] bombExist, int x, int y) {
		int numField = board.numd;

		SurroundedInformation si = new SurroundedInformation();

		if (x - 1 >= 0) {
			int type = (int) board.data[x - 1][y];
			boolean bomb = bombExist[x - 1][y];

			if (Constant.isWall(type)) {
				si.numWall++;
			} else if (bomb) {
				boolean bombFixed = false;
				boolean bombFixedByAgent = false;
				if (x - 2 >= 0) {
					int type2 = (int) board.data[x - 2][y];
					boolean bomb2 = bombExist[x - 2][y];
					if (Constant.isWall(type2) || bomb2) {
						bombFixed = true;
					} else if (Constant.isAgent(type2)) {
						bombFixedByAgent = true;
					}
				} else {
					bombFixed = true;
				}
				if (bombFixed) {
					si.numBombFixed++;
				} else if (bombFixedByAgent) {
					si.numBombFixedByAgent++;
				} else {
					si.numBombKickable++;
				}
			} else if (Constant.isAgent(type)) {
				si.numAgent++;
			}
		} else {
			si.numWall++;
		}

		if (x + 1 < numField) {
			int type = (int) board.data[x + 1][y];
			boolean bomb = bombExist[x + 1][y];

			if (Constant.isWall(type)) {
				si.numWall++;
			} else if (bomb) {
				boolean bombFixed = false;
				boolean bombFixedByAgent = false;
				if (x + 2 < numField) {
					int type2 = (int) board.data[x + 2][y];
					boolean bomb2 = bombExist[x + 2][y];
					if (Constant.isWall(type2) || bomb2) {
						bombFixed = true;
					} else if (Constant.isAgent(type2)) {
						bombFixedByAgent = true;
					}
				} else {
					bombFixed = true;
				}
				if (bombFixed) {
					si.numBombFixed++;
				} else if (bombFixedByAgent) {
					si.numBombFixedByAgent++;
				} else {
					si.numBombKickable++;
				}
			} else if (Constant.isAgent(type)) {
				si.numAgent++;
			}
		} else {
			si.numWall++;
		}

		if (y - 1 >= 0) {
			int type = (int) board.data[x][y - 1];
			boolean bomb = bombExist[x][y - 1];

			if (Constant.isWall(type)) {
				si.numWall++;
			} else if (bomb) {
				boolean bombFixed = false;
				boolean bombFixedByAgent = false;
				if (y - 2 >= 0) {
					int type2 = (int) board.data[x][y - 2];
					boolean bomb2 = bombExist[x][y - 2];
					if (Constant.isWall(type2) || bomb2) {
						bombFixed = true;
					} else if (Constant.isAgent(type2)) {
						bombFixedByAgent = true;
					}
				} else {
					bombFixed = true;
				}
				if (bombFixed) {
					si.numBombFixed++;
				} else if (bombFixedByAgent) {
					si.numBombFixedByAgent++;
				} else {
					si.numBombKickable++;
				}
			} else if (Constant.isAgent(type)) {
				si.numAgent++;
			}
		} else {
			si.numWall++;
		}

		if (y + 1 < numField) {
			int type = (int) board.data[x][y + 1];
			boolean bomb = bombExist[x][y + 1];

			if (Constant.isWall(type)) {
				si.numWall++;
			} else if (bomb) {
				boolean bombFixed = false;
				boolean bombFixedByAgent = false;
				if (y + 2 < numField) {
					int type2 = (int) board.data[x][y + 2];
					boolean bomb2 = bombExist[x][y + 2];
					if (Constant.isWall(type2) || bomb2) {
						bombFixed = true;
					} else if (Constant.isAgent(type2)) {
						bombFixedByAgent = true;
					}
				} else {
					bombFixed = true;
				}
				if (bombFixed) {
					si.numBombFixed++;
				} else if (bombFixedByAgent) {
					si.numBombFixedByAgent++;
				} else {
					si.numBombKickable++;
				}
			} else if (Constant.isAgent(type)) {
				si.numAgent++;
			}
		} else {
			si.numWall++;
		}

		return si;
	}

	
	
	static public int numSurrounded(MyMatrix board, int x, int y) {
		int numField = board.numd;

		int num = 0;
		if (x > 0) {
			int type = (int) board.data[x - 1][y];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		if (x < numField - 1) {
			int type = (int) board.data[x + 1][y];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		if (y > 0) {
			int type = (int) board.data[x][y - 1];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		if (y < numField - 1) {
			int type = (int) board.data[x][y + 1];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) num++;
		} else {
			num++;
		}

		return num;
	}

	static public int numSurrounded2(MyMatrix board, int x, int y, int exceptAgent) {
		int numField = board.numd;

		int num = 0;
		int numMove = 0;
		if (x > 0) {
			int type = (int) board.data[x - 1][y];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) {
				num++;
			} else {
				int numAgent = numAgent(board, x - 1, y, exceptAgent);
				if (numAgent >= 1) {
					numMove++;
				}
			}
		} else {
			num++;
		}

		if (x < numField - 1) {
			int type = (int) board.data[x + 1][y];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) {
				num++;
			} else {
				int numAgent = numAgent(board, x + 1, y, exceptAgent);
				if (numAgent >= 1) {
					numMove++;
				}
			}
		} else {
			num++;
		}

		if (y > 0) {
			int type = (int) board.data[x][y - 1];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) {
				num++;
			} else {
				int numAgent = numAgent(board, x, y - 1, exceptAgent);
				if (numAgent >= 1) {
					numMove++;
				}
			}
		} else {
			num++;
		}

		if (y < numField - 1) {
			int type = (int) board.data[x][y + 1];
			if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Bomb || Constant.isAgent(type)) {
				num++;
			} else {
				int numAgent = numAgent(board, x, y + 1, exceptAgent);
				if (numAgent >= 1) {
					numMove++;
				}
			}
		} else {
			num++;
		}

		return num + numMove;
	}
	
	static public int numWall(MyMatrix board, int x, int y) {
		int numField = board.numd;

		int num = 0;
		if (x > 0) {
			int type = (int) board.data[x - 1][y];
			if (type == Constant.Rigid || type == Constant.Wood) num++;
		} else {
			num++;
		}

		if (x < numField - 1) {
			int type = (int) board.data[x + 1][y];
			if (type == Constant.Rigid || type == Constant.Wood) num++;
		} else {
			num++;
		}

		if (y > 0) {
			int type = (int) board.data[x][y - 1];
			if (type == Constant.Rigid || type == Constant.Wood) num++;
		} else {
			num++;
		}

		if (y < numField - 1) {
			int type = (int) board.data[x][y + 1];
			if (type == Constant.Rigid || type == Constant.Wood) num++;
		} else {
			num++;
		}

		return num;
	}

	static public MyMatrix ComputeOptimalDistance(MyMatrix board, int sx, int sy, int maxStep) throws Exception {
		int numField = board.numd;

		boolean[][] blocked = new boolean[numField][numField];
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Flames || type == Constant.Bomb || Constant.isAgent(type)) {
					blocked[x][y] = true;
				}
			}
		}

		int[][] currentPositions = new int[122][2];
		int[][] nextPositions = new int[122][2];

		double[][] dis = new double[numField][numField];
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				dis[x][y] = Double.MAX_VALUE;
			}
		}
		dis[sx][sy] = 0;

		currentPositions[0][0] = sx;
		currentPositions[0][1] = sy;
		currentPositions[1][0] = -1;

		int step = 0;
		while (true) {
			if (step >= maxStep) break;

			int counter = 0;
			for (int[] targetPos : currentPositions) {
				if (targetPos[0] == -1) break;
				int x = targetPos[0];
				int y = targetPos[1];
				if (x > 0) {
					int x2 = x - 1;
					int y2 = y;
					if (blocked[x2][y2] == false) {
						double temp1 = dis[x][y] + 1;
						double temp2 = dis[x2][y2];
						if (temp1 < temp2) {
							dis[x2][y2] = temp1;
							nextPositions[counter][0] = x2;
							nextPositions[counter][1] = y2;
							counter++;
						}
					}
				}
				if (x < numField - 1) {
					int x2 = x + 1;
					int y2 = y;
					if (blocked[x2][y2] == false) {
						double temp1 = dis[x][y] + 1;
						double temp2 = dis[x2][y2];
						if (temp1 < temp2) {
							dis[x2][y2] = temp1;
							nextPositions[counter][0] = x2;
							nextPositions[counter][1] = y2;
							counter++;
						}
					}
				}
				if (y > 0) {
					int x2 = x;
					int y2 = y - 1;
					if (blocked[x2][y2] == false) {
						double temp1 = dis[x][y] + 1;
						double temp2 = dis[x2][y2];
						if (temp1 < temp2) {
							dis[x2][y2] = temp1;
							nextPositions[counter][0] = x2;
							nextPositions[counter][1] = y2;
							counter++;
						}
					}
				}
				if (y < numField - 1) {
					int x2 = x;
					int y2 = y + 1;
					if (blocked[x2][y2] == false) {
						double temp1 = dis[x][y] + 1;
						double temp2 = dis[x2][y2];
						if (temp1 < temp2) {
							dis[x2][y2] = temp1;
							nextPositions[counter][0] = x2;
							nextPositions[counter][1] = y2;
							counter++;
						}
					}
				}
			}

			nextPositions[counter][0] = -1;
			counter++;

			if (counter == 1) break;

			int[][] temp = currentPositions;
			currentPositions = nextPositions;
			nextPositions = temp;

			step++;
		}

		return new MyMatrix(dis);
	}

	static public int ComputeFirstDirection(MyMatrix dis, int xNow, int yNow) {
		int numField = dis.numd;

		int disNow = (int) dis.data[xNow][yNow];

		int dir = 0;
		while (true) {
			if (disNow == 0) {
				break;
			}

			if (xNow > 0) {
				int xNew = xNow - 1;
				int yNew = yNow;
				int temp = (int) dis.data[xNew][yNew];
				if (temp == disNow - 1) {
					disNow = temp;
					xNow = xNew;
					yNow = yNew;
					dir = 2;
					continue;
				}
			}

			if (xNow < numField - 1) {
				int xNew = xNow + 1;
				int yNew = yNow;
				int temp = (int) dis.data[xNew][yNew];
				if (temp == disNow - 1) {
					disNow = temp;
					xNow = xNew;
					yNow = yNew;
					dir = 1;
					continue;
				}
			}

			if (yNow > 0) {
				int xNew = xNow;
				int yNew = yNow - 1;
				int temp = (int) dis.data[xNew][yNew];
				if (temp == disNow - 1) {
					disNow = temp;
					xNow = xNew;
					yNow = yNew;
					dir = 4;
					continue;
				}
			}

			if (yNow < numField - 1) {
				int xNew = xNow;
				int yNew = yNow + 1;
				int temp = (int) dis.data[xNew][yNew];
				if (temp == disNow - 1) {
					disNow = temp;
					xNow = xNew;
					yNow = yNew;
					dir = 3;
					continue;
				}
			}
		}

		return dir;
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
					print2 = "--R--";
					print3 = "      ";
				} else if (type == Constant.AgentDummy) {
					print1 = "      ";
					print2 = "  ‚c  ";
					print3 = "      ";
				} else if (type == Constant.Agent0) {
					if (lll == 0) {
						print1 = "i‚Pj";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "i‚Pj";
						print2 = "^b_";
						print3 = "  ƒn—";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print1 = "i‚Qj";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "i‚Qj";
						print2 = "^b_";
						print3 = "  ƒn—";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print1 = "i‚Rj";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "i‚Rj";
						print2 = "^b_";
						print3 = "  ƒn—";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print1 = "i‚Sj";
						print2 = "^b_";
						print3 = "  ƒn  ";
					} else {
						print1 = "i‚Sj";
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
