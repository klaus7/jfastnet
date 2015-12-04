package com.jfastnet.messages;

import lombok.Getter;

import java.util.List;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class StackedMessage extends Message {

	@Getter
	List<Message> messages;

	public StackedMessage(List<Message> messages) {
		this.messages = messages;
	}

	@Override
	public ReliableMode getReliableMode() {
		return ReliableMode.UNRELIABLE;
	}
}
