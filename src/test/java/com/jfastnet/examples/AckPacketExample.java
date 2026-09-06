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

import com.jfastnet.Client;
import com.jfastnet.Config;
import com.jfastnet.IPeerController;
import com.jfastnet.Server;
import com.jfastnet.messages.Message;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/** Sending mode {@link Message.ReliableMode#ACK_PACKET}: reliable, but unordered.
 *
 * <p>The receiver answers every message with an acknowledge packet. As long as
 * the sender doesn't get that packet, it resends the message: after 400 ms at
 * first, then with a growing interval (see
 * {@link com.jfastnet.processors.ReliableModeAckProcessor.ProcessorConfig}).
 * Messages are processed as soon as they arrive, so a resent message is
 * processed after the messages that were sent later. Use this mode for
 * independent events that must not get lost, like an item being spawned.</p>
 *
 * <p>The example first drops one packet on purpose to show the out-of-order
 * processing and the {@link Message#ackCallback()} on the sending side, then
 * it sends a burst of events through a connection that loses 10 % of all
 * packets in both directions. Note that the resend interval doubles with
 * every unsuccessful attempt, so a single unlucky event can take a few seconds
 * to get through.</p> */
public class AckPacketExample {

	private static final int PORT = 15150;

	/** Order in which the client processed the events. */
	private static final List<Integer> processedOrder = new CopyOnWriteArrayList<>();

	/** Events the server knows to have arrived, because the client acknowledged them. */
	private static final Set<Integer> acknowledged = ConcurrentHashMap.newKeySet();

	/** Print every single event? Turned off for the burst in the second part. */
	private static volatile boolean printEvents = true;

	/** An event that must arrive, but whose order doesn't matter. */
	public static class SpawnEvent extends Message {

		int number;

		/** no-arg constructor required for serialization. */
		private SpawnEvent() {}

		SpawnEvent(int number) {
			this.number = number;
		}

		@Override
		public ReliableMode getReliableMode() {
			return ReliableMode.ACK_PACKET;
		}

		/** Called on the receiving side. */
		@Override
		public void process(Object context) {
			processedOrder.add(number);
			if (printEvents) {
				System.out.println("[client] processed spawn event #" + number);
			}
		}

		/** Called on the sending side as soon as the acknowledge packet for
		 * this message arrives. */
		@Override
		public void ackCallback() {
			acknowledged.add(number);
			if (printEvents) {
				System.out.println("[server] spawn event #" + number + " was acknowledged by the client");
			}
		}
	}

	public static void main(String[] args) throws InterruptedException {
		Server server = new Server(new Config().setBindPort(PORT));
		Client client = new Client(new Config().setPort(PORT));
		try {
			server.start();
			client.start();
			client.blockingWaitUntilConnected();

			System.out.println("--- Part 1: one packet gets lost ---");
			server.send(new SpawnEvent(1));
			server.send(new SpawnEvent(2));
			runUntil(() -> processedOrder.size() == 2, 5_000, server, client);

			// The next packet the client receives is thrown away, as if the
			// network had lost it. That is going to be spawn event #3.
			client.getConfig().debug.setDiscardNextPacket(true);
			for (int number = 3; number <= 10; number++) {
				server.send(new SpawnEvent(number));
			}
			runUntil(() -> processedOrder.size() == 10 && acknowledged.size() == 10, 10_000, server, client);
			System.out.println("Sent order:      [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]");
			System.out.println("Processed order: " + processedOrder);
			System.out.println("Event #3 was resent by the server and processed last. That is fine for");
			System.out.println("independent events; use SEQUENCE_NUMBER when the order matters.");
			checkEachEventProcessedOnce(10);

			System.out.println("--- Part 2: 10 % packet loss in both directions ---");
			printEvents = false;
			processedOrder.clear();
			acknowledged.clear();
			server.getConfig().debug.setEnabled(true).setLostPacketsPercentage(10);
			client.getConfig().debug.setEnabled(true).setLostPacketsPercentage(10);
			int burst = 100;
			for (int number = 1; number <= burst; number++) {
				server.send(new SpawnEvent(number));
			}
			// Lost events get resent until they are acknowledged. A lost
			// acknowledgement causes a resend as well, which the client
			// recognises as a duplicate and drops without processing it again.
			runUntil(() -> processedOrder.size() == burst && acknowledged.size() == burst, 60_000, server, client);
			checkEachEventProcessedOnce(burst);
			System.out.printf("All %d events were processed exactly once and acknowledged.%n", burst);
		} finally {
			client.stop();
			server.stop();
		}
	}

	private static void checkEachEventProcessedOnce(int eventCount) {
		if (new HashSet<>(processedOrder).size() != eventCount || processedOrder.size() != eventCount) {
			throw new IllegalStateException("Expected " + eventCount + " distinct events, got " + processedOrder);
		}
	}

	/** Calls process() on all peers until the condition is met. A game does
	 * the same once per frame: resending messages, requesting missing ones and
	 * sending queued message parts all happen inside process(). */
	private static void runUntil(BooleanSupplier condition, long timeoutMs, IPeerController... peers) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				throw new IllegalStateException("Gave up after " + timeoutMs + " ms.");
			}
			for (IPeerController peer : peers) {
				peer.process();
			}
			Thread.sleep(10);
		}
	}
}
