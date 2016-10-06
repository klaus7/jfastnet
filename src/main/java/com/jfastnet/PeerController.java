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

import com.jfastnet.events.DisabledStackedMessagesEvent;
import com.jfastnet.messages.*;
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
	private long lastTimestamp;

	/** Increases until next queued message is sent. */
	private long queueDelayIncrease;

	/** List of queued messages. A FIFO queue. */
	private List<Message> queuedMessages = new ArrayList<>();

	/** Current state information. */
	@Getter
	protected State state;

	@Getter
	protected Config config;

	public PeerController(Config config) {
		assert config != null : "Config may not be null!";
		this.config = config;
		this.state = new State(config);
		if (config.internalReceiver == null) {
			config.internalReceiver = this;
		}
		if (config.internalSender == null) {
			config.internalSender = this;
		}
	}

	@Override
	public boolean start() {
		return this.state.getUdpPeer().start();
	}

	@Override
	public void stop() {
		log.info("Stopping UDP peer controller.");
		send(new LeaveRequest());
		this.state.getUdpPeer().stop();
	}

	@Override
	public void process() {
		retrieveTimeDelta();

		if (queueDelayIncrease > config.queuedMessagesDelay && !queuedMessages.isEmpty()) {
			final Message firstMessage = queuedMessages.remove(0);
			send(firstMessage);
			queueDelayIncrease = 0;
		}
		state.getProcessables().forEach(ISimpleProcessable::process);
		state.getUdpPeer().process();
	}

	private void retrieveTimeDelta() {
		final long timestamp = config.timeProvider.get();
		queueDelayIncrease += (timestamp - lastTimestamp);
		lastTimestamp = timestamp;
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

		state.getUdpPeer().send(message);
		log.trace("Sent message: {}", message);

		if (!afterSend(message)) {
			return false;
		}

		return true;
	}

	private boolean hasSmallEnoughPayloadSizeToSend(Message message) {
		if (message.payload instanceof byte[]) {
			byte[] payload = (byte[]) message.payload;
			if (payload.length > config.maximumUdpPacketSize && !(message instanceof MessagePart)) {
				return false;
			}
		} else {
			log.error("Payload is no byte array.");
		}
		return true;
	}

	boolean checkPayloadSize(Message message) {
		if (!hasSmallEnoughPayloadSizeToSend(message)) {
			getState().idProvider.stepBack(message);
			if (config.autoSplitTooBigMessages && !Message.ReliableMode.UNRELIABLE.equals(message.getReliableMode())) {
				autoSplitMessage(message);
			} else {
				if (message instanceof StackedMessage) {
					StackedMessage stackedMessage = (StackedMessage) message;
					log.info(" -> Message is a stacked message: DISABLING stacked messages now");
					disableStackedMessages(stackedMessage);
					downsizeStackedMessage(stackedMessage);
					if (hasSmallEnoughPayloadSizeToSend(stackedMessage)) {
						return true;
					} else {
						// Still not small enough
						Message lastMessage = stackedMessage.getLastMessage();
						if (lastMessage != null) {
							this.send(lastMessage);
						}
					}
				}
				// Write error message
				// OS could prevent too big messages from being sent.
				log.error("Message {} exceeds configured maximumUdpPacketSize of {}. Payload size is {}.",
						new Object[]{message, config.maximumUdpPacketSize, message.payloadLength()});
			}
			return false;
		}
		return true;
	}

	private void autoSplitMessage(Message message) {
		log.info("Auto splitting message: {}", message);
		final List<MessagePart> parts = MessagePart.createFromMessage(state, message.getMsgId(), message, config.maximumUdpPacketSize - MessagePart.MESSAGE_HEADER_SIZE, message.getReliableMode());
		if (parts.size() > 0) {
			parts.forEach(this::queue);
		} else {
			log.error("Message {} exceeds configured maximumUdpPacketSize of {}. Payload size is {}.",
					new Object[]{message, config.maximumUdpPacketSize, message.payloadLength()});
			log.error(" -> Parts couldn't be created for message {}", message);
		}
	}

	private void disableStackedMessages(StackedMessage stackedMessage) {
		state.getEventLog().add(new DisabledStackedMessagesEvent(stackedMessage));
		state.setEnableStackedMessages(false);
	}

	private void downsizeStackedMessage(StackedMessage stackedMessage) {
		List<Message> messages = stackedMessage.getMessages();
		if (messages.size() > 0) {
			// Reduce messages
			while (!hasSmallEnoughPayloadSizeToSend(stackedMessage) && messages.size() > 1) {
				// Remove oldest message
				messages.remove(0);
				// Recreate payload
				createPayload(stackedMessage);
			}
		}
	}

	protected boolean afterSend(Message message) {
		for (IMessageSenderPostProcessor processor : state.getMessageSenderPostProcessors()) {
			if (processor.afterSend(message) == null) {
				log.trace("Processor {} discarded message {} at afterSend", processor, message);
				return false;
			}
		}
		return true;
	}

	/** Run pre-processors and congestion control.
	 * @param message message about to send
	 * @return true if we are ready to send the message, false otherwise */
	public boolean beforeSend(Message message) {
		for (IMessageSenderPreProcessor processor : state.getMessageSenderPreProcessors()) {
			if (processor.beforeCongestionControl(message) == null) {
				log.trace("Processor {} discarded message {} at beforeCongestionControl", processor, message);
				return false;
			}
		}

		// TODO: congestion control

		for (IMessageSenderPreProcessor processor : state.getMessageSenderPreProcessors()) {
			if (processor.beforeSend(message) == null) {
				log.trace("Processor {} discarded message {} at beforeSend", processor, message);
				return false;
			}
		}
		return true;
	}

	/** Set id in message and prepare to send. */
	public boolean resolveMessage(Message message) {
		message.resolve(config, state);
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
		if (!state.getUdpPeer().createPayload(message)) {
			log.error("Creation of payload for {} failed.", message);
			return false;
		}
		return true;
	}

	@Override
	public void receive(Message message) {
		message.getFeatures().resolve();

		for (IMessageReceiverPreProcessor processor : state.getMessageReceiverPreProcessors()) {
			if (processor.beforeReceive(message) == null) {
				log.trace("Processor {} discarded message {} at beforeReceive", processor, message);
				return;
			}
		}

		log.trace("Received message: {}", message);

		if (message instanceof IInstantProcessable) {
			message.process(config.context);
		} else {
			config.externalReceiver.receive(message);
		}

		for (IMessageReceiverPostProcessor processor : state.getMessageReceiverPostProcessors()) {
			if (processor.afterReceive(message) == null) {
				log.trace("Processor {} discarded message {} at afterReceive", processor, message);
				return;
			}
		}
	}
}
