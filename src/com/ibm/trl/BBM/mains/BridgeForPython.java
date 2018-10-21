package com.ibm.trl.BBM.mains;

import java.util.TreeMap;

import ibm.ANACONDA.Core.MyMatrix;

public class BridgeForPython {

	TreeMap<Integer, Agent[]> agentsMap = new TreeMap<Integer, Agent[]>();

	public BridgeForPython() throws Exception {
	}

	private MyMatrix buffer2Matrix(byte[] buffer) {
		java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(buffer);
		int n = buf.getInt(), m = buf.getInt();
		MyMatrix ret = new MyMatrix(n, m);
		for (int i = 0; i < n; ++i) {
			for (int j = 0; j < m; ++j) {
				ret.data[i][j] = buf.getDouble();
			}
		}
		return ret;
	}

	public void init_agent(int pid, int me) {
		try {
			System.out.println("init_agent, " + me);
			Agent[] agents = agentsMap.get(pid);
			if (agents == null) {
				agents = new Agent[4];
				for (int i = 0; i < 4; i++) {
					agents[i] = new Agent(i + 10);
				}
				agentsMap.put(pid, agents);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void episode_end(int pid, int me, int reward) {
		try {
			System.out.println("episode_end, " + me + ", reward = " + reward);
			Agent[] agents = agentsMap.get(pid);
			agents[me - 10].episode_end(reward);
			agents[me - 10] = new Agent(me);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int act(int pid, int me, int x, int y, int ammo, int blast_strength, boolean can_kick, byte[] board_buffer, byte[] bomb_blast_strength_buffer, byte[] bomb_life_buffer, byte[] alive_buffer,
			byte[] enemies_list_buffer) {
		MyMatrix board = buffer2Matrix(board_buffer);
		MyMatrix bomb_blast_strength = buffer2Matrix(bomb_blast_strength_buffer);
		MyMatrix bomb_life = buffer2Matrix(bomb_life_buffer);
		MyMatrix alive = buffer2Matrix(alive_buffer);
		MyMatrix enemies = buffer2Matrix(enemies_list_buffer);

		try {
			// System.out.println("aa");
			Agent[] agents = agentsMap.get(pid);
			return agents[me - 10].act(x, y, ammo, blast_strength, can_kick, board, bomb_blast_strength, bomb_life, alive, enemies);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
}
