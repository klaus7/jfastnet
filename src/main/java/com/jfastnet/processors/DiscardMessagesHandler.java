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

import com.jfastnet.messages.Message;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

/** @author Klaus Pfeiffer <klaus@allpiper.com> */
public class DiscardMessagesHandler implements IMessageReceiverPreProcessor{

	// TODO potentially growing over time
	private Map<Object, Message> msgs = new HashMap<>();

	@Override
	public Message beforeReceive(Message incomingMessage) {
		Object discardableKey = incomingMessage.getDiscardableKey();
		if (discardableKey != null) {
			Message message = msgs.get(discardableKey);
			if (message == null) {
				msgs.put(discardableKey, incomingMessage);
				return incomingMessage;
			} else {
				long incomingMessageTimestamp = incomingMessage.getTimestamp();
				long existingMessageTimestamp = message.getTimestamp();
				if (incomingMessageTimestamp >= existingMessageTimestamp) {
					// incoming message is newer
					msgs.put(discardableKey, incomingMessage);
					return incomingMessage;
				} else {
					// discard message, because incoming message is older than
					// already retrieved message
					return null;
				}
			}
		}
		return incomingMessage;
	}

	/** Can be used to discard messages of a certain class and a certain key. */
	@EqualsAndHashCode
	public static class ClassKey {
		private Class clazz;
		private Object key;

		public ClassKey(Class clazz, Object key) {
			this.clazz = clazz;
			this.key = key;
		}
	}
}
