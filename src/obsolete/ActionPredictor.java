package obsolete;

import java.io.File;

import com.ibm.trl.BBM.mains.Agent;
import com.ibm.trl.BBM.mains.Constant;
import com.ibm.trl.BBM.mains.ForwardModel;
import com.ibm.trl.BBM.mains.GlobalParameter;
import com.ibm.trl.BBM.mains.StatusHolder;
import com.ibm.trl.BBM.mains.Agent.Ability;
import com.ibm.trl.BBM.mains.ForwardModel.Pack;
import com.ibm.trl.BBM.mains.StatusHolder.AgentEEE;
import com.ibm.trl.BBM.mains.StatusHolder.BombEEE;
import com.ibm.trl.BBM.mains.StatusHolder.FlameCenterEEE;

import ibm.ANACONDA.Core.MatrixUtility;
import ibm.ANACONDA.Core.MyMatrix;

public class ActionPredictor {
	static int numField = GlobalParameter.numField;
	static int width = 2;
	static int numC = width * 2 + 1;
	static int numd = numC * numC * 100;
	int agentID;
	MyMatrix A;
	MyMatrix b;
	static MyMatrix one;

	static {
		try {
			one = new MyMatrix(6, 1, 1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ActionPredictor(int agentID) throws Exception {
		this.agentID = agentID;
		
		A = new MyMatrix(6, numd, 0);
		b = new MyMatrix(6, 1, 1.0 / 6.0);
		if (new File("data/A_" + agentID + ".dat").exists()) {
			A = MatrixUtility.ReadMatrixFromBinaryFile(new File("data/A_" + agentID + ".dat"));
		}
		if (new File("data/b_" + agentID + ".dat").exists()) {
			b = MatrixUtility.ReadMatrixFromBinaryFile(new File("data/b_" + agentID + ".dat"));
		}
	}
	
	public void finishOneEpisode() throws Exception {
		MatrixUtility.WriteMatrixToBinaryFile(new File("data/A_" + agentID + ".dat"), A);
		MatrixUtility.WriteMatrixToBinaryFile(new File("data/b_" + agentID + ".dat"), b);

		MatrixUtility.WriteMatrixToFile(new File("data/A_" + agentID + ".csv"), A);
	}

	static private int getType(MyMatrix board, int x, int y) {
		if (x >= 0 && x < numField && y >= 0 && y < numField) return (int) board.data[x][y];
		return Constant.Rigid;
	}

	static private BombEEE getBomb(BombEEE[][] bombMap, int x, int y) {
		if (x >= 0 && x < numField && y >= 0 && y < numField) return bombMap[x][y];
		return null;
	}

	static private FlameCenterEEE getFlame(FlameCenterEEE[][] flameMap, int x, int y) {
		if (x >= 0 && x < numField && y >= 0 && y < numField) return flameMap[x][y];
		return null;
	}

	static private MyMatrix sig(MyMatrix v) {
		int numd = v.numd;
		int numt = v.numt;
		MyMatrix ret = new MyMatrix(numt, numd);
		for (int t = 0; t < numt; t++) {
			for (int d = 0; d < numd; d++) {
				double temp = v.data[t][d];
				double temp2 = 1 / (1 + Math.exp(-temp));
				ret.data[t][d] = temp2;
			}
		}
		return ret;
	}

	int[] index = new int[1000];

	public void lean(Pack packPre, Pack packNow) throws Exception {
		int ai = agentID - 10;

		AgentEEE[] agentsNow = new AgentEEE[4];
		for (AgentEEE aaa : packNow.sh.getAgentEntry()) {
			agentsNow[aaa.agentID - 10] = aaa;
		}

		AgentEEE[] agentsPre = new AgentEEE[4];
		for (AgentEEE aaa : packPre.sh.getAgentEntry()) {
			agentsPre[aaa.agentID - 10] = aaa;
		}

		BombEEE[][] bombMap = new BombEEE[numField][numField];
		for (BombEEE bbb : packPre.sh.getBombEntry()) {
			bombMap[bbb.x][bbb.y] = bbb;
		}

		FlameCenterEEE[][] flameMap = new FlameCenterEEE[numField][numField];
		for (FlameCenterEEE fff : packPre.sh.getFlameCenterEntry()) {
			flameMap[fff.x][fff.y] = fff;
		}

		AgentEEE aaaNow = agentsNow[ai];
		AgentEEE aaaPre = agentsPre[ai];
		if (aaaNow == null || aaaPre == null) return;

		Ability absNow = packNow.abs[ai];
		Ability absPre = packPre.abs[ai];

		// 前回と今回の状態からエージェントのアクションを決定する。
		int action = -1;
		if (aaaNow.x == aaaPre.x && aaaNow.y == aaaPre.y) {
			if (absNow.numBombHold == absPre.numBombHold) {
				action = 0;
			} else {
				action = 5;
			}
		} else if (aaaNow.x == aaaPre.x - 1) {
			action = 1;
		} else if (aaaNow.x == aaaPre.x + 1) {
			action = 2;
		} else if (aaaNow.y == aaaPre.y - 1) {
			action = 3;
		} else if (aaaNow.y == aaaPre.y + 1) {
			action = 4;
		}

		// エージェントの周りの状況を特徴ベクトルにする。
		int offset = 0;
		int num = 0;
		for (int dx = -width; dx <= width; dx++) {
			int x = aaaPre.x + dx;
			for (int dy = -width; dy <= width; dy++) {
				int y = aaaPre.y + dy;

				// マンハッタン距離がwidth以下なら飛ばす。
				if (Math.abs(dx) + Math.abs(dy) > width) continue;

				int type = getType(packPre.board, x, y);

				// 0-6
				int i = -1;
				if (type == Constant.Passage) {
					i = 0;
				} else if (type == Constant.Rigid) {
					i = 1;
				} else if (type == Constant.Wood) {
					i = 2;
				} else if (type == Constant.ExtraBomb) {
					i = 3;
				} else if (type == Constant.IncrRange) {
					i = 4;
				} else if (type == Constant.Kick) {
					i = 5;
				} else if (Constant.isAgent(type)) {
					i = 6;
				}

				// 7-51
				int j = -1;
				BombEEE bbb = getBomb(bombMap, x, y);
				if (bbb != null) {
					int life = bbb.life - 1;
					if (life > 2) life = 2;
					int power = bbb.power - 2;
					if (power > 2) power = 2;
					int dir = bbb.dir;
					if (dir == 5) dir = 0;
					j = dir * (3 * 3) + life * 3 + power + 7;
				}

				// 52-54
				int k = -1;
				FlameCenterEEE fff = getFlame(flameMap, x, y);
				if (fff != null) {
					int life = fff.life - 1;
					k = life + 52;
				}

				if (i >= 0) {
					index[num] = offset + i;
					num++;
				}
				if (j >= 0) {
					index[num] = offset + j;
					num++;
				}
				if (k >= 0) {
					index[num] = offset + k;
					num++;
				}

				offset += 100;
			}
		}
		index[num] = -1;
		num++;

		// 係数をUpdateする。
		MyMatrix gbar = new MyMatrix(b);
		for (int act = 0; act < 6; act++) {
			for (int i : index) {
				if (i == -1) break;
				gbar.data[act][0] += A.data[act][i];
			}
		}
		MyMatrix g = sig(gbar);
		MyMatrix dg = one.minus(g).timesByElement(g);
		MyMatrix y = new MyMatrix(6, 1);
		y.data[action][0] = 1;
		MyMatrix db = y.minus(g).timesByElement(dg);

		double step = 0.01;

		b.plusEquals(db.times(step));

		System.out.println("===" + agentID + "===");
		MatrixUtility.OutputMatrix(g);
		MatrixUtility.OutputMatrix(b);

		for (int act = 0; act < 6; act++) {
			for (int i : index) {
				if (i == -1) break;
				A.data[act][i] += db.data[act][0] * step;
			}
		}

		System.out.println("===");

		// A.timesEquals(0.9999);
	}

	public double[] predict() throws Exception {
		return null;
	}

}
