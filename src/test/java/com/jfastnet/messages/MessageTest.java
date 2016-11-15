package com.jfastnet.messages;

import com.esotericsoftware.kryo.Kryo;
import com.jfastnet.AbstractTest;
import com.jfastnet.Config;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class MessageTest extends AbstractTest {

	boolean registerClass = false;

	public static class Message1 extends Message {}
	public static class Message2 extends Message {
		long value = 0;
		public Message2(long value) { this.value = value; }
	}

	@Test
	public void sizeTest() {
		Config config;

		config = newClientConfig();
		byte[] b1 = config.serialiser.serialise(new Message1());
		log.info("size unregistered message class: {}", b1.length);

		registerClass = true;
		config = newClientConfig();

		byte[] b2 = config.serialiser.serialise(new Message1());
		log.info("size registered message class: {}", b2.length);

		assertThat("Message with registered class has to be smaller", b2.length, is(lessThan(b1.length)));
		assertThat("Naked message should have at max 4 bytes", b2.length, lessThanOrEqualTo(4));
	}

	@Test
	public void sizeTestLongValue() {
		Config config;

		config = newClientConfig();
		byte[] b1;
		b1 = config.serialiser.serialise(new Message2(0));
		log.info("size unreg. zero long value: {}", b1.length);
		b1 = config.serialiser.serialise(new Message2(Long.MAX_VALUE));
		log.info("size unreg. max long value:  {}", b1.length);

		registerClass = true;
		config = newClientConfig();

		b1 = config.serialiser.serialise(new Message2(0));
		log.info("size reg. zero long value:   {}", b1.length);
		b1 = config.serialiser.serialise(new Message2(112233));
		log.info("size reg. long value 112233: {}", b1.length);
		b1 = config.serialiser.serialise(new Message2(Long.MAX_VALUE));
		log.info("size reg. max long value:    {}", b1.length);
	}

	@Override
	public void customizeKryo(Kryo kryo) {
		if (registerClass) {
			kryo.register(Message1.class);
			kryo.register(Message2.class);
		}
	}
}