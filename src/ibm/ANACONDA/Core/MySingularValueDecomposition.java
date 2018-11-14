/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package ibm.ANACONDA.Core;

public class MySingularValueDecomposition {
	MyMatrix S, V, U;

	public MySingularValueDecomposition(MyMatrix S, MyMatrix V, MyMatrix U) {
		this.S = S;
		this.V = V;
		this.U = U;
	}

	public MyMatrix getD() {
		return S;
	}

	public MyMatrix getV() {
		return V;
	}

	public MyMatrix getU() {
		return U;
	}
}
