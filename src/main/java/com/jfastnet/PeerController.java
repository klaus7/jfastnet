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

import com.jfastnet.messages.IInstantProcessable;
import com.jfastnet.messages.LeaveRequest;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.MessagePart;
import com.jfastnet.processors.IMessageReceiverPostProcessor;
import com.jfastnet.processors.IMessageReceiverPreProcessor;
import com.jfastnet.processors.IMessageSenderPostProcessor;
import com.jfastnet.processors.IMessageSenderPreProcessor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class PeerController implements IPeerController {

	/** Last timestamp. Needed for evaluation of passed time. */
	private long lastTS;

	/** Increases until next queued message is sent. */
	private long queueDelayInc;

	/** List of queued messages. A FIFO queue. */
	private List<Message> queuedMessages = new ArrayList<>();

	@Getter
	protected Config config;

	public PeerController(Config config) {
		this.config = config;
		if (config.receiver == null) {
			config.receiver = this;
		}
		if (config.sender == null) {
			config.sender = this;
		}
	}

	@Override
	public boolean start() {
		return this.config.udpPeer.start();
	}

	@Override
	public void stop() {
		log.info("Stopping UDP peer controller.");
		send(new LeaveRequest());
		this.config.udpPeer.stop();
	}

	@Override
	public void process() {
		retrieveTimeDelta();

		if (queueDelayInc > config.queuedMessagesDelay && !queuedMessages.isEmpty()) {
			final Message firstMessage = queuedMessages.remove(0);
			send(firstMessage);
			queueDelayInc = 0;
		}
		config.processables.forEach(ISimpleProcessable::process);
		config.udpPeer.process();
	}

	private void retrieveTimeDelta() {
		final long timestamp = config.timeProvider.get();
		queueDelayInc += (timestamp - lastTS);
		lastTS = timestamp;
	}

	@Override
	public boolean queue(Message message) {
		return queuedMessages.add(message);
	}

	@Override
	public boolean send(Message message) {
		if (!resolveMessage(message)) {
			return false;
		}
		if (!createPayload(message)) {
			return false;
		}
		if (!beforeSend(message)) {
			return false;
		}
		if (!checkPayloadSize(message)) {
			return false;
		}

		config.udpPeer.send(message);
		log.trace("Sent message: {}", message);

		if (!afterSend(message)) {
			return false;
		}

		return true;
	}

	private boolean checkPayloadSize(Message message) {
		if (message.payload instanceof byte[]) {
			byte[] payload = (byte[]) message.payload;
			if (payload.length > config.maximumUdpPacketSize && !(message instanceof MessagePart)) {
				config.idProvider.stepBack(message);
				if (config.autoSplitTooBigMessages) {
					log.info("Auto splitting message: {}", message);
					final List<MessagePart> parts = MessagePart.createFromMessage(config, message.getMsgId(), message, config.maximumUdpPacketSize - MessagePart.MESSAGE_HEADER_SIZE, message.getReliableMode());
					if (parts != null && parts.size() > 0) {
						parts.forEach(this::queue);
					} else {
						log.error("Message {} exceeds configured maximumUdpPacketSize of {}. Payload size is {}.",
								new Object[]{message, config.maximumUdpPacketSize, payload.length});
						log.error("Parts couldn't be created for message {}", message);
					}
				} else {
					// Write error message
					// OS could prevent too big messages from being sent.
					log.error("Message {} exceeds configured maximumUdpPacketSize of {}. Payload size is {}.",
							new Object[]{message, config.maximumUdpPacketSize, payload.length});
				}
				return false;
			}
		} else {
			log.error("Payload is no byte array.");
		}
		return true;
	}

	protected boolean afterSend(Message message) {
		for (IMessageSenderPostProcessor processor : config.messageSenderPostProcessors) {
			if (processor.afterSend(message) == null) {
				log.info("Processor {} discarded message {} at afterSend", processor, message);
				return false;
			}
		}
		return true;
	}

	/** Run pre-processors and congestion control.
	 * @param message message about to send
	 * @return true if we are ready to send the message, false otherwise */
	public boolean beforeSend(Message message) {
		for (IMessageSenderPreProcessor processor : config.messageSenderPreProcessors) {
			if (processor.beforeCongestionControl(message) == null) {
				log.info("Processor {} discarded message {} at beforeCongestionControl", processor, message);
				return false;
			}
		}

		// TODO: congestion control

		for (IMessageSenderPreProcessor processor : config.messageSenderPreProcessors) {
			if (processor.beforeSend(message) == null) {
				log.info("Processor {} discarded message {} at beforeSend", processor, message);
				return false;
			}
		}
		return true;
	}

	/** Set id in message and prepare to send. */
	public boolean resolveMessage(Message message) {
		message.resolveConfig(config);
		message.prepareToSend();
		if (message.getMsgId() == 0L) {
			message.resolveId();
			if (log.isTraceEnabled()) {
				log.trace("Message id {} resolved for: {}", message.getMsgId(), message.getClass().getSimpleName());
			}
		}
		return true;
	}

	/** Create payload for message. */
	public boolean createPayload(Message message) {
		if (!config.udpPeer.createPayload(message)) {
			log.error("Creation of payload for {} failed.", message);
			return false;
		}
		return true;
	}

	@Override
	public void receive(Message message) {
		message.getFeatures().resolve();

		for (IMessageReceiverPreProcessor processor : config.messageReceiverPreProcessors) {
			if (processor.beforeReceive(message) == null) {
				log.trace("Processor {} discarded message {} at beforeReceive", processor, message);
				return;
			}
		}

		log.trace("Received message: {}", message);

		if (message instanceof IInstantProcessable) {
			message.process();
		} else {
			config.externalReceiver.receive(message);
		}

		for (IMessageReceiverPostProcessor processor : config.messageReceiverPostProcessors) {
			if (processor.afterReceive(message) == null) {
				log.trace("Processor {} discarded message {} at afterReceive", processor, message);
				return;
			}
		}
	}
}
