package com.ibm.trl.BBM.Simulator;

public class Agent extends Obj {
	boolean isAlive = true;
	int x, y;
	int ammo = 1;

	public Agent(int x, int y) {
		this.x = x;
		this.y = y;
	}
}
