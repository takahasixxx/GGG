package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.Node;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;
import com.ibm.trl.BBM.mains.StatusHolder.EEE;
import com.ibm.trl.BBM.mains.StatusHolder.FlameCenterEEE;

import ibm.ANACONDA.Core.MyMatrix;

public class FutureTrack {

	int numField;

	public FutureTrack(int numField) {
		this.numField = numField;
	}

	public void Step(MyMatrix board, Node[][] bombMap, int[] actions, Ability absNow[], StatusHolder shNow) throws Exception {

		// TODO 木を壊す。

		MyMatrix boardNext = new MyMatrix(board);

		Ability[] absNext = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNext[i] = new Ability(absNow[i]);
		}
		/////////////////////////////////////////////////////////////////////////////////////
		// FlameCenterの時刻を進める。
		/////////////////////////////////////////////////////////////////////////////////////
		List<FlameCenterEEE> flameCenterNext = new ArrayList<FlameCenterEEE>();
		for (FlameCenterEEE fffNow : shNow.getFlameCenterEntry()) {
			if (fffNow.life == 1) continue;
			FlameCenterEEE fffNext = new FlameCenterEEE(fffNow);
			fffNext.life--;
			flameCenterNext.add(fffNext);
		}

		// 残っているFrameCenterからMyFlameを作って、boardのFlameで残っている部分があったら。Passageを表示する。
		MyMatrix myFlameNext = new MyMatrix(numField, numField);
		for (FlameCenterEEE fffNext : flameCenterNext) {
			BBMUtility.PrintFlame(boardNext, myFlameNext, fffNext.x, fffNext.y, fffNext.power, 1);
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				if (myFlameNext.data[x][y] == 1) {
					if (boardNext.data[x][y] != Constant.Flames) {
						System.out.println("おかしい");
						throw new Exception("error");
					}
				} else {
					if (boardNext.data[x][y] == Constant.Flames) {
						boardNext.data[x][y] = Constant.Passage;
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
		for (AgentEEE eee : shNow.getAgentEntry()) {
			int agentID = eee.agentID;
			int agentIndex = agentID - 10;
			int action = actions[agentIndex];

			int x2 = eee.x;
			int y2 = eee.y;
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
				if (bombMap[x2][y2] == null) {
					if (absNext[agentIndex].numBombHold > 0) {
						// 爆弾を追加。
						added.add(new BombEEE(eee.x, eee.y, agentID, 10, 0, absNext[agentIndex].strength));
						absNext[agentIndex].numBombHold--;
					}
				}
			}
			if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
				x2 = eee.x;
				y2 = eee.y;
			} else {
				int type = (int) boardNext.data[x2][y2];
				if (Constant.isWall(type)) {
					x2 = eee.x;
					y2 = eee.y;
				}
			}

			agentsNow[agentIndex] = eee;
			agentsNext[agentIndex] = new AgentEEE(x2, y2, eee.agentID);
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
			for (BombEEE bombNow : eees) {
				bombsNow[index] = bombNow;
				int life = bombNow.life;
				int dir = bombNow.dir;
				int x2 = bombNow.x;
				int y2 = bombNow.y;
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
					x2 = bombNow.x;
					y2 = bombNow.y;
				} else {
					int type = (int) boardNext.data[x2][y2];
					if (Constant.isWall(type) || Constant.isItem(type)) {
						x2 = bombNow.x;
						y2 = bombNow.y;
					}
				}
				bombsNext[index] = new BombEEE(x2, y2, bombNow.owner, life - 1, dir, bombNow.power);
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
				for (int aj = ai + 1; aj < 4; aj++) {
					if (absNow[aj].isAlive == false) continue;
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
			boolean[] backBomb = new boolean[4];
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
			for (EEE eee : agentsNext) {
				if (eee == null) continue;
				occupancyAgent.data[eee.x][eee.y]++;
			}

			for (EEE eee : bombsNext) {
				occupancyBomb.data[eee.x][eee.y]++;
			}

			while (true) {
				boolean isChanged = false;

				for (int ai = 0; ai < 4; ai++) {
					if (absNow[ai].isAlive == false) continue;
					EEE eeeNow = agentsNow[ai];
					EEE eeeNext = agentsNext[ai];
					if (eeeNext.isSamePosition(eeeNow)) continue;
					if (occupancyAgent.data[eeeNext.x][eeeNext.y] > 1 || occupancyBomb.data[eeeNext.x][eeeNext.y] > 1) {
						eeeNext.x = eeeNow.x;
						eeeNext.y = eeeNow.y;
						occupancyAgent.data[eeeNext.x][eeeNext.y]++;
						isChanged = true;
					}
				}

				for (int bi = 0; bi < numBomb; bi++) {
					EEE eeeNow = bombsNow[bi];
					EEE eeeNext = bombsNext[bi];
					if (eeeNext.x == eeeNow.x && eeeNext.y == eeeNow.y) continue;
					if (occupancyAgent.data[eeeNext.x][eeeNext.y] > 1 || occupancyBomb.data[eeeNext.x][eeeNext.y] > 1) {
						eeeNext.x = eeeNow.x;
						eeeNext.y = eeeNow.y;
						occupancyBomb.data[eeeNext.x][eeeNext.y]++;
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
		int[] bomb_kicked_by = new int[numBomb];
		for (int bi = 0; bi < numBomb; bi++) {
			bomb_kicked_by[bi] = -1;
		}

		for (int bi = 0; bi < numBomb; bi++) {
			BombEEE bbbNow = bombsNow[bi];
			BombEEE bbbNext = bombsNext[bi];

			// 移動したい場所にAgentがいない。問題なく移動できる。
			if (occupancyAgent.data[bbbNext.x][bbbNext.y] == 0) continue;

			// 衝突相手のエージェントを探す。
			int agentIndex = -1;
			for (int j = 0; j < 4; j++) {
				if (absNow[j].isAlive == false) continue;
				if (bbbNext.isSamePosition(agentsNext[j])) {
					agentIndex = j;
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
				bombsNext2[bi] = new BombEEE(x2, y2, bbbNext.owner, bbbNext.life, dir, bbbNext.power);
				bomb_kicked_by[bi] = agentIndex;
			} else {
				bombsNext2[bi] = new BombEEE(bbbNow.x, bbbNow.y, bbbNext.owner, bbbNext.life, 0, bbbNext.power);
				agentsNext2[agentIndex] = new AgentEEE(aaaNow.x, aaaNow.y, aaaNext.agentID);
			}
		}

		boolean isChanged = false;
		for (int bi = 0; bi < numBomb; bi++) {
			BombEEE bbb = bombsNext2[bi];
			if (bbb == null) continue;
			bombsNext[bi] = bbb;
			occupancyBomb.data[bbb.x][bbb.y]++;
			isChanged = true;
		}

		for (int ai = 0; ai < 4; ai++) {
			AgentEEE aaa = agentsNext2[ai];
			if (aaa == null) continue;
			agentsNext[ai] = aaa;
			occupancyAgent.data[aaa.x][aaa.y]++;
			isChanged = true;
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// とりあえず、ここまでの手続きで矛盾は無いはずだけど、それでも矛盾が発生している場合は、元の位置に戻す。
		/////////////////////////////////////////////////////////////////////////////////////
		while (isChanged) {
			isChanged = false;
			for (int ai = 0; ai < 4; ai++) {
				if (absNow[ai].isAlive == false) continue;
				EEE aaaNow = agentsNow[ai];
				EEE aaaNext = agentsNext[ai];
				if (aaaNow.isSamePosition(aaaNext) == false && (occupancyAgent.data[aaaNext.x][aaaNext.y] > 1 || occupancyBomb.data[aaaNext.x][aaaNext.y] > 0)) {
					aaaNext.x = aaaNow.x;
					aaaNext.y = aaaNow.y;
					occupancyAgent.data[aaaNext.x][aaaNext.y]++;
					isChanged = true;
				}
			}

			for (int bi = 0; bi < numBomb; bi++) {
				BombEEE bbbNow = bombsNow[bi];
				BombEEE bbbNext = bombsNext[bi];

				if (bbbNow.isSamePosition(bbbNext) && bomb_kicked_by[bi] == -1) continue;

				if (occupancyAgent.data[bbbNext.x][bbbNext.y] > 1 || occupancyBomb.data[bbbNext.x][bbbNext.y] > 1) {
					bbbNext.x = bbbNow.x;
					bbbNext.y = bbbNow.y;
					bbbNext.dir = 0;
					occupancyBomb.data[bbbNext.x][bbbNext.y]++;
					int agentIndex = bomb_kicked_by[bi];
					if (agentIndex != -1) {
						EEE aaaNext = agentsNext[agentIndex];
						EEE aaaNow = agentsNow[agentIndex];
						aaaNext.x = aaaNow.x;
						aaaNext.y = aaaNow.y;
						occupancyAgent.data[aaaNext.x][aaaNext.y]++;
						bomb_kicked_by[bi] = -1;
					}
					isChanged = true;
				}
			}
		}

		// TODO agentsNextの位置でアイテムがあったら、能力に反映させる処理が必要。

		/////////////////////////////////////////////////////////////////////////////////////
		// Flameの処理
		/////////////////////////////////////////////////////////////////////////////////////
		boolean hasNewExplosions = false;

		for (int bi = 0; bi < numBomb; bi++) {
			BombEEE bbbNext = bombsNext[bi];
			if (bbbNext.life == 0) {
				absNext[bbbNext.owner].numBombHold++;
				FlameCenterEEE fff = new FlameCenterEEE(bbbNext.x, bbbNext.y, 3, bbbNext.power);
				flameCenterNext.add(fff);
				bombsNext[bi] = null;
				hasNewExplosions = true;
			}
		}

		if (hasNewExplosions) {
			hasNewExplosions = false;
			while (true) {
				for (int bi = 0; bi < numBomb; bi++) {
					BombEEE bbbNext = bombsNext[bi];
					if (bbbNext == null) continue;
					if (myFlameNext.data[bbbNext.x][bbbNext.y] == 1) {
						// TODO エージェントの所有爆弾数を増やす
						absNext[bbbNext.owner].numBombHold++;
						FlameCenterEEE fff = new FlameCenterEEE(bbbNext.x, bbbNext.y, 3, bbbNext.power);
						flameCenterNext.add(fff);
						bombsNext[bi] = null;
						BBMUtility.PrintFlame(boardNext, myFlameNext, fff.x, fff.y, fff.power, 1);
						hasNewExplosions = true;
					}
				}

				if (hasNewExplosions == false) break;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// AgentがFlameに巻き込まれていたら殺す。
		/////////////////////////////////////////////////////////////////////////////////////
		for (int ai = 0; ai < 4; ai++) {
			Ability abNext = absNext[ai];
			if (abNext.isAlive == false) continue;
			EEE aaaNext = agentsNext[ai];
			if (myFlameNext.data[aaaNext.x][aaaNext.y] == 1) {
				abNext.isAlive = false;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// 次ステップの状態を作る。
		/////////////////////////////////////////////////////////////////////////////////////
		StatusHolder shNext = new StatusHolder(numField);
		for (int ai = 0; ai < 4; ai++) {
			Ability abNext = absNext[ai];
			if (abNext.isAlive == false) continue;
			AgentEEE aaaNext = agentsNext[ai];
			shNext.setAgent(aaaNext.x, aaaNext.y, aaaNext.agentID);
		}

		for (int i = 0; i < numBomb; i++) {
			BombEEE bbbNext = bombsNext[i];
			if (bbbNext == null) continue;
			shNext.setBomb(bbbNext.x, bbbNext.y, bbbNext.owner, bbbNext.life, bbbNext.dir, bbbNext.power);
		}

		for (FlameCenterEEE fff : flameCenterNext) {
			shNext.setFlameCenter(fff.x, fff.y, fff.life, fff.power);
		}

		System.out.println(shNow);
		System.out.println(shNext);
	}

	public void Compute(MyMatrix board, Node[][] bombMap, Ability abs[]) throws Exception {

		StatusHolder shNow = new StatusHolder(numField);

		// 時刻0の初期状態を求める。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (Constant.isAgent(type)) {
					shNow.setAgent(x, y, type);
				}
			}
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				Node node = bombMap[x][y];
				if (node == null) continue;
				if (node.type == Constant.Bomb) {
					shNow.setBomb(x, y, node.owner, node.lifeBomb, node.moveDirection, node.power);
				} else if (node.type == Constant.Flames) {
					shNow.setFlameCenter(x, y, node.lifeFlameCenter, node.power);
				}
			}
		}

		Ability[] absNow = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNow[i] = new Ability(abs[i]);
		}

		if (true) {
			int[] actions = { 1, 2, 3, 5 };
			Step(board, bombMap, actions, absNow, shNow);
		}
	}

}
