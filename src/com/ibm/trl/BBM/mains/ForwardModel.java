/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;

import ibm.ANACONDA.Core.MyMatrix;

public class ForwardModel {

	private static final int numField = GlobalParameter.numField;

	static public class Pack implements Serializable {
		private static final long serialVersionUID = -8052436421835761684L;
		MyMatrix board;
		MyMatrix flameLife;
		Ability[] abs;
		StatusHolder sh;

		public Pack(MyMatrix board, MyMatrix flameLife, Ability[] abs, StatusHolder sh) {
			this.board = board;
			this.flameLife = flameLife;
			this.abs = abs;
			this.sh = sh;
		}

		public Pack(Pack pack) {
			this.board = new MyMatrix(pack.board);
			this.flameLife = new MyMatrix(pack.flameLife);
			this.abs = new Ability[4];
			for (int ai = 0; ai < 4; ai++) {
				abs[ai] = new Ability(pack.abs[ai]);
			}
			this.sh = new StatusHolder(pack.sh);
		}

		public void removeAgent(int agentID) {
			AgentEEE aaa = sh.getAgent(agentID);
			if (aaa == null) return;
			BombEEE bbb = sh.getBomb(aaa.x, aaa.y);
			if (bbb == null) {
				board.data[aaa.x][aaa.y] = Constant.Passage;
			} else {
				board.data[aaa.x][aaa.y] = Constant.Bomb;
			}
			sh.removeAgent(agentID);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Pack == false) return false;
			Pack pack = (Pack) obj;

			for (int ai = 0; ai < 4; ai++) {
				if (abs[ai].equals(pack.abs[ai]) == false) return false;
			}

			if (sh.equals(pack.sh) == false) return false;

			try {
				if (board.minus(pack.board).normF() > 0) return false;
				if (flameLife.minus(pack.flameLife).normF() > 0) return false;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}

			return true;
		}

