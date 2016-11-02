/*******************************************************************************
 * Copyright 2015 Klaus Pfeiffer - klaus@allpiper.com
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
import com.jfastnet.State;
import com.jfastnet.idprovider.ReliableModeIdProvider;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.StackAckMessage;
import com.jfastnet.messages.StackedMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class StackedMessageProcessor extends AbstractMessageProcessor<StackedMessageProcessor.ProcessorConfig> implements IMessageReceiverPreProcessor, IMessageSenderPreProcessor, IServerHooks {

	@SuppressWarnings("unchecked")
	private static final Stack EMPTY_STACK = new Stack(0, Collections.EMPTY_LIST);

	/** Client id, Msg Id. */
	private Map<Integer, Long> lastAckMessageIdMap = new ConcurrentHashMap<>();

	private long myLastAckMessageId;

	private Map<Long, Message> unacknowledgedSentMessagesMap = new ConcurrentHashMap<>();

	public StackedMessageProcessor(Config config, State state) {
		super(config, state);
		if (!config.idProviderClass.equals(ReliableModeIdProvider.class)) {
			log.warn("StackedMessageProcessor only works with the ReliableModeIdProvider.");
		}
	}

	@Override
	public void onUnregister(int clientId) {
		lastAckMessageIdMap.remove(clientId);
	}

	@Override
	public Message beforeReceive(Message message) {
		if (message instanceof StackAckMessage) {
			StackAckMessage stackAckMessage = (StackAckMessage) message;
			log.trace("Received acknowledge message for id {}", stackAckMessage.getLastReceivedId());
			Long lastId = lastAckMessageIdMap.get(message.getSenderId());
			if (lastId == null || lastId < stackAckMessage.getLastReceivedId()) {
				lastAckMessageIdMap.put(message.getSenderId(), stackAckMessage.getLastReceivedId());
			}
			return null;
		}
		if (message instanceof StackedMessage) {
			StackedMessage stackedMessage = (StackedMessage) message;
			if (stackedMessage.getMessages().size() > 0) {
				receiveStackedMessage(stackedMessage);
				Message lastReceivedMessage = stackedMessage.getMessages().get(stackedMessage.getMessages().size() - 1);
				long lastReceivedId = lastReceivedMessage.getMsgId();
				if (lastReceivedId - myLastAckMessageId >= processorConfig.stackedMessagesAckThreshold) {
					config.internalSender.send(new StackAckMessage(lastReceivedId));
					myLastAckMessageId = lastReceivedId;
					log.trace("Send acknowledge message for id: {}", lastReceivedId);
				}
			}
			return null;
		}
		return message;
	}

	private void receiveStackedMessage(StackedMessage stackedMessage) {
		for (Message message : stackedMessage.getMessages()) {
			message.copyAttributesFrom(stackedMessage);
			log.trace("Received stack message: {}", message);
			config.internalReceiver.receive(message);
		}
	}

	@Override
	public Message beforeSend(Message message) {
		// Check if message is "stackable" and don't stack messages that get resent
		if (state.isEnableStackedMessages() && message.stackable() && !message.isResendMessage()) {
			checkCorrectIdProvider();
			cleanUpUnacknowledgedSentMessagesMap();
			unacknowledgedSentMessagesMap.put(message.getMsgId(), message);
			if (isSentToAllFromServer(message.getReceiverId())) {
				state.getProcessorOf(MessageLogProcessor.class).afterSend(message);
				Set<Integer> clientIds = state.getClientStates().idSet();
				clientIds.forEach(id -> {
					Stack stack = createStackForReceiver(message, id);
					stack.send(config, state);
				});
				return null; // Discard message
			} else {
				Stack stack = createStackForReceiver(message, message.getReceiverId());
				if (stack.stackSendingIsReasonable()) {
					state.getProcessorOf(MessageLogProcessor.class).afterSend(message);
					stack.send(config, state);
					return null; // Discard message
				}
			}
		}
		return message;
	}

	private void checkCorrectIdProvider() {
		if (!config.idProviderClass.equals(ReliableModeIdProvider.class)) {
			throw new UnsupportedOperationException("StackedMessageProcessor only works with the ReliableModeIdProvider!");
		}
	}

	private void cleanUpUnacknowledgedSentMessagesMap() {
		if (state.isHost()) {
			Set<Integer> clientIds = state.getClientStates().idSet();
			long maxAckId = 0L;
			for (Integer clientId : clientIds) {
				long clientLastAckId = lastAckMessageIdMap.getOrDefault(clientId, 0L);
				maxAckId = Math.min(maxAckId, clientLastAckId);
			}
			final long finalMaxAckId = maxAckId;
			unacknowledgedSentMessagesMap.keySet().removeIf(id -> id < finalMaxAckId);
		} else {
			long serverLastAckId = lastAckMessageIdMap.getOrDefault(0, 0L);
			unacknowledgedSentMessagesMap.keySet().removeIf(id -> id < serverLastAckId);
		}
	}

	/** Create individual stack for receiver id. */
	private Stack createStackForReceiver(Message newMessage, int receiverId) {
		long startStackMsgId = lastAckMessageIdMap.getOrDefault(receiverId, -1L) + 1L;
		Stack stack = EMPTY_STACK;
		// At least the new message id must be present
		if (newMessage.getMsgId() >= startStackMsgId) {
			List<Message> messages = new ArrayList<>();
			log.trace( " ++++ begin stack ++++");
			for (long msgId = startStackMsgId;
				 msgId <= newMessage.getMsgId() && messages.size() < processorConfig.maximumNumberOfMessagesPerStack;
				 msgId++) {

				Message stackMsg = unacknowledgedSentMessagesMap.get(msgId);
				if (stackMsg != null) {
					// Be tolerant about missing ids.
					// Not every id has to be present, because some messages
					// may not be stackable.
					messages.add(stackMsg);
					log.trace(" ** added to stack: {}", stackMsg);
				} else {
					log.trace(" ** not added to stack: {}", msgId);
				}
			}
			stack = new Stack(receiverId, messages);
			log.trace( " ++++ end stack ++++");
		}
		return stack;
	}

	private boolean isSentToAllFromServer(int receiverId) {
		return config.senderId == 0 && receiverId == 0;
	}

	@Override
	public Class<ProcessorConfig> getConfigClass() {
		return ProcessorConfig.class;
	}

	@Setter @Getter
	@Accessors(chain = true)
	public static class ProcessorConfig {
		/** After X received stacked messages we send an ack packet. */
		public int stackedMessagesAckThreshold = 7;
		public int maximumNumberOfMessagesPerStack = stackedMessagesAckThreshold * 3;
	}

	private static class Stack {
		private final int receiverId;
		private final List<Message> messages;

		public Stack(int receiverId, List<Message> messages) {
			this.receiverId = receiverId;
			this.messages = messages;
		}

		boolean stackSendingIsReasonable() {
			return messages.size() > 1;
		}

		void send(Config config, State state) {
			if (messages.isEmpty()) {
				log.trace("Stack was empty on send.");
				return;
			}
			StackedMessage stackedMessage = new StackedMessage(messages);
			stackedMessage.setReceiverId(receiverId);
			config.internalSender.send(stackedMessage);
			if (!state.idProvider.resolveEveryClientMessage()) {
				// Clear receiver id, if every client receives the same id for a particular message
				messages.forEach(message -> message.setReceiverId(0));
			}
		}
	}
}
