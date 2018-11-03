package com.ibm.trl.BBM.mains;

import ibm.ANACONDA.Core.MyMatrix;

public class MapInformation {

	static final int numField = GlobalParameter.numField;

	public MyMatrix board;
	public MyMatrix power;
	public MyMatrix life;

	public MapInformation(MyMatrix board, MyMatrix bomb_blast_strength, MyMatrix bomb_life) {
		this.board = board;
		this.power = bomb_blast_strength;
		this.life = bomb_life;
	}

	public int getType(int x, int y) {
		if (x >= 0 && x < numField && y >= 0 && y < numField) return (int) board.data[x][y];
		return Constant.Rigid;
	}

	public int getLife(int x, int y) {
		if (x >= 0 && x < numField && y >= 0 && y < numField) return (int) life.data[x][y];
		return 0;
	}

	public int getPower(int x, int y) {
		if (x >= 0 && x < numField && y >= 0 && y < numField) return (int) power.data[x][y];
		return 0;
	}
}
