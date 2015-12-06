/*******************************************************************************
 * Copyright 2015 Klaus Pfeiffer <klaus@allpiper.com>
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
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
import com.jfastnet.messages.Message;
import com.jfastnet.messages.RequestSeqIdsMessage;
import com.jfastnet.util.NullsafeHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Must be thread-safe.
 *
 * @author Klaus Pfeiffer - klaus@allpiper.com
 */
@Slf4j
public class ReliableModeSequenceProcessor implements ISimpleProcessable, IMessageReceiverPreProcessor, IMessageSenderPostProcessor, IServerHooks {

	public static final AtomicLong ZERO_ATOMIC_LONG = new AtomicLong();
	@Getter
	private final Map<Integer, AtomicLong> lastMessageIdMap = new HashMap<>();

	@Getter
	private final Map<Integer, AtomicLong> lastInOrderMessageId = new HashMap<>();

	private final Map<Integer, Set<Long>> absentMessageIds = new NullsafeHashMap<Integer, Set<Long>>() {
		@Override
		protected Set<Long> newInstance() {
			return new CopyOnWriteArraySet<>();
		}
	};

	private final Map<Integer, List<Message>> heldBackMessages = new NullsafeHashMap<Integer, List<Message>>() {
		@Override
		protected List<Message> newInstance() {
			return new ArrayList<>();
		}
	};

	private final Map<Integer, ReentrantLock> clientLock = new NullsafeHashMap<Integer, ReentrantLock>() {
		@Override
		protected ReentrantLock newInstance() {
			return new ReentrantLock();
		}
	};

	private long lastCheck;

	private Config config;

	/** Set to true when we receive an out-of-order message. */
	private volatile boolean outOfSync;

	public ReliableModeSequenceProcessor(Config config) {
		this.config = config;
	}

	@Override
	public void onUnregister(int clientId) {
		lastMessageIdMap.remove(clientId);
		lastInOrderMessageId.remove(clientId);
		absentMessageIds.remove(clientId);
		heldBackMessages.remove(clientId);
	}

	@Override
	public void process() {
		if (heldBackMessages.size() > 0) {
			for (Map.Entry<Integer, List<Message>> entry : heldBackMessages.entrySet()) {
				Integer clientId = entry.getKey();
				ReentrantLock lock = clientLock.get(clientId);
				if (lock.tryLock()) {
					try {
						List<Message> messages = entry.getValue();
						Long lastMsgId = lastMessageIdMap.getOrDefault(clientId, ZERO_ATOMIC_LONG).get();
						if (messages != null && !messages.isEmpty()) {
							long expectedMessageId = lastMsgId + 1;
							Collections.sort(messages);
							// catch up with held back messages
							Set<Message> removes = new HashSet<>();
							for (int i = 0; i < messages.size(); i++) {
								Message message = messages.get(i);
								if (message.getMsgId() == expectedMessageId) {
									log.trace("Catch up with {}", message);
									// lastMessageId gets set in receive
									config.receiver.receive(message);
									expectedMessageId++;
									removes.add(message);
								}
							}
							messages.removeAll(removes);
						}
					} finally {
						lock.unlock();
					}
				}
			}
		}

		long currentTime = config.timeProvider.get();
		if (currentTime > lastCheck + 500) {
			lastCheck = currentTime;
			for (Map.Entry<Integer, Set<Long>> entry : absentMessageIds.entrySet()) {
				if (entry.getValue().size() > 0) {
					Integer clientId = entry.getKey();
					requestAbsentIds(clientId, absentMessageIds.get(clientId));
				}
			}
		}
	}

	private boolean addReceivedMessage(MessageKey key) {
		absentMessageIds.get(key.clientId).remove(key.messageId);
		return true;
	}

	@Override
	public Message beforeReceive(Message message) {
		if (Message.ReliableMode.SEQUENCE_NUMBER.equals(message.getReliableMode())) {
			int senderId = message.getSenderId();
			ReentrantLock lock = clientLock.get(senderId);
			lock.lock();
			try {
				MessageKey key = MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, senderId, message.getMsgId());
				addReceivedMessage(key);

				Long lastMsgId = lastMessageIdMap.getOrDefault(senderId, ZERO_ATOMIC_LONG).get();
				if (message.getMsgId() <= lastMsgId) {
					// Discard old messages - don't handle already received messages.
					return null;
				}

				List<Message> clientHeldBackMessages = heldBackMessages.get(senderId);
				if (!handleReceivedMessage(key)) {
					// Don't handle out of order messages yet
					log.trace("Last received message: {}", message);
					clientHeldBackMessages.add(message);
					return null;
				}

//				if (!message.getProcessFlags().passReliableModeSequenceProcessor) {
					clientHeldBackMessages.removeIf(heldBackMsg -> heldBackMsg.getMsgId() <= message.getMsgId());
//				}

				return message;
			} finally {
				lock.unlock();
			}
		}
		return message;
	}

	private boolean handleReceivedMessage(MessageKey key) {
		// msgId has to be sequential in this case
		int clientId = key.clientId;
		long messageId = key.messageId;
		Set<Long> clientAbsentMessageIds = absentMessageIds.get(clientId);
		if (!clientAbsentMessageIds.contains(messageId)) {
			AtomicLong lastMsgIdAtomicLong = lastMessageIdMap.get(clientId);
			if (lastMsgIdAtomicLong == null) {
				lastMsgIdAtomicLong = new AtomicLong();
				lastMessageIdMap.put(clientId, lastMsgIdAtomicLong);
			}
			Long lastMsgId = lastMsgIdAtomicLong.get();
			long expectedMessageId = lastMsgId + 1;
			if (messageId == expectedMessageId) {
				lastMsgIdAtomicLong.incrementAndGet();
				outOfSync = false;
				return true;

			} else if (messageId > expectedMessageId) {

				List<Message> clientHeldBackMessages = new ArrayList<>(heldBackMessages.get(clientId));
				for (long i = expectedMessageId; i < messageId; i++) {
					boolean hasIt = false;
					for (Message clientHeldBackMessage : clientHeldBackMessages) {
						if (i == clientHeldBackMessage.getMsgId()) {
							hasIt = true;
							break;
						}
					}
					if (!hasIt) {
						clientAbsentMessageIds.add(i);
					}
				}
				Collections.sort(clientHeldBackMessages);

				// catch up with held back messages
				Set<Message> removes = new HashSet<>();
				for (int i = 0; i < clientHeldBackMessages.size(); i++) {
					Message heldBackMsg = clientHeldBackMessages.get(i);
					if (heldBackMsg.getMsgId() == (lastMsgIdAtomicLong.get() + 1)) {
						log.trace("Catch up with {}", heldBackMsg);
//						heldBackMsg.getProcessFlags().passReliableModeSequenceProcessor = true; // to not increment again
						config.receiver.receive(heldBackMsg);
//						lastMsgIdAtomicLong.incrementAndGet();
						removes.add(heldBackMsg);
					} else if (heldBackMsg.getMsgId() < (lastMsgIdAtomicLong.get() + 1)) {
						removes.add(heldBackMsg);
					} else {
						break;
					}
				}
				heldBackMessages.get(clientId).removeAll(removes);

//				if (messageId == (lastMsgIdAtomicLong.get() + 1)) {
//					return true; // FIXME -> problems with stackable + unstackable
//				}

				if (!outOfSync) {
					// skipped message
					log.warn("Skipped message: received messageId: {}, lastMsgId: {}", new Object[]{messageId, lastMsgId});
					if (clientAbsentMessageIds.size() > 0) {
						requestAbsentIds(clientId, clientAbsentMessageIds);
					}
					outOfSync = true;
				}
			}
		}
		return false;
	}

	private void requestAbsentIds(int clientId, Set<Long> clientAbsentMessageIds) {
		List<Long> requestIdsTmp = new ArrayList<>(clientAbsentMessageIds);
		Collections.sort(requestIdsTmp);
		List<Long> requestIds = new ArrayList<>();
		// request at most X ids
		for (int i = 0; i < Math.min(config.maximumRequestAbsentIds, requestIdsTmp.size()); i++) {
			requestIds.add(requestIdsTmp.get(i));
		}
		RequestSeqIdsMessage requestSeqIdsMessage = new RequestSeqIdsMessage(requestIds, clientId);
		config.sender.send(requestSeqIdsMessage);
		config.netStats.requestedMissingMessages.addAndGet(clientAbsentMessageIds.size());
	}

	@Override
	public Message afterSend(Message message) {
		if (Message.ReliableMode.SEQUENCE_NUMBER.equals(message.getReliableMode())) {
			config.netStats.sentMessages.incrementAndGet();
		}
		return message;
	}
}