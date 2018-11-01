package com.ibm.trl.BBM.mains;

import java.util.TreeMap;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;

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
			double time = 1.0 * timeTotal / numcall;
			System.out.println("episode_end, " + me + ", reward = " + reward + ", pid=" + pid + ", timeAverage=" + time + ", timeMax=" + timeMax);
			Agent[] agents = agentsMap.get(pid);
			agents[me - 10].episode_end(reward);
			agents[me - 10] = new Agent(me);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static long timeMax = 0;
	static long timeTotal = 0;
	static int numcall = 0;

	public int act(int pid, int me, int x, int y, int ammo, int blast_strength, boolean can_kick, byte[] board_buffer, byte[] bomb_blast_strength_buffer, byte[] bomb_life_buffer, byte[] alive_buffer,
			byte[] enemies_list_buffer) {

		try {
			long timeStart = System.currentTimeMillis();
			MyMatrix board = buffer2Matrix(board_buffer);
			MyMatrix bomb_blast_strength = buffer2Matrix(bomb_blast_strength_buffer);
			MyMatrix bomb_life = buffer2Matrix(bomb_life_buffer);
			MyMatrix alive = buffer2Matrix(alive_buffer);
			MyMatrix enemies = buffer2Matrix(enemies_list_buffer);
			Agent[] agents = agentsMap.get(pid);
			int action = agents[me - 10].act(x, y, ammo, blast_strength, can_kick, board, bomb_blast_strength, bomb_life, alive, enemies);
			long timeEnd = System.currentTimeMillis();
			long timeDel = timeEnd - timeStart;
			timeTotal += timeDel;
			numcall++;
			if (numcall > 100 && timeDel > timeMax) timeMax = timeDel;
			System.out.println("timeDel = " + timeDel);
			return action;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	int counter = 0;
	Pack packNow = null;
	int[] actions = new int[4];
	ForwardModel fm = new ForwardModel();

	public void check_env(int flag, byte[] b1, byte[] b2, byte[] b3, byte[] b4, byte[] b5, byte[] b6, byte[] b7) {
		System.out.println("check_env");
		MyMatrix board = buffer2Matrix(b1);
		MyMatrix life = buffer2Matrix(b2);
		MyMatrix power = buffer2Matrix(b3);
		MyMatrix owner = buffer2Matrix(b4);
		MyMatrix dir = buffer2Matrix(b5);
		MyMatrix flame = buffer2Matrix(b6);
		MyMatrix info = buffer2Matrix(b7);
		counter++;

		Ability[] abs = new Ability[4];
		for (int ai = 0; ai < 4; ai++) {
			abs[ai] = new Ability();
			abs[ai].isAlive = info.data[ai][0] == 1;
			abs[ai].numBombHold = (int) info.data[ai][3];
			abs[ai].numMaxBomb = 100;
			abs[ai].strength = (int) info.data[ai][4];
			abs[ai].strength_fix = (int) info.data[ai][4];
			abs[ai].kick = info.data[ai][5] == 1;
		}

		StatusHolder sh = new StatusHolder();
		for (int ai = 0; ai < 4; ai++) {
			if (info.data[ai][0] == 0) continue;
			int x = (int) info.data[ai][1];
			int y = (int) info.data[ai][2];
			sh.setAgent(x, y, ai + 10);
		}

		for (int x = 0; x < 11; x++) {
			for (int y = 0; y < 11; y++) {
				int lll = (int) life.data[x][y];
				if (lll == 0) continue;
				int ppp = (int) power.data[x][y];
				int ooo = (int) owner.data[x][y];
				int ddd = (int) dir.data[x][y];
				sh.setBomb(x, y, ooo, lll, ddd, ppp);
			}
		}

		Pack pack = new Pack(board, flame, abs, sh);

		if (flag == 0) {
			packNow = pack;
		} else if (flag == 1) {
			try {
				Pack packNextAns = pack;
				Pack packNext = fm.Step(packNow, actions);

				// アイテム取得時の変化が表現できずに違いがでるため。とりあえず。
				// packNext.abs = packNextAns.abs;

				for (int ai = 0; ai < 4; ai++) {
					packNext.abs[ai].numMaxBomb = 100;
					if (packNext.abs[ai].strength > 4) packNext.abs[ai].strength = 4;
				}

				for (int x = 0; x < 11; x++) {
					for (int y = 0; y < 11; y++) {
						int type = (int) packNext.board.data[x][y];
						if (Constant.isItem(type)) {
							packNext.board.data[x][y] = Constant.Passage;
						}
					}
				}

				for (int x = 0; x < 11; x++) {
					for (int y = 0; y < 11; y++) {
						int type = (int) packNextAns.board.data[x][y];
						if (Constant.isItem(type)) {
							packNextAns.board.data[x][y] = Constant.Passage;
						}
					}
				}

				if (packNext.equals(packNextAns) == false) {

					boolean fff = packNext.equals(packNextAns);
					System.out.println(fff);

					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("packNow");
					System.out.println(packNow.toString());
					System.out.println("=============================================================");
					System.out.println("packNextAns");
					System.out.println(packNextAns.toString());
					System.out.println("=============================================================");
					System.out.println("packNext");
					System.out.println(packNext.toString());
					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("=============================================================");
					System.out.println("=============================================================");

				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void check_actions(byte[] b1) {
		System.out.println("check_env");
		MyMatrix actions = buffer2Matrix(b1);
		for (int ai = 0; ai < 4; ai++) {
			this.actions[ai] = (int) actions.data[ai][0];
		}
	}
}
