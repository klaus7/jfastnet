package com.jfastnet.processors;

import com.jfastnet.AbstractTest;
import com.jfastnet.Config;
import com.jfastnet.MessageKey;
import com.jfastnet.MessageLog;
import com.jfastnet.idprovider.ReliableModeIdProvider;
import com.jfastnet.messages.Message;
import com.jfastnet.util.NullsafeHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class StackedMessageProcessorTest extends AbstractTest {

	private static final AtomicInteger receivedCounter = new AtomicInteger();
	private static ThreadLocal<AtomicInteger> stackableReceived;
	private static ThreadLocal<AtomicInteger> unstackableReceived;
	private static final AtomicInteger closeMsgReceived = new AtomicInteger();
	private static final Map<Integer, List<Message>> receivedMessages = new HashMap<>();

	private static Map<Integer, Set<Long>> stackableIds = new NullsafeHashMap<Integer, Set<Long>>() {
		@Override protected Set<Long> newInstance() {return new HashSet<>();}
	};

	private static Map<Integer, Set<Long>> unstackableIds = new NullsafeHashMap<Integer, Set<Long>>() {
		@Override protected Set<Long> newInstance() {return new HashSet<>();}
	};

	static boolean fail = false;

	static class StackableMsg1 extends Message {
		@Override
		public boolean stackable() {
			return true;
		}

		@Override
		public void process(Object context) {
			receivedCounter.incrementAndGet();
			int unstackableReceivedCounter = unstackableReceived.get().get();
			int stackableReceivedCounter = stackableReceived.get().incrementAndGet();
			log.info("########### STACKABLE ### ClientID: {} ### MsgID: {} ### Number: {}",
					new Object[]{getConfig().senderId, getMsgId(), stackableReceivedCounter});
			addReceived(this);
			printMsg(this);

			CircularFifoQueue<Message> received = getState().getProcessorOf(MessageLogProcessor.class).getMessageLog().getReceived();
			long expectedId = received.size() == 0 ? 1L : received.get(received.size() - 1).getMsgId();
			if (getMsgId() != expectedId) {
				log.error("Wrong id found! Expected: {}, Actual: {}", expectedId, getMsgId());
				fail = true;
			}
//			if (stackableReceivedCounter <= unstackableReceivedCounter) {
//				log.error("Stackable must have a greater id! stackableReceived: {}, unstackableReceived: {}", stackableReceivedCounter, unstackableReceivedCounter);
//				fail = true;
//			}
			if (getConfig() != null && stackableIds.containsKey(getConfig().senderId)) {
				if (stackableIds.get(getConfig().senderId).contains(getMsgId())) {
					log.error("Stackables already contained. senderId: {}, msgId: {}", getConfig().senderId, getMsgId());
					fail = true;
				}
			}
		}
	}

	static class StackableMsg2 extends StackableMsg1 {
		@Override
		public void process(Object context) {
			addReceived(this);
			closeMsgReceived.incrementAndGet();
			log.info("Close msg #" + closeMsgReceived.get());
		}
	}

	static class UnStackableMsg1 extends Message {
		@Override
		public void process(Object context) {
			receivedCounter.incrementAndGet();
			int unstackableReceivedCounter = unstackableReceived.get().incrementAndGet();
			log.info("########### UNSTACKABLE ### ClientID: {} ### MsgID: {} ### Number: {}",
					new Object[]{getConfig().senderId, getMsgId(), unstackableReceivedCounter});
			addReceived(this);
			printMsg(this);
			if (getConfig() != null && unstackableIds.containsKey(getConfig().senderId)) {
				if (unstackableIds.get(getConfig().senderId).contains(getMsgId())) {
					log.error("Stackables already contained {}, {}", getConfig().senderId, getMsgId());
					fail = true;
				}
			}
		}
	}

	private synchronized static void addReceived(Message message) {
		List<Message> messages = receivedMessages.getOrDefault(message.getConfig().senderId, new ArrayList<>());
		messages.add(message);
		receivedMessages.put(message.getConfig().senderId, messages);
	}

	private static void printMsg(Message msg) {
//		log.info("+++++++++++++ msg-id: " + msg.getMsgId());
//		try {
//			throw new Exception();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}

	@Test
	public void testStacking() {
		reset();
		start(8,
				() -> {
					Config config = newClientConfig().setStackKeepAliveMessages(true);
					config.debug.enabled = true;
					config.debug.lostPacketsPercentage = 5;
					config.setIdProviderClass(ReliableModeIdProvider.class);
					return config;
				});
		logBig("Send broadcast messages to clients");

		int messageCount = 40;
		for (int i = 0; i < messageCount; i++) {
			server.send(new StackableMsg1());
		}
		server.send(new StackableMsg2());

		int timeoutInSeconds = 15;
		waitForCondition("Not all messages received.", timeoutInSeconds,
				() -> closeMsgReceived.get() == clients.size(),
				() -> "Received close messages: " + closeMsgReceived);

		assertThat(receivedCounter.get(), is(messageCount * clients.size()));

		assertThat(fail, is(false));
	}

	@Test
	public void testStackingWithUnstackables() {
		reset();
		start(4,
				() -> {
					Config config = newClientConfig();
					config.debug.enabled = true;
					config.debug.lostPacketsPercentage = 5;
					config.setIdProviderClass(ReliableModeIdProvider.class);
					return config;
				});
		logBig("Send broadcast messages to clients");

		int messageCount = 100;
		for (int i = 0; i < messageCount; i++) {
			server.send(new StackableMsg1());
			server.send(new UnStackableMsg1());
		}
		server.send(new StackableMsg2());

		int timeoutInSeconds = 15;
		waitForCondition("Not all messages received.", timeoutInSeconds,
				() -> closeMsgReceived.get() == clients.size(),
				() -> "Received close messages: " + closeMsgReceived);

		assertThat(receivedCounter.get(), is(messageCount * 2 * clients.size()));
		assertThat(fail, is(false));

		log.info("Check order of received messages");
		for (int i = 1; i <= clients.size(); i++) {
			List<Message> messages = receivedMessages.get(i);
			assertThat(messages, is(notNullValue()));
			assertThat(messages.size(), greaterThan(0));
			long lastId = messages.get(0).getMsgId();
			for (Message message : messages) {
				assertThat(message.getMsgId(), is(lastId));
				lastId++;
			}
		}

		log.info("Check ids in message log");
		MessageLog messageLog = server.getState().getProcessorOf(MessageLogProcessor.class).getMessageLog();
		for (long i = 1; i <= messageCount * 2; i++) {
			Message message = messageLog.getSent(MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, 0, i));
			assertThat("Message was null, id=" + i, message, is(notNullValue()));
			assertThat(message.getMsgId(), is(i));
		}
	}

	@Test
	public void testLostPacketCorrectReceiveOrder() {
		reset();
		start(4,
				() -> {
					Config config = newClientConfig();
					config.debug.enabled = true;
					config.debug.lostPacketsPercentage = 0;
					config.setIdProviderClass(ReliableModeIdProvider.class);
					return config;
				});
		logBig("Send broadcast messages to clients");

		discardNextPacket();
		server.send(new StackableMsg1());
		server.send(new UnStackableMsg1());

		server.send(new StackableMsg1());
		discardNextPacket();
		server.send(new UnStackableMsg1());

		discardNextPacket();
		server.send(new StackableMsg1());
		discardNextPacket();
		server.send(new UnStackableMsg1());

		server.send(new StackableMsg1());
		server.send(new UnStackableMsg1());

		// Send close message
		server.send(new StackableMsg2());

		int timeoutInSeconds = 5;
		waitForCondition("Not all messages received.", timeoutInSeconds,
				() -> closeMsgReceived.get() == clients.size(),
				() -> "Received close messages: " + closeMsgReceived);

		assertThat(fail, is(false));
	}

	private void discardNextPacket() {
		clients.forEach(client -> client.getConfig().debug.discardNextPacket = true);
	}


	private void reset() {
		receivedCounter.set(0);
		closeMsgReceived.set(0);
		stackableIds.clear();
		unstackableIds.clear();
		receivedMessages.clear();
		fail = false;
		stackableReceived = new ThreadLocal<AtomicInteger>() {
			@Override
			protected AtomicInteger initialValue() {
				return new AtomicInteger();
			}
		};
		unstackableReceived = new ThreadLocal<AtomicInteger>() {
			@Override
			protected AtomicInteger initialValue() {
				return new AtomicInteger();
			}
		};
	}
}