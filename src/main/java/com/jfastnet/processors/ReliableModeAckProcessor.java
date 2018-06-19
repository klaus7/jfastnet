/*******************************************************************************
 * Copyright 2018 Klaus Pfeiffer - klaus@allpiper.com
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

import com.jfastnet.*;
import com.jfastnet.messages.AckMessage;
import com.jfastnet.messages.ConnectRequest;
import com.jfastnet.messages.IAckMessage;
import com.jfastnet.messages.Message;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Retrieval of messages with the reliable mode set to ACK must be confirmed
 * by sending an ACK message. This processor controls the resending of
 * messages and the retrieval of ACK messages.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class ReliableModeAckProcessor extends AbstractMessageProcessor<ReliableModeAckProcessor.ProcessorConfig> implements ISimpleProcessable, IMessageReceiverPreProcessor, IMessageReceiverPostProcessor, IMessageSenderPreProcessor, IServerHooks {

	/** Timestamp of last resend check. */
	private long lastResendCheck;

	/** Messages where we didn't receive an ack packet yet. */
	@Getter
	Map<MessageKey, MessageContainer> messagesAwaitingAck = new ConcurrentHashMap<>();

	/** Set of received message ids. */
	private final Set<MessageKey> receivedMsgIds = new HashSet<>(8192);

	/** To determine new resend time interval. */
	private final Map<MessageKey, Long> sentMsgIds = new ConcurrentHashMap<>();

	/** Used to determine if enough messages where resent for a particular receiver id. */
	private final Map<Integer, Integer> maxNumberOfSentMessagesCheckMap = new HashMap<>();

	public ReliableModeAckProcessor(Config config, State state) {
		super(config, state);
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
		if (lastResendCheck < currentTimeMillis - processorConfig.resendCheckInterval) {
			lastResendCheck = currentTimeMillis;
			maxNumberOfSentMessagesCheckMap.clear();
			for (Iterator<Map.Entry<MessageKey, MessageContainer>> iterator = messagesAwaitingAck.entrySet().iterator(); iterator.hasNext(); ) {
				Map.Entry<MessageKey, MessageContainer> entry = iterator.next();
				MessageContainer messageContainer = entry.getValue();
				Message message = messageContainer.message;
				if (message == null) {
					iterator.remove();
					log.error("Message from message container was null.");
				} else if (messageContainer.nextResendTry < currentTimeMillis) {
					int receiverId = entry.getKey().clientId;
					Integer resentMessagesForReceiver = maxNumberOfSentMessagesCheckMap.getOrDefault(receiverId, 0);
					if (resentMessagesForReceiver > processorConfig.maximumNumberOfResentMessagesPerCheck) {
						continue;
					}
					iterator.remove();
					// For server side: only send to players where message is missing
					message.setReceiverId(receiverId);
					log.info("Resend UDP message {}", message);
					message.setResendMessage(true);
					if (!state.connected && !(message instanceof ConnectRequest)) {
						log.info("Don't resend {} - not connected!", message);
						return;
					}
					config.internalSender.send(message);
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
		if (messageContainer != null && messageContainer.message != null) {
			messageContainer.message.setConfig(config);
			messageContainer.message.setState(state);
			messageContainer.message.ackCallback();
		}
	}

	/** After sending of message it will be put into a map so we can later
	 * check if we have to resend this message. */
	protected MessageContainer put(MessageKey key, Message message) {
		log.trace("PUT: {}, {}", key, message);
		Long oldValue = sentMsgIds.get(key);
		// Increase resend time continually
		long newValue = (oldValue != null ? oldValue * 2L : processorConfig.resendTimeThreshold);
		if (newValue > processorConfig.resendTimeThresholdUpperLimit) {
			if (processorConfig.discardOnResendTimeLimitOutrun) {
				// That was the last try to send this message then.
				return null;
			}
			newValue = processorConfig.resendTimeThresholdUpperLimit;
		}
		sentMsgIds.put(key, newValue);
		return messagesAwaitingAck.put(key,
				new MessageContainer(System.currentTimeMillis() + newValue, message, newValue));
	}

	@Override
	public Message beforeReceive(Message message) {
		if (message instanceof IAckMessage) {
			IAckMessage ackMessages = (IAckMessage) message;
			ack(MessageKey.newKey(Message.ReliableMode.ACK_PACKET, message.getSenderId(), ackMessages.getAckId()));
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
		config.internalSender.send(ackMessage);
	}

	@Override
	public Message beforeSend(Message message) {
		if (Message.ReliableMode.ACK_PACKET.equals(message.getReliableMode())) {
			int receiverId = message.getReceiverId();
			if (state.isHost() && receiverId == 0) {
				// Sent to all and we are the host
				Set<Integer> idSet = state.getClientStates().idSet();
				idSet.forEach(clientId -> put(MessageKey.newKey(Message.ReliableMode.ACK_PACKET, clientId, message.getMsgId()), message));
			} else {
				put(MessageKey.newKey(Message.ReliableMode.ACK_PACKET, receiverId, message.getMsgId()), message);
			}
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

	@Override
	public Class<ProcessorConfig> getConfigClass() {
		return ProcessorConfig.class;
	}

	@Setter @Getter
	@Accessors(chain = true)
	public static class ProcessorConfig {
		/** Resend message where we didn't receive an ack packet in X ms. */
		public long resendTimeThreshold = 400;

		/** Don't grow the resent time after reaching this limit. */
		public long resendTimeThresholdUpperLimit = 10_000;

		/** Discard messages hitting the resent time limit. Reliable messages
		 * could get lost! */
		public boolean discardOnResendTimeLimitOutrun = false;

		/** Interval for checking if we need to resend a message. */
		public int resendCheckInterval = 100;

		/** Maximum number of resent messages per check interval and per receiver id. */
		public int maximumNumberOfResentMessagesPerCheck = 7;
	}

}
