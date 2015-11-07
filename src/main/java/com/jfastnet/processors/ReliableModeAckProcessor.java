/*******************************************************************************
 * Copyright 2015 Klaus Pfeiffer <klaus@allpiper.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jfastnet.processors;

import com.jfastnet.Config;
import com.jfastnet.IServerHooks;
import com.jfastnet.ISimpleProcessable;
import com.jfastnet.MessageKey;
import com.jfastnet.messages.AckMessage;
import com.jfastnet.messages.ConnectRequest;
import com.jfastnet.messages.IAckMessage;
import com.jfastnet.messages.Message;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Retrieval of messages with the reliable mode set to ACK must be confirmed
 * by sending an ACK message. This processor controls the resending of
 * messages and the retrieval of ACK messages.
 * @author Klaus Pfeiffer <klaus@allpiper.com> */
@Slf4j
public class ReliableModeAckProcessor implements ISimpleProcessable, IMessageReceiverPreProcessor, IMessageReceiverPostProcessor, IMessageSenderPreProcessor, IServerHooks {

	/** Resend message where we didn't receive an ack packet in X ms. */
	@Setter
	private long resendTimeThreshold = 400;

	/** Don't grow the resent time after reaching this limit. */
	@Setter
	private long resendTimeThresholdUpperLimit = 10_000;

	/** Discard messages hitting the resent time limit. Reliable messages
	 * could get lost! */
	@Setter
	private boolean discardOnResendTimeLimitOutrun = false;

	@Setter
	private boolean useAlternativeSenderOnResendTimeLimitOutrun = true;

	/** Interval for checking if we need to resend a message. */
	@Setter
	int resendCheckInterval = 100;

	/** Timestamp of last resend check. */
	long lastResendCheck;

	/** Messages where we didn't receive an ack packet yet. */
	@Getter
	Map<MessageKey, MessageContainer> messagesAwaitingAck = new ConcurrentHashMap<>();

	/** Set of received message ids. */
	Set<MessageKey> receivedMsgIds = new HashSet<>(8192);

	/** To determine new resend time interval. */
	private Map<MessageKey, Long> sentMsgIds = new ConcurrentHashMap<>();

	public Config config;

	public ReliableModeAckProcessor(Config config) {
		this.config = config;
	}

	@Override
	public void onUnregister(int clientId) {
		removeIdFromMessageKeySet(clientId, messagesAwaitingAck.keySet());
		removeIdFromMessageKeySet(clientId, sentMsgIds.keySet());
		removeIdFromMessageKeySet(clientId, receivedMsgIds);
	}

	private void removeIdFromMessageKeySet(int clientId, Set<MessageKey> messageKeys) {
		for (Iterator<MessageKey> iterator = messageKeys.iterator(); iterator.hasNext(); ) {
			MessageKey key = iterator.next();
			if (key.clientId == clientId) {
				iterator.remove();
			}
		}
	}

	@Override
	public void process() {
		// check for resend
		long currentTimeMillis = config.timeProvider.get();
		if (lastResendCheck < currentTimeMillis - resendCheckInterval) {
			lastResendCheck = currentTimeMillis;
			for (Iterator<Map.Entry<MessageKey, MessageContainer>> iterator = messagesAwaitingAck.entrySet().iterator(); iterator.hasNext(); ) {
				Map.Entry<MessageKey, MessageContainer> entry = iterator.next();
				MessageContainer messageContainer = entry.getValue();
				Message message = messageContainer.message;
				if (message == null) {
					log.error("Message from message container was null.");
				} else if (messageContainer.nextResendTry < currentTimeMillis) {
					iterator.remove();
					// For server side: only send to players where message is missing
					message.setReceiverId(entry.getKey().clientId);
					log.info("Resend UDP message {}", message);
					message.setResendMessage(true);
					if (!config.connected && !(message instanceof ConnectRequest)) {
						log.info("Don't resend {} - not connected!", message);
						return;
					}
					config.sender.send(message);
				}
			}
		}
	}

	/**
	 * @param key action id
	 * @return false if action was already received before, true otherwise
	 */
	private boolean addReceivedMessage(MessageKey key) {
		return receivedMsgIds.add(key);
	}

	private void ack(MessageKey key) {
		log.trace("ACK: {}", key);
		MessageContainer messageContainer;
		messageContainer = messagesAwaitingAck.remove(key);
		if (messageContainer != null && messageContainer.message instanceof ConnectRequest) {
			log.info("Reliable UDP connection established!");
			config.connectionEstablished = true;
		}
	}

	/** After sending of message it will be put into a map so we can later
	 * check if we have to resend this message. */
	protected MessageContainer put(MessageKey key, Message message) {
		log.trace("PUT: {}, {}", key, message);
		Long oldValue = sentMsgIds.get(key);
		// Increase resend time continually
		long newValue = (oldValue != null ? oldValue * 2L : resendTimeThreshold);
		if (newValue > resendTimeThresholdUpperLimit) {
			if (discardOnResendTimeLimitOutrun) {
				// That was the last try to send this message then.
				return null;
			}
			newValue = resendTimeThresholdUpperLimit;
		}
		sentMsgIds.put(key, newValue);
		return messagesAwaitingAck.put(key,
				new MessageContainer(System.currentTimeMillis() + newValue, message, newValue));
	}

	@Override
	public Message beforeReceive(Message message) {
		if (message instanceof IAckMessage) {
			IAckMessage ackMessages = (IAckMessage) message;
			for (Long id : ackMessages.getAckIds()) {
				ack(MessageKey.newKey(Message.ReliableMode.ACK_PACKET, message.getSenderId(), id));
			}
			if (message instanceof AckMessage) {
				return null;
			}
		}
		if (Message.ReliableMode.ACK_PACKET.equals(message.getReliableMode())) {
			MessageKey key = MessageKey.newKey(Message.ReliableMode.ACK_PACKET, message.getSenderId(), message.getMsgId());
			sendAckMessage(key);
			if (receivedMsgIds.contains(key)) {
				// Message already received
				return null;
			}
		}
		return message;
	}

	@Override
	public Message afterReceive(Message message) {
		if (Message.ReliableMode.ACK_PACKET.equals(message.getReliableMode())) {
			MessageKey key = MessageKey.newKey(Message.ReliableMode.ACK_PACKET, message.getSenderId(), message.getMsgId());
			if (addReceivedMessage(key)) {
				return message;
			}
			return null;
		}
		return message;
	}

	/** Send acknowledge message to other end. */
	private void sendAckMessage(MessageKey key) {
		AckMessage ackMessage = new AckMessage(key.messageId);
		ackMessage.setReceiverId(key.clientId);
		config.sender.send(ackMessage);
	}

	@Override
	public Message beforeCongestionControl(Message message) {
		return message;
	}

	@Override
	public Message beforeSend(Message message) {
		if (Message.ReliableMode.ACK_PACKET.equals(message.getReliableMode())) {
			put(MessageKey.newKey(Message.ReliableMode.ACK_PACKET, message.getReceiverId(), message.getMsgId()), message);
		}
		return message;
	}

	private static class MessageContainer {
		/** Timestamp of next resend. */
		public long nextResendTry;
		/** Actual message. */
		public Message message;

		public long resendTimeInterval;

		public MessageContainer(long nextResendTry, Message message, long resendTimeInterval) {
			this.nextResendTry = nextResendTry;
			this.message = message;
			this.resendTimeInterval = resendTimeInterval;
		}
	}

}
