/*******************************************************************************
 * Copyright 2016 Klaus Pfeiffer - klaus@allpiper.com
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

import com.jfastnet.State;
import com.jfastnet.exceptions.DeserialiseException;
import lombok.extern.slf4j.Slf4j;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class CompressedMessage<E> extends Message<E> {

	byte[] compressedPayload;

	private CompressedMessage() {}

	public static <E> Message<E> createFrom(State state, Message<E> containerMessage) {
		CompressedMessage<E> compressedMessage = new CompressedMessage<>();
		compressedMessage.copyAttributesFrom(containerMessage);
		containerMessage.setSenderId(0);

		state.getUdpPeer().createPayload(containerMessage);
		if (containerMessage.payload instanceof byte[]) {
			byte[] bytes = (byte[]) containerMessage.payload;
			if (state.getConfig().compressBigMessages) {
				byte[] compressedBytes = MessagePart.compress(bytes);
				if (compressedBytes != null) {
					compressedMessage.compressedPayload = compressedBytes;
					return compressedMessage;
				}
			}
		}
		return containerMessage;
	}

	@Override
	public Message<E> beforeReceive() {
		byte[] decompressed = MessagePart.decompress(compressedPayload);
		Message deserialised = getConfig().serialiser.deserialise(decompressed, 0, decompressed.length);
		if (deserialised == null) {
			log.error("Deserialised message was null! See previous errors.");
			throw new DeserialiseException("Deserialised message was null! See previous errors.");
		}
		deserialised.copyAttributesFrom(this);
		deserialised.getFeatures().resolve();
		deserialised.msgId = this.msgId;
		return deserialised;
	}
}
