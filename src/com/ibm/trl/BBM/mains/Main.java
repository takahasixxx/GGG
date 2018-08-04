package com.ibm.trl.BBM.mains;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.ibm.trl.NN.BottomCell;
import com.ibm.trl.NN.Manager;
import com.ibm.trl.NN.SigmoidCell;
import com.ibm.trl.NN.TopCell;

import ibm.ANACONDA.Core.MyMatrix;
import py4j.GatewayServer;

public class Main {

	public static void main(String[] args) {
		try {

			// ‚Î‚Á‚­Prop‚ÌƒeƒXƒg
			if (false) {
				NormalDistribution nd = new NormalDistribution();

				int numt = 10000;
				int numd = 100;
				List<MyMatrix> xs = new ArrayList<MyMatrix>();
				List<MyMatrix> ys = new ArrayList<MyMatrix>();
				MyMatrix a = new MyMatrix(1, numd);
				for (int d = 0; d < numd; d++) {
					a.data[0][d] = nd.sample() / numd * 10;
				}

				for (int t = 0; t < numt; t++) {
					MyMatrix x = new MyMatrix(numd, 1);
					for (int d = 0; d < numd; d++) {
						x.data[d][0] = nd.sample();
					}
					MyMatrix y = a.times(x);
					double temp = y.data[0][0];
					y.data[0][0] = 1 / (1 + Math.exp(-temp));

					xs.add(x);
					ys.add(y);
				}

				TopCell top = new TopCell(numd, 1);
				BottomCell bottom = new BottomCell(numd);
				SigmoidCell sig = new SigmoidCell(numd, 1);

				top.AddInput(sig);
				sig.AddInput(bottom);

				Manager manager = new Manager(top, bottom);
				manager.SetData(xs, ys);
				manager.Learn();
			}

			BridgeForPython app = new BridgeForPython();
			GatewayServer server = new GatewayServer(app);
			server.start();
			System.out.println("Gateway Server Started");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
