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
import com.jfastnet.messages.CompressedMessage;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.MessagePart;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Messages that don't fit into a single UDP packet.
 *
 * <p>A packet must not exceed {@link Config#maximumUdpPacketSize} (1024 bytes
 * by default). A reliable message with a bigger payload is split into
 * {@link MessagePart}s automatically ({@link Config#autoSplitTooBigMessages}).
 * The parts are queued, leave the queue one at a time from process()
 * ({@link Config#queuedMessagesDelay} ms apart) and are put back together on
 * the other side, which then processes the original message. Unreliable
 * messages are never split: send() logs an error and returns false.</p>
 *
 * <p>With {@link Config#compressBigMessages} the parts are compressed first,
 * which makes a big difference for repetitive data like a tile map. A single
 * message can also be compressed on its own with
 * {@link CompressedMessage#createFrom}. Sender and receiver have to agree on
 * the compression setting.</p> */
public class BigMessageExample {

	private static final int PORT = 15150;

	/** Size of the level in tiles, about 20 times the size of a packet. */
	private static final int TILE_COUNT = 20_000;

	/** Level that arrived at the server. */
	private static final AtomicReference<LevelData> receivedLevel = new AtomicReference<>();

	/** Number of message parts the server received for the current level. */
	private static final AtomicInteger receivedParts = new AtomicInteger();

	/** A complete level. Far too big for a single packet. */
	public static class LevelData extends Message {

		byte[] tiles;

		/** no-arg constructor required for serialization. */
		private LevelData() {}

		LevelData(byte[] tiles) {
			this.tiles = tiles;
		}

		/** Called on the receiving side once all parts have arrived. */
		@Override
		public void process(Object context) {
			receivedLevel.set(this);
		}
	}

	public static void main(String[] args) throws InterruptedException {
		byte[] level = createLevel();
		System.out.println("Sending a level of " + TILE_COUNT + " tiles, the packet size limit is 1024 bytes.");
		sendLevel(level, false);
		sendLevel(level, true);
	}

	private static void sendLevel(byte[] level, boolean compress) throws InterruptedException {
		String mode = compress ? "With compression" : "Without compression";
		Config serverConfig = new Config().setBindPort(PORT).setCompressBigMessages(compress);
		// Every received message passes through the external receiver,
		// JFastNet's own messages included. This one counts the message parts
		// and hands every message on to process(), like the default receiver.
		serverConfig.setExternalReceiver(message -> {
			if (message instanceof MessagePart) {
				receivedParts.incrementAndGet();
			}
			message.process(null);
		});
		Server server = new Server(serverConfig);
		Client client = new Client(new Config().setPort(PORT).setCompressBigMessages(compress));
		try {
			server.start();
			client.start();
			client.blockingWaitUntilConnected();

			receivedLevel.set(null);
			receivedParts.set(0);
			// Too big for a packet, so it gets split up and the parts are queued.
			client.send(new LevelData(level));
			// The parts are sent from process(), one at a time.
			runUntil(() -> receivedLevel.get() != null, 30_000, server, client);
			checkLevel(level);
			System.out.printf("%s the level arrived in %s.%n", mode,
					receivedParts.get() == 1 ? "a single part" : receivedParts.get() + " parts");

			if (compress) {
				// A single message can also be compressed on its own. If the
				// result fits into a packet, no splitting is needed at all.
				receivedLevel.set(null);
				receivedParts.set(0);
				Message compressed = CompressedMessage.createFrom(client.getState(), new LevelData(level));
				client.send(compressed);
				runUntil(() -> receivedLevel.get() != null, 30_000, server, client);
				checkLevel(level);
				System.out.printf("As a CompressedMessage the level travelled in a single packet of %d bytes.%n",
						compressed.payloadLength());
			}
		} finally {
			client.stop();
			server.stop();
		}
	}

	/** A tile map: mostly floor, walls around the border and every 40th column. */
	private static byte[] createLevel() {
		int width = 80;
		byte[] tiles = new byte[TILE_COUNT];
		for (int i = 0; i < tiles.length; i++) {
			int x = i % width;
			int y = i / width;
			boolean wall = x == 0 || x == width - 1 || y == 0 || y == tiles.length / width - 1 || x % 40 == 0;
			tiles[i] = (byte) (wall ? 1 : 0);
		}
		return tiles;
	}

	private static void checkLevel(byte[] level) {
		if (!Arrays.equals(receivedLevel.get().tiles, level)) {
			throw new IllegalStateException("The received level differs from the sent one.");
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
