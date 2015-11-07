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
import com.jfastnet.MessageKey;
import com.jfastnet.messages.IOrderedMessage;
import com.jfastnet.messages.Message;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** @author Klaus Pfeiffer - klaus@allpiper.com
 * @deprecated use a ReliableSequenceMessage instead */
@Deprecated
public class OrderedUdpHandler implements IMessageReceiverPreProcessor, IMessageReceiverPostProcessor {

	/** Messages awaiting previous messages they are based on. The key describes
	 * the message id on which the value is based on. */
	private Map<Long, Message> queuedMessages = new ConcurrentHashMap<>();

	/** Set of received message ids. */
	Set<MessageKey> receivedMsgIds = new HashSet<>(8192);

	public Config config;

	public OrderedUdpHandler(Config config) {
		this.config = config;
	}

	/**
	 * Check if received message requires another message to be received before
	 * and if that message was already received.
	 *
	 * @param message incoming message
	 * @return true, if message is ready for processing.
	 */
	@Override
	public Message beforeReceive(Message message) {
		if (message instanceof IOrderedMessage) {
			IOrderedMessage orderedUdpMessage = (IOrderedMessage) message;
			long basedOnActionId = orderedUdpMessage.getBasedOnMessageId();
			if (basedOnActionId == 0) {
				return message;
			}
			boolean contains = receivedMsgIds.contains(MessageKey.newKey(message.getReliableMode(), message.getSenderId(), basedOnActionId));
			if (!contains) {
				queuedMessages.put(basedOnActionId, message);
				return null;
			}
			return message;
		}
		return message;
	}

	@Override
	public Message afterReceive(Message message) {
		long msgId = message.getMsgId();
		receivedMsgIds.add(MessageKey.newKey(message.getReliableMode(), message.getSenderId(), msgId));
		Message queuedMessage = queuedMessages.remove(msgId);
		if (queuedMessage != null) {
			// Message received on which another message is based on
			config.externalReceiver.receive(queuedMessage);
		}
		return message;
	}
}