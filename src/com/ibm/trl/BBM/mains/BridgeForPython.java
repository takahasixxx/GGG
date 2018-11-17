/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.ModelParameter;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;

import ibm.ANACONDA.Core.MyMatrix;

public class BridgeForPython {

	static TreeMap<Integer, Game> gameMap = new TreeMap<Integer, Game>();
	static int gameCounter = 0;
	static Game[] gamesFinished = new Game[100000];

	class Game {
		int gameID = -1;
		ModelParameter param;
		Agent[] agents = new Agent[4];
		int[] rewards = null;

		public Game(int gameID, ModelParameter param) throws Exception {
			this.gameID = gameID;
			this.param = param;
		}
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

	/************************************************************************************************
	 * 
	 *
	 * 
	 * 
	 * 
	 * 勝敗管理関連。 パラメータグリッドサーチ関連
	 * 
	 * 
	 * 
	 * 
	 * 
	 *************************************************************************************************/

	int onePack = 400;

	public void start_game(int pid) {
		try {
			System.out.println("BridgeForPython, start_game, pid=" + pid);

			List<ModelParameter> params = new ArrayList<ModelParameter>();
			if (true) {
				double usualCell = 3.5;
				double attackCell = 2.5;
				// for (double rateLevel : new double[] { 1.6 }) {
				for (double rateLevel : new double[] { 1.25 }) {
					// for (double rateLevel : new double[] { 1.2, 1.4 }) {
					// for (double rateLevel : new double[] { 1.8, 1.9 }) {
					// for (double rateLevel : new double[] { 1.8, 1.9 }) {
					// for (double gainOffset : new double[] { 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8 }) {
					for (double gainOffset : new double[] { 1.0 }) {

						ModelParameter param = new ModelParameter();
						param.rateLevel = rateLevel;
						param.gainOffset = gainOffset;
						param.usualThreshold = usualCell;
						param.attackThreshold = attackCell;
						params.add(param);
					}
				}
			}

			synchronized (gameMap) {
				ModelParameter param = params.get(gameCounter / onePack % params.size());

				Game game = new Game(gameCounter, param);
				gameMap.put(pid, game);
				gameCounter++;
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void finish_game(int pid, int r1, int r2, int r3, int r4) {
		try {
			System.out.println("BridgeForPython, finish_game, pid=" + pid);

			// 結果を過去ログに移す。
			Game game;
			synchronized (gameMap) {
				game = gameMap.get(pid);
				gameMap.remove(pid);
			}

			// TODO
			if (true) {
				Agent[] agents = game.agents;
				String line = String.format("BridgeForPython, finish_game, gameID, %d, reward, %d, %d, %d, %d, lastframe, %d, %d", game.gameID, r1, r2, r3, r4, agents[1].frame, agents[3].frame);
				System.out.println(line);
			}

			game.agents = null;
			game.rewards = new int[] { r1, r2, r3, r4 };
			gamesFinished[game.gameID] = game;

			// onePackひとかたまりで、全部埋まっていたら、そこの性能を出力する。
			for (int i = 0; i * onePack < gamesFinished.length; i++) {
				int win = 0;
				int lose = 0;
				int tie = 0;
				ModelParameter param = null;
				for (int p = 0; p < onePack; p++) {
					int index = i * onePack + p;
					if (index >= gamesFinished.length) break;
					if (gamesFinished[index] == null) continue;
					param = gamesFinished[index].param;
					int[] rewards = gamesFinished[index].rewards;
					if (rewards[0] == 1) {
						lose++;
					} else if (rewards[1] == 1) {
						win++;
					} else if (rewards[0] == -1 && rewards[1] == -1) {
						tie++;
					}
				}
				int total = win + lose + tie;
				if (total == 0) continue;

				double totalRate = 1.0 * total / total;
				double winRate = 1.0 * win / total;
				double loseRate = 1.0 * lose / total;
				double tieRate = 1.0 * tie / total;

				String line = String.format("finish_game, shot=%d, %s, (total/win/lose/tie) = (%3d, %3d, %3d, %3d) = (%f, %f, %f, %f)", i, param.toString(), total, win, lose, tie, totalRate, winRate,
						loseRate, tieRate);
				System.out.println(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void changed() {
		System.out.println("changed");
	}

	/************************************************************************************************
	 * 
	 *
	 * 
	 * 
	 * 
	 * Pythonのエージェントオブジェクトと紐付いている関数群
	 * 
	 * 
	 * 
	 * 
	 * 
	 *************************************************************************************************/
	public void init_agent(int pid, int caller_id, int me) {
		try {
			System.out.println("BridgeForPython, init_agent, pid=" + pid + ", caller_id=" + caller_id + ", agent_id=" + me);

			synchronized (gameMap) {
				Game game = gameMap.get(pid);
				if (game == null) {
					ModelParameter param = new ModelParameter();
					game = new Game(gameCounter, param);
					gameMap.put(pid, game);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void episode_end(int pid, int caller_id, int me, int reward) {
		try {
			System.out.println("BridgeForPython, episode_end, pid=" + pid + ", caller_id=" + caller_id + ", agent_id=" + me + ", reward = " + reward);

			double timeAverage = 1.0 * timeTotal / numcall;
			System.out.println("episode_end, pid=" + pid + ", caller_id=" + caller_id + ", agent_id=" + me + ", timeAverage=" + timeAverage + ", timeMax=" + timeMax);

			Agent agent;
			synchronized (gameMap) {
				Game game = gameMap.get(pid);
				agent = game.agents[me - 10];
			}

			agent.episode_end(reward);

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	static long timeMax = 0;
	static long timeTotal = 0;
	static int numcall = 0;

	public int act(int pid, int caller_id, int me, int x, int y, int ammo, int blast_strength, boolean can_kick, byte[] board_buffer, byte[] bomb_blast_strength_buffer, byte[] bomb_life_buffer,
			byte[] alive_buffer, byte[] enemies_list_buffer, int friend, boolean isCollapse) {
		try {
			System.out.println("BridgeForPython, act start, pid=" + pid + ", caller_id=" + caller_id + ", agent_id=" + me);

			long timeStart = System.currentTimeMillis();

			MyMatrix board = buffer2Matrix(board_buffer);
			MyMatrix bomb_blast_strength = buffer2Matrix(bomb_blast_strength_buffer);
			MyMatrix bomb_life = buffer2Matrix(bomb_life_buffer);
			MyMatrix alive = buffer2Matrix(alive_buffer);
			MyMatrix enemies = buffer2Matrix(enemies_list_buffer);

			Agent agent;
			synchronized (gameMap) {
				Game game = gameMap.get(pid);
				agent = game.agents[me - 10];
				if (agent == null) {
					agent = new Agent(me, game.param);
					game.agents[me - 10] = agent;
				}
			}

			int action = agent.act(x, y, ammo, blast_strength, can_kick, board, bomb_blast_strength, bomb_life, alive, enemies, friend, isCollapse);

			long timeEnd = System.currentTimeMillis();
			long timeDel = timeEnd - timeStart;
			timeTotal += timeDel;
			numcall++;
			if (numcall > 100 && timeDel > timeMax) timeMax = timeDel;
			System.out.println("timeDel = " + timeDel);
			System.out.println("BridgeForPython, act end__, pid=" + pid + ", caller_id=" + caller_id + ", agent_id=" + me);
			return action;
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		return 0;
	}

	/************************************************************************************************
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 *
	 * 
	 * 
	 * 
	 * ForwardModelのデバッグ用
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 *************************************************************************************************/

	int counter = 0;
	Pack packNow = null;
	int[] actions = new int[4];
	ForwardModel fm = new ForwardModel();

	public int check_env(int flag, byte[] b1, byte[] b2, byte[] b3, byte[] b4, byte[] b5, byte[] b6, byte[] b7) {
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
				// TODO collapseをちゃんとやるか？
				Pack packNext = fm.Step(false, 0, packNow, actions);

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
		return 0;
	}

	public int check_actions(byte[] b1) {
		System.out.println("check_env");
		MyMatrix actions = buffer2Matrix(b1);
		for (int ai = 0; ai < 4; ai++) {
			this.actions[ai] = (int) actions.data[ai][0];
		}
		return 0;
	}
}
