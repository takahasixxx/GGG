package com.ibm.trl.BBM.Simulator;

public class Item extends Obj {
	int x, y;
	int life = 2;
	Agent bommer;

	public Item(int x, int y, Agent bommer) {
		this.x = x;
		this.y = y;
		this.bommer = bommer;
	}
}
