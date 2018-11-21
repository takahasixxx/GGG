/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;

import ibm.ANACONDA.Core.MatrixUtility;
import ibm.ANACONDA.Core.MyMatrix;

public class BombTracker {

	static final boolean verbose = GlobalParameter.verbose;
	static final int numField = GlobalParameter.numField;

	static public class Node {
		public int x;
		public int y;
		public int life;
		public int power;
		public boolean[] dirs = new boolean[5];

		public Node(int x, int y, int life, int power, boolean[] dirs) {
			this.x = x;
			this.y = y;
			this.life = life;
			this.power = power;
			this.dirs = dirs;
		}

		public Node(Node n) {
			this.x = n.x;
			this.y = n.y;
			this.life = n.life;
			this.power = n.power;
		}

		public String toString() {
			return String.format("● (%2d,%2d), type=%2d, lifeB=%2d, power=%2d  \n", x, y, life, power);
		}
	}

	static public class ResultBT {
		Node[][] bombMap;
		MyMatrix flames;

		public ResultBT(Node[][] bombMap, MyMatrix flames) {
			this.bombMap = bombMap;
			this.flames = flames;
		}
	}

	static ResultBT computeBombMap(int maxPower, List<MapInformation> mapsOrg, List<MapInformation> exmapsOrg) throws Exception {

		List<MapInformation> maps = new ArrayList<MapInformation>();
		maps.addAll(mapsOrg);
		Collections.reverse(maps);

		List<MapInformation> exmaps = new ArrayList<MapInformation>();
		exmaps.addAll(exmapsOrg);
		Collections.reverse(exmaps);

		List<BombEEE> bbbs = new ArrayList<BombEEE>();

		MyMatrix flames = new MyMatrix(numField, numField);
		{
			MapInformation mapNow = maps.get(0);
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int type = mapNow.getType(x, y);
					if (type == Constant.Flames) {
						flames.data[x][y] = 4;
					}
				}
			}
		}

		int numt = maps.size();
		for (int t = 1; t < numt; t++) {

			MapInformation mapNow = maps.get(t);
			MapInformation mapPre = maps.get(t - 1);
			MapInformation exmapNow = exmaps.get(t);
			MapInformation exmapPre = exmaps.get(t - 1);

			// 炎のライフを1減じる。
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (flames.data[x][y] > 0) {
						flames.data[x][y] -= 1;
					}
				}
			}

			// 爆弾を整理する。同じ奴は消す。無効のやつは消す。
			if (true) {
				List<BombEEE> bbbs_temp = new ArrayList<BombEEE>();
				for (BombEEE bbb2 : bbbs) {
					if (bbb2.life <= 0) continue;
					boolean find = false;
					for (BombEEE bbb_temp : bbbs_temp) {
						if (bbb_temp.equals(bbb2)) {
							find = true;
							break;
						}
					}
					if (find) continue;
					bbbs_temp.add(bbb2);
				}
				bbbs = bbbs_temp;
			}

			// 今のフレームまでで見つかっている爆弾を動かす。
			for (BombEEE bbb : bbbs) {
				if (bbb.life > 0) {
					int x = bbb.x;
					int y = bbb.y;

					// 次フレームの位置を計算する。
					int x2 = x;
					int y2 = y;
					if (bbb.dir == 1) {
						x2 = x - 1;
					} else if (bbb.dir == 2) {
						x2 = x + 1;
					} else if (bbb.dir == 3) {
						y2 = y - 1;
					} else if (bbb.dir == 4) {
						y2 = y + 1;
					}

					// 次のフレームに移動先に障害物があるかどうか調べる。
					int type2 = exmapPre.getType(x2, y2);
					if (Constant.isWall(type2) || Constant.isAgent(type2)) {
						x2 = x;
						y2 = y;
						bbb.dir = 0;
					}

					bbb.x = x2;
					bbb.y = y2;
				}
				bbb.life--;
			}

			// 爆発している爆弾に巻き込まれている爆弾があれば、life=0にして、dir=0にする。
			while (true) {

				// 爆発してるやつで矛盾してる爆弾があれば、無効にする。
				for (BombEEE bbb : bbbs) {
					// Fogの中じゃないのに、観測と食い違っている爆弾は、無効にする。
					if (bbb.life > 0) {
						int type = mapNow.getType(bbb.x, bbb.y);
						int life = mapNow.getLife(bbb.x, bbb.y);
						int power = mapNow.getPower(bbb.x, bbb.y);
						if (type == Constant.Fog) continue;
						if (type == Constant.Flames) continue;
						if (life != bbb.life || power != bbb.power) {
							bbb.life = -1000;
							if (t > 15) {
								System.out.println("???");
							}
						}
					} else if (bbb.life == 0) {
						MyMatrix flames_test = new MyMatrix(flames);
						BBMUtility.PrintFlame(exmapPre.board, flames_test, bbb.x, bbb.y, bbb.power, 3);
						boolean mujun = false;
						for (int x = 0; x < numField; x++) {
							for (int y = 0; y < numField; y++) {
								int type = mapNow.getType(x, y);
								if (type == Constant.Fog) continue;
								if (flames_test.data[x][y] > 0) {
									if (type != Constant.Flames) {
										mujun = true;
										break;
									}
								}
							}
							if (mujun) break;
						}
						if (mujun) {
							bbb.life = -1000;
							if (t > 15) {
								System.out.println("???");
							}
						} else {
							flames = flames_test;
						}
					}
				}

				// 連鎖爆弾に巻き込まれるやつは爆発させる。
				boolean changed = false;

				for (BombEEE bbb : bbbs) {
					if (bbb.life == 0) {
						BBMUtility.PrintFlame(exmapPre.board, flames, bbb.x, bbb.y, bbb.power, 3);
					}
				}

				for (BombEEE bbb : bbbs) {
					if (bbb.life > 0) {
						if (flames.data[bbb.x][bbb.y] > 0) {
							bbb.life = 0;
							changed = true;
						}
					}
				}

				if (changed == false) break;
			}

			{
				// 矛盾チェックしてみる。
				boolean[][] add = new boolean[numField][numField];
				boolean mujunRemove = false;
				boolean mujunAdd = false;
				for (int x = 0; x < numField; x++) {
					for (int y = 0; y < numField; y++) {
						int type = mapNow.getType(x, y);
						if (type == Constant.Fog) continue;

						// 独自ロジックではFlamesなのに、MapではFlamesじゃない場合。
						if (flames.data[x][y] > 0) {
							if (type != Constant.Flames) {
								flames.data[x][y] = 0;
								mujunRemove = true;
							}
						}

						// MapではFlamesなのに、独自ロジックではFlamesじゃない場合。
						if (type == Constant.Flames) {
							if (flames.data[x][y] == 0) {
								flames.data[x][y] = 3;
								mujunAdd = true;
								add[x][y] = true;
							}
						}
					}
				}
				// System.out.println("t=" + t + ", 矛盾＝" + mujun);

				// MapではFlameなのに、独自ロジックではFlamesじゃない場合、追加したFlamesと接続しているFlamesは、Lifeが３になっているかもしれない。
				if (mujunAdd) {
					for (int x = 0; x < numField; x++) {
						for (int y = 0; y < numField; y++) {
							if (add[x][y]) {
								for (int[] vec : GlobalParameter.onehopList) {
									int dir = vec[0];
									int dx = vec[1];
									int dy = vec[2];
									if (dir == 0) continue;
									for (int w = 0; w <= maxPower; w++) {
										int x2 = x + dx * w;
										int y2 = y + dy * w;
										if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;

										int type = mapNow.getType(x2, y2);
										if (type != Constant.Fog) break;

										flames.data[x2][y2] = 3;
									}
								}
							}
						}
					}
				}
			}

			// Fogの中じゃないのに、観測と食い違っている爆弾は、無効にする。
			for (BombEEE bbb : bbbs) {
				if (bbb.life > 0) {
					int type = mapNow.getType(bbb.x, bbb.y);
					int life = mapNow.getLife(bbb.x, bbb.y);
					int power = mapNow.getPower(bbb.x, bbb.y);
					if (type == Constant.Fog) continue;
					if (life != bbb.life || power != bbb.power) {
						bbb.life = -1000;
						if (t > 15) {
							System.out.println("???");
						}
					}
				}
			}

			// TODO
			if (false) {
				System.out.println("=========================================================");
				System.out.println("t = " + t);
				BBMUtility.printBoard2(exmapNow.board, mapNow.board, mapNow.life, mapNow.power);
				MatrixUtility.OutputMatrix(flames);
				System.out.println("=========================================================");
			}

			// 新しい爆弾を見つける。
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					int power = mapNow.getPower(x, y);
					int life = mapNow.getLife(x, y);
					if (life > 0 && life <= 8) {
						// 現在、すでに考慮済みの爆弾であれば、新規に組み込む必要はない。
						{
							boolean find = false;
							for (BombEEE bbb : bbbs) {
								if (bbb.x == x && bbb.y == y && bbb.life == life && bbb.power == power) {
									find = true;
									break;
								}
							}
							if (find) continue;
						}

						// 前フレームの周囲の状況で、移動方向を推定する。
						boolean none = true;
						for (int[] pos : GlobalParameter.onehopList) {
							int dir = pos[0];
							int dx = pos[1];
							int dy = pos[2];
							int x2 = x + dx;
							int y2 = y + dy;
							int type2 = mapPre.getType(x2, y2);
							int power2 = mapPre.getPower(x2, y2);
							int life2 = mapPre.getLife(x2, y2);
							int dirBomb = 0;
							if (dir == 0) {
								dirBomb = 0;
							} else if (dir == 1) {
								dirBomb = 2;
							} else if (dir == 2) {
								dirBomb = 1;
							} else if (dir == 3) {
								dirBomb = 4;
							} else if (dir == 4) {
								dirBomb = 3;
							}

							int x3 = x + 2 * dx;
							int y3 = y + 2 * dy;
							int type3 = exmapNow.getType(x3, y3);

							if (type2 == Constant.Fog) {
								if (type3 != Constant.Rigid) {
									// Fogから飛び込んできた爆弾の移動
									BombEEE bbb = new BombEEE(x, y, -1, life, dirBomb, power);
									bbbs.add(bbb);
									none = false;
								}
							} else {
								if (power2 == power) {
									if (life2 == life + 1) {
										// 視界の中の爆弾の移動
										BombEEE bbb = new BombEEE(x, y, -1, life, dirBomb, power);
										bbbs.add(bbb);
										none = false;
										if (t > 1) {
											// System.out.println("ないはず？??" + bbb);
										}
									}
								}
							}
						}

						if (none) {
							// トラッキングミスしてる爆弾の移動
							BombEEE bbb = new BombEEE(x, y, -1, life, 0, power);
							bbbs.add(bbb);
							// System.out.println("ないはず？" + bbb);
						}
					} else if (life == 9) {
						// 新規設置の爆弾
						BombEEE bbb = new BombEEE(x, y, -1, life, 0, power);
						bbbs.add(bbb);
					}
				}
			}
		}

		Node[][] bombMap = new Node[numField][numField];
		for (BombEEE bbb : bbbs) {
			if (bbb.life <= 0) continue;
			if (bombMap[bbb.x][bbb.y] == null) {
				boolean[] dirs = new boolean[5];
				dirs[bbb.dir] = true;
				bombMap[bbb.x][bbb.y] = new Node(bbb.x, bbb.y, bbb.life, bbb.power, dirs);
			} else {
				bombMap[bbb.x][bbb.y].dirs[bbb.dir] = true;
			}
		}
		return new ResultBT(bombMap, flames);
	}

	static Node[][] computeBombMap_Old(MapInformation map, List<MapInformation> mapsOld) throws Exception {

		MapInformation mapOld = mapsOld.get(0);

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// 現ステップで見えている爆弾だけ作り出す。
		// 全ステップから動きが推測できるものは動き情報も入れておく。それ以外は停止状態にしておく。
		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		Node[][] bombMap = new Node[numField][numField];
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int power = map.getPower(x, y);
				int life = map.getLife(x, y);
				if (life > 0) {
					// 前フレームの周囲の状況で、移動方向を推定する。
					boolean[] dirs = new boolean[5];
					boolean none = true;
					for (int[] pos : GlobalParameter.onehopList) {
						int dir = pos[0];
						int dx = pos[1];
						int dy = pos[2];
						int x2 = x + dx;
						int y2 = y + dy;
						int type2 = mapOld.getType(x2, y2);
						int power2 = mapOld.getPower(x2, y2);
						int life2 = mapOld.getLife(x2, y2);
						int dir2 = 0;
						if (dir == 0) {
							dir2 = 0;
						} else if (dir == 1) {
							dir2 = 2;
						} else if (dir == 2) {
							dir2 = 1;
						} else if (dir == 3) {
							dir2 = 4;
						} else if (dir == 4) {
							dir2 = 3;
						}

						int x3 = x + 2 * dx;
						int y3 = y + 2 * dy;
						int type3 = mapOld.getType(x3, y3);

						if (type2 == Constant.Fog) {
							if (type3 != Constant.Rigid) {
								dirs[dir2] = true;
								none = false;
							}
						} else {
							if (power2 == power) {
								if (life2 == life + 1) {
									dirs[dir2] = true;
									none = false;
								}
							}
						}
					}
					if (none) dirs[0] = true;

					Node node = new Node(x, y, life, power, dirs);
					bombMap[x][y] = node;
				}
			}
		}

		return bombMap;
	}
}
