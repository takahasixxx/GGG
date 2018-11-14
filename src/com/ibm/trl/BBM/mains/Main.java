/********************************************
 * (C) Copyright IBM Corp. 2018
 ********************************************/
package com.ibm.trl.BBM.mains;

import py4j.GatewayServer;

public class Main {

	public static void main(String[] args) {
		try {
			BridgeForPython app = new BridgeForPython();
			GatewayServer server = new GatewayServer(app);
			server.start();
			System.out.println("Gateway Server Started");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
