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

import com.esotericsoftware.kryo.Kryo;
import com.jfastnet.config.SerialiserConfig;
import com.jfastnet.messages.*;
import com.jfastnet.peers.javanet.JavaNetPeer;
import com.jfastnet.serialiser.KryoSerialiser;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.util.ArrayList;
import java.util.List;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public abstract class AbstractTest {
	public interface Callable<V> {
		/**
		 * Computes a result, or throws an exception if unable to do so.
		 *
		 * @return computed result
		 * @throws Exception if unable to compute a result
		 */
		V call();
	}

	public static final int DEFAULT_INTERVAL = 100;

	public Server server;
	public List<Client> clients;
	public Client client1;
	public Client client2;

	public void start() {
		start(1);
	}

	public void start(int clients ) {
		start(clients, newServerConfig(), () -> newClientConfig());
	}

	@SneakyThrows
	public void start(int clientCount, Config serverConfig, Callable<Config> clientConfig ) {
		server = new Server(serverConfig);
		clients = new ArrayList<>();
		for (int i = 0; i < clientCount; i++) {
			Config config = clientConfig.call();
			clients.add(new Client(config));
			if (i == 0) client1 = clients.get(i);
			if (i == 1) client2 = clients.get(i);
		}

		log.info("Start server");
		server.start();
		log.info("Start clients");
		clients.forEach(Client::start);

		log.info("Wait for clients to successfully connect to server");
		final boolean[] proceed = {false};
		while (!proceed[0]) {
			proceed[0] = true;
			server.process();
			for (Client client : clients) {
				client.process();
				if (!client.isConnected()) {
					proceed[0] = false;
					log.info("Client {} not connected!", client.getConfig().senderId);
				}
			}
			Thread.sleep(200);
		}
		clients.forEach(Client::blockingWaitUntilConnected);
		log.info("All clients connected successfully!");

		waitForCondition("Not all clients joined.", 3, () -> server.clients.size() == clientCount, () -> "Clients: " + server.clients.size() + ", Expected: " + clientCount);
	}

	public Message getLastReceivedMessage() {
		return getLastReceivedMessageFromLog(server.getConfig().messageLog.received, true, null);
	}
	public Message getLastReceivedMessage(int clientIndex) {
		return getLastReceivedMessage(clientIndex, true, null);
	}
	public Message getLastReceivedMessage(int clientIndex, Class messageType) {
		return getLastReceivedMessage(clientIndex, true, messageType);
	}
	public Message getLastReceivedMessage(int clientIndex, boolean ignoreSystemMessages, Class messageType) {
		List<Message> received = clients.get(clientIndex).getConfig().messageLog.received;
		return getLastReceivedMessageFromLog(received, ignoreSystemMessages, messageType);
	}

	public Message getLastReceivedMessageFromLog(List<Message> received, boolean ignoreSystemMessages, Class type) {
		if (received.size() == 0) {
			return null;
		}
		for (int i = received.size() - 1; i >= 0; i--) {
			Message message = received.get(i);
			if (type != null) {
				if (!type.isAssignableFrom(message.getClass())) {
					continue;
				}
			} else if (ignoreSystemMessages) {
				if (message instanceof SequenceKeepAlive) continue;
				if (message instanceof AckMessage) continue;
				if (message instanceof ConnectResponse) continue;
				if (message instanceof RequestSeqIdsMessage) continue;
			}
			return message;
		}
		// If all are system messages, return the last
		return received.get(received.size() - 1);
	}

	public boolean allClientsReceivedMessageTypeOf(Class msgClass) {
		for (int i = 0; i < clients.size(); i++) {
			Message msg = getLastReceivedMessage(i);
			if (msg == null) {
				log.info("Message from client index {} was null", i);
				log.info("Received count: {} / {}", i, clients.size());
				return false;
			}
			if (!msgClass.isAssignableFrom(msg.getClass())) {
				//log.info("{} is not assignable from {}", msgClass, msg.getClass());
				log.info("Received count: {} / {}", i, clients.size());
				for (int j = 0; j < clients.size(); j++) {
					log.info(" -- {}: {}", clients.get(j).config.senderId, getLastReceivedMessage(j));
				}
				return false;
			}
		}
		return true;
	}

	@After
	public void tearDown() {
		if (server != null) {
			server.stop();
		}
		if (clients != null) {
			clients.forEach(Client::stop);
		}
	}

	public Config newClientConfig() {
		ThreadLocal<Kryo> kryos =  new ThreadLocal<Kryo>() {
			protected Kryo initialValue() {
				Kryo kryo = new Kryo();
				kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
				return kryo;
			}
		};
		Config config = new Config();
		config.host = "localhost";
		config.port = 15150;
		config.bindPort = 0;
		config.serialiser = new KryoSerialiser(new SerialiserConfig(), kryos);
		config.udpPeer = new JavaNetPeer(config);
//		config.udpPeer = new KryoNettyPeer(config);
		config.externalReceiver = Message::process;
		config.keepAliveInterval = 500;
		config.messageLog.receiveFilter = message -> true;
		config.compressBigMessages = true;
		config.autoSplitTooBigMessages = true;

		return (config);
	}

	public Config newServerConfig() {
		return newClientConfig().setBindPort(15150).setPort(0);
	}

	public void logBig(String text) {
		//log.info("");
		System.out.println();
		log.info("***************************************************************");
		log.info("* " + text);
		log.info("***************************************************************");
		System.out.println();
	}

	public void waitForCondition(String errorMsg, int timeoutInSeconds, Callable<Boolean> condition) {
		waitForCondition(errorMsg, timeoutInSeconds, DEFAULT_INTERVAL, condition);
	}

	public void waitForCondition(String errorMsg, int timeoutInSeconds, Callable<Boolean> condition, Callable<String> out) {
		waitForCondition(errorMsg, timeoutInSeconds, DEFAULT_INTERVAL, condition, out);
	}

	public void waitForCondition(String errorMsg, int timeoutInSeconds, int interval, Callable<Boolean> condition) {
		waitForCondition(errorMsg, timeoutInSeconds, interval, condition, ()->"");
	}
	public void waitForCondition(String errorMsg, int timeoutInSeconds, int interval, Callable<Boolean> condition, Callable<String> out) {
		int intervalInMilis = interval;
		int i = timeoutInSeconds * 1000 + 1000;
		int icall = 0;
		try {
			Boolean call = condition.call();
			while (!call) {
				Thread.sleep(intervalInMilis);

				server.process();
				clients.forEach(Client::process);

				i -= interval;
				icall += interval;

				if (icall > 1000 || i <= 0) {
					icall = 0;
					// only check every second to not completely mess up console log messages
					call = condition.call();
				}
				if (i <= 0 && !call) {
					log.info(out.call());
					Assert.fail(errorMsg + " [Condition didn't evaluate to true in time. Timeout was " + timeoutInSeconds + "]");
				}
			}
		} catch (Exception e) {
			try {
				log.info(out.call());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
			Assert.fail(errorMsg);
		}
	}

}
