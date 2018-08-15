package com.ibm.trl.BBM.backup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.trl.BBM.backup.StatusHolder.EEE;
import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.Agent.Node;
import com.ibm.trl.BBM.mains.BBMUtility;
import com.ibm.trl.BBM.mains.Constant;

import ibm.ANACONDA.Core.MatrixUtility;
import ibm.ANACONDA.Core.MyMatrix;

public class FutureTrack {

	int numField;

	public FutureTrack(int numField) {
		this.numField = numField;
	}

	public void Step(MyMatrix board, Node[][] bombMap, int[] actions, Ability absNow[], StatusHolder shNow) throws Exception {

		// TODO 木を壊す。

		Ability[] absNext = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNext[i] = new Ability(absNow[i]);
		}
		/////////////////////////////////////////////////////////////////////////////////////
		// FlameCenterの時刻を進める。
		/////////////////////////////////////////////////////////////////////////////////////
		List<EEE> flameCenterNext = new ArrayList<EEE>();
		for (EEE fff : shNow.getFlameCenterEntry()) {
			fff.param1--;
			if (fff.param1 == 0) continue;
			flameCenterNext.add(fff);
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// AgentとBombの移動の処理。
		/////////////////////////////////////////////////////////////////////////////////////
		EEE[] agentsNow = new EEE[4];
		EEE[] agentsNext = new EEE[4];
		List<EEE> added = new ArrayList<EEE>();
		for (EEE eee : shNow.getAgentEntry()) {
			int agentID = eee.param1;
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
						added.add(new EEE(eee.x, eee.y, 10, 0, absNext[agentIndex].strength, agentID));
						absNext[agentIndex].numBombHold--;
					}
				}
			}
			if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
				x2 = eee.x;
				y2 = eee.y;
			} else {
				int type = (int) board.data[x2][y2];
				if (Constant.isWall(type)) {
					x2 = eee.x;
					y2 = eee.y;
				}
			}

			agentsNow[agentIndex] = eee;
			agentsNext[agentIndex] = new EEE(x2, y2, eee.param1, eee.param2, eee.value1, eee.value2);
		}

		EEE[] bombsNow;
		EEE[] bombsNext;
		int numBomb;
		{
			Collection<EEE> eees = shNow.getBombEntry();
			numBomb = eees.size();
			bombsNow = new EEE[numBomb];
			bombsNext = new EEE[numBomb];

			int index = 0;
			for (EEE eee : eees) {
				bombsNow[index] = eee;
				int life = eee.param1;
				int dir = eee.param2;
				int x2 = eee.x;
				int y2 = eee.y;
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
					x2 = eee.x;
					y2 = eee.y;
				} else {
					int type = (int) board.data[x2][y2];
					if (Constant.isWall(type) || Constant.isItem(type)) {
						x2 = eee.x;
						y2 = eee.y;
					}
				}
				bombsNext[index] = new EEE(x2, y2, life - 1, dir, eee.value1, eee.value2);
				index++;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// 新ステップで新たに配置された爆弾を追加する。
		/////////////////////////////////////////////////////////////////////////////////////
		if (true) {
			EEE[] bombsNowAdded = new EEE[numBomb + added.size()];
			EEE[] bombsNextAdded = new EEE[numBomb + added.size()];
			for (int i = 0; i < numBomb; i++) {
				bombsNowAdded[i] = bombsNow[i];
				bombsNextAdded[i] = bombsNext[i];
			}
			for (int i = 0; i < added.size(); i++) {
				bombsNowAdded[numBomb + i] = added.get(i);
				bombsNextAdded[numBomb + i] = new EEE(added.get(i));
				bombsNextAdded[numBomb + i].param1--;
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
		EEE[] agentsNext2 = new EEE[4];
		EEE[] bombsNext2 = new EEE[numBomb];
		int[] bomb_kicked_by = new int[numBomb];
		for (int bi = 0; bi < numBomb; bi++) {
			bomb_kicked_by[bi] = -1;
		}

		for (int bi = 0; bi < numBomb; bi++) {
			EEE bbbNow = bombsNow[bi];
			EEE bbbNext = bombsNext[bi];

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
			EEE aaaNow = agentsNow[agentIndex];
			EEE aaaNext = agentsNext[agentIndex];

			// エージェントが動いていない場合。「爆弾を新規設置してから動いていないケース」「爆弾が動いているケース」しかない。
			if (aaaNow.isSamePosition(aaaNext)) {
				if (bbbNow.isSamePosition(bbbNext) == false) {
					// 爆弾が動いてる場合、止める。
					bombsNext2[bi] = new EEE(bbbNow.x, bbbNow.y, bbbNext.param1, 0, bbbNext.value1, bbbNext.value2);
				}
				continue;
			}

			// 以降の処理では、エージェントが動いている前提。

			// エージェントがキックできない場合、エージェントも爆弾も停止する。
			if (absNow[agentIndex].kick == false) {
				bombsNext2[bi] = new EEE(bbbNow.x, bbbNow.y, bbbNext.param1, 0, bbbNext.value1, bbbNext.value2);
				agentsNext2[agentIndex] = new EEE(aaaNow.x, aaaNow.y, aaaNext.param1, aaaNext.param2, aaaNext.value1, aaaNext.value2);
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
				int type = (int) board.data[x2][y2];
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
				bombsNext2[bi] = new EEE(x2, y2, bbbNext.param1, dir, bbbNext.value1, bbbNext.value2);
				bomb_kicked_by[bi] = agentIndex;
			} else {
				bombsNext2[bi] = new EEE(bbbNow.x, bbbNow.y, bbbNext.param1, 0, bbbNext.value1, bbbNext.value2);
				agentsNext2[agentIndex] = new EEE(aaaNow.x, aaaNow.y, aaaNext.param1, aaaNext.param2, aaaNext.value1, aaaNext.value2);
			}
		}

		boolean isChanged = false;
		for (int bi = 0; bi < numBomb; bi++) {
			EEE bbb = bombsNext2[bi];
			if (bbb == null) continue;
			bombsNext[bi] = bbb;
			occupancyBomb.data[bbb.x][bbb.y]++;
			isChanged = true;
		}

		for (int ai = 0; ai < 4; ai++) {
			EEE aaa = agentsNext2[ai];
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
				EEE bbbNow = bombsNow[bi];
				EEE bbbNext = bombsNext[bi];

				if (bbbNow.isSamePosition(bbbNext) && bomb_kicked_by[bi] == -1) continue;

				if (occupancyAgent.data[bbbNext.x][bbbNext.y] > 1 || occupancyBomb.data[bbbNext.x][bbbNext.y] > 1) {
					bbbNext.x = bbbNow.x;
					bbbNext.y = bbbNow.y;
					bbbNext.param2 = 0;
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

		for (int i = 0; i < numBomb; i++) {
			EEE bbbNext = bombsNext[i];
			if (bbbNext.param1 == 0) {
				// TODO エージェントの所有爆弾数を増やす
				EEE fff = new EEE(bbbNext.x, bbbNext.y, 3, 0, bbbNext.value1, bbbNext.value2);
				flameCenterNext.add(fff);
				bombsNext[i] = null;
				hasNewExplosions = true;
			}
		}

		MyMatrix myFlame = new MyMatrix(numField, numField);
		for (EEE fff : flameCenterNext) {
			BBMUtility.PrintFlame(board, myFlame, fff.x, fff.y, fff.value1, 1);
		}

		if (hasNewExplosions) {
			hasNewExplosions = false;
			while (true) {
				for (int i = 0; i < numBomb; i++) {
					EEE bbbNext = bombsNext[i];
					if (bbbNext == null) continue;
					if (myFlame.data[bbbNext.x][bbbNext.y] == 1) {
						// TODO エージェントの所有爆弾数を増やす
						EEE fff = new EEE(bbbNext.x, bbbNext.y, 3, 0, bbbNext.value1, bbbNext.value2);
						flameCenterNext.add(fff);
						bombsNext[i] = null;
						BBMUtility.PrintFlame(board, myFlame, fff.x, fff.y, fff.value1, 1);
						hasNewExplosions = true;
					}
				}

				if (hasNewExplosions == false) break;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// AgentがFlameに巻き込まれていたら殺す。
		/////////////////////////////////////////////////////////////////////////////////////
		for (int i = 0; i < 4; i++) {
			Ability abNext = absNext[i];
			if (abNext.isAlive == false) continue;
			EEE aaaNext = agentsNext[i];
			if (myFlame.data[aaaNext.x][aaaNext.y] == 1) {
				abNext.isAlive = false;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////
		// 次ステップの状態を作る。
		/////////////////////////////////////////////////////////////////////////////////////
		StatusHolder shNext = new StatusHolder(numField);
		for (int i = 0; i < 4; i++) {
			Ability abNext = absNext[i];
			if (abNext.isAlive == false) continue;
			EEE aaaNext = agentsNext[i];
			shNext.setAgent(aaaNext.param1, aaaNext.x, aaaNext.y);
		}

		for (int i = 0; i < numBomb; i++) {
			EEE bbbNext = bombsNext[i];
			if (bbbNext == null) continue;
			shNext.setBomb(bbbNext.param1, bbbNext.param2, bbbNext.x, bbbNext.y, bbbNext.value1);
		}

		for (EEE fff : flameCenterNext) {
			shNext.setFlameCenter(fff.param1, fff.x, fff.y, fff.value1);
		}

		MyMatrix boardNext = new MyMatrix(board);

		System.out.println(shNow);
		System.out.println(shNext);
	}

	static long time1, time2, time3, time4, time5, time6;

	private void RRR(int depth, long condition, MyMatrix board, Node[][] bombMap, int[][] actions, Ability absNow[], StatusHolder shNow, List<StatusHolder> sequence) throws Exception {

		// TODO 炎確定のところは、死亡確定OR移動できないという取扱をする。
		long timeStart;
		long timeEnd;

		if (depth >= 1) return;

		// if (situation.contains(condition)) return;
		// situation.add(condition);

		StatusHolder shNext = new StatusHolder(numField);

		Ability[] absNext = new Ability[4];
		for (int i = 0; i < 4; i++) {
			absNext[i] = new Ability(absNow[i]);
		}

		// 各エージェントのactionsが爆弾使用だけで構成されていて、所有爆弾数0だったら実現できないのでリターン。1以上持っていたら、所有爆弾数を1だけ減じる。
		for (int i = 0; i < 4; i++) {
			if (absNow[i].isAlive == false) continue;

			boolean actionIsBombOnly = true;
			for (int action : actions[i]) {
				if (action != 5) {
					actionIsBombOnly = false;
					break;
				}
			}

			if (actionIsBombOnly) {
				absNext[i].numBombHold--;
				if (absNext[i].numBombHold < 0) return;
				if (absNow[i].justBombed) return;
				absNext[i].justBombed = true;
			} else {
				absNext[i].justBombed = false;
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// エージェントを動かす。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();

		boolean[] doSomething = new boolean[4];
		for (EEE eee : shNow.getAgentEntry()) {
			int agentID = eee.param1;
			int agentIndex = agentID - 10;
			int x = eee.x;
			int y = eee.y;

			Ability abNow = absNow[agentIndex];
			int[] actionss = actions[agentIndex];

			for (int action : actionss) {
				if (action == 0) {
					// 何もしない。その場にいとどまるケース
					shNext.setAgent(agentID, x, y);
					doSomething[agentIndex] = true;
				} else if (action >= 1 && action <= 4) {
					int dir = action;

					int x2 = x;
					int y2 = y;
					if (dir == 1) {
						x2 = x - 1;
					} else if (dir == 2) {
						x2 = x + 1;
					} else if (dir == 3) {
						y2 = y - 1;
					} else if (dir == 4) {
						y2 = y + 1;
					}
					if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) continue;

					int type2 = (int) board.data[x2][y2];
					if (type2 == Constant.Rigid) continue;
					if (type2 == Constant.Wood && shNow.isWoodBrake(x2, y2) == false) continue;

					// それ以外は移動できる。
					shNext.setAgent(agentID, x2, y2);
					doSomething[agentIndex] = true;

					// 爆弾が置いてある可能性があって、キックできれば、爆弾をキックすることができる。
					// if(false) {
					if (abNow.kick) {
						int x3 = x;
						int y3 = y;
						if (dir == 1) {
							x3 = x - 2;
						} else if (dir == 2) {
							x3 = x + 2;
						} else if (dir == 3) {
							y3 = y - 2;
						} else if (dir == 4) {
							y3 = y + 2;
						}
						if (x3 > 0 && x3 < numField && y3 >= 0 && y3 < numField) {

							boolean isKickable = true;
							int type3 = (int) board.data[x3][y3];
							if (type3 == Constant.Rigid) {
								isKickable = false;
							}
							if (type3 == Constant.Wood && shNow.isWoodBrake(x3, y3) == false) {
								isKickable = false;
							}

							if (isKickable) {
								for (int life = 1; life < 10; life++) {
									for (int moveDirection = 1; moveDirection <= 5; moveDirection++) {
										if (shNow.isBombExist(life, moveDirection, x2, y2)) {
											int power = shNow.getBombPower(life, moveDirection, x2, y2);
											shNext.setBomb(life - 1, dir, x3, y3, power);
										}
									}
								}
							}
						}
					}
				} else if (action == 5) {
					// 爆弾を置くケース
					if (abNow.numBombHold > 0) {
						shNext.setBomb(9, 5, x, y, abNow.strength);
						shNext.setAgent(agentID, x, y);
						doSomething[agentIndex] = true;
					}
				}
			}
		}

		for (int i = 0; i < 4; i++) {
			if (absNow[i].isAlive == false) continue;
			if (doSomething[i] == false) return;
		}
		timeEnd = System.currentTimeMillis();
		time1 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// 爆弾を移動させながらLifeを一個減らす。0になったらFCへ移動
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();

		for (EEE eee : shNow.getBombEntry()) {
			int x = eee.x;
			int y = eee.y;
			int life = eee.param1;
			int moveDirection = eee.param2;
			int power = eee.value1;

			int x2 = x;
			int y2 = y;
			if (moveDirection == 1) {
				x2 = x - 1;
			} else if (moveDirection == 2) {
				x2 = x + 1;
			} else if (moveDirection == 3) {
				y2 = y - 1;
			} else if (moveDirection == 4) {
				y2 = y + 1;
			}

			boolean isStop = false;
			if (x2 < 0 || x2 >= numField || y2 < 0 || y2 >= numField) {
				isStop = true;
			} else {
				int type2 = (int) board.data[x2][y2];
				if (type2 == Constant.Rigid) {
					isStop = true;
				}
				if (type2 == Constant.Wood && shNow.isWoodBrake(x2, y2) == false) {
					isStop = true;
				}
			}

			if (isStop) {
				if (life - 1 == 0) {
					shNext.setFlameCenter(3, x, y, power);
				} else {
					shNext.setBomb(life - 1, 5, x, y, power);
				}
			} else {
				if (life - 1 == 0) {
					shNext.setFlameCenter(3, x2, y2, power);
				} else {
					shNext.setBomb(life - 1, moveDirection, x2, y2, power);
				}

				boolean isStopable = false;
				for (int ai = 0; ai < 4; ai++) {
					if (shNext.isAgentExist(ai + 10, x2, y2)) {
						isStopable = true;
						break;
					}
				}

				if (isStopable) {
					if (life - 1 == 0) {
						shNext.setFlameCenter(3, x, y, power);
					} else {
						shNext.setBomb(life - 1, 5, x, y, power);
					}
				}
			}
		}
		timeEnd = System.currentTimeMillis();
		time2 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// FCを一個減らす。0になったら除外する。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		for (EEE eee : shNow.getFlameCenterEntry()) {
			int x = eee.x;
			int y = eee.y;
			int life = eee.param1;
			int power = eee.value1;
			shNext.setFlameCenter(life - 1, x, y, power);
		}
		timeEnd = System.currentTimeMillis();
		time3 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// FCからFlamesを計算する。
		// 1ステップ後のFCで連鎖する可能性のある爆弾があれば、それもFCに移動する。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();
		MyMatrix myBoard = new MyMatrix(board);
		for (EEE eee : shNow.getWoodBrakeEntry()) {
			int x = eee.x;
			int y = eee.y;
			int type = (int) myBoard.data[x][y];
			if (type == Constant.Wood) {
				myBoard.data[x][y] = Constant.Passage;
			}
		}
		timeEnd = System.currentTimeMillis();
		time4 += timeEnd - timeStart;

		timeStart = System.currentTimeMillis();
		MyMatrix myFlame = new MyMatrix(numField, numField);
		for (EEE eee : shNext.getFlameCenterEntry()) {
			int x = eee.x;
			int y = eee.y;
			// int life = eee.param1;
			int power = eee.value1;
			BBMUtility.PrintFlame(myBoard, myFlame, x, y, power, 1);
		}

		while (true) {
			if (false) {
				System.out.println("=========================");
				MatrixUtility.OutputMatrix(myFlame);
				System.out.println("=========================");
			}

			boolean isChanged = false;
			// 炎に巻き込まれる爆弾で、FCに変化させた形跡が無いものは、FCに変化させる。
			for (EEE eee : shNext.getBombEntry()) {
				int x = eee.x;
				int y = eee.y;
				int power = eee.value1;
				if (myFlame.data[x][y] == 1 && shNext.isFlameCenterExist(3, x, y) == false) {
					shNext.setFlameCenter(3, x, y, power);
					BBMUtility.PrintFlame(myBoard, myFlame, x, y, power, 1);
					isChanged = true;
				}
			}
			if (isChanged == false) break;
		}

		// TODO test
		if (false) {
			MyMatrix myFlame2 = new MyMatrix(numField, numField);
			for (EEE eee : shNext.getBombEntry()) {
				int x = eee.x;
				int y = eee.y;
				int power = eee.value1;
				for (EEE eee2 : shNext.getBombEntry()) {
					int x2 = eee2.x;
					int y2 = eee2.y;
					int power2 = eee2.value1;
					BBMUtility.PrintFlame(myBoard, myFlame2, x, y, power, 1);
					BBMUtility.PrintFlame(myBoard, myFlame2, x2, y2, power2, 1);
				}
			}
		}

		timeEnd = System.currentTimeMillis();
		time5 += timeEnd - timeStart;

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// 新規FCでWBを計算する。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		timeStart = System.currentTimeMillis();

		for (EEE eee : shNow.getWoodBrakeEntry()) {
			int x = eee.x;
			int y = eee.y;
			shNext.setWoodBrake(x, y);
		}
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (type == Constant.Wood && myFlame.data[x][y] == 1) {
					shNext.setWoodBrake(x, y);
				}
			}
		}

		/////////////////////////////////////////////////////////////////////////////////////////////////
		// エージェント毎に死ぬパターンを数え上げる。
		// TODO もっと賢くしたほうが良いかも。
		/////////////////////////////////////////////////////////////////////////////////////////////////
		for (int agentIndex = 0; agentIndex < 4; agentIndex++) {
			int agentID = agentIndex + 10;
			for (int x = 0; x < numField; x++) {
				for (int y = 0; y < numField; y++) {
					if (shNext.isAgentExist(agentID, x, y) == false) continue;

					counter_global[agentIndex][depth]++;
					if (myFlame.data[x][y] == 1) {
						death_global[agentIndex][depth]++;
					}
				}
			}
		}
		timeEnd = System.currentTimeMillis();
		time6 += timeEnd - timeStart;

		// 次ステップを呼び出す。
		// ここで、どの程度こまかく分岐するかを調整する。
		// まずは、「爆弾を置く」と「上下左右の移動」で場合分けする。
		for (int a1 = 0; a1 < 5; a1++) {
			for (int a2 = 0; a2 < 5; a2++) {
				for (int a3 = 0; a3 < 5; a3++) {
					for (int a4 = 0; a4 < 5; a4++) {
						int id = a1 * 5 * 5 * 5 + a2 * 5 * 5 + a3 * 5 + a4;
						int[][] actionsNext = { { a1 }, { a2 }, { a3 }, { a4 } };
						sequence.add(shNow);
						// RRR(depth+1, id, board, bombMap, actionsNext, absNow, shNow, sequence);
						RRR(depth + 1, condition * 6 * 6 * 6 * 6 + id, board, bombMap, actionsNext, absNext, shNext, sequence);
						sequence.remove(shNow);
					}
				}
			}
		}

		if (false) {
			for (int i = 0; i < 16; i++) {
				// TODO 分岐条件の整理
				if (depth >= 2 && i != 15) continue;
				// if (i != 15) continue;
				boolean[] move = new boolean[4];
				for (int k = 0; k < 4; k++) {
					int flag = (i >> k) & 1;
					if (flag == 0) {
						move[k] = false;
					} else {
						move[k] = true;
					}
				}

				int[][] actionsNext = new int[4][];
				for (int k = 0; k < 4; k++) {
					if (move[k] == true) {
						actionsNext[k] = new int[] { 0, 1, 2, 3, 4 };
					} else {
						actionsNext[k] = new int[] { 5 };
					}
				}

				// System.out.println("depth = " + depth + ", call child, i=" + i);
				sequence.add(shNext);
				RRR(depth + 1, condition * 16 + i, board, bombMap, actionsNext, absNext, shNext, sequence);
				sequence.remove(shNext);
			}
		}

		// if (false) {
		if (depth == 10) {
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			System.out.println("==");
			System.out.println("depth = " + depth);
			System.out.println("==");
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			System.out.println("================================================================================");
			for (StatusHolder sh : sequence) {
				System.out.println(sh.toString());
			}
			System.out.println("================================================================================");
			// return;
		}
	}

	double[][] counter_global = new double[4][100];
	double[][] death_global = new double[4][100];
	Set<Long> situation = new TreeSet<Long>();

	public void Compute(MyMatrix board, Node[][] bombMap, Ability abs[]) throws Exception {
		situation.clear();

		counter_global = new double[4][100];
		death_global = new double[4][100];

		StatusHolder shNow = new StatusHolder(numField);

		// 時刻0の初期状態を求める。
		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				int type = (int) board.data[x][y];
				if (Constant.isAgent(type)) {
					shNow.setAgent(type, x, y);
				}
			}
		}

		for (int x = 0; x < numField; x++) {
			for (int y = 0; y < numField; y++) {
				Node node = bombMap[x][y];
				if (node == null) continue;
				if (node.type == Constant.Bomb) {
					shNow.setBomb(node.lifeBomb, node.moveDirection, x, y, node.power);
				} else if (node.type == Constant.Flames) {
					shNow.setFlameCenter(node.lifeFlameCenter, x, y, node.power);
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

		List<StatusHolder> sequence = new ArrayList<StatusHolder>();
		if (false) {
			for (int a1 = 0; a1 < 5; a1++) {
				for (int a2 = 0; a2 < 5; a2++) {
					for (int a3 = 0; a3 < 5; a3++) {
						for (int a4 = 0; a4 < 5; a4++) {
							int id = a1 * 5 * 5 * 5 + a2 * 5 * 5 + a3 * 5 + a4;
							int[][] actionsNext = { { a1 }, { a2 }, { a3 }, { a4 } };
							sequence.add(shNow);
							RRR(0, id, board, bombMap, actionsNext, absNow, shNow, sequence);
							sequence.remove(shNow);
						}
					}
				}
			}
		}

		if (false) {
			for (int i = 0; i < 16; i++) {
				boolean[] move = new boolean[4];
				for (int k = 0; k < 4; k++) {
					int flag = (i >> k) & 1;
					if (flag == 0) {
						move[k] = false;
					} else {
						move[k] = true;
					}
				}

				int[][] actionsNext = new int[4][];
				for (int k = 0; k < 4; k++) {
					if (move[k] == true) {
						actionsNext[k] = new int[] { 0, 1, 2, 3, 4 };
					} else {
						actionsNext[k] = new int[] { 5 };
					}
				}

				// System.out.println("TOP, call child, i=" + i);
				sequence.add(shNow);
				RRR(0, i, board, bombMap, actionsNext, absNow, shNow, sequence);
				sequence.remove(shNow);
			}
		}

		for (int i = 0; i < 4; i++) {
			for (int depth = 0; depth < 20; depth++) {
				double rate = death_global[i][depth] / counter_global[i][depth];
				System.out.print(String.format("%10.5f", rate));
			}
			System.out.println();
		}
		System.out.println();

		// 計算時間を表示
		if (true) {
			double t1 = time1 * 0.001;
			double t2 = time2 * 0.001;
			double t3 = time3 * 0.001;
			double t4 = time4 * 0.001;
			double t5 = time5 * 0.001;
			double t6 = time6 * 0.001;
			String line = String.format("1=%f, 2=%f, 3=%f, 4=%f, 5=%f, 6=%f", t1, t2, t3, t4, t5, t6);
			System.out.println(line);
		}
	}

}