		@Override
		public String toString() {
			MyMatrix life = new MyMatrix(numField, numField);
			MyMatrix power = new MyMatrix(numField, numField);
			for (BombEEE bbb : sh.getBombEntry()) {
				life.data[bbb.x][bbb.y] = bbb.life;
				power.data[bbb.x][bbb.y] = bbb.power;
			}
			String text = "";
			text += "===================================\n";
			text += "===================================\n";
			text += BBMUtility.printBoard2_str(board, board, life, power);
			text += "===================================\n";
			text += "===================================\n";
			return text;
		}
	}

	public Pack Step(boolean collapse, int frame, Pack pack, int[] actions) throws Exception {
		return Step(collapse, frame, pack.board, pack.flameLife, pack.abs, pack.sh, actions);
	}

	public Pack Step(boolean collapse, int frame, MyMatrix boardNow, MyMatrix flameLifeNow, Ability absNow[], StatusHolder shNow, int[] actions) throws Exception {

		MyMatrix boardNext = new MyMatrix(boardNow);

		Ability[] absNext = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNext[i] = new Ability(absNow[i]);
		}

		boolean[][] bombExistingMap = new boolean[numField][numField];
		for (EEE bbb : shNow.getBombEntry()) {
			bombExistingMap[bbb.x][bbb.y] = true;
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// FlameCenterの時刻を進める。
		/////////////////////////////////////////////////////////////////////////////////////

		MyMatrix flameLifeNext = new MyMatrix(flameLifeNow);
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				if (flameLifeNext.data[x][y] >= 1) {
					flameLifeNext.data[x][y]--;
				}
			}
		}

		// 古いFlameCenterをレンダリングすると、フレーム終端が木だったのかが分からない。BoardNowとMyFlameNextの共通部分が今残っているFlameになる。その処理をする。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				if (flameLifeNext.data[x][y] == 0) {
					// myFlame側でFlameが消滅したのに、board側でFlamesが残っていたら、boardのFlamesをPassageにする。
					if (boardNext.data[x][y] == Constant.Flames) {
						boardNext.data[x][y] = Constant.Passage;
					}
				} else {
					// myFlame側でFlameが発生してるのに、board側でFlamesが消えていたら、myFlame側をクリアする。
					if (boardNext.data[x][y] != Constant.Flames) {
						flameLifeNext.data[x][y] = 0;
						// TODO ここは到達し得ない？
						System.out.println("Forward Model: error??");
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// AgentとBombの移動の処理。
		/////////////////////////////////////////////////////////////////////////////////////
		AgentEEE[] agentsNow = new AgentEEE[4];
		AgentEEE[] agentsNext = new AgentEEE[4];
		List<BombEEE> added = new ArrayList<BombEEE>();
		for (AgentEEE aaaNow : shNow.getAgentEntry()) {
			int agentID = aaaNow.agentID;
			int agentIndex = agentID - 10;
			int action = actions[agentIndex];

			boardNext.data[aaaNow.x][aaaNow.y] = Constant.Passage;

			int x2 = aaaNow.x;
			int y2 = aaaNow.y;
			if (action == 0) {
			} else if (action == 1) {
				x2 -= 1;
			} else if (action == 2) {
				x2 += 1;
			} else if (action == 3) {
				y2 -= 1;
			} else if (action == 4) {
				y2 += 1;
			} else if (action == 5) {
				if (bombExistingMap[x2][y2] == false) {
					if (absNext[agentIndex].numBombHold > 0) {
						// 爆弾を追加。
						added.add(new BombEEE(aaaNow.x, aaaNow.y, agentID, 10, 0, absNext[agentIndex].strength));
						absNext[agentIndex].numBombHold--;
					}
				}
			}
			if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
				x2 = aaaNow.x;
				y2 = aaaNow.y;
			} else {
				int type = (int) boardNext.data[x2][y2];
				if (Constant.isWall(type)) {
					x2 = aaaNow.x;
					y2 = aaaNow.y;
				}
			}

			agentsNow[agentIndex] = aaaNow;
			agentsNext[agentIndex] = new AgentEEE(x2, y2, aaaNow.agentID);
		}

		BombEEE[] bombsNow;
		BombEEE[] bombsNext;
		int numBomb;
		{
			Collection<BombEEE> eees = shNow.getBombEntry();
			numBomb = eees.size();
			bombsNow = new BombEEE[numBomb];
			bombsNext = new BombEEE[numBomb];

			int index = 0;
			for (BombEEE bbbNow : eees) {

				boardNext.data[bbbNow.x][bbbNow.y] = Constant.Passage;

				bombsNow[index] = bbbNow;
				int life = bbbNow.life;
				int dir = bbbNow.dir;
				int x2 = bbbNow.x;
				int y2 = bbbNow.y;
				if (dir == 1) {
					x2--;
				} else if (dir == 2) {
					x2++;
				} else if (dir == 3) {
					y2--;
				} else if (dir == 4) {
					y2++;
				}
				if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
					x2 = bbbNow.x;
					y2 = bbbNow.y;
				} else {
					int type = (int) boardNext.data[x2][y2];
					if (Constant.isWall(type) || Constant.isItem(type)) {
						x2 = bbbNow.x;
						y2 = bbbNow.y;
					}
				}
				bombsNext[index] = new BombEEE(x2, y2, bbbNow.owner, life - 1, dir, bbbNow.power);
				index++;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// 新ステップで新たに配置された爆弾を追加する。
		/////////////////////////////////////////////////////////////////////////////////////
		if (true) {
			BombEEE[] bombsNowAdded = new BombEEE[numBomb + added.size()];
			BombEEE[] bombsNextAdded = new BombEEE[numBomb + added.size()];
			for (int i = 0; i < numBomb; i++) {
				bombsNowAdded[i] = bombsNow[i];
				bombsNextAdded[i] = bombsNext[i];
			}
			for (int i = 0; i < added.size(); i++) {
				bombsNowAdded[numBomb + i] = added.get(i);
				bombsNextAdded[numBomb + i] = new BombEEE(added.get(i));
				bombsNextAdded[numBomb + i].life--;
			}

			numBomb = numBomb + added.size();
			bombsNow = bombsNowAdded;
			bombsNext = bombsNextAdded;
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// AgentとBombがクロスしてたら引き戻す。
		/////////////////////////////////////////////////////////////////////////////////////
		if (true) {
			// Agent同士でクロスする場合
			boolean[] backAgent = new boolean[4];
			for (int ai = 0; ai < 4; ai++) {
				if (absNow[ai].isAlive == false) continue;
				if (agentsNow[ai] == null) continue;
				for (int aj = ai + 1; aj < 4; aj++) {
					if (absNow[aj].isAlive == false) continue;
					if (agentsNow[aj] == null) continue;
					EEE eee1Now = agentsNow[ai];
					EEE eee2Now = agentsNow[aj];
					EEE eee1Next = agentsNext[ai];
					EEE eee2Next = agentsNext[aj];
					if (eee1Now.isSamePosition(eee2Next) && eee2Now.isSamePosition(eee1Next)) {
						backAgent[ai] = true;
						backAgent[aj] = true;
					}
				}
			}

			// Bomb同士でクロスする場合。
			boolean[] backBomb = new boolean[numBomb];
			for (int bi = 0; bi < numBomb; bi++) {
				for (int bj = bi + 1; bj < numBomb; bj++) {
					EEE eee1Now = bombsNow[bi];
					EEE eee2Now = bombsNow[bj];
					EEE eee1Next = bombsNext[bi];
					EEE eee2Next = bombsNext[bj];
					if (eee1Now.isSamePosition(eee2Next) && eee2Now.isSamePosition(eee1Next)) {
						backBomb[bi] = true;
						backBomb[bj] = true;
					}
				}
			}

			// AgentとBombでクロスする場合。Agentは引戻さない。
			for (int ai = 0; ai < 4; ai++) {
				if (absNow[ai].isAlive == false) continue;
				if (agentsNow[ai] == null) continue;
				for (int j = 0; j < numBomb; j++) {
					EEE eee1Now = agentsNow[ai];
					EEE eee1Next = agentsNext[ai];
					EEE eee2Now = bombsNow[j];
					EEE eee2Next = bombsNext[j];
					if (eee1Now.isSamePosition(eee2Next) && eee2Now.isSamePosition(eee1Next)) {
						backBomb[j] = true;
					}
				}
			}

			// 引き戻す必要があるやつは、位置を引き戻す。
			for (int ai = 0; ai < 4; ai++) {
				if (absNow[ai].isAlive == false) continue;
				if (agentsNow[ai] == null) continue;
				if (backAgent[ai]) {
					agentsNext[ai].x = agentsNow[ai].x;
					agentsNext[ai].y = agentsNow[ai].y;
				}
			}
			for (int bi = 0; bi < numBomb; bi++) {
				if (backBomb[bi]) {
					bombsNext[bi].x = bombsNow[bi].x;
					bombsNext[bi].y = bombsNow[bi].y;
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// AgentとBombが同じ場所に向かおうとしてたら引き戻す。
		/////////////////////////////////////////////////////////////////////////////////////
		MyMatrix occupancyAgent = new MyMatrix(numField, numField);
		MyMatrix occupancyBomb = new MyMatrix(numField, numField);
		{
			for (EEE aaaNext : agentsNext) {
				if (aaaNext == null) continue;
				occupancyAgent.data[aaaNext.x][aaaNext.y]++;
			}

			for (EEE bbbNext : bombsNext) {
				occupancyBomb.data[bbbNext.x][bbbNext.y]++;
			}

			while (true) {
				boolean isChanged = false;

				for (int ai = 0; ai < 4; ai++) {
					if (absNow[ai].isAlive == false) continue;
					if (agentsNow[ai] == null) continue;
					EEE aaaNow = agentsNow[ai];
					EEE aaaNext = agentsNext[ai];
					if (aaaNext.isSamePosition(aaaNow)) continue;
					if (occupancyAgent.data[aaaNext.x][aaaNext.y] > 1 || occupancyBomb.data[aaaNext.x][aaaNext.y] > 1) {
						aaaNext.x = aaaNow.x;
						aaaNext.y = aaaNow.y;
						occupancyAgent.data[aaaNext.x][aaaNext.y]++;
						isChanged = true;
					}
				}

				for (int bi = 0; bi < numBomb; bi++) {
					EEE bbbNow = bombsNow[bi];
					EEE bbbNext = bombsNext[bi];
					if (bbbNext.x == bbbNow.x && bbbNext.y == bbbNow.y) continue;
					if (occupancyAgent.data[bbbNext.x][bbbNext.y] > 1 || occupancyBomb.data[bbbNext.x][bbbNext.y] > 1) {
						bbbNext.x = bbbNow.x;
						bbbNext.y = bbbNow.y;
						occupancyBomb.data[bbbNext.x][bbbNext.y]++;
						isChanged = true;
					}
				}

				if (isChanged == false) break;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// 衝突が発生しているAgentとBombの処理をする。
		/////////////////////////////////////////////////////////////////////////////////////
		AgentEEE[] agentsNext2 = new AgentEEE[4];
		BombEEE[] bombsNext2 = new BombEEE[numBomb];

		int[] kicked_bomb_indexed_by_agent = new int[4];
		for (int ai = 0; ai < 4; ai++) {
			kicked_bomb_indexed_by_agent[ai] = -1;
		}

		int[] agent_indexed_by_kicked_bomb = new int[numBomb];
		for (int bi = 0; bi < numBomb; bi++) {
			agent_indexed_by_kicked_bomb[bi] = -1;
		}

		for (int bi = 0; bi < numBomb; bi++) {
			BombEEE bbbNow = bombsNow[bi];
			BombEEE bbbNext = bombsNext[bi];

			// 移動したい場所にAgentがいない。問題なく移動できる。
			if (occupancyAgent.data[bbbNext.x][bbbNext.y] == 0) continue;

			// 衝突相手のエージェントを探す。
			int agentIndex = -1;
			for (int aj = 0; aj < 4; aj++) {
				if (absNow[aj].isAlive == false) continue;
				if (agentsNow[aj] == null) continue;
				if (bbbNext.isSamePosition(agentsNext[aj])) {
					agentIndex = aj;
					break;
				}
			}
			if (agentIndex == -1) continue;
			AgentEEE aaaNow = agentsNow[agentIndex];
			AgentEEE aaaNext = agentsNext[agentIndex];

			// エージェントが動いていない場合。「爆弾を新規設置してから動いていないケース」「爆弾が動いているケース」しかない。
			if (aaaNow.isSamePosition(aaaNext)) {
				if (bbbNow.isSamePosition(bbbNext) == false) {
					// 爆弾が動いてる場合、止める。
					bombsNext2[bi] = new BombEEE(bbbNow.x, bbbNow.y, bbbNext.owner, bbbNext.life, 0, bbbNext.power);
				}
				continue;
			}

			// 以降の処理では、エージェントが動いている前提。

			// エージェントがキックできない場合、エージェントも爆弾も停止する。
			if (absNow[agentIndex].kick == false) {
				bombsNext2[bi] = new BombEEE(bbbNow.x, bbbNow.y, bbbNext.owner, bbbNext.life, 0, bbbNext.power);
				agentsNext2[agentIndex] = new AgentEEE(aaaNow.x, aaaNow.y, aaaNext.agentID);
				continue;
			}

			// 以降の処理では、エージェントは動いておりキックできる前提。

			int dir = actions[agentIndex];
			int x2 = aaaNext.x;
			int y2 = aaaNext.y;
			if (dir == 1) {
				x2--;
			} else if (dir == 2) {
				x2++;
			} else if (dir == 3) {
				y2--;
			} else if (dir == 4) {
				y2++;
			}

			boolean kickable = true;
			if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
				kickable = false;
			} else {
				int type = (int) boardNext.data[x2][y2];
				if (occupancyAgent.data[x2][y2] > 0) {
					kickable = false;
				} else if (occupancyBomb.data[x2][y2] > 0) {
					kickable = false;
				} else if (Constant.isWall(type)) {
					kickable = false;
				} else if (Constant.isItem(type)) {
					kickable = false;
				}
			}

			// 爆弾がキックできるときは、キックする。できないときは、停止する。
			if (kickable) {
				occupancyBomb.data[bbbNext.x][bbbNext.y] = 0;
				bombsNext2[bi] = new BombEEE(x2, y2, bbbNext.owner, bbbNext.life, dir, bbbNext.power);
				agent_indexed_by_kicked_bomb[bi] = agentIndex;
				kicked_bomb_indexed_by_agent[agentIndex] = bi;
			} else {
				bombsNext2[bi] = new BombEEE(bbbNow.x, bbbNow.y, bbbNext.owner, bbbNext.life, bbbNext.dir, bbbNext.power);
				agentsNext2[agentIndex] = new AgentEEE(aaaNow.x, aaaNow.y, aaaNext.agentID);
			}
		}

		boolean isChanged = false;

		for (int bi = 0; bi < numBomb; bi++) {
			BombEEE bbbNext2 = bombsNext2[bi];
			if (bbbNext2 == null) continue;
			bombsNext[bi] = bbbNext2;
			occupancyBomb.data[bbbNext2.x][bbbNext2.y]++;
			isChanged = true;
		}

		for (int ai = 0; ai < 4; ai++) {
			AgentEEE aaaNext2 = agentsNext2[ai];
			if (aaaNext2 == null) continue;
			agentsNext[ai] = aaaNext2;
			occupancyAgent.data[aaaNext2.x][aaaNext2.y]++;
			isChanged = true;
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// とりあえず、ここまでの手続きで矛盾は無いはずだけど、それでも矛盾が発生している場合は、元の位置に戻す。
		/////////////////////////////////////////////////////////////////////////////////////
		while (isChanged) {
			isChanged = false;
			for (int ai = 0; ai < 4; ai++) {
				if (absNow[ai].isAlive == false) continue;
				if (agentsNow[ai] == null) continue;
				EEE aaaNow = agentsNow[ai];
				EEE aaaNext = agentsNext[ai];
				if (aaaNow.isSamePosition(aaaNext) == false && (occupancyAgent.data[aaaNext.x][aaaNext.y] > 1 || occupancyBomb.data[aaaNext.x][aaaNext.y] > 0)) {
					int bi = kicked_bomb_indexed_by_agent[ai];
					if (bi != -1) {
						BombEEE bbbNow = bombsNow[bi];
						BombEEE bbbNext = bombsNext[bi];
						bbbNext.x = bbbNow.x;
						bbbNext.y = bbbNow.y;
						occupancyBomb.data[bbbNow.x][bbbNow.y]++;
						agent_indexed_by_kicked_bomb[bi] = -1;
						kicked_bomb_indexed_by_agent[ai] = -1;
					}
					aaaNext.x = aaaNow.x;
					aaaNext.y = aaaNow.y;
					occupancyAgent.data[aaaNext.x][aaaNext.y]++;
					isChanged = true;
				}
			}

			for (int bi = 0; bi < numBomb; bi++) {
				BombEEE bbbNow = bombsNow[bi];
				BombEEE bbbNext = bombsNext[bi];

				int ai = agent_indexed_by_kicked_bomb[bi];

				if (bbbNow.isSamePosition(bbbNext) && ai == -1) continue;

				if (occupancyAgent.data[bbbNext.x][bbbNext.y] > 0 || occupancyBomb.data[bbbNext.x][bbbNext.y] > 1) {
					bbbNext.x = bbbNow.x;
					bbbNext.y = bbbNow.y;
					bbbNext.dir = 0;
					occupancyBomb.data[bbbNext.x][bbbNext.y]++;
					if (ai != -1) {
						EEE aaaNext = agentsNext[ai];
						EEE aaaNow = agentsNow[ai];
						aaaNext.x = aaaNow.x;
						aaaNext.y = aaaNow.y;
						occupancyAgent.data[aaaNext.x][aaaNext.y]++;
						kicked_bomb_indexed_by_agent[ai] = -1;
						agent_indexed_by_kicked_bomb[bi] = -1;
					}
					isChanged = true;
				}
			}
		}

		for (int bi = 0; bi < numBomb; bi++) {
			BombEEE bbbNow = bombsNow[bi];
			BombEEE bbbNext = bombsNext[bi];
			if (bbbNow.isSamePosition(bbbNext) && agent_indexed_by_kicked_bomb[bi] == -1) {
				bbbNext.dir = 0;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// agentsNextの位置でアイテムがあったら、能力に反映させる。
		/////////////////////////////////////////////////////////////////////////////////////
		for (int ai = 0; ai < 4; ai++) {
			if (absNow[ai].isAlive == false) continue;
			if (agentsNow[ai] == null) continue;
			EEE aaaNext = agentsNext[ai];
			int type = (int) boardNow.data[aaaNext.x][aaaNext.y];
			if (type == Constant.ExtraBomb) {
				absNext[ai].numBombHold++;
				absNext[ai].numMaxBomb++;
			} else if (type == Constant.Kick) {
				absNext[ai].kick = true;
			} else if (type == Constant.IncrRange) {
				absNext[ai].strength++;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// Flameの処理
		/////////////////////////////////////////////////////////////////////////////////////

		boolean[][] explodedMap = new boolean[numField][numField];
		boolean hasNewExplosions = false;

		for (int bi = 0; bi < numBomb; bi++) {
			BombEEE bbbNext = bombsNext[bi];
			if (bbbNext.life == 0) {
				hasNewExplosions = true;
			} else {
				int type = (int) boardNext.data[bbbNext.x][bbbNext.y];
				if (type == Constant.Flames) {
					bbbNext.life = 0;
					hasNewExplosions = true;
				}
			}
		}

		while (hasNewExplosions) {
			hasNewExplosions = false;

			for (int bi = 0; bi < numBomb; bi++) {
				BombEEE bbbNext = bombsNext[bi];
				if (bbbNext == null) continue;
				if (bbbNext.life > 0) continue;
				if (bbbNext.owner != -1) absNext[bbbNext.owner - 10].numBombHold++;
				BBMUtility.PrintFlame(boardNext, explodedMap, bbbNext.x, bbbNext.y, bbbNext.power);
				bombsNext[bi] = null;
			}

			for (int bi = 0; bi < numBomb; bi++) {
				BombEEE bbbNext = bombsNext[bi];
				if (bbbNext == null) continue;
				if (explodedMap[bbbNext.x][bbbNext.y] == true) {
					bbbNext.life = 0;
					hasNewExplosions = true;
				}
			}
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				if (explodedMap[x][y] == true) {
					flameLifeNext.data[x][y] = 3;
					boardNext.data[x][y] = Constant.Flames;
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// AgentがFlameに巻き込まれていたら殺す。
		/////////////////////////////////////////////////////////////////////////////////////
		for (int ai = 0; ai < 4; ai++) {
			Ability abNext = absNext[ai];
			if (abNext.isAlive == false) continue;
			if (agentsNow[ai] == null) continue;
			EEE aaaNext = agentsNext[ai];
			if (flameLifeNext.data[aaaNext.x][aaaNext.y] > 0) {
				abNext.isAlive = false;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// 時刻がきたら崩壊させる。
		/////////////////////////////////////////////////////////////////////////////////////
		if (collapse) {
			int delta = -1;
			// int delta = 0;
			int dis = -1;
			if (frame == 500 + delta) {
				dis = 0;
			} else if (frame == 575 + delta) {
				dis = 1;
			} else if (frame == 650 + delta) {
				dis = 2;
			} else if (frame == 725 + delta) {
				dis = 3;
			}
			if (dis != -1) {
				for (int p = 0; p < numField; p++) {
					for (int hen = 0; hen < 4; hen++) {
						int x = -1, y = -1;
						if (hen == 0) {
							x = dis;
							y = p;
						} else if (hen == 1) {
							x = numField - 1 - dis;
							y = p;
						} else if (hen == 2) {
							x = p;
							y = dis;
						} else if (hen == 3) {
							x = p;
							y = numField - 1 - dis;
						}
						boardNext.data[x][y] = Constant.Rigid;
						// TODO FlamesがCollapseに巻き込まれていたら消す。これは実施しないほうが実際のロジックになる？？
						// flameLifeNext.data[x][y] = 0;
					}
				}

				// エージェントがCollapseに巻き込まれていたら殺す。
				for (int ai = 0; ai < 4; ai++) {
					Ability abNext = absNext[ai];
					if (abNext.isAlive == false) continue;
					if (agentsNow[ai] == null) continue;
					AgentEEE aaaNext = agentsNext[ai];
					int type = (int) boardNext.data[aaaNext.x][aaaNext.y];
					if (type == Constant.Rigid) {
						abNext.isAlive = false;
					}
				}

				// 爆弾がCollapseに巻き込まれていたら消す。
				for (int i = 0; i < numBomb; i++) {
					BombEEE bbbNext = bombsNext[i];
					if (bbbNext == null) continue;
					int type = (int) boardNext.data[bbbNext.x][bbbNext.y];
					if (type == Constant.Rigid) {
						bombsNext[i] = null;
					}
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// 次ステップの状態を作る。
		/////////////////////////////////////////////////////////////////////////////////////
		StatusHolder shNext = new StatusHolder();

		for (int i = 0; i < numBomb; i++) {
			BombEEE bbbNext = bombsNext[i];
			if (bbbNext == null) continue;
			shNext.setBomb(bbbNext.x, bbbNext.y, bbbNext.owner, bbbNext.life, bbbNext.dir, bbbNext.power);
			boardNext.data[bbbNext.x][bbbNext.y] = Constant.Bomb;
		}

		for (int ai = 0; ai < 4; ai++) {
			Ability abNext = absNext[ai];
			if (abNext.isAlive == false) continue;
			if (agentsNow[ai] == null) continue;
			AgentEEE aaaNext = agentsNext[ai];
			shNext.setAgent(aaaNext.x, aaaNext.y, aaaNext.agentID);
			boardNext.data[aaaNext.x][aaaNext.y] = aaaNext.agentID;
		}

		return new Pack(boardNext, flameLifeNext, absNext, shNext);
	}
}
