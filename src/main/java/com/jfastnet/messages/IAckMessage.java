package com.jfastnet.messages;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public interface IAckMessage {

	/** Id that is acknowledged. */
	long getAckId();
}
