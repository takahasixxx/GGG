package com.ibm.trl.NN;

import java.util.List;

import ibm.ANACONDA.Core.MyMatrix;

public class BottomCell extends Cell {

	List<MyMatrix> xList;

	public BottomCell(int numdY) {
		super(0, numdY);
	}

	public void SetX(List<MyMatrix> xList) {
		this.xList = xList;
	}

	@Override
	// 最下位のセルなので、説明変数をそのまま出力する。
	public List<MyMatrix> ForwardProp(List<MyMatrix> xList) throws Exception {
		return this.xList;
	}

	@Override
	public List<MyMatrix> BackProp(List<MyMatrix> yestList, List<MyMatrix> dEdyestList) throws Exception {
		return dEdyestList;
	}

	@Override
	public void AccumlateParameterSlope(List<MyMatrix> xList, List<MyMatrix> yestList, List<MyMatrix> dEdyestList, List<MyMatrix> dEdxList) throws Exception {
	}

	@Override
	public void UpdateParameter() throws Exception {
	}

	@Override
	public void Feedback(boolean good) throws Exception {
	}

	@Override
	public void Clean() throws Exception {
	}
}
