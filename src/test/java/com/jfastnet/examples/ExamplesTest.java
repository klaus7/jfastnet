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

package com.jfastnet.examples;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Runs every example, so they can't silently stop working. Each example
 * checks its own expectations and throws if they aren't met. */
public class ExamplesTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(120);

	@Test
	public void helloWorld() {
		run(HelloWorld::main);
	}

	@Test
	public void unreliable() {
		run(UnreliableExample::main);
	}

	@Test
	public void ackPacket() {
		run(AckPacketExample::main);
	}

	@Test
	public void sequenceNumber() {
		run(SequenceNumberExample::main);
	}

	@Test
	public void stackedMessages() {
		run(StackedMessagesExample::main);
	}

	@Test
	public void broadcast() {
		run(BroadcastExample::main);
	}

	@Test
	public void bigMessage() {
		run(BigMessageExample::main);
	}

	private static void run(ThrowingConsumer<String[]> example) {
		assertTimeoutPreemptively(TIMEOUT, () -> example.accept(new String[0]));
	}
}
