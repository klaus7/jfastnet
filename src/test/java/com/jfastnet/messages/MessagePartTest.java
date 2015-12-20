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

import com.jfastnet.AbstractTest;
import com.jfastnet.Config;
import com.jfastnet.State;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class MessagePartTest extends AbstractTest {

	static class BigMessage extends Message {
		String s;

		public BigMessage(int lines) {
			Random r = new Random(System.currentTimeMillis());
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lines; i++) {
				//sb.append("asdfasdfasdfasdasdfadfasdsadfa"); // 30 chars
				for (int j = 0; j < 15; j++) {
					sb.append((char) r.nextInt());
				}
			}
			// 1 char UTF-8 encoded in ASCII space consumes 1 byte of memory
			// payload size = 50 * 30 byte = 1500 byte
			sb.append("END");
			// + 3 = 1503 byte
			s = sb.toString();
		}

		@Override
		public ReliableMode getReliableMode() {
			return ReliableMode.ACK_PACKET;
		}

		@Override
		public void process(Object context) {}
	}


	boolean bigMsgReceived = false;
	@Test
	public void testPartsReceived() {

		Config config = newClientConfig();
		config.externalReceiver = message -> {
			if (message instanceof BigMessage) {
				BigMessage bigMessage = (BigMessage) message;
				bigMsgReceived = true;
			}
		};
		BigMessage bigMessage = new BigMessage(150);
		bigMessage.setConfig(config);
		State state = new State(config);
		bigMessage.setState(state);
		List<MessagePart> messageParts = MessagePart.createFromMessage(state, 0, bigMessage, 1024, Message.ReliableMode.ACK_PACKET);

		assertNotNull("message parts are null", messageParts);
		messageParts.forEach(messagePart -> messagePart.copyAttributesFrom(bigMessage));
		MessagePart lastPart = messageParts.get(messageParts.size() - 1);
		assertTrue("last messag part must have set last flag to true", lastPart.last);

		lastPart.process(null);
		assertFalse(bigMsgReceived);

		messageParts.forEach(messagePart -> {
			if (messagePart.partNumber % 2 == 0) messagePart.process(null);
		});
		assertFalse(bigMsgReceived);

		messageParts.forEach(messagePart -> {
			if (messagePart.partNumber % 2 == 1) messagePart.process(null);
		});
		assertTrue(bigMsgReceived);

	}

	@Test
	public void testCompression() {
		String input = "This is a compression test";
		byte[] bytes = input.getBytes();
		byte[] compressed = MessagePart.compress(bytes);
		byte[] decompressed = MessagePart.decompress(compressed);
		String s = new String(decompressed);
		System.out.println(s);
		assertEquals(input, s);
	}

}