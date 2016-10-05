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
import com.jfastnet.MessageLog;
import com.jfastnet.State;
import com.jfastnet.messages.Message;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.function.Predicate;

/** Puts filtered messages into the message log.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
public class MessageLogProcessor extends AbstractMessageProcessor<MessageLogProcessor.ProcessorConfig> implements IMessageSenderPostProcessor, IMessageReceiverPreProcessor {

	/** Message log collects messages for resending. */
	@Getter
	private MessageLog messageLog;

	public MessageLogProcessor(Config config, State state) {
		super(config, state);
		this.messageLog = new MessageLog(config, processorConfig);
	}

	@Override
	public Message beforeReceive(Message message) {
		messageLog.addReceived(message);
		return message;
	}

	@Override
	public Message afterSend(Message message) {
		messageLog.addSent(message);
		return message;
	}

	@Override
	public Class<ProcessorConfig> getConfigClass() {
		return ProcessorConfig.class;
	}

	@Setter @Getter
	@Accessors(chain = true)
	public static class ProcessorConfig {
		public int receivedMessagesLimit = 1000;
		public int sentMessagesMapLimit = 16000;
		public Predicate<Message> messageLogReceiveFilter = new MessageLog.NoMessagesPredicate();
		public Predicate<Message> messageLogSendFilter = new MessageLog.ReliableMessagesPredicate();
	}

}
