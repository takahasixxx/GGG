/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.NN;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;

import ibm.ANACONDA.Core.MyMatrix;

public class SigmoidCell extends Cell {

	MyMatrix W;
	MyMatrix b;
	MyMatrix one;
	double stepSize = 0.01;

	int counter;
	MyMatrix dW;
	MyMatrix db;

	public SigmoidCell(int numdX, int numdY) {
		super(numdX, numdY);

		W = new MyMatrix(numdY, numdX);
		b = new MyMatrix(numdY, 1);
		one = new MyMatrix(numdY, 1, 1);

		if (true) {
			NormalDistribution nd = new NormalDistribution();
			for (int t = 0; t < numdY; t++) {
				for (int d = 0; d < numdX; d++) {
					W.data[t][d] = nd.sample() * 1.0e-7;
				}
			}
			for (int t = 0; t < numdY; t++) {
				b.data[t][0] = nd.sample() * 1.0e-7;
			}
		}

		counter = 0;
		dW = new MyMatrix(numdY, numdX);
		db = new MyMatrix(numdY, 1);

	}

	List<MyMatrix> dEdybarList = new ArrayList<MyMatrix>();

	@Override
	public List<MyMatrix> ForwardProp(List<MyMatrix> xList) throws Exception {
		List<MyMatrix> yestList = new ArrayList<MyMatrix>();

		// W.setAll(0);
		// b.setAll(0);

		for (MyMatrix x : xList) {
			MyMatrix ybar = W.times(x).plus(b);
			MyMatrix yest = sig(ybar);
			yestList.add(yest);
		}
		return yestList;
	}

	@Override
	public List<MyMatrix> BackProp(List<MyMatrix> yestList, List<MyMatrix> dEdyestList) throws Exception {
		dEdybarList.clear();

		int numt = yestList.size();
		List<MyMatrix> dEdxList = new ArrayList<MyMatrix>();
		for (int t = 0; t < numt; t++) {
			MyMatrix yest = yestList.get(t);
			MyMatrix dyestdybar = yest.timesByElement(one.minus(yest)).transpose();
			MyMatrix dEdyest = dEdyestList.get(t);
			MyMatrix dEdybar = dEdyest.timesByElement(dyestdybar);
			MyMatrix dEdx = dEdybar.times(W);
			dEdybarList.add(dEdybar);
			dEdxList.add(dEdx);
		}
		return dEdxList;
	}

	@Override
	public void AccumlateParameterSlope(List<MyMatrix> xList, List<MyMatrix> yestList, List<MyMatrix> dEdyestList, List<MyMatrix> dEdxListList) throws Exception {
		int numt = xList.size();

		MyMatrix dEdW = new MyMatrix(W.numt, W.numd);
		for (int t = 0; t < numt; t++) {
			MyMatrix x = xList.get(t);
			MyMatrix dEdybar = dEdybarList.get(t);
			dEdW.plusEquals(dEdybar.transpose().times(x.transpose()));
		}
		// dEdW.timesEquals(1.0 / numt);

		MyMatrix dEdb = new MyMatrix(b.numt, b.numd);
		for (int t = 0; t < numt; t++) {
			MyMatrix dEdybar = dEdybarList.get(t);
			dEdb.plusEquals(dEdybar.transpose());
		}
		// dEdb.timesEquals(1.0 / numt);

		this.dW.plusEquals(dEdW);
		this.db.plusEquals(dEdb);
		this.counter += numt;
	}

	@Override
	public void UpdateParameter() throws Exception {
		if (counter == 0) return;
		// stepSize = 30;
		stepSize = 0.1;
		double lambda = 0.0;

		MyMatrix moveW = dW.times(-stepSize / counter).plus(W.times(-lambda * stepSize));
		MyMatrix moveb = db.times(-stepSize / counter).plus(b.times(-0 * stepSize));

		W.plusEquals(moveW);
		b.plusEquals(moveb);

		// moveb.setAll(0);
		// b.setAll(0);

		// System.out.println("===W===");
		// MatrixUtility.OutputMatrix(W);
		// System.out.println("===moveW===");
		// MatrixUtility.OutputMatrix(moveW);
		// System.out.println("===b===");
		// MatrixUtility.OutputMatrix(b);
		// System.out.println("===moveb===");
		// MatrixUtility.OutputMatrix(moveb);
	}

	@Override
	public void Feedback(boolean good) throws Exception {
	}

	@Override
	public void Clean() throws Exception {
		dEdybarList.clear();
		counter = 0;
		dW = new MyMatrix(numdY, numdX);
		db = new MyMatrix(numdY, 1);
	}

	private MyMatrix sig(MyMatrix v) {
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
}
