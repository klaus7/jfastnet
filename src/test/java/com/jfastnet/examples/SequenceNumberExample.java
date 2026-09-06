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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/** Sending mode {@link Message.ReliableMode#SEQUENCE_NUMBER}: reliable and ordered.
 *
 * <p>Every message gets a consecutive number. The receiver processes a message
 * only after all messages before it, so as soon as it spots a gap it asks the
 * sender for the missing messages and holds the later ones back until the gap
 * is filled. There is no acknowledgement traffic in the normal case, which
 * makes this the cheaper of the two reliable modes. It is the default mode of
 * every message and the right choice for commands whose order matters. How
 * many missing ids are requested at once and how often is configured in
 * {@link com.jfastnet.processors.ReliableModeSequenceProcessor.ProcessorConfig}.</p>
 *
 * <p>A gap can only be noticed when a later message arrives. If the last
 * message of a burst gets lost, the next keep alive message
 * ({@link Config#keepAliveInterval}) reveals the gap, so a shorter interval
 * means faster recovery at the price of a few more packets.</p>
 *
 * <p>The example first drops one packet on purpose to show that the later
 * commands wait for the missing one, then it sends a burst of commands through
 * a connection that loses 20 % of all packets in both directions.</p> */
public class SequenceNumberExample {

	private static final int PORT = 15150;

	/** Order in which the client executed the commands. */
	private static final List<Integer> processedOrder = new CopyOnWriteArrayList<>();

	/** Print every single command? Turned off for the burst in the second part. */
	private static volatile boolean printCommands = true;

	/** A command that has to be executed in the same order on every peer. */
	public static class GameCommand extends Message {

		int number;

		/** no-arg constructor required for serialization. */
		private GameCommand() {}

		GameCommand(int number) {
			this.number = number;
		}

		/** SEQUENCE_NUMBER is the default anyway, this is just to make it explicit. */
		@Override
		public ReliableMode getReliableMode() {
			return ReliableMode.SEQUENCE_NUMBER;
		}

		/** Called on the receiving side, strictly in sending order. */
		@Override
		public void process(Object context) {
			processedOrder.add(number);
			if (printCommands) {
				System.out.println("[client] executed command #" + number);
			}
		}
	}

	public static void main(String[] args) throws InterruptedException {
		// A short keep alive interval lets the client notice a lost last message quickly.
		Server server = new Server(new Config().setBindPort(PORT).setKeepAliveInterval(500));
		Client client = new Client(new Config().setPort(PORT).setKeepAliveInterval(500));
		try {
			server.start();
			client.start();
			client.blockingWaitUntilConnected();

			System.out.println("--- Part 1: one packet gets lost ---");
			server.send(new GameCommand(1));
			server.send(new GameCommand(2));
			runUntil(() -> processedOrder.size() == 2, 5_000, server, client);

			// The next packet the client receives is thrown away, as if the
			// network had lost it. That is going to be command #3.
			client.getConfig().debug.setDiscardNextPacket(true);
			for (int number = 3; number <= 10; number++) {
				server.send(new GameCommand(number));
			}
			runUntil(() -> processedOrder.size() == 10, 10_000, server, client);
			System.out.println("Sent order:      [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]");
			System.out.println("Processed order: " + processedOrder);
			System.out.println("When command #4 arrived the client noticed that #3 was missing, requested it");
			System.out.println("from the server and held #4 to #10 back until #3 had arrived.");
			checkProcessedInOrder(10);

			System.out.println("--- Part 2: 20 % packet loss in both directions ---");
			printCommands = false;
			processedOrder.clear();
			server.getConfig().debug.setEnabled(true).setLostPacketsPercentage(20);
			client.getConfig().debug.setEnabled(true).setLostPacketsPercentage(20);
			long resentBefore = server.getConfig().netStats.resentMessages.get();
			int burst = 100;
			for (int number = 1; number <= burst; number++) {
				server.send(new GameCommand(number));
			}
			runUntil(() -> processedOrder.size() == burst, 30_000, server, client);
			checkProcessedInOrder(burst);
			long resent = server.getConfig().netStats.resentMessages.get() - resentBefore;
			System.out.printf("All %d commands were executed in order. The server resent %d messages on request.%n",
					burst, resent);
		} finally {
			client.stop();
			server.stop();
		}
	}

	private static void checkProcessedInOrder(int commandCount) {
		if (processedOrder.size() != commandCount) {
			throw new IllegalStateException("Expected " + commandCount + " commands, got " + processedOrder);
		}
		for (int i = 0; i < commandCount; i++) {
			if (processedOrder.get(i) != i + 1) {
				throw new IllegalStateException("Commands were processed out of order: " + processedOrder);
			}
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
