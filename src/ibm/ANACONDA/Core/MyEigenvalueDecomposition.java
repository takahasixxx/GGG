package ibm.ANACONDA.Core;

public class MyEigenvalueDecomposition {
	MyMatrix D, V;

	public MyEigenvalueDecomposition(MyMatrix D, MyMatrix V) {
		this.D = D;
		this.V = V;
	}

	public MyMatrix getD() {
		return D;
	}

	public MyMatrix getV() {
		return V;
	}

}
