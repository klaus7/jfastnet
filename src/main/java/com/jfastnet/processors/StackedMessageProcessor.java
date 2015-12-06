package com.jfastnet.processors;

import com.jfastnet.Config;
import com.jfastnet.IServerHooks;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.StackAckMessage;
import com.jfastnet.messages.StackedMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class StackedMessageProcessor implements IMessageReceiverPreProcessor, IMessageSenderPreProcessor, IServerHooks {

	private Map<Integer, Long> lastAckMessageIdMap = new ConcurrentHashMap<>();

	private long myLastAckMessageId;

	private Map<Long, Message> unacknowledgedSentMessagesMap = new ConcurrentHashMap<>();

	private Config config;
	//private long lastReceivedStackedMessageChild;

	public StackedMessageProcessor(Config config) {
		this.config = config;
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
				if (lastReceivedId - myLastAckMessageId >= config.stackedMessagesAckThreshold) {
					config.sender.send(new StackAckMessage(lastReceivedId));
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
//			if (message.getMsgId() > lastReceivedStackedMessageChild) {
				message.setConfig(stackedMessage.getConfig());
				message.socketAddressSender = stackedMessage.socketAddressSender;
				message.setSenderId(stackedMessage.getSenderId());
				log.trace("Received stack message: {}", message);
				config.receiver.receive(message);
//				lastReceivedStackedMessageChild = message.getMsgId();
//			}
		}
	}

	@Override
	public Message beforeSend(Message message) {
		// Check if message is "stackable" and don't stack resent messages
		if (message.stackable() && !message.isResendMessage()) {
			cleanUpUnacknowledgedSentMessagesMap();
			unacknowledgedSentMessagesMap.put(message.getMsgId(), message);
			if (sentToAllFromServer(message.getReceiverId())) {
				Set<Integer> clientIds = config.state.clients.keySet();
				clientIds.forEach(id -> createStackForReceiver(message, id));
			} else if (createStackForReceiver(message, message.getReceiverId())) {
				return null;
			}
		}
		return message;
	}

	private void cleanUpUnacknowledgedSentMessagesMap() {
		if (config.state.isHost) {
			Set<Integer> clientIds = config.state.clients.keySet();
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

	private boolean createStackForReceiver(Message newMessage, int receiverId) {
		long startStackMsgId = lastAckMessageIdMap.getOrDefault(receiverId, -1L) + 1L;
		if (newMessage.getMsgId() > startStackMsgId) {
			List<Message> messages = new ArrayList<>();
			log.trace( " ++++ begin stack ++++");
			for (long msgId = startStackMsgId; msgId <= newMessage.getMsgId() && messages.size() < config.stackedMessagesAckThreshold * 2; msgId++) {
				Message stackMsg = unacknowledgedSentMessagesMap.get(msgId);
				if (stackMsg != null) {
					// Be tolerant about missing ids.
					// Not every id has to be present, because some messages
					// may not be stackable.
					log.trace(" ** added to stack: {}", stackMsg);
					messages.add(stackMsg);
				} else {
					log.trace(" ** not added to stack: {}", msgId);
				}
			}
			log.trace( " ++++ end stack ++++");
			if (messages.size() > 1) {
				// Discard message and send stacked message instead
				StackedMessage stackedMessage = new StackedMessage(messages);
				stackedMessage.setReceiverId(receiverId);
				config.sender.send(stackedMessage);
				return true;
			}
		}
		return false;
	}

	boolean sentToAllFromServer(int receiverId) {
		return config.senderId == 0 && receiverId == 0;
	}
}
