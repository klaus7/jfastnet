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
import com.jfastnet.util.ConcurrentSizeLimitedMap;
import com.jfastnet.util.SizeLimitedList;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Logs incoming and outgoing messages. Per default only reliable messages
 * get logged and an upper bound for the message log is used.
 * <br/>
 * The reliable sequence processor will grab required messages from the message
 * log. So don't change the filter unless you know what you're doing.
 * @author Klaus Pfeiffer <klaus@allpiper.com> */
@Slf4j
public class MessageLog {

	public List<Message> received = new SizeLimitedList<>(3000);
	public List<Message> sent = new SizeLimitedList<>(3000);
	public Map<MessageKey, Message> sentMap = new ConcurrentSizeLimitedMap<>(3000);

	public Predicate<Message> receiveFilter = new NoMessagesPredicate();
	public Predicate<Message> sendFilter = new ReliableMessagesPredicate();

	public void addReceived(Message message) {
		if (receiveFilter.test(message)) {
			received.add(message);
		}
	}

	public void addSent(Message message) {
		if (sendFilter.test(message)) {
			sent.add(message);
			MessageKey messageKey = MessageKey.newKey(message.getReliableMode(), message.getReceiverId(), message.getMsgId());
			log.trace("Put into sent-log: {} -- {}", messageKey, message);
			sentMap.put(messageKey, message);
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
