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

package com.jfastnet.messages;

import com.jfastnet.Config;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Used for bigger messages to be transferred in parts.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class MessagePart extends Message implements IDontFrame {

	/** Whether this is the last part to construct the message. */
	boolean last;

	/** The id servers the purpose to allow receiving of multiple different
	 * big messages. */
	int id;

	/** The number tells us which part of the message we received. */
	int partNumber;

	/** The payload of the message part. */
	byte[] bytes;

	private MessagePart(int id, int partNumber, byte[] bytes) {
		this.id = id;
		this.partNumber = partNumber;
		this.bytes = bytes;
	}

	public static List<MessagePart> createFromMessage(Config config, int id, Message message, int chunkSize) {
		return createFromMessage(config, id, message, chunkSize, message.getReliableMode());
	}

	public static List<MessagePart> createFromMessage(Config config, int id, Message message, int chunkSize, ReliableMode reliableMode) {
		config.udpPeer.createPayload(message);
		// createPayload has to create a byte array
		// Depends on the UDP peer if this is possible.
		if (message.payload instanceof byte[]) {
			byte[] bytes = (byte[]) message.payload;
			return createFromByteArray(id, bytes, chunkSize, reliableMode);
		}
		log.error("Message could not be created, because of missing byte array payload.");
		return null;
	}

	public static List<MessagePart> createFromByteArray(int id, byte[] bytes, int chunkSize, ReliableMode reliableMode) {
		log.info("Create message with {} bytes and chunk size {}", bytes.length, chunkSize);

		int from = 0;
		int to = chunkSize;
		int partNumber = 0;

		List<MessagePart> messages = new ArrayList<>();
		while (from < bytes.length) {
			byte[] chunk = Arrays.copyOfRange(bytes, from, to);
			if (ReliableMode.SEQUENCE_NUMBER.equals(reliableMode)) {
				messages.add(new MessagePart(id, partNumber, chunk));
			} else if (ReliableMode.ACK_PACKET.equals(reliableMode)) {
				messages.add(new AckMessagePart(id, partNumber, chunk));
			}
			partNumber++;
			from += chunkSize;
			to += chunkSize;
		}
		messages.get(messages.size() - 1).last = true;

		return messages;
	}

	@Override
	public void process() {
		log.trace("Part number {} of id {} received.", partNumber, id);

		SortedMap<Integer, SortedMap<Integer, MessagePart>> arrayBufferMap = getConfig().byteArrayBufferMap;
		SortedMap<Integer, MessagePart> byteArrayBuffer = arrayBufferMap.get(id);
		if (byteArrayBuffer == null) {
			byteArrayBuffer = new TreeMap<>();
			arrayBufferMap.put(id, byteArrayBuffer);
		}

		byteArrayBuffer.put(partNumber, this);
		if (allPartsReceived()) {
			Collection<byte[]> values = byteArrayBuffer.values().stream().collect(Collectors.mapping(messagePart -> messagePart.bytes, Collectors.toList()));
			log.info("Last of {} parts for splitted message received.", values.size());
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				for (byte[] value : values) {
					bos.write(value);
				}
				bos.flush();
				byte[] byteArray = bos.toByteArray();
				Message messageFromByteArray = getConfig().serialiser.deserialise(getConfig(), byteArray, 0, bos.size());
				if (messageFromByteArray == null) {
					log.error("Deserialised message was null! See previous errors.");
				} else {
					log.info("Message created: {}", messageFromByteArray);
					getConfig().externalReceiver.receive(messageFromByteArray);
				}
			} catch (IOException e) {
				log.error("Error writing byte array.", e);
			}
			byteArrayBuffer.clear();
		}
	}

	public boolean allPartsReceived() {
		if (ReliableMode.SEQUENCE_NUMBER.equals(getReliableMode())) {
			return last;
		} else if (ReliableMode.ACK_PACKET.equals(getReliableMode())) {
			SortedMap<Integer, MessagePart> byteArrayBuffer = getConfig().byteArrayBufferMap.get(id);
			if (byteArrayBuffer == null) {
				log.trace("byteArrayBuffer == null");
				return false;
			}
			Collection<MessagePart> messageParts = byteArrayBuffer.values();
			// Check if the last part was already received
			boolean hasLastPart = messageParts.stream().filter(messagePart -> messagePart.last).count() > 0L;
			if (!hasLastPart) {
				log.trace("!hasLastPart");
				return false;
			}
			// Check if all required messages are received
			int expectedPartNumber = 0;
			for (MessagePart messagePart : messageParts) {
				if (messagePart.partNumber != expectedPartNumber) {
					log.trace("messagePart.partNumber != expectedPartNumber: {} != {}", messagePart.partNumber, expectedPartNumber);
					return false;
				}
				expectedPartNumber++;
			}
			return true;
		} else {
			throw new UnsupportedOperationException("Unsupported reliable mode.");
		}
	}

	/** MessagePart with ACK reliable mode. */
	public static class AckMessagePart extends MessagePart {
		private AckMessagePart(int id, int partNumber, byte[] bytes) {
			super(id, partNumber, bytes);
		}
		@Override
		public ReliableMode getReliableMode() {
			return ReliableMode.ACK_PACKET;
		}
	}
}
