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
import com.jfastnet.idprovider.ReliableModeIdProvider;
import com.jfastnet.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/** Stacked messages: reliable, ordered and repaired without a round trip.
 *
 * <p>A message that returns true from {@link Message#stackable()} isn't sent on
 * its own. Instead every outgoing packet carries all stackable messages that
 * the receiver hasn't acknowledged yet. When a packet gets lost, the next
 * packet contains the lost messages again, so the receiver never has to ask
 * for them and doesn't wait a round trip before it can continue. The receiver
 * acknowledges in batches (after 7 messages or 300 ms, see
 * {@link com.jfastnet.processors.StackedMessageProcessor.ProcessorConfig}),
 * which keeps the stacks small. Stacking builds on SEQUENCE_NUMBER, the default
 * mode, and requires the {@link ReliableModeIdProvider}, which hands out the
 * same message ids to all clients. Sending a stackable message with the default
 * id provider throws an UnsupportedOperationException.</p>
 *
 * <p>The example sends the same burst of world updates to three clients with
 * 20 % packet loss twice: first as plain sequenced messages, then as stackable
 * ones. Watch how often the server has to resend a message on request.</p> */
public class StackedMessagesExample {

	private static final int PORT = 15150;
	private static final int CLIENT_COUNT = 3;
	private static final int UPDATE_COUNT = 30;

	/** Key: client id, value: numbers of the updates the client processed, in processing order. */
	private static final Map<Integer, List<Integer>> processedByClient = new ConcurrentHashMap<>();

	/** A world update sent as an ordinary SEQUENCE_NUMBER message. */
	public static class WorldUpdate extends Message {

		int number;

		/** no-arg constructor required for serialization. */
		WorldUpdate() {}

		WorldUpdate(int number) {
			this.number = number;
		}

		/** Called on the receiving side. */
		@Override
		public void process(Object context) {
			// getConfig() belongs to the receiving peer, so its senderId is the id of this client.
			processedByClient.computeIfAbsent(getConfig().senderId, id -> new CopyOnWriteArrayList<>()).add(number);
		}
	}

	/** The same update, but stackable. */
	public static class StackableWorldUpdate extends WorldUpdate {

		/** no-arg constructor required for serialization. */
		private StackableWorldUpdate() {}

		StackableWorldUpdate(int number) {
			super(number);
		}

		@Override
		public boolean stackable() {
			return true;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		Server server = new Server(newConfig().setBindPort(PORT));
		List<Client> clients = new ArrayList<>();
		try {
			server.start();
			for (int i = 0; i < CLIENT_COUNT; i++) {
				Client client = new Client(newConfig().setPort(PORT));
				clients.add(client);
				client.start();
				client.blockingWaitUntilConnected();
			}
			// Simulate a bad connection: every client loses 20 % of the packets it receives.
			clients.forEach(client -> client.getConfig().debug.setEnabled(true).setLostPacketsPercentage(20));

			System.out.println("--- Part 1: plain SEQUENCE_NUMBER messages ---");
			sendBurst(server, clients, WorldUpdate::new);

			System.out.println("--- Part 2: stackable messages ---");
			sendBurst(server, clients, StackableWorldUpdate::new);
		} finally {
			clients.forEach(Client::stop);
			server.stop();
		}
	}

	private static Config newConfig() {
		Config config = new Config();
		// Stacking requires the same message ids for all clients.
		config.setIdProviderClass(ReliableModeIdProvider.class);
		// Let the keep alive messages carry the stack as well, then even a
		// lost last packet gets repaired without a request.
		config.setStackKeepAliveMessages(true);
		config.setKeepAliveInterval(500);
		return config;
	}

	private static void sendBurst(Server server, List<Client> clients, IntFunction<WorldUpdate> newUpdate) throws InterruptedException {
		processedByClient.clear();
		long resentBefore = server.getConfig().netStats.resentMessages.get();
		long start = System.currentTimeMillis();

		for (int number = 1; number <= UPDATE_COUNT; number++) {
			server.send(newUpdate.apply(number));
			// One frame of the game loop: process everyone, then wait for the next frame.
			processAll(server, clients);
			Thread.sleep(20);
		}
		runUntil(() -> allClientsProcessedEverything(), 30_000, server, clients);

		long duration = System.currentTimeMillis() - start;
		long resent = server.getConfig().netStats.resentMessages.get() - resentBefore;
		System.out.printf("All %d clients processed all %d updates in order after %d ms.%n", CLIENT_COUNT, UPDATE_COUNT, duration);
		System.out.printf("The server had to resend %d messages on request.%n", resent);
	}

	private static boolean allClientsProcessedEverything() {
		if (processedByClient.size() < CLIENT_COUNT) {
			return false;
		}
		for (List<Integer> processed : processedByClient.values()) {
			if (processed.size() < UPDATE_COUNT) {
				return false;
			}
			for (int i = 0; i < processed.size(); i++) {
				if (processed.get(i) != i + 1) {
					throw new IllegalStateException("Updates were processed out of order: " + processed);
				}
			}
		}
		return true;
	}

	/** Calls process() on all peers, like a game does once per frame: resending
	 * messages, requesting missing ones and sending queued message parts all
	 * happen inside process(). */
	private static void processAll(Server server, List<Client> clients) {
		server.process();
		clients.forEach(IPeerController::process);
	}

	/** Keeps processing all peers until the condition is met. */
	private static void runUntil(BooleanSupplier condition, long timeoutMs, Server server, List<Client> clients) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				throw new IllegalStateException("Gave up after " + timeoutMs + " ms.");
			}
			processAll(server, clients);
			Thread.sleep(10);
		}
	}
}
