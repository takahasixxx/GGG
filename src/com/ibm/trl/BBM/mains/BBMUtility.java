/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import ibm.ANACONDA.Core.MyMatrix;

public class BBMUtility {

	static public boolean isMoveable(int numField, MyMatrix board, int x, int y, int dir) {
		if (dir == 0) return true;
		if (dir == 5) return true;

		int x2 = x;
		int y2 = y;
		if (dir == 1) {
			x2--;
		} else if (dir == 2) {
			x2++;
		} else if (dir == 3) {
			y2--;
		} else if (dir == 4) {
			y2++;
		}

		if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) return false;

		int type = (int) board.data[x2][y2];
		if (Constant.isWall(type)) {
			return false;
		} else {
			return true;
		}
	}

	static public int numWoodBrakable(MyMatrix board, int x, int y, int strength) {
		int numField = board.numd;
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

	static private int GGG_getType(MyMatrix board, int x, int y) {
		int numField = board.numd;
		if (x >= 0 && x < numField && y >= 0 && y < numField) return (int) board.data[x][y];
		return Constant.Rigid;
	}

	static private boolean GGG_bombExist(boolean[][] bombExist, int x, int y) {
		int numField = bombExist.length;
		if (x >= 0 && x < numField && y >= 0 && y < numField) return bombExist[x][y];
		return false;
	}

	static private boolean GGG_isWall(int type, int type2, boolean bomb, boolean bomb2, boolean kick) {
		if (Constant.isWall(type)) return true;
		else return false;
	}

	static private boolean GGG_isStop2(int type, int type2, boolean bomb, boolean bomb2, boolean kick) {
		if (Constant.isWall(type)) return true;

		if (kick == false) {
			if (bomb) {
				return true;
			} else {
				return false;
			}
		} else {
			if (bomb && (bomb2 || Constant.isWall(type2))) {
				return true;
			} else {
				return false;
			}
		}
	}

	static public int GGG_Tateana(MyMatrix board, boolean[][] bombExist, int x, int y, boolean kick, int power) {
		int numField = board.numd;

		// boolean[] success = { true, true, true, true };
		int[] wmax = { -1, -1, -1, -1 };
		int[][] endType = new int[4][2];
		boolean[][] endBomb = new boolean[4][2];
		boolean[] isClose = new boolean[4];

		for (int dir = 0; dir < 4; dir++) {
			for (int w = 0; w < numField; w++) {
				int u = 0;
				int v = 0;
				if (dir == 0) {
					u = -1;
					v = 0;
				} else if (dir == 1) {
					u = 1;
					v = 0;
				} else if (dir == 2) {
					u = 0;
					v = -1;
				} else if (dir == 3) {
					u = 0;
					v = 1;
				}

				int x2 = x + w * u;
				int y2 = y + w * v;

				int type_f = GGG_getType(board, x2, y2);
				int type_ff = GGG_getType(board, x2 + u, y2 + v);
				int type_r = GGG_getType(board, x2 + v, y2 + u);
				int type_rr = GGG_getType(board, x2 + 2 * v, y2 + 2 * u);
				int type_l = GGG_getType(board, x2 - v, y2 - u);
				int type_ll = GGG_getType(board, x2 - 2 * v, y2 - 2 * u);

				boolean bomb_f = GGG_bombExist(bombExist, x2, y2);
				boolean bomb_ff = GGG_bombExist(bombExist, x2 + u, y2 + v);
				boolean bomb_r = GGG_bombExist(bombExist, x2 + v, y2 + u);
				boolean bomb_rr = GGG_bombExist(bombExist, x2 + 2 * v, y2 + 2 * u);
				boolean bomb_l = GGG_bombExist(bombExist, x2 - v, y2 - u);
				boolean bomb_ll = GGG_bombExist(bombExist, x2 - 2 * v, y2 - 2 * u);

				if (w == 0) {
					if (GGG_isWall(type_r, type_rr, bomb_r, bomb_rr, kick) == false || GGG_isWall(type_l, type_ll, bomb_l, bomb_ll, kick) == false) {
						// 左右が開いてたら、トンネルになってない。
						wmax[dir] = w - 1;
						break;
					}
				} else {
					if (GGG_isWall(type_f, type_ff, bomb_f, bomb_ff, kick)) {
						// 終端である。左右が閉じており、前方も閉じているタイプの終端にぶつかった。
						endType[dir][0] = type_f;
						endType[dir][1] = type_ff;
						endBomb[dir][0] = bomb_f;
						endBomb[dir][1] = bomb_ff;
						isClose[dir] = true;
						wmax[dir] = w - 1;
						break;
					} else if (GGG_isWall(type_r, type_rr, bomb_r, bomb_rr, kick) && GGG_isWall(type_l, type_ll, bomb_l, bomb_ll, kick)) {
						// 前方は開いているが、左右が閉じている。トンネルが続いてる。
						continue;
					} else {
						// 終端である。左右が開いてしまっている。
						endType[dir][0] = type_f;
						endType[dir][1] = type_ff;
						endBomb[dir][0] = bomb_f;
						endBomb[dir][1] = bomb_ff;
						isClose[dir] = false;
						wmax[dir] = w - 1;
						break;
					}
				}
			}
		}

		if (wmax[0] == -1 && wmax[1] == -1 && wmax[2] == -1 && wmax[3] == -1) {
			// 一歩目で、縦にも横にも左右が閉じていなかったら、トンネル不成立。
			return 0;
		} else if (wmax[0] == 0 && wmax[1] == 0 && wmax[2] == 0 && wmax[3] == 0) {
			// 1マスでトンネル完成状態
			return 1;
		} else {

			int sideA = -1;
			int sideB = -1;
			if (wmax[0] >= 0 && wmax[1] >= 0) {
				// 横方向にトンネル
				sideA = 0;
				sideB = 1;
			} else if (wmax[2] >= 0 && wmax[3] >= 0) {
				// 縦方向にトンネル
				sideA = 2;
				sideB = 3;
			}

			// トンネルの長さがpower以上だったら、不成立
			int length = wmax[sideA] + wmax[sideB] + 1;
			if (length > power - 1) return 0;

			int sideOpen = -1;
			if (isClose[sideA] == false && isClose[sideB] == false) {
				// 左右が開いていいたら、不成立
				return 0;
			} else if (isClose[sideA] == false && isClose[sideB] == true) {
				// 0方向が開いている。1方向は閉じている。0方向の開き具合で評価する。
				sideOpen = sideA;
			} else if (isClose[sideA] == true && isClose[sideB] == false) {
				// 1方向が開いている。0方向は閉じている。1方向の開き具合で評価する。
				sideOpen = sideB;
			} else if (isClose[sideA] == true && isClose[sideB] == true) {
				// 両方向閉じている。完成
				return 1;
			}

			if (kick) {
				String temae = "";
				for (int i = 0; i < 2; i++) {
					if (Constant.isAgent(endType[sideOpen][i])) {
						temae += "A";
					} else {
						temae += "N";
					}
					if (endBomb[sideOpen][i]) {
						temae += "B";
					}
					if (i == 0) {
						temae += ",";
					}
				}
				int score = 0;
				if (temae.equals("NB,NB")) {
					score = 1;
				} else if (temae.equals("NB,AB")) {
					score = 2;
				} else if (temae.equals("NB,A")) {
					score = 3;
				} else if (temae.equals("AB,N")) {
					score = 4;
				} else if (temae.equals("A,N")) {
					score = 5;
				} else if (temae.equals("AB,NB")) {
					score = 2;
				} else if (temae.equals("A,NB")) {
					score = 3;
				}
				return score;
			} else {
				String temae = "";
				for (int i = 0; i < 1; i++) {
					if (Constant.isAgent(endType[sideOpen][i])) {
						temae += "A";
					} else {
						temae += "N";
					}
					if (endBomb[sideOpen][i]) {
						temae += "B";
					}
					if (i == 0) {
						temae += ",";
					}
				}

				int score = 0;
				if (temae.equals("NB,")) {
					score = 1;
				} else if (temae.equals("AB,")) {
					score = 2;
				} else if (temae.equals("A,")) {
					score = 3;
				}
				return score;
			}
		}
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

	static public int numRigid(MyMatrix board, int x, int y) {
		int numField = board.numd;

		int num = 0;
		if (x > 0) {
			int type = (int) board.data[x - 1][y];
			if (type == Constant.Rigid) num++;
		} else {
			num++;
		}

		if (x < numField - 1) {
			int type = (int) board.data[x + 1][y];
			if (type == Constant.Rigid) num++;
		} else {
			num++;
		}

		if (y > 0) {
			int type = (int) board.data[x][y - 1];
			if (type == Constant.Rigid) num++;
		} else {
			num++;
		}

		if (y < numField - 1) {
			int type = (int) board.data[x][y + 1];
			if (type == Constant.Rigid) num++;
		} else {
			num++;
		}

		return num;
	}

	static public MyMatrix ComputeOptimalDistance(MyMatrix board, int sx, int sy, int maxStep, boolean ignoreBombAndFlames) throws Exception {
		int numField = board.numd;

		boolean[][] blocked = new boolean[numField][numField];
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (ignoreBombAndFlames == false) {
					if (type == Constant.Rigid || type == Constant.Wood || type == Constant.Flames || type == Constant.Bomb || Constant.isAgent(type)) {
						blocked[x][y] = true;
					}
				} else {
					if (type == Constant.Rigid || type == Constant.Wood || Constant.isAgent(type)) {
						blocked[x][y] = true;
					}
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
						print = "①";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print = "２";
					} else {
						print = "②";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print = "３";
					} else {
						print = "③";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print = "４";
					} else {
						print = "④";
					}
				}

				System.out.print(print);
			}
			System.out.println();
		}
	}

	static String[] zenkakuNumber = { "０", "１", "２", "３", "４", "５", "６", "７", "８", "９", "10", "11", "12", "13" };

	static public void printBoard2(MyMatrix board, MyMatrix board_org, MyMatrix life, MyMatrix power) {
		String text = printBoard2_str(board, board_org, life, power);
		System.out.println(text);
	}

	static public String printBoard2_str(MyMatrix board, MyMatrix board_org, MyMatrix life, MyMatrix power) {
		int numt = board.numt;
		int numd = board.numd;
		String alltext = "";
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
					print1 = "回回回";
					print2 = "回回回";
					print3 = "回回回";
				} else if (type == Constant.Wood) {
					print1 = "ロロロ";
					print2 = "ロロロ";
					print3 = "ロロロ";
				} else if (type == Constant.Bomb) {
					print1 = "／￣＼";
					print2 = "＝" + zenkakuNumber[lll] + "＝";
					print3 = "＼" + zenkakuNumber[ppp] + "／";
				} else if (type == Constant.Flames) {
					print1 = "＃＃＃";
					print2 = "＃＃＃";
					print3 = "＃＃＃";
				} else if (type == Constant.Fog) {
					print1 = "FFFFFF";
					print2 = "FFFFFF";
					print3 = "FFFFFF";
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
					print2 = "--蹴--";
					print3 = "      ";
				} else if (type == Constant.AgentDummy) {
					print1 = "      ";
					print2 = "  Ｄ  ";
					print3 = "      ";
				} else if (type == Constant.Agent0) {
					if (lll == 0) {
						print1 = "（１）";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "（１）";
						print2 = "／｜＼";
						print3 = "  ハ＠";
					}
				} else if (type == Constant.Agent1) {
					if (lll == 0) {
						print1 = "（２）";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "（２）";
						print2 = "／｜＼";
						print3 = "  ハ＠";
					}
				} else if (type == Constant.Agent2) {
					if (lll == 0) {
						print1 = "（３）";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "（３）";
						print2 = "／｜＼";
						print3 = "  ハ＠";
					}
				} else if (type == Constant.Agent3) {
					if (lll == 0) {
						print1 = "（４）";
						print2 = "／｜＼";
						print3 = "  ハ  ";
					} else {
						print1 = "（４）";
						print2 = "／｜＼";
						print3 = "  ハ＠";
					}
				}

				int type_org = (int) board_org.data[x][y];
				if (type_org == Constant.Fog) {
					// print1 = "\u001b[30;47m" + print1 + "\u001b[00m";
					// print2 = "\u001b[30;47m" + print2 + "\u001b[00m";
					// print3 = "\u001b[30;47m" + print3 + "\u001b[00m";
				}

				line1 += print1;
				line2 += print2;
				line3 += print3;
			}

			alltext += line1 + "\n";
			alltext += line2 + "\n";
			alltext += line3 + "\n";
		}

		return alltext;
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

	static public void PrintFlame(MyMatrix boardPre, boolean[][] myFlame, int x, int y, int power) {
		int numField = boardPre.numd;

		myFlame[x][y] = true;

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

				myFlame[xSeek][ySeek] = true;

				// 前ステップがWoodだったら、このセルを終端としてFlameが止まるのでループ終了
				if (typePre == Constant.Wood) break;
			}
		}
	}

	static double add_log(double a, double b) {
		if (a == Double.NEGATIVE_INFINITY && b == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;
		if (a > b) {
			return Math.log(1 + Math.exp(b - a)) + a;
		} else {
			return Math.log(Math.exp(a - b) + 1) + b;
		}
	}

	static double sub_log(double a, double b) {
		if (a == Double.NEGATIVE_INFINITY && b == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;
		if (a > b) {
			return Math.log(1 - Math.exp(b - a)) + a;
		} else {
			return Math.log(Math.exp(a - b) - 1) + b;
		}
	}

	static double total_log(MyMatrix a) {

		double max = Double.NEGATIVE_INFINITY;
		for (int t = 0; t < a.numt; t++) {
			for (int d = 0; d < a.numd; d++) {
				if (a.data[t][d] > max) {
					max = a.data[t][d];
				}
			}
		}
		if (max == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;

		double total = 0;
		for (int t = 0; t < a.numt; t++) {
			for (int d = 0; d < a.numd; d++) {
				total += Math.exp(a.data[t][d] - max);
			}
		}

		return Math.log(total) + max;
	}
}
