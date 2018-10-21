package com.ibm.trl.BBM.mains;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ibm.ANACONDA.Core.MyMatrix;

public class StatusHolder implements Serializable {
	private static final long serialVersionUID = -1727998598195786413L;
	private static final int numField = GlobalParameter.numField;

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
		public boolean equals(Object obj) {
			if (obj instanceof AgentEEE == false) return false;
			AgentEEE aaa = (AgentEEE) obj;
			if (agentID == aaa.agentID && aaa.x == x && aaa.y == y) {
				return true;
			} else {
				return false;
			}
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
		public boolean equals(Object obj) {
			if (obj instanceof BombEEE == false) return false;
			BombEEE bbb = (BombEEE) obj;
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

	private List<AgentEEE> agentList = new ArrayList<AgentEEE>();
	private AgentEEE[] agents = new AgentEEE[4];

	private List<BombEEE> bombList = new ArrayList<BombEEE>();
	private BombEEE[][] bombMap = new BombEEE[numField][numField];

	public StatusHolder() {
	}

	public StatusHolder(StatusHolder sh) {
		for (AgentEEE aaa : sh.agentList) {
			this.setAgent(aaa.x, aaa.y, aaa.agentID);
		}

		for (BombEEE bbb : sh.bombList) {
			this.setBomb(bbb.x, bbb.y, bbb.owner, bbb.life, bbb.dir, bbb.power);
		}
	}

	public void setAgent(int x, int y, int agentID) {
		AgentEEE aaa = new AgentEEE(x, y, agentID);
		agents[agentID - 10] = aaa;
		agentList.add(aaa);
	}

	public void removeAgent(int agentID) {
		AgentEEE aaa = agents[agentID - 10];
		if (aaa == null) return;
		agents[agentID - 10] = null;
		agentList.remove(aaa);
	}

	public Collection<AgentEEE> getAgentEntry() {
		return agentList;
	}

	public AgentEEE getAgent(int agentID) {
		return agents[agentID - 10];
	}

	public void setBomb(int x, int y, int owner, int life, int dir, int power) {
		BombEEE eee = new BombEEE(x, y, owner, life, dir, power);
		bombMap[x][y] = eee;
		bombList.add(eee);
	}

	public BombEEE getBomb(int x, int y) {
		return bombMap[x][y];
	}

	public Collection<BombEEE> getBombEntry() {
		return bombList;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof StatusHolder == false) return false;
		StatusHolder sh = (StatusHolder) obj;

		if (agentList.size() != sh.agentList.size()) return false;
		if (bombList.size() != sh.bombList.size()) return false;

		for (int ai = 0; ai < 4; ai++) {
			if (agents[ai] == null && sh.agents[ai] == null) continue;
			if (agents[ai] == null && sh.agents[ai] != null) return false;
			if (agents[ai] != null && sh.agents[ai] == null) return false;
			if (agents[ai].equals(sh.agents[ai]) == false) return false;
		}

		for (BombEEE bbb : bombList) {
			boolean find = false;
			for (BombEEE bbb2 : sh.bombList) {
				if (bbb.equals(bbb2)) {
					find = true;
					break;
				}
			}
			if (find == false) return false;
		}

		return true;
	}

	@Override
	public String toString() {
		String output = "";

		////////////////////////////////////////////////////////////////////////////////
		//
		// Agent‚ÌˆÊ’u‚ðo—Í‚·‚éB
		//
		////////////////////////////////////////////////////////////////////////////////
		output += "========================================\n";
		output += "========================================\n";
		output += "========================================\n";
		output += "Agent\n";

		MyMatrix temp = new MyMatrix(numField, numField);
		for (int ai = 0; ai < 4; ai++) {
			AgentEEE aaa = agents[ai];
			if (aaa == null) continue;
			temp.data[aaa.x][aaa.y] = aaa.agentID;
		}

		for (int x = 0; x < numField; x++) {
			String line1 = "";
			for (int y = 0; y < numField; y++) {
				int id = (int) temp.data[x][y];
				String moji = "[";
				if (id == 10) {
					moji = "‡@";
				} else if (id == 11) {
					moji = "‡A";
				} else if (id == 12) {
					moji = "‡B";
				} else if (id == 13) {
					moji = "‡C";
				}
				line1 += moji;
			}

			output += line1 + "\n";
		}

		////////////////////////////////////////////////////////////////////////////////
		//
		// ”š’e‚ÌˆÊ’u‚ðo—Í‚·‚éB
		//
		////////////////////////////////////////////////////////////////////////////////

		return output;
	}
}
