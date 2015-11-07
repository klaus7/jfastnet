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

import com.jfastnet.idprovider.ClientIdReliableModeIdProvider;
import com.jfastnet.idprovider.ReliableModeIdProvider;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.MessagePart;
import com.jfastnet.processors.ReliableModeSequenceProcessor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** @author Klaus Pfeiffer <klaus@allpiper.com> */
@Slf4j
public class ServerTest extends AbstractTest {

	public static final int TEST_GLOBAL_TIMEOUT = 30;
	public static int received = 0;
	private boolean udpClientRunning = true;

	private static BigMessage receivedBigMessage;

	static class DefaultUnreliableMessage extends Message {
		@Override public ReliableMode getReliableMode() { return ReliableMode.UNRELIABLE; }
		@Override public void process() { received++; }
	}

	static class DefaultUnreliableMessageSpecific extends Message {
		public int value;
		public DefaultUnreliableMessageSpecific() {}
		public DefaultUnreliableMessageSpecific(int value) {this.value = value;}
		@Override public ReliableMode getReliableMode() {return ReliableMode.UNRELIABLE;}
		@Override public void process() {received++;}
	}

	static class DefaultReliableSeqMessage extends Message {
		@Override public ReliableMode getReliableMode() { return ReliableMode.SEQUENCE_NUMBER; }
		@Override public void process() { received++; }
	}

	@ToString(callSuper = true)
	static class DefaultReliableSeqMessageSpecific extends Message {
		public int value;
		public DefaultReliableSeqMessageSpecific() {}
		public DefaultReliableSeqMessageSpecific(int value) {this.value = value;}
		@Override public ReliableMode getReliableMode() {return ReliableMode.SEQUENCE_NUMBER;}
		@Override public void process() {received++;}
	}

	static class BigMessage extends Message {
		String s;

		public BigMessage() {
			this(50);
		}
		public BigMessage(int size) {
			Random r = new Random(System.currentTimeMillis());
			StringBuilder sb = new StringBuilder();
			sb.append("BEGIN__");
			for (int i = 0; i < size; i++) {
				for (int j = 0; j < 15; j++) {
					sb.append((char) r.nextInt());
				}
			}
			// 1 char UTF-8 encoded in ASCII space consumes 1 byte of memory
			// payload size = 50 * 30 byte = 1500 byte
			sb.append("__END");
			// + 3 = 1503 byte
			s = sb.toString();
		}

		@Override
		public void process() {
			log.info("Big message received.");
			receivedBigMessage = this;
			received++;

		}
	}

	public void reset() {
		received = 0;
		receivedBigMessage = null;
		udpClientRunning = true;
	}

	@Test
	public void testSequence() {
		reset();

		Config newServerConfig = newServerConfig();
		newServerConfig.setExternalReceiver(Message::process);
		Server server = new Server(newServerConfig) {
			@Override
			public boolean send(Message message) {
				return super.send(message);
			}
			@Override
			public boolean send(int clientId, Message message) {
				return super.send(clientId, message);
			}
		};
		Client client1 = new Client(newClientConfig().setExternalReceiver(message -> {
			// is called after all the processor magic
			if (udpClientRunning) {
				message.process();
			}
		}));

		this.server = server;
		server.start();
		clients = new ArrayList<>();
		clients.add(client1);
		client1.setClientId(987);
		client1.start();
		client1.blockingWaitUntilConnected();

		assertThat(server.clients.size(), is(1));

		DefaultUnreliableMessage msg1 = new DefaultUnreliableMessage();
		client1.send(msg1);
		log.info("Last message id: {}", msg1.getMsgId());

		assertThat(msg1.getMsgId(), is(2L));
		checkReceived();

		log.info("Clear id and create new.");
		msg1.clearId();
		client1.send(msg1);
		log.info("Last message id: {}", msg1.getMsgId());

		assertThat(msg1.getMsgId(), is(3L));
		checkReceived();

		log.info("Send big message.");
		BigMessage bigMessage = new BigMessage();
		String forLaterCheck = bigMessage.s;
		List<MessagePart> messageParts = MessagePart.createFromMessage(client1.config, 0, bigMessage, 1024);
		// 41
		log.info("Parts: {}", messageParts.size()); // 15 + 1
		messageParts.forEach(client1::send);

		waitFor(1500);
		checkReceived();

		messageParts.forEach(client1::queue);

		assertThat("Received message not the same.", receivedBigMessage.s, is(equalTo(forLaterCheck)));
	}

