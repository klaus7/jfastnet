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

package com.jfastnet.messages;

import com.jfastnet.Config;
import com.jfastnet.State;
import com.jfastnet.messages.features.MessageFeatures;
import com.jfastnet.messages.features.TimestampFeature;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.net.InetSocketAddress;

/** The base class for all messages. Subclass this class for your own messages.
 * @param <E> context object for the processing of the message
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
@Getter
@EqualsAndHashCode
@Accessors(chain = true)
public abstract class Message<E> implements Serializable, Comparable<Message> {
	/** */
	private static final long serialVersionUID = 1L;

	public static final MessageFeatures DEFAULT_MESSAGE_FEATURES = new MessageFeatures.Immutable();

	/** Unique message id. */
	@Getter
	long msgId;

	/** Sender id of message. <b>Attention!</b> Don't use this in responses,
	 * as it will always be the host's id! */
	@Setter
	private int senderId;

	/** Received id is only used during sending. */
	@Setter @Getter
	private transient int receiverId;

	/** A message that is getting resent, because of unsuccessful transmission.
	 * These messages may not be stopped from sending, when we are over threshold
	 * because then the server could stall out if enough messages get lost and
	 * it is waiting for acknowledge messages. */
	@Setter @Getter
	private transient boolean resendMessage;

	/** Address from receiving or to sending socket. */
	public transient InetSocketAddress socketAddressSender;

	/** Address from recipient. */
	public transient InetSocketAddress socketAddressRecipient;

	/** Serialized payload of message. The data that actually gets transmitted. */
	public transient Object payload;

	/** Config gets set upon sending / receiving of message. */
	@Setter @Getter
	private transient Config config;

	/** State gets set upon sending / receiving of message. */
	@Setter @Getter
	private transient State state;

	public Message() {}

	public Message copyAttributesFrom(Message message) {
		config = message.config;
		state = message.state;
		socketAddressSender = message.socketAddressSender;
		senderId = message.senderId;
		return this;
	}

	/** Additional features can be specified for every message.
	 * E.g. when a message should contain a timestamp or when the message
	 * has to be secured with a hash value. */
	public MessageFeatures getFeatures() {
		return DEFAULT_MESSAGE_FEATURES;
	}

	public void resolve(Config config, State state) {
		this.config = config;
		this.state = state;
		this.senderId = config.senderId;
		getFeatures().resolveConfig(config);
	}

	/** Clear id. */
	public void clearId() {
		this.msgId = 0;
	}

	/** Resolve id via id provider. */
	public void resolveId() {
		this.msgId = state.idProvider.createIdFor(this);
		log.trace(" * Resolved ID {} for {}", msgId, this);
	}

	/** Method called on processing of message.
	 * @param context context object */
	public void process(E context) {}

	@Override
	public int compareTo(final Message o) {
		if (config != null) {
			return state.idProvider.compare(this, o);
		}
		// compare by player id
		int compare = Integer.compare(getReceiverId(), o.getReceiverId());
		if (compare != 0) return compare;

		// compare by reliable mode
		compare = Integer.compare(getReliableMode().ordinal(), o.getReliableMode().ordinal());
		if (compare != 0) return compare;

		// second by id
		compare = Long.compare(msgId, o.msgId);
		if (compare != 0) return compare;

		return compare;
	}

	/** Override if you need something done before sending. */
	public void prepareToSend() {}

	/** Override if you need something done before receiving. */
	public Message<E> beforeReceive() 			{ return this; }

	/** Specify the reliable mode for this message. */
	public ReliableMode getReliableMode() 		{ return ReliableMode.SEQUENCE_NUMBER; }

	/** If this message is sent with the ACK reliable mode and is then
	 * acknowledged from the other side, this callback is called. */
	public void ackCallback() {}

	/** If this message can be stacked. Only use in conjunction with SEQUENCE_NUMBER. */
	public boolean stackable() 					{ return false; }

	/** If this message should be broadcasted by the server upon receipt. */
	public boolean broadcast() 					{ return false; }

	/** Whether message should be sent back to sender when broadcasting. */
	public boolean sendBroadcastBackToSender()	{ return true; }

	/** You can specify a unique key for this message. If the same key is
	 * received another time, the message will be discarded. */
	public Object getDiscardableKey() 			{ return null; }

	/** If timeout is greater than zero the message will be discarded if
	 * received too late.
	 * @return timeout in ms */
	public int getTimeOut() 					{ return 0; }

	/** If used, must be handled by the receiver manually. */
	public int executionPriority() 				{ return 0; }

	/** @return true if message should be discarded. */
	public boolean discard() {
		if (getTimeOut() > 0 && config.timeProvider.get() > getTimestamp() + getTimeOut()) {
			log.trace("Message {} discarded. TimeProvider: {}, TimeStamp+TimeOut: {}", new Object[]{this, config.timeProvider.get(), getTimestamp() + getTimeOut()});
			return true;
		} else {
			return false;
		}
	}

	public String toString() {
		return getClass().getName() + "(msgId=" + this.msgId + ", reliableMode=" + getReliableMode() + ", senderId=" + this.senderId + ", receiverId=" + this.receiverId + ")";
	}

	public long getTimestamp() {
		TimestampFeature timestampFeature = getFeatures().get(TimestampFeature.class);
		if (timestampFeature != null) {
			return timestampFeature.timestamp;
		}
		return 0L;
	}

	public int payloadLength() {
		if (payload instanceof byte[]) {
			byte[] payload = (byte[]) this.payload;
			return payload.length;
		} else {
			return -1;
		}
	}

	public enum ReliableMode {
		/** Message doesn't have to be transmitted reliably. */
		UNRELIABLE,
		/** Receiving side sends acknowledge packet to confirm receit. */
		ACK_PACKET,
		/** Receiving side checks consecutively numbered messages. */
		SEQUENCE_NUMBER,
	}
}
