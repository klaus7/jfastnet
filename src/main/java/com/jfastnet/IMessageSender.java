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

package com.jfastnet;

import com.jfastnet.messages.GenericMessage;
import com.jfastnet.messages.Message;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public interface IMessageSender {

	/**
	 * @param message message to send
	 * @return false if message could not be sent, true otherwise. If true is
	 * returned, there can still be errors due to a possible non-blocking
	 * send of the message. */
	boolean send(Message message);

	/** Used to send a plain object. */
	default boolean send(Object message) {
		return send(new GenericMessage(message));
	}

	/** Queue message for later sending. It is advisable to queue big number of
	 * messages like message parts created from big messages. */
	default boolean queue(Message message) {
		// Default implementation is to just send the message
		return send(message);
	}
}
