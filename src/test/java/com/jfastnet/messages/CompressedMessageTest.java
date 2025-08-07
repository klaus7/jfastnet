package com.jfastnet.messages;

import com.esotericsoftware.kryo.Kryo;
import com.jfastnet.AbstractTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.Random;
import java.util.function.Supplier;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class CompressedMessageTest extends AbstractTest {

	private static int received;

	public static class Message1 extends Message {
		long value = 0;
		public Message1(long value) { this.value = value; }

		@Override
		public void process(Object context) {
			received++;
		}
	}

	public static class Message2 extends Message {
		long[] longArray;

		public Message2(long[] longArray) {
			this.longArray = longArray;
		}

		@Override
		public void process(Object context) {
			received++;
		}
	}

	@Test
	public void testCompressedMessages() {
		start(1);
		Message msg;

		log.info(" * Test with one additional long field value 0");
		testWithMessage(() -> new Message1(1));

		log.info(" * Test with one additional long field value Long.MAX_VALUE");
		testWithMessage(() -> new Message1(Long.MAX_VALUE));

		log.info(" * Test with long array field and random values");
		testWithMessage(() -> new Message2(createLongArrayWithRandomValues(200)));
	}

	private void testWithMessage(Supplier<Message> messageSupplier) {
		received = 0;
		Message msg;
		msg = messageSupplier.get();
		server.send(msg);
		log.info("Payload length uncompressed message: {}", msg.payloadLength());
		waitForCondition("message not received.", 1, () -> received == 1);

		msg = CompressedMessage.createFrom(server.getState(), messageSupplier.get());
		server.send(msg);
		log.info("Payload length compressed message: {}", msg.payloadLength());
		waitForCondition("message not received.", 1, () -> received == 2);
	}

	private long[] createLongArrayWithRandomValues(int size) {
		Random random = new Random(System.currentTimeMillis());
		long[] arr = new long[size];
		for (int i = 0; i < size; i++) {
			arr[i] = random.nextLong();
		}
		return arr;
	}

	@Override
	public void customizeKryo(Kryo kryo) {
		super.customizeKryo(kryo);
		kryo.register(CompressedMessage.class);
		kryo.register(Message1.class);
		kryo.register(Message2.class);
	}


}