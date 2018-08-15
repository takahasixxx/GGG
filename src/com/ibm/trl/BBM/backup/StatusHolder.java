package com.ibm.trl.BBM.backup;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public class StatusHolder {
	int numField;
	// private byte[][][] AG;
	// private byte[][][][] BB;
	// private byte[][][] FC;
	// private byte[][] WB;

	int numParam = 100;

	static class EEE {
		int x;
		int y;
		int param1;
		int param2;
		int value1;
		int value2;

		public EEE(int x, int y, int param1, int param2, int value1, int value2) {
			this.x = x;
			this.y = y;
			this.param1 = param1;
			this.param2 = param2;
			this.value1 = value1;
			this.value2 = value2;
		}

		public EEE(EEE e) {
			this.x = e.x;
			this.y = e.y;
			this.param1 = e.param1;
			this.param2 = e.param2;
			this.value1 = e.value1;
			this.value2 = e.value2;
		}

		public boolean isSamePosition(EEE e) {
			if (this.x == e.x && this.y == e.y) return true;
			return false;
		}

		@Override
		public String toString() {
			String line = String.format("(%2d,%2d), param1=%2d, param2=%2d, value1=%2d, value2=%s2\n", x, y, param1, param2, value1, value2);
			return line;
		}
	}

	private Map<Integer, EEE> agMap = new TreeMap<Integer, EEE>();
	private Map<Integer, EEE> bbMap = new TreeMap<Integer, EEE>();
	private Map<Integer, EEE> fcMap = new TreeMap<Integer, EEE>();
	private Map<Integer, EEE> wbMap = new TreeMap<Integer, EEE>();

	public StatusHolder(int numField) {
		this.numField = numField;
	}

	public void setAgent(int agent, int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + agent * numParam;
		EEE eee = new EEE(x, y, agent, 0, 1, 0);
		agMap.put(index, eee);
	}

	public boolean isAgentExist(int agent, int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + agent * numParam;
		if (agMap.get(index) == null) return false;
		return true;
	}

	public Collection<EEE> getAgentEntry() {
		return agMap.values();
	}

	public void setBomb(int life, int moveDirection, int x, int y, int power) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam + moveDirection;
		EEE eee = new EEE(x, y, life, moveDirection, power, 0);
		bbMap.put(index, eee);
	}

	public boolean isBombExist(int life, int moveDirection, int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam + moveDirection;
		if (bbMap.get(index) == null) return false;
		return true;
	}

	public int getBombPower(int life, int moveDirection, int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam + moveDirection;
		EEE eee = bbMap.get(index);
		if (eee == null) return 0;
		return eee.value1;
	}

	public Collection<EEE> getBombEntry() {
		return bbMap.values();
	}

	public void setFlameCenter(int life, int x, int y, int power) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam;
		EEE eee = new EEE(x, y, life, 0, power, 0);
		fcMap.put(index, eee);
	}

	public boolean isFlameCenterExist(int life, int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam;
		if (fcMap.get(index) == null) return false;
		return true;
	}

	public int getFlameCenterPower(int life, int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam + life * numParam;
		EEE eee = fcMap.get(index);
		if (eee == null) return 0;
		return eee.value1;
	}

	public Collection<EEE> getFlameCenterEntry() {
		return fcMap.values();
	}

	public void setWoodBrake(int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam;
		EEE eee = new EEE(x, y, 0, 0, 1, 0);
		wbMap.put(index, eee);
	}

	public boolean isWoodBrake(int x, int y) {
		int index = x * numParam * numParam * numField + y * numParam * numParam;
		if (wbMap.get(index) == null) return false;
		return true;
	}

	public Collection<EEE> getWoodBrakeEntry() {
		return wbMap.values();
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
					if (this.isAgentExist(i + 10, x, y)) {
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
						if (this.isBombExist(life, moveDirection, x, y)) {
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
						if (this.isBombExist(life, moveDirection, x, y)) {
							int power = this.getBombPower(life, moveDirection, x, y);
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
		output += "========================================\n";
		output += "========================================\n";
		output += "========================================\n";
		output += "FlameCenter\n";
		for (int x = 0; x < numField; x++) {
			String line1 = "";
			String line2 = "";
			String line3 = "";
			for (int y = 0; y < numField; y++) {
				String print1 = "";
				String print2 = "";
				String print3 = "";

				String[] as = new String[4];
				for (int life = 3; life >= 1; life--) {
					if (this.isFlameCenterExist(life, x, y)) {
						int power = this.getFlameCenterPower(life, x, y);
						as[life] = String.format("%d", power);
					} else {
						as[life] = " ";
					}
				}

				print1 = "/3:" + as[3] + " \\";
				print2 = "|2:" + as[2] + " |";
				print3 = "\\1:" + as[1] + " /";

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
		// FlameCenterの位置を出力する。
		//
		////////////////////////////////////////////////////////////////////////////////
		output += "========================================\n";
		output += "========================================\n";
		output += "========================================\n";
		output += "WoodBrake\n";
		for (int x = 0; x < numField; x++) {
			String line1 = "";
			String line2 = "";
			String line3 = "";
			for (int y = 0; y < numField; y++) {
				String print1 = "";
				String print2 = "";
				String print3 = "";

				if (this.isWoodBrake(x, y)) {
					print1 = "■■■";
					print2 = "■■■";
					print3 = "■■■";
				} else {
					print1 = "□□□";
					print2 = "□□□";
					print3 = "□□□";
				}
				line1 += print1;
				line2 += print2;
				line3 += print3;
			}

			output += line1 + "\n";
			output += line2 + "\n";
			output += line3 + "\n";
		}

		return output;
	}
}
