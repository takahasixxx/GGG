package com.ibm.trl.NN;

import java.util.ArrayList;
import java.util.List;

import ibm.ANACONDA.Core.Dataset;
import ibm.ANACONDA.Core.MyMatrix;

public class Manager {

	TopCell topCell;
	BottomCell bottomCell;

	List<MyMatrix> xs;
	List<MyMatrix> ys;

	public Manager(TopCell topCell, BottomCell bottomCell) {
		this.topCell = topCell;
		this.bottomCell = bottomCell;
	}

	public void SetData(Dataset X, Dataset Y) {
		int numt = X.GetNumSample();
		int numdX = X.GetNumDimension();
		int numdY = Y.GetNumDimension();

		// 学習データを用意
		ArrayList<MyMatrix> xs = new ArrayList<MyMatrix>();
		ArrayList<MyMatrix> ys = new ArrayList<MyMatrix>();
		for (int t = 0; t < numt; t++) {
			MyMatrix x = X.mat.getMatrix(t, t, 0, numdX - 1).transpose();
			MyMatrix y = Y.mat.getMatrix(t, t, 0, numdY - 1).transpose();
			xs.add(x);
			ys.add(y);
		}

		this.SetData(xs, ys);
	}

	public void SetData(List<MyMatrix> xs, List<MyMatrix> ys) {
		this.xs = xs;
		this.ys = ys;

		topCell.Set_yAnsList(ys);
		bottomCell.SetX(xs);
	}

	public void Learn() throws Exception {
		for (int frame = 0; frame < 1000000; frame++) {
			// 順伝搬
			topCell.ForwardProp_Framework();

			// 逆伝搬
			bottomCell.BackProp_Framework();

			// パラメータのアップデート
			topCell.AccumulatedParameterSlope_Framework();

			topCell.Feedback_Framework(true);

			topCell.Clean_Framework();

			double gosa = topCell.ComputeGosa();
			System.out.println(Math.log(gosa));
		}
	}

	public void AccumulatedParameter(MyMatrix x, MyMatrix y) throws Exception {
		List<MyMatrix> xs = new ArrayList<MyMatrix>();
		List<MyMatrix> ys = new ArrayList<MyMatrix>();
		xs.add(x);
		ys.add(y);
		topCell.Set_yAnsList(ys);
		bottomCell.SetX(xs);

		// 順伝搬
		topCell.ForwardProp_Framework();

		// 逆伝搬
		bottomCell.BackProp_Framework();

		topCell.AccumulatedParameterSlope_Framework();
	}

	public void ApplySlope() throws Exception {
		topCell.UpdateParameter_Framework();

		double gosa = topCell.ComputeGosa();
		System.out.println("===gosa===");
		System.out.println(gosa);

		topCell.Clean_Framework();
	}

	public void LearnOneShot(List<MyMatrix> xs, List<MyMatrix> ys) throws Exception {
		topCell.Set_yAnsList(ys);
		bottomCell.SetX(xs);

		// 順伝搬
		topCell.ForwardProp_Framework();

		// 逆伝搬
		bottomCell.BackProp_Framework();

		// パラメータのアップデート
		topCell.AccumulatedParameterSlope_Framework();

		double gosa = topCell.ComputeGosa();
		System.out.println(Math.log(gosa));

		// 一連の作業をクリーンにする。
		topCell.Clean_Framework();

	}
}
