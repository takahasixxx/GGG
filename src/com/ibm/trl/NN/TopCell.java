/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.NN;

import java.util.ArrayList;
import java.util.List;

import ibm.ANACONDA.Core.MyMatrix;

public class TopCell extends Cell {

	List<MyMatrix> yansList;
	List<MyMatrix> yestList;

	public TopCell(int numdX, int numY) {
		super(numdX, numY);
	}

	public void Set_yAnsList(List<MyMatrix> yansList) {
		this.yansList = yansList;
	}

	@Override
	// 最上位のセルなので、下位のセルの出力を何もせずに予測値にする。
	public List<MyMatrix> ForwardProp(List<MyMatrix> xList) throws Exception {
		this.yestList = xList;
		return yestList;
	}

	@Override
	public List<MyMatrix> BackProp(List<MyMatrix> yestList, List<MyMatrix> dEdyestList) throws Exception {
		int numt = yestList.size();

		List<MyMatrix> dEdxList = new ArrayList<MyMatrix>();
		for (int t = 0; t < numt; t++) {
			MyMatrix yest = yestList.get(t);
			MyMatrix yans = yansList.get(t);
			MyMatrix dEdyest = yest.minus(yans).transpose();
			dEdxList.add(dEdyest);
		}

		return dEdxList;
	}

	double counter = 0;
	double totalGosa = 0;

	@Override
	public void AccumlateParameterSlope(List<MyMatrix> xList, List<MyMatrix> yestList, List<MyMatrix> dEdyestList, List<MyMatrix> dEdxList) throws Exception {
		int numt = yestList.size();
		for (int t = 0; t < numt; t++) {
			MyMatrix yans = yansList.get(t);
			MyMatrix yest = yestList.get(t);
			double norm = yans.minus(yest).normF();
			totalGosa += norm * norm;
			counter++;
		}
	}

	@Override
	public void UpdateParameter() throws Exception {
	}

	@Override
	public void Feedback(boolean good) throws Exception {
	}

	@Override
	public void Clean() throws Exception {
		totalGosa = 0;
		counter = 0;
	}

	public double ComputeGosa() throws Exception {
		return totalGosa / counter;
	}

}
