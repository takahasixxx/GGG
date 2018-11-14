/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package ibm.ANACONDA.Core;

import java.util.Comparator;

public class SortValueComparator implements Comparator<SortValue> {

	@Override
	public int compare(SortValue a, SortValue b) {
		return Double.compare(a.value, b.value);
	}
}
