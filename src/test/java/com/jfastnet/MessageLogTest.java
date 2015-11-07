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

import com.jfastnet.messages.Message;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class MessageLogTest {

	private volatile int i;

	static class NumberMessage extends Message {
		int number;
		public NumberMessage(int number) {
			this.number = number;
		}
	}

	@Test
	public void testMessageLog() throws InterruptedException {
		Config config = new Config();
		MessageLog messageLog = new MessageLog();
		messageLog.receiveFilter = new MessageLog.ReliableMessagesPredicate();
		assertThat(messageLog.received.size(), is(0));
		messageLog.addReceived(new Message() {
			@Override
			public ReliableMode getReliableMode() {
				return ReliableMode.UNRELIABLE;
			}
		});
		assertThat(messageLog.received.size(), is(0));
		messageLog.addReceived(new Message() {
			@Override
			public ReliableMode getReliableMode() {
				return ReliableMode.SEQUENCE_NUMBER;
			}
		});
		assertThat(messageLog.received.size(), is(1));

		Thread t1 = new Thread(() -> {
			while (true) {
				i++;
				NumberMessage msg = new NumberMessage(i);
				msg.resolveConfig(config);
				msg.resolveId();
				messageLog.addSent(msg);
			}
		});
		t1.start();

		int count = 4000;
		while (i < count);
		int j = 3000;
		while (j < count) {
			j++;
			while (j + 1000 > i);

			Message msg;

			msg = messageLog.sentMap.get(MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, 0, j));
			assertNotNull("msg id: " + j, msg);
			assertThat(msg.getMsgId(), is((long) j));

			int offset = 500;
			msg = messageLog.sentMap.get(MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, 0, j - offset));
			assertThat(msg.getMsgId(), is((long) j - offset));
		}
		t1.interrupt();
	}
}