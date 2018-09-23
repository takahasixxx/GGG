package com.ibm.trl.BBM.mains;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public class StatusHolder implements Serializable {
	private static final long serialVersionUID = -1727998598195786413L;
	int numField;
	int numParam = 100;

	static public class EEE implements Serializable {
		private static final long serialVersionUID = -2353862178915635651L;
		public int x;
		public int y;

		public EEE(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public EEE(EEE e) {
			this.x = e.x;
			this.y = e.y;
		}

		public boolean isSamePosition(EEE e) {
			if (this.x == e.x && this.y == e.y) return true;
			return false;
		}
	}

	static public class AgentEEE extends EEE {
		private static final long serialVersionUID = 4716124278872758663L;
		public int agentID;

		public AgentEEE(int x, int y, int agentID) {
			super(x, y);
			this.agentID = agentID;
		}

		public AgentEEE(AgentEEE a) {
			super(a);
			this.agentID = a.agentID;
		}

		@Override
		public String toString() {
			String line = String.format("AgentEEE : (%2d,%2d), agentID=%2d\n", x, y, agentID);
			return line;
		}
	}

	static public class BombEEE extends EEE {
		private static final long serialVersionUID = -1626810300949537606L;
		public int owner;
		public int life;
		public int dir;
		public int power;

		public BombEEE(int x, int y, int owner, int life, int dir, int power) {
			super(x, y);
			this.owner = owner;
			this.life = life;
			this.dir = dir;
			this.power = power;
		}

		public BombEEE(BombEEE b) {
			super(b);
			this.owner = b.owner;
			this.life = b.life;
			this.dir = b.dir;
			this.power = b.power;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof BombEEE == false) return false;
			BombEEE bbb = (BombEEE) o;
			if (bbb.x == x && bbb.y == y && bbb.owner == owner && bbb.life == life && bbb.dir == dir && bbb.power == power) {
				return true;
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			String line = String.format("BombEEE : (%2d,%2d), onwer=%2d, life=%2d, dir=%2d, power=%2d\n", x, y, owner, life, dir, power);
			return line;
		}
	}

	private Map<Integer, AgentEEE> agMap = new TreeMap<Integer, AgentEEE>();
	private Map<Integer, BombEEE> bbMap = new TreeMap<Integer, BombEEE>();

	public StatusHolder(int numField) {
		this.numField = numField;
	}

	public void setAgent(int x, int y, int agentID) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + agentID * numParam;
		AgentEEE eee = new AgentEEE(x, y, agentID);
		agMap.put(index, eee);
	}

	public boolean isAgentExist(int x, int y, int agent) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + agent * numParam;
		if (agMap.get(index) == null) return false;
		return true;
	}

	public Collection<AgentEEE> getAgentEntry() {
		return agMap.values();
	}

	public void setBomb(int x, int y, int owner, int life, int dir, int power) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam + dir;
		BombEEE eee = new BombEEE(x, y, owner, life, dir, power);
		bbMap.put(index, eee);
	}

	public boolean isBombExist(int x, int y, int life, int dir) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam + dir;
		if (bbMap.get(index) == null) return false;
		return true;
	}

	public int getBombPower(int x, int y, int life, int dir) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam + dir;
		BombEEE eee = bbMap.get(index);
		if (eee == null) return 0;
		return eee.power;
	}

	public Collection<BombEEE> getBombEntry() {
		return bbMap.values();
	}

	@Override
	public String toString() {
		String output = "";

		////////////////////////////////////////////////////////////////////////////////
		//
		// Agentの位置を出力する。
		//
		////////////////////////////////////////////////////////////////////////////////
		output += "========================================\n";
		output += "========================================\n";
		output += "========================================\n";
		output += "Agent\n";
		for (int x = 0; x < numField; x++) {
			String line1 = "";
			String line2 = "";
			String line3 = "";
			for (int y = 0; y < numField; y++) {
				String print1 = "";
				String print2 = "";
				String print3 = "";

				String[] as = new String[4];
				for (int i = 0; i < 4; i++) {
					if (this.isAgentExist(x, y, i + 10)) {
						as[i] = "●";
					} else {
						as[i] = "○";
					}
				}

				print1 = as[0] + "ー" + as[1];
				print2 = "｜＋｜";
				print3 = as[2] + "ー" + as[3];

				line1 += print1;
				line2 += print2;
				line3 += print3;
			}

			output += line1 + "\n";
			output += line2 + "\n";
			output += line3 + "\n";
		}

		////////////////////////////////////////////////////////////////////////////////
		//
		// 爆弾の位置を出力する。
		//
		////////////////////////////////////////////////////////////////////////////////

		for (int life = 9; life >= 1; life--) {
			boolean exist = false;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					for (int moveDirection = 0; moveDirection <= 5; moveDirection++) {
						if (this.isBombExist(x, y, life, moveDirection)) {
							exist = true;
						}
					}
				}
			}
			if (exist == false) continue;

			output += "========================================\n";
			output += "========================================\n";
			output += "========================================\n";
			output += "Bomb life=" + life + "\n";

			for (int x = 0; x < numField; x++) {
				String line1 = "";
				String line2 = "";
				String line3 = "";
				for (int y = 0; y < numField; y++) {
					String print1 = "";
					String print2 = "";
					String print3 = "";

					String[] as = new String[6];
					for (int moveDirection = 0; moveDirection <= 5; moveDirection++) {
						if (this.isBombExist(x, y, life, moveDirection)) {
							int power = this.getBombPower(x, y, life, moveDirection);
							as[moveDirection] = String.format("%d", power);
							if (moveDirection == 0) as[5] = as[0];
							if (moveDirection == 5) as[0] = as[5];
						} else {
							as[moveDirection] = " ";
						}
					}

					print1 = "// " + as[1] + " \\";
					print2 = "||" + as[3] + as[0] + as[4] + "|";
					print3 = "\\\\_" + as[2] + "_/";

					line1 += print1;
					line2 += print2;
					line3 += print3;
				}

				output += line1 + "\n";
				output += line2 + "\n";
				output += line3 + "\n";
			}
		}

		////////////////////////////////////////////////////////////////////////////////
		//
		// FlameCenterの位置を出力する。
		//
		////////////////////////////////////////////////////////////////////////////////
		// output += "========================================\n";
		// output += "========================================\n";
		// output += "========================================\n";
		// output += "FlameCenter\n";
		// for (int x = 0; x < numField; x++) {
		// String line1 = "";
		// String line2 = "";
		// String line3 = "";
		// for (int y = 0; y < numField; y++) {
		// String print1 = "";
		// String print2 = "";
		// String print3 = "";
		//
		// String[] as = new String[4];
		// for (int life = 3; life >= 1; life--) {
		// if (this.isFlameCenterExist(x, y, life)) {
		// int power = this.getFlameCenterPower(x, y, life);
		// as[life] = String.format("%d", power);
		// } else {
		// as[life] = " ";
		// }
		// }
		//
		// print1 = "/3:" + as[3] + " \\";
		// print2 = "|2:" + as[2] + " |";
		// print3 = "\\1:" + as[1] + " /";
		//
		// line1 += print1;
		// line2 += print2;
		// line3 += print3;
		// }
		//
		// output += line1 + "\n";
		// output += line2 + "\n";
		// output += line3 + "\n";
		// }

		return output;
	}
}
