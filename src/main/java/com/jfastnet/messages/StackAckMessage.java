package com.jfastnet.messages;

import lombok.Getter;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class StackAckMessage extends Message {

	@Getter
	private long lastReceivedId;

	public StackAckMessage(long lastReceivedId) {
		this.lastReceivedId = lastReceivedId;
	}

	@Override
	public ReliableMode getReliableMode() {
		return ReliableMode.UNRELIABLE;
	}
}
