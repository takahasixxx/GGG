package com.ibm.trl.BBM.mains;

import java.util.List;

public class BombTracker {

	static boolean verbose = GlobalParameter.verbose;
	static int numField = GlobalParameter.numField;

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

	static Node[][] computeBombMap(MapInformation map, List<MapInformation> mapsOld) throws Exception {

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
						int type3 = mapOld.getLife(x3, y3);

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
