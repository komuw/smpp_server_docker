/****************************************************************************
 * DelayedInboundQueue.java
 *
 * Copyright (C) Martin Woolley 2001
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
 * $Header: /var/cvsroot/SMPPSim2/src/java/com/seleniumsoftware/SMPPSim/DelayedInboundQueue.java,v 1.4 2014/05/25 10:42:26 martin Exp $
 ****************************************************************************
 */
package com.seleniumsoftware.SMPPSim;

import com.seleniumsoftware.SMPPSim.exceptions.InboundQueueFullException;
import com.seleniumsoftware.SMPPSim.pdu.*;
import java.util.logging.*;
import java.util.*;

public class DelayedInboundQueue implements Runnable {

	private static DelayedInboundQueue diqueue;

	private static Logger logger = Logger.getLogger("com.seleniumsoftware.smppsim");

	private Smsc smsc = Smsc.getInstance();

	private InboundQueue iqueue = InboundQueue.getInstance();

	// Pdus that are awaiting resubmission via the inbound queue
	// On resubmission they are *moved* from this queue to the inbound queue
	ArrayList<Pdu> delayed_queue_pdus;

	// Seqno key, number of retry attempts value
	// added to or updated when a Pdu comes here for retry
	// removed when we get a call back indicating successful delivery
	Hashtable<Integer, Integer> delayed_queue_attempts;

	private int period;

	private int max_attempts;

	public static DelayedInboundQueue getInstance() {
		if (diqueue == null)
			diqueue = new DelayedInboundQueue();
		return diqueue;
	}

	private DelayedInboundQueue() {
		period = SMPPSim.getDelayed_iqueue_period();
		max_attempts = SMPPSim.getDelayed_inbound_queue_max_attempts();
		delayed_queue_attempts = new Hashtable<Integer, Integer>();
		delayed_queue_pdus = new ArrayList<Pdu>();
	}

	public void retryLater(Pdu pdu) {

		Integer seqno = new Integer(pdu.getSeq_no());

		// deal with counting the number of retry attempts
		synchronized (delayed_queue_attempts) {
			Integer attempts = delayed_queue_attempts.get(seqno);
			if (attempts != null) {
				int next_value = attempts.intValue() + 1;
				delayed_queue_attempts.put(seqno, new Integer(next_value));
			} else {
				delayed_queue_attempts.put(seqno, new Integer(1));
			}
		}

		// queue the PDU in the delayed PDU queue for subsequent resubmission
		synchronized (delayed_queue_pdus) {
			logger.info("DelayedInboundQueue: adding PDU to delayed PDU queue for retry <" + pdu.toString() + ">");
			delayed_queue_pdus.add(pdu);
			logger.info("DelayedInboundQueue: now contains " + delayed_queue_pdus.size() + " PDU(s)");
		}
	}

	public void deliveredOK(Pdu pdu) {
		Integer seqno = new Integer(pdu.getSeq_no());
		synchronized (delayed_queue_attempts) {
			Integer attempts = delayed_queue_attempts.get(seqno);
			if (attempts != null) {
				delayed_queue_attempts.remove(seqno);
				logger.info("Retried message successfully delivered");
			}
		}
	}

	public void run() {

		// this code periodically processes the contents of the delayed inbound queue, *moving*
		// messages that are old enough to the active inbound queue for attempted delivery.

		logger.info("Starting DelayedInboundQueue service....");

		while (smsc.isRunning()) {
			try {
				Thread.sleep(period);
			} catch (InterruptedException e) {
			}

			if (smsc.isRunning()) {
				int dcount = delayed_queue_pdus.size();
				logger.info("Processing " + dcount + " messages in the delayed inbound queue");
				synchronized (delayed_queue_pdus) {
					synchronized (delayed_queue_attempts) {
						Pdu[] pdus = new Pdu[delayed_queue_pdus.size()];
						// copy delayed PDUs to an array so it can be safely iterated over without risk of concurrent modification issues
						pdus = delayed_queue_pdus.toArray(pdus);
						for (int i = 0; i < pdus.length; i++) {
							Pdu mo = pdus[i];
							try {
								Integer seqno = new Integer(mo.getSeq_no());
								// find out how many times we've already tried to deliver this PDU
								Integer attempts = delayed_queue_attempts.get(seqno);
								if (attempts != null) {
									int attempt_count = attempts.intValue();
									if (attempt_count < max_attempts) {
										iqueue.addMessage(mo);
										logger.info("PDU has been resubmitted to inbound queue: seqno=" + mo.getSeq_no());
										attempt_count++;
										delayed_queue_attempts.put(seqno, new Integer(attempt_count));
										delayed_queue_pdus.remove(mo);
										logger.info("PDU has been removed from delayed PDU queue: seqno=" + mo.getSeq_no());
									} else {
										logger.info("MO message not delivered after max (" + max_attempts + ") allowed attempts so giving up : "
												+ mo.getSeq_no());
										delayed_queue_attempts.remove(seqno);
									}
								} else {
									logger.warning("No record of retry attempts for PDU sequence no. " + mo.getSeq_no());
								}
							} catch (InboundQueueFullException e) {
								// try again next time around
							}
						}
					}
				}
			}
		}
		logger.info("DelayedInboundQueue is exiting");
	}

}