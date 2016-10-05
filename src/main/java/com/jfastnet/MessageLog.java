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

package com.jfastnet;

import com.jfastnet.messages.Message;
import com.jfastnet.processors.MessageLogProcessor;
import com.jfastnet.util.FifoMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/** Logs incoming and outgoing messages. Per default only reliable messages
 * get logged and an upper bound for the message log is used.
 *
 * The reliable sequence processor will grab required messages from the message
 * log. So don't change the filter unless you know what you're doing.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class MessageLog {

	@Getter
	private CircularFifoQueue<Message> received;

	/** First-in, first-out map of sent messages. If a missing id is requested
	 * by another peer, this map gets queried for it. */
	private FifoMap<MessageKey, Message> sentMap;

	private final ReentrantLock sentMapLock = new ReentrantLock();

	private Config config;

	private MessageLogProcessor.ProcessorConfig processorConfig;

	public MessageLog(Config config, MessageLogProcessor.ProcessorConfig processorConfig) {
		this.config = config;
		this.processorConfig = processorConfig;
		received = new CircularFifoQueue<>(processorConfig.receivedMessagesLimit);
		sentMap = new FifoMap<>(processorConfig.sentMessagesMapLimit);
	}

	public synchronized void addReceived(Message message) {
		if (processorConfig.messageLogReceiveFilter.test(message)) {
			received.add(message);
		}
	}

	public Message getSent(MessageKey key) {
		sentMapLock.lock();
		try {
			return sentMap.get(key);
		} finally {
			sentMapLock.unlock();
		}
	}

	public void addSent(Message message) {
		if (processorConfig.messageLogSendFilter.test(message)) {
			MessageKey messageKey = MessageKey.newKey(message.getReliableMode(), message.getReceiverId(), message.getMsgId());
			if (sentMap.containsKey(messageKey)) {
				log.trace("Message already in map! Skipping!");
				return;
			}
			sentMapLock.lock();
			try {
				log.trace("Put into sent-log: {} -- {}", messageKey, message);
				sentMap.put(messageKey, message);
			} finally {
				sentMapLock.unlock();
			}
		}
	}

	/** All messages are logged. */
	public static class AllMessagesPredicate implements Predicate<Message> {
		@Override
		public boolean test(Message message) {
			return true;
		}
	}

	/** No messages are logged. */
	public static class NoMessagesPredicate implements Predicate<Message> {
		@Override
		public boolean test(Message message) {
			return false;
		}
	}

	/** Only reliable messages are logged. */
	public static class ReliableMessagesPredicate implements Predicate<Message> {
		@Override
		public boolean test(Message message) {
			return !Message.ReliableMode.UNRELIABLE.equals(message.getReliableMode());
		}
	}

}
