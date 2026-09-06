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
import com.jfastnet.Server;
import com.jfastnet.messages.Message;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Sending mode {@link Message.ReliableMode#UNRELIABLE}: fire and forget.
 *
 * <p>An unreliable message is sent exactly once. If the packet gets lost,
 * nobody notices and nothing is resent, which makes this the cheapest sending
 * mode: no acknowledgements, no sequence bookkeeping, no waiting. Use it for
 * data that is superseded by the next message anyway, like the position of a
 * player that is sent many times per second.</p>
 *
 * <p>The example simulates a lossy network with {@link Config.Debug} and shows
 * that the game simply keeps going although about a third of the updates never
 * arrive.</p> */
public class UnreliableExample {

	private static final int PORT = 15150;

	/** Number of position updates the client sends. */
	private static final int UPDATE_COUNT = 100;

	/** Number of updates that arrived at the server. */
	private static final AtomicInteger receivedCount = new AtomicInteger();

	/** Most recent update the server received. */
	private static final AtomicReference<PositionUpdate> latestUpdate = new AtomicReference<>();

	/** A position update. Missing one is fine, the next one supersedes it. */
	public static class PositionUpdate extends Message {

		int tick;
		float x;
		float y;

		/** no-arg constructor required for serialization. */
		private PositionUpdate() {}

		PositionUpdate(int tick, float x, float y) {
			this.tick = tick;
			this.x = x;
			this.y = y;
		}

		/** The default mode is SEQUENCE_NUMBER, so it has to be overridden. */
		@Override
		public ReliableMode getReliableMode() {
			return ReliableMode.UNRELIABLE;
		}

		/** Called on the receiving side. Unreliable messages are processed
		 * as soon as they arrive, in whatever order they arrive. */
		@Override
		public void process(Object context) {
			receivedCount.incrementAndGet();
			latestUpdate.set(this);
		}
	}

	public static void main(String[] args) throws InterruptedException {
		Server server = new Server(new Config().setBindPort(PORT));
		Client client = new Client(new Config().setPort(PORT));
		try {
			server.start();
			client.start();
			client.blockingWaitUntilConnected();

			// Simulate a bad connection: from now on the server throws away
			// 30 % of all packets it receives, as if the network had lost them.
			server.getConfig().debug.setEnabled(true).setLostPacketsPercentage(30);

			// Send the position over and over, like a game does on every frame.
			for (int tick = 1; tick <= UPDATE_COUNT; tick++) {
				client.send(new PositionUpdate(tick, tick * 0.5f, 10f));
				server.process();
				client.process();
				Thread.sleep(5);
			}

			// Give the packets that are still in flight a moment to arrive.
			// Waiting any longer wouldn't change a thing: lost packets stay lost.
			Thread.sleep(200);

			PositionUpdate latest = latestUpdate.get();
			if (latest == null) {
				throw new IllegalStateException("Not a single update arrived.");
			}
			System.out.printf("The server received %d of %d position updates and never asked for the rest.%n",
					receivedCount.get(), UPDATE_COUNT);
			System.out.printf("Latest known position: tick %d at (%.1f, %.1f)%n", latest.tick, latest.x, latest.y);
		} finally {
			client.stop();
			server.stop();
		}
	}
}
