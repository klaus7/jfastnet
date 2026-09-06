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
import com.jfastnet.IServerHooks;
import com.jfastnet.Server;
import com.jfastnet.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/** Broadcasting to all clients and sending to a single client.
 *
 * <p>A client only ever talks to the server. To reach the other clients it
 * sends a message whose {@link Message#broadcast()} returns true: the server
 * processes it and relays it to all connected clients, the sender included
 * (return false from {@link Message#sendBroadcastBackToSender()} to leave the
 * sender out). The server reaches all clients with {@link Server#send(Message)}
 * and a single one with {@link Server#send(int, Message)}. Broadcasting works
 * with every reliable mode; the messages below use the default
 * SEQUENCE_NUMBER.</p> */
public class BroadcastExample {

	private static final int PORT = 15150;

	/** Client id -> player name. The server assigns the ids when the clients connect. */
	private static final Map<Integer, String> names = new ConcurrentHashMap<>();

	/** Player name -> lines the player has received. */
	private static final Map<String, List<String>> inboxes = new ConcurrentHashMap<>();

	/** A chat line. Sent by a client, relayed to everybody by the server. */
	public static class ChatMessage extends Message {

		String from;
		String text;

		/** no-arg constructor required for serialization. */
		private ChatMessage() {}

		ChatMessage(String from, String text) {
			this.from = from;
			this.text = text;
		}

		/** The server relays this message to all clients after processing it. */
		@Override
		public boolean broadcast() {
			return true;
		}

		/** Called on the server and afterwards on every client. */
		@Override
		public void process(Object context) {
			if (getState().isHost()) {
				System.out.println("[server] relaying chat message from " + from);
			} else {
				deliver(this, "chat from " + from + ": " + text);
			}
		}
	}

	/** A line from the server, sent to all clients or to a single one. */
	public static class ServerNotice extends Message {

		String text;

		/** no-arg constructor required for serialization. */
		private ServerNotice() {}

		ServerNotice(String text) {
			this.text = text;
		}

		@Override
		public void process(Object context) {
			deliver(this, "notice: " + text);
		}
	}

	/** Puts the line into the inbox of the client that received the message. */
	private static void deliver(Message message, String line) {
		// getConfig() belongs to the receiving peer, so its senderId is the id of this client.
		String me = names.get(message.getConfig().senderId);
		System.out.println("[" + me + "] " + line);
		inboxes.computeIfAbsent(me, name -> new CopyOnWriteArrayList<>()).add(line);
	}

	public static void main(String[] args) throws InterruptedException {
		Config serverConfig = new Config().setBindPort(PORT);
		// The server hooks tell us when clients come and go.
		serverConfig.setServerHooks(new IServerHooks() {
			@Override
			public void onRegister(int clientId) {
				System.out.println("[server] client " + clientId + " joined");
			}

			@Override
			public void onUnregister(int clientId) {
				System.out.println("[server] client " + clientId + " left");
			}
		});
		Server server = new Server(serverConfig);
		List<Client> clients = new ArrayList<>();
		try {
			server.start();
			Client alice = connect(clients, "Alice");
			Client bob = connect(clients, "Bob");
			connect(clients, "Carol");

			// Alice's message goes to the server, which relays it to Alice, Bob and Carol.
			alice.send(new ChatMessage("Alice", "Hello everyone!"));
			runUntil(() -> inboxSize("Alice") == 1 && inboxSize("Bob") == 1 && inboxSize("Carol") == 1, 5_000, server, clients);

			// The server can address all clients or a single one.
			server.send(new ServerNotice("Welcome to the server!"));
			server.send(bob.getConfig().senderId, new ServerNotice("Bob, you are up next."));
			runUntil(() -> inboxSize("Alice") == 2 && inboxSize("Bob") == 3 && inboxSize("Carol") == 2, 5_000, server, clients);

			System.out.println("Alice received: " + inboxes.get("Alice"));
			System.out.println("Bob received:   " + inboxes.get("Bob"));
			System.out.println("Carol received: " + inboxes.get("Carol"));
		} finally {
			clients.forEach(Client::stop);
			server.stop();
		}
	}

	private static Client connect(List<Client> clients, String name) {
		Client client = new Client(new Config().setPort(PORT));
		clients.add(client);
		client.start();
		client.blockingWaitUntilConnected();
		if (!client.isConnected()) {
			throw new IllegalStateException(name + " couldn't connect.");
		}
		// The server assigned an id to the client, remember whose it is.
		names.put(client.getConfig().senderId, name);
		System.out.println(name + " is client " + client.getConfig().senderId);
		return client;
	}

	private static int inboxSize(String name) {
		List<String> inbox = inboxes.get(name);
		return inbox == null ? 0 : inbox.size();
	}

	/** Calls process() on all peers until the condition is met. A game does
	 * the same once per frame: resending messages, requesting missing ones and
	 * sending queued message parts all happen inside process(). */
	private static void runUntil(BooleanSupplier condition, long timeoutMs, Server server, List<Client> clients) throws InterruptedException {
		List<IPeerController> peers = new ArrayList<>(clients);
		peers.add(server);
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
