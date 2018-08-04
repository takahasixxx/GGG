package com.ibm.trl.NN;

import java.util.ArrayList;
import java.util.List;

import ibm.ANACONDA.Core.MyMatrix;

abstract public class Cell {

	protected int numdY;
	protected int numdX;
	private List<Cell> outcells = new ArrayList<Cell>();
	private Cell incell = null;

	public Cell(int numdX, int numdY) {
		this.numdY = numdY;
		this.numdX = numdX;
	}

	public void AddOutput(Cell cell) throws Exception {
		outcells.add(cell);
		cell.incell = this;
	}

	public void AddInput(Cell cell) throws Exception {
		incell = cell;
		cell.outcells.add(this);
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/********************************************************************
	 * 
	 * 
	 * 
	 * フォワードプロパゲーション
	 * 
	 * 
	 * 
	 ********************************************************************/
	private List<MyMatrix> yestList = null;

	public void ForwardProp_Framework() throws Exception {

		List<MyMatrix> xList = null;
		if (this instanceof BottomCell == false) {
			incell.ForwardProp_Framework();
			xList = incell.yestList;
		}

		yestList = ForwardProp(xList);
	}

	/********************************************************************
	 * 
	 * 
	 * 
	 * バックプロパゲーション
	 * 
	 * 
	 * 
	 ********************************************************************/

	private List<MyMatrix> dEdxList = null;

	public void BackProp_Framework() throws Exception {

		List<MyMatrix> dEdyestList = null;
		if (this instanceof TopCell == false) {

			int numt = yestList.size();

			dEdyestList = new ArrayList<MyMatrix>();
			for (int t = 0; t < numt; t++) {
				MyMatrix temp = new MyMatrix(1, numdY);
				dEdyestList.add(temp);
			}

			for (Cell outcell : outcells) {
				outcell.BackProp_Framework();
				for (int t = 0; t < numt; t++) {
					dEdyestList.get(t).plusEquals(outcell.dEdxList.get(t));
				}
			}
		}

		dEdxList = BackProp(yestList, dEdyestList);

	}

	/********************************************************************
	 * 
	 * 
	 * 
	 * パラメータの勾配を積算する。
	 * 
	 * 
	 * 
	 ********************************************************************/
	public void AccumulatedParameterSlope_Framework() throws Exception {

		List<MyMatrix> xList = null;
		if (this instanceof BottomCell == false) {
			xList = incell.yestList;
		}

		List<MyMatrix> dEdyestList = null;
		if (this instanceof TopCell == false) {

			int numt = this.yestList.size();

			dEdyestList = new ArrayList<MyMatrix>();
			for (int t = 0; t < numt; t++) {
				MyMatrix temp = new MyMatrix(1, numdY);
				dEdyestList.add(temp);
			}

			for (Cell outcell : outcells) {
				for (int t = 0; t < numt; t++) {
					dEdyestList.get(t).plusEquals(outcell.dEdxList.get(t));
				}
			}
		}

		AccumlateParameterSlope(xList, yestList, dEdyestList, dEdxList);

		// 上流下流の関連セルも呼び出す。
		if (this instanceof BottomCell == false) {
			incell.AccumulatedParameterSlope_Framework();
		}
	}

	/********************************************************************
	 * 
	 * 
	 * 
	 * パラメータをUpdateする。
	 * 
	 * 
	 * 
	 ********************************************************************/

	public void UpdateParameter_Framework() throws Exception {

		UpdateParameter();

		if (this instanceof BottomCell == false) {
			incell.UpdateParameter_Framework();
		}
	}

	/********************************************************************
	 * 
	 * 
	 * 
	 * パラメータ変化が良かったか悪かったかのFeedbackを処理する。
	 * 
	 * 
	 * 
	 ********************************************************************/

	public void Feedback_Framework(boolean good) throws Exception {

		Feedback(good);

		if (this instanceof BottomCell == false) {
			incell.Feedback_Framework(good);
		}
	}

	/********************************************************************
	 * 
	 * 
	 * 
	 * 最適化処理一回分が完了したとして、内部パラメータを全部クリアする。
	 * 
	 * 
	 * 
	 ********************************************************************/
	public void Clean_Framework() throws Exception {
		Clean();

		if (this instanceof BottomCell == false) {
			incell.Clean_Framework();
		}

		yestList = null;
		dEdxList = null;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	abstract public List<MyMatrix> ForwardProp(List<MyMatrix> xList) throws Exception;

	abstract public List<MyMatrix> BackProp(List<MyMatrix> yestList, List<MyMatrix> dEdyestList) throws Exception;

	abstract public void AccumlateParameterSlope(List<MyMatrix> xList, List<MyMatrix> yestList, List<MyMatrix> dEdyestList, List<MyMatrix> dEdxList) throws Exception;

	abstract public void UpdateParameter() throws Exception;

	abstract public void Feedback(boolean good) throws Exception;

	abstract public void Clean() throws Exception;

}