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

package com.jfastnet.messages.features;

import com.jfastnet.AbstractTest;
import com.jfastnet.messages.Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class ChecksumFeatureTest extends AbstractTest {

	static {
		log.info("ChecksumFeatureTest class is being loaded.");
	}

	public static final long V1 = 52353254L;
	public static final long V2 = 9654787L;

	public static class ChecksumTestMsg extends Message {
		long v1 = V1;
		long v2 = V2;

		@Getter
		MessageFeatures features = new MessageFeatures();

		public ChecksumTestMsg() {
			getFeatures().add(new ChecksumFeature());
		}
	}

	@Test
	public void testChecksum() {
		start();

		server.send(new ChecksumTestMsg());

		waitForCondition("No message received.", 1, () -> allClientsReceivedMessageTypeOf(ChecksumTestMsg.class));

		Message message = getLastReceivedMessage(0);

		assertThat("Message is of wrong type.", message, instanceOf(ChecksumTestMsg.class));
		assertNotNull("Message is null.", message);
		assertNotNull("Features are null.", message.getFeatures());

		ChecksumFeature checksumFeature = message.getFeatures().get(ChecksumFeature.class);
		assertNotNull("Checksum feature is null.", checksumFeature);

		assertNotEquals("Checksum may not be 0.", checksumFeature.getCrcValue(), 0);
		//assertTrue("Wrong checksum, but this test should have failed earlier!", checksumFeature.check(message));
	}

}