package com.jfastnet.messages;

import java.util.Collection;

/** @author Klaus Pfeiffer <klaus@allpiper.com> */
public interface IAckMessage {

	/** Ids that are acknowledged. */
	Collection<Long> getAckIds();
}
