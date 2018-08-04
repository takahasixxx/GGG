package com.ibm.trl.BBM.Simulator;

import java.util.ArrayList;
import java.util.List;

public class Simulator {
	int numb = 11;

	Panel[][] board = new Panel[numb][numb];

	Agent[][] agentMap = new Agent[numb][numb];
	Agent[] agents;

	Item[][] ItemMap = new Item[numb][numb];
	List<Item> items = new ArrayList<Item>();

	Bomb[][] BombMap = new Bomb[numb][numb];
	List<Bomb> bombs = new ArrayList<Bomb>();

	Flame[][] FlameMap = new Flame[numb][numb];
	List<Flame> flames = new ArrayList<Flame>();

	public void step() throws Exception {

	}

}
