package com.ibm.trl.BBM.mains;

public class StatusHolder {
	private int numField;
	private byte[][][] AG;
	private byte[][][][] BB;
	private byte[][][] FC;
	private byte[][] WB;

	public StatusHolder(int numField) {
		this.numField = numField;
		AG = new byte[4][numField][numField];
		BB = new byte[10][6][numField][numField];
		FC = new byte[4][numField][numField];
		WB = new byte[numField][numField];
	}

	public void setAgent(int agent, int x, int y) {
		AG[agent - 10][x][y] = 1;
	}

	public boolean isAgentExist(int agent, int x, int y) {
		if (AG[agent - 10][x][y] == 1) return true;
		else return false;
	}

	public void setBomb(int life, int moveDirection, int x, int y, int power) {
		BB[life][moveDirection][x][y] = (byte) Math.max(BB[life][moveDirection][x][y], power);
	}

	public boolean isBombExist(int life, int moveDirection, int x, int y) {
		int power = getBombPower(life, moveDirection, x, y);
		if (power == 0) return false;
		else return true;
	}

	public int getBombPower(int life, int moveDirection, int x, int y) {
		return BB[life][moveDirection][x][y];
	}

	public void setFlameCenter(int life, int x, int y, int power) {
		FC[life][x][y] = (byte) Math.max(FC[life][x][y], power);
	}

	public boolean isFlameCenterExist(int life, int x, int y) {
		int power = getFlameCenterPower(life, x, y);
		if (power == 0) return false;
		else return true;
	}

	public int getFlameCenterPower(int life, int x, int y) {
		return FC[life][x][y];
	}

	public void setWoodBrake(int x, int y) {
		WB[x][y] = 1;
	}

	public boolean isWoodBrake(int x, int y) {
		if (WB[x][y] == 1) return true;
		else return false;
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
					for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
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
					for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
						if (this.isBombExist(life, moveDirection, x, y)) {
							int power = this.getBombPower(life, moveDirection, x, y);
							as[moveDirection] = String.format("%d", power);
						} else {
							as[moveDirection] = " ";
						}
					}

					print1 = "// " + as[1] + " \\";
					print2 = "||" + as[3] + as[5] + as[4] + "|";
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
