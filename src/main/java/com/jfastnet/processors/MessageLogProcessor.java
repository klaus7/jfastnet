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
import com.jfastnet.State;
import com.jfastnet.messages.Message;

/** Puts filtered messages into the message log.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
public class MessageLogProcessor extends AbstractMessageProcessor implements IMessageSenderPostProcessor, IMessageReceiverPreProcessor {

	public MessageLogProcessor(Config config, State state) {
		super(config, state);
	}

	@Override
	public Message beforeReceive(Message message) {
		state.getMessageLog().addReceived(message);
		return message;
	}

	@Override
	public Message afterSend(Message message) {
		state.getMessageLog().addSent(message);
		return message;
	}
}
