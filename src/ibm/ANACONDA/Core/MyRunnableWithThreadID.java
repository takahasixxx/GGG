/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package ibm.ANACONDA.Core;

abstract public class MyRunnableWithThreadID extends MyRunnable {
	abstract public void setTheadID(int threadID, int numThread) throws Exception;
}
