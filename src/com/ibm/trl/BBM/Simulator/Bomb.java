package com.ibm.trl.BBM.Simulator;

public class Bomb extends Obj {
	int x, y;
	int life = 2;
	Agent bommer;

	public Bomb(int x, int y, Agent bommer) {
		this.x = x;
		this.y = y;
		this.bommer = bommer;
	}
}
