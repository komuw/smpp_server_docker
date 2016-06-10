/****************************************************************************
 * LifeCycleManager.java
 *
 * Copyright (C) Selenium Software Ltd 2006
 *
 * This file is part of SMPPSim.
 *
 * SMPPSim is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * SMPPSim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMPPSim; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * @author martin@seleniumsoftware.com
 * http://www.woolleynet.com
 * http://www.seleniumsoftware.com
 * $Header: /var/cvsroot/SMPPSim2/src/java/com/seleniumsoftware/SMPPSim/LifeCycleManager.java,v 1.8 2012/11/12 19:09:59 martin Exp $
 ****************************************************************************/

package com.seleniumsoftware.SMPPSim;

import com.seleniumsoftware.SMPPSim.pdu.*;

import java.util.logging.*;

public class LifeCycleManager {

	private static Logger logger = Logger.getLogger("com.seleniumsoftware.smppsim");

	private Smsc smsc = Smsc.getInstance();

	private double transitionThreshold;

	private double deliveredThreshold;

	private double undeliverableThreshold;

	private double acceptedThreshold;

	private double rejectedThreshold;

	private double enrouteThreshold;

	private int maxTimeEnroute;

	private int discardThreshold;

	private double transition;

	private double stateChoice;

	public LifeCycleManager() {
		double a = (double) SMPPSim.getPercentageThatTransition() + 1.0;
		transitionThreshold = (a / 100);
		logger.finest("transitionThreshold=" + transitionThreshold);
		logger.finest("SMPPSim.getPercentageThatTransition()=" + SMPPSim.getPercentageThatTransition());
		maxTimeEnroute = SMPPSim.getMaxTimeEnroute();
		logger.finest("maxTimeEnroute=" + maxTimeEnroute);
		discardThreshold = SMPPSim.getDiscardFromQueueAfter();
		logger.finest("discardThreshold=" + discardThreshold);
		deliveredThreshold = ((double) SMPPSim.getPercentageDelivered() / 100);
		logger.finest("deliveredThreshold=" + deliveredThreshold);
		// .90
		undeliverableThreshold = deliveredThreshold + ((double) SMPPSim.getPercentageUndeliverable() / 100);
		logger.finest("undeliverableThreshold=" + undeliverableThreshold);
		// .90 + .06 = .96
		acceptedThreshold = undeliverableThreshold + ((double) SMPPSim.getPercentageAccepted() / 100);
		logger.finest("acceptedThreshold=" + acceptedThreshold);
		// .96 + .02 = .98
		rejectedThreshold = acceptedThreshold + ((double) SMPPSim.getPercentageRejected() / 100);
		logger.finest("rejectedThreshold=" + rejectedThreshold);
		// .98 + .02 = 1.00
	}

	public MessageState setState(MessageState m) {
		// Should a transition take place at all?
		if (isTerminalState(m.getState()))
			return m;
		byte currentState = m.getState();
		transition = Math.random();
		if ((transition < transitionThreshold) || ((System.currentTimeMillis() - m.getSubmit_time()) > maxTimeEnroute)) {
			// so which transition should it be?
			stateChoice = Math.random();
			if (stateChoice < deliveredThreshold) {
				m.setState(PduConstants.DELIVERED);
				logger.finest("State set to DELIVERED");
			} else if (stateChoice < undeliverableThreshold) {
				m.setState(PduConstants.UNDELIVERABLE);
				logger.finest("State set to UNDELIVERABLE");
			} else if (stateChoice < acceptedThreshold) {
				m.setState(PduConstants.ACCEPTED);
				logger.finest("State set to ACCEPTED");
			} else {
				m.setState(PduConstants.REJECTED);
				logger.finest("State set to REJECTED");
			}
		}
		if (isTerminalState(m.getState())) {
			m.setFinal_time(System.currentTimeMillis());
			// If delivery receipt requested prepare it....
			SubmitSM p = m.getPdu();
			logger.info("Message:"+p.getSeq_no()+" state="+getStateName(m.getState()));
			if (p.getRegistered_delivery_flag() == 1 && currentState != m.getState()) {
				prepDeliveryReceipt(m, p);
			} else {
				if (p.getRegistered_delivery_flag() == 2 && currentState != m.getState()) {
					if (isFailure(m.getState())) {
						prepDeliveryReceipt(m, p);
					}
				}
			}
		}
		return m;
	}

	void prepDeliveryReceipt(MessageState m, SubmitSM p) {
		logger.info("Delivery Receipt requested");
		smsc.prepareDeliveryReceipt(p, m.getMessage_id(), m.getState(), 1, 1, m.getErr());
	}

	boolean isFailure(byte state) {
		switch (state) {
		case PduConstants.DELIVERED:
			return false;
		case PduConstants.ACCEPTED:
			return false;
		default:
			return true;
		}
	}

	public boolean isTerminalState(byte state) {
		if ((state == PduConstants.DELIVERED) || (state == PduConstants.EXPIRED) || (state == PduConstants.DELETED) || (state == PduConstants.UNDELIVERABLE)
				|| (state == PduConstants.ACCEPTED) || (state == PduConstants.REJECTED))
			return true;
		else
			return false;
	}

	public String getStateName(byte state) {
		switch (state) {
		case PduConstants.DELIVERED:
			return "DELIVERED";
		case PduConstants.EXPIRED:
			return "EXPIRED";
		case PduConstants.DELETED:
			return "DELETED";
		case PduConstants.UNDELIVERABLE:
			return "UNDELIVERABLE";
		case PduConstants.ACCEPTED:
			return "ACCEPTED";
		case PduConstants.REJECTED:
			return "ACCEPTED";
		case PduConstants.UNKNOWN:
			return "UNKNOWN";
		case PduConstants.ENROUTE:
			return "ENROUTE";
		default:
			return "Invalid state value";
		}
	}

	public boolean messageShouldBeDiscarded(MessageState m) {
		long now = System.currentTimeMillis();
		long age = now - m.getSubmit_time();
		if (isTerminalState(m.getState())) {
			if (age > discardThreshold)
				return true;
		}
		return false;
	}

}