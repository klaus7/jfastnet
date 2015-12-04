package com.jfastnet.messages;

import com.esotericsoftware.kryo.Kryo;
import com.jfastnet.AbstractTest;
import com.jfastnet.Config;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.junit.Assert.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class MessageTest extends AbstractTest {

	boolean registerClass = false;

	public static class Message1 extends Message {}


	@Test
	public void sizeTest() {
		Config config;

		config = newClientConfig();
		byte[] b1 = config.serialiser.serialise(new Message1());
		log.info("size: {}", b1.length);

		registerClass = true;
		config = newClientConfig();

		byte[] b2 = config.serialiser.serialise(new Message1());
		log.info("size: {}", b2.length);

		assertThat("Message with registered class has to be smaller", b2.length, is(lessThan(b1.length)));
		assertThat("Naked message should have at max 4 bytes", b2.length, lessThanOrEqualTo(4));
	}

	@Override
	public void customizeKryo(Kryo kryo) {
		if (registerClass) {
			kryo.register(Message1.class);
		}
	}
}