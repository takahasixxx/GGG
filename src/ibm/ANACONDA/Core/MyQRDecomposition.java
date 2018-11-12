package ibm.ANACONDA.Core;

public class MyQRDecomposition {
	MyMatrix Q, R, H;

	public MyQRDecomposition(MyMatrix Q, MyMatrix R, MyMatrix H) {
		this.Q = Q;
		this.R = R;
		this.H = H;
	}

	public MyMatrix getQ() {
		return Q;
	}

	public MyMatrix getR() {
		return R;
	}

	public MyMatrix getH() {
		return H;
	}

}