	@Test
	public void testBigMessageQueuing() {
		reset();
		start(1);

		log.info("Send big message.");
		BigMessage bigMessage = new BigMessage();
		String forLaterCheck = bigMessage.s;
		List<MessagePart> messageParts = MessagePart.createFromMessage(client1.config, 0, bigMessage, 1024);
		// 41
		log.info("Parts: {}", messageParts.size()); // 15 + 1
		log.info("Test queued sending of big message");
		messageParts.forEach(client1::queue);

		waitForCondition("Big message after queuing not received.", 3, () -> received == 1);
		checkReceived();

		assertThat("Received message not the same.", receivedBigMessage.s, is(equalTo(forLaterCheck)));
	}

	@Test
	public void testBigMessageAckQueuing() {
		reset();
		start(1, newServerConfig(), () -> newClientConfig().setDebug(true).setDebugLostPackagePercentage(20));

		BigMessage bigMessage;
		String forLaterCheck;
		List<MessagePart> messageParts;

		logBig("Send big message.");
		receivedBigMessage = null;
		bigMessage = new BigMessage(150);
		forLaterCheck = bigMessage.s;
		messageParts = MessagePart.createFromMessage(client1.config, 0, bigMessage, 1024, Message.ReliableMode.ACK_PACKET);

		log.info("Parts: {}", messageParts.size()); // 15 + 1
		log.info("Test queued sending of big message");
		messageParts.forEach(client1::queue);

		waitForCondition("Big message after queuing not received.", 3, () -> received == 1);
		checkReceived();

		assertThat("Received message not the same.", receivedBigMessage.s, is(equalTo(forLaterCheck)));


		logBig("Send a smaller message");
		receivedBigMessage = null;
		bigMessage = new BigMessage(50);
		forLaterCheck = bigMessage.s;
		messageParts = MessagePart.createFromMessage(client1.config, 0, bigMessage, 1024, Message.ReliableMode.ACK_PACKET);

		log.info("Parts: {}", messageParts.size()); // 15 + 1
		log.info("Test queued sending of big message");
		messageParts.forEach(client1::queue);

		waitForCondition("Big message after queuing not received.", 3, () -> received == 1);
		checkReceived();

		assertThat("Received message not the same.", receivedBigMessage.s, is(equalTo(forLaterCheck)));


		logBig("Send not all packets of big message");
		receivedBigMessage = null;
		bigMessage = new BigMessage(150);
		forLaterCheck = bigMessage.s;
		messageParts = MessagePart.createFromMessage(client1.config, 0, bigMessage, 1024, Message.ReliableMode.ACK_PACKET);

		log.info("Parts: {}", messageParts.size()); // 15 + 1
		log.info("Test queued sending of big message");
		for (int i = 0; i < messageParts.size(); i++) {
			MessagePart messagePart = messageParts.get(i);
			if (i % 2 == 0 || i == messageParts.size() - 1) {
				client1.queue(messagePart);
			}
		}

		waitFor(300);


//		logBig("Clear byte buffer.");
//		server.config.byteArrayBufferMap.clear();

		logBig("Send big message after unsuccessful delivery on different id.");
		receivedBigMessage = null;
		received = 0;
		bigMessage = new BigMessage(150);
		forLaterCheck = bigMessage.s;
		messageParts = MessagePart.createFromMessage(client1.config, 1, bigMessage, 1024, Message.ReliableMode.ACK_PACKET);

		log.info("Parts: {}", messageParts.size()); // 15 + 1
		log.info("Test queued sending of big message");
		messageParts.forEach(client1::queue);

		waitForCondition("Big message after queuing not received.", 6, () -> received == 1, () -> "recv.: "+ received);
		checkReceived();

		assertThat("Received message not the same.", receivedBigMessage.s, is(equalTo(forLaterCheck)));

	}

	@Test
	public void sendToMultipleClientsUnreliableTest() {
		reset();
		start(4, newServerConfig(), () -> {
			Config config = newClientConfig();
			config.messageLog.receiveFilter = message -> true; // don't filter messages
			config.messageLog.sendFilter = message -> true; // don't filter messages
			return config;
		});
		server.send(new DefaultUnreliableMessage());
		waitForCondition("Not all messages received.", 1, () -> allClientsReceivedMessageTypeOf(DefaultUnreliableMessage.class));

		server.send(new DefaultUnreliableMessageSpecific(1).setReceiverId(1));
		server.send(new DefaultUnreliableMessageSpecific(2).setReceiverId(2));
		server.send(new DefaultUnreliableMessageSpecific(3).setReceiverId(3));
		server.send(new DefaultUnreliableMessageSpecific(4).setReceiverId(4));
		waitForCondition("Not all messages received.", 1, () -> allClientsReceivedMessageTypeOf(DefaultUnreliableMessageSpecific.class));

		assertThat(((DefaultUnreliableMessageSpecific) getLastReceivedMessage(0)).value, is(1));
		assertThat(((DefaultUnreliableMessageSpecific) getLastReceivedMessage(1)).value, is(2));
		assertThat(((DefaultUnreliableMessageSpecific) getLastReceivedMessage(2)).value, is(3));
		assertThat(((DefaultUnreliableMessageSpecific) getLastReceivedMessage(3)).value, is(4));

	}

	/** 50 % packet loss single client. */
	@Test
	public void sendToClientReliableSeqTest() {
		reset();
		start(1,
				newServerConfig()
						.setIdProvider(new ClientIdReliableModeIdProvider()),
				() -> {
					Config config = newClientConfig();
					config.idProvider = new ClientIdReliableModeIdProvider();
					config.debug = true;
					// 10 % Packet loss should be enough
					config.debugLostPackagePercentage = 50;
					return config;
				});
		logBig("Send broadcast messages to clients");
		server.send(new DefaultReliableSeqMessage());
		int timeoutInSeconds = TEST_GLOBAL_TIMEOUT;
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessage.class));

		logBig("Send specific messages to all clients");
		int value = 1;
		server.send(new DefaultReliableSeqMessageSpecific(value).setReceiverId(1));
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessageSpecific.class));
		assertThat(((DefaultReliableSeqMessageSpecific) getLastReceivedMessage(0)).value, is(value));

		server.send(new DefaultReliableSeqMessageSpecific(2).setReceiverId(1));
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> getLastReceivedMessage(0) instanceof DefaultReliableSeqMessageSpecific && ((DefaultReliableSeqMessageSpecific) getLastReceivedMessage(0)).value == 2, () -> getLastReceivedMessage(0).toString());

		server.send(new DefaultReliableSeqMessageSpecific(3).setReceiverId(1));
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> getLastReceivedMessage(0) instanceof DefaultReliableSeqMessageSpecific && ((DefaultReliableSeqMessageSpecific) getLastReceivedMessage(0)).value == 3, () -> getLastReceivedMessage(0).toString());

		logBig("Send broadcast messages to clients");
		server.send(new DefaultReliableSeqMessage());
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessage.class));
	}

	/** Sending to multiple clients with specific ids enabled. */
	@Test
	public void sendToMultipleClientsSpecificReliableSeqTest() {
		reset();
		start(8, newServerConfig()
						.setIdProvider(new ClientIdReliableModeIdProvider()),
				() -> {
					Config config = newClientConfig();
					config.idProvider = new ClientIdReliableModeIdProvider();
					config.debug = true;
					config.debugLostPackagePercentage = 10;
					return config;
				});

		logBig("Send broadcast messages to clients");
		server.send(new DefaultReliableSeqMessage());
		int timeoutInSeconds = TEST_GLOBAL_TIMEOUT;
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessage.class));

		logBig("Send specific messages to all clients");
		for (int i = 0; i < clients.size(); i++) {
			server.send(new DefaultReliableSeqMessageSpecific(i + 1).setReceiverId(i + 1));
		}
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessageSpecific.class));

		for (int i = 0; i < clients.size(); i++) {
			assertThat(((DefaultReliableSeqMessageSpecific) getLastReceivedMessage(i)).value, is(i + 1));
		}

		logBig("Send broadcast messages to clients");
		server.send(new DefaultReliableSeqMessage());
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessage.class));

		logBig("Send from clients to server");
		for (int i = 0; i < clients.size(); i++) {
			Client client = clients.get(i);
			client.send(new DefaultReliableSeqMessageSpecific(10 + i));
			final int finalI = i;
			waitForCondition("Client message missing.", timeoutInSeconds, () -> getLastReceivedMessage() instanceof DefaultReliableSeqMessageSpecific && ((DefaultReliableSeqMessageSpecific) getLastReceivedMessage()).value == 10 + finalI);
		}
	}

	/** Sending to multiple clients with specific ids disabled. */
	@Test
	public void sendToMultipleClientsReliableSeqTest() {
		reset();
		start(8, newServerConfig()
						.setIdProvider(new ReliableModeIdProvider()),
				() -> {
					Config config = newClientConfig();
					config.idProvider = new ReliableModeIdProvider();
					config.debug = true;
					config.debugLostPackagePercentage = 10;
					return config;
				});

		int timeoutInSeconds = TEST_GLOBAL_TIMEOUT;

		logBig("Send broadcast messages to clients");
		server.send(new DefaultReliableSeqMessage());
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessage.class));

		logBig("Send from clients to server");
		log.info("Wait for every client to receive its proper message.");
		for (int i = 0; i < clients.size(); i++) {
			Client client = clients.get(i);
			client.send(new DefaultReliableSeqMessageSpecific(10 + i));
			final int finalI = i;
			waitForCondition("Client message on server missing.", timeoutInSeconds,
					() -> getLastReceivedMessage() instanceof DefaultReliableSeqMessageSpecific && ((DefaultReliableSeqMessageSpecific) getLastReceivedMessage()).value == 10 + finalI,
					() -> "Last received message: " + getLastReceivedMessage() + (getLastReceivedMessage() instanceof DefaultReliableSeqMessageSpecific ?", value: "+ ((DefaultReliableSeqMessageSpecific) getLastReceivedMessage()).value : ""));
		}

		logBig("Check re-connect of client");
		long lastSeqIdFromServer = client1.getConfig().getProcessorOf(ReliableModeSequenceProcessor.class).getLastMessageIdMap().getOrDefault(0, 0L);
		log.info("Last Seq-Id from server: {}", lastSeqIdFromServer);
		client1.stop();
		clients.remove(client1);
		final int client1Id = client1.config.senderId;

		waitFor(100);

		Config config = newClientConfig();
		config.setSenderId(client1Id);
		client1 = new Client(config);
		clients.add(client1);

		client1.start();
		client1.blockingWaitUntilConnected();
		long lastSeqIdFromServer2 = client1.getConfig().getProcessorOf(ReliableModeSequenceProcessor.class).getLastMessageIdMap().getOrDefault(0, 0L);
		log.info("Last Seq-Id from server: {}", lastSeqIdFromServer2);

		assertThat("Didn't retrieve current reliable sequence id from server.", lastSeqIdFromServer2, is(greaterThanOrEqualTo(lastSeqIdFromServer)));


		logBig("Send broadcast messages to clients");
		server.send(new DefaultReliableSeqMessage());
		waitForCondition("Not all messages received.", timeoutInSeconds, () -> allClientsReceivedMessageTypeOf(DefaultReliableSeqMessage.class));

	}

	private void checkReceived() {
		waitFor(100);
		MatcherAssert.assertThat(received, Matchers.is(1));
		received = 0;
	}

	private void checkReceived(int count) {
		waitFor(100);
		MatcherAssert.assertThat(received, Matchers.is(count));
		received = 0;
	}

	private void checkNotReceived() {
		waitFor(100);
		MatcherAssert.assertThat(received, Matchers.is(0));
		received = 0;
	}


	public void waitFor(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}