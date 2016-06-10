/*
 * Created on 25 May 2014
 *
 */
package com.seleniumsoftware.SMPPSim;

import java.util.logging.Logger;

public class EmbeddedUser {

	private static Logger logger = Logger.getLogger("com.seleniumsoftware.smppsim");

	public static void main(String[] args) throws Exception {

		SMPPSim smppsim = new SMPPSim();
		smppsim.start(args[0]);
		
		// allow SMPPSim to run for 30 seconds then shut it down
		Thread.sleep(30000);
		
		Smsc.getInstance().stop();

		logger.info("EmbeddedUser: Exiting");
	}

}
