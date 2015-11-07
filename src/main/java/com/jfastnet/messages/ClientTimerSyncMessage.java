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

package com.jfastnet.messages;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** Send the client his offsetToHost.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class ClientTimerSyncMessage extends Message implements IDontFrame {

	private long retrievedOffsetToHost = 0;
	private long retrievedRoundTripTime = 0;

	/** Retrieved offset. */
	@Getter
	@Setter(value = AccessLevel.PROTECTED)
	private static long realOffsetToHost = 0;

	/** Slowly adapted offset. */
	@Getter
	@Setter
	private static long offsetToHost = 0;

	@Getter
	@Setter(value = AccessLevel.PROTECTED)
	private static long roundTripTime = 0;

	@java.beans.ConstructorProperties({"retrievedOffsetToHost", "retrievedRoundTripTime"})
	public ClientTimerSyncMessage(long retrievedOffsetToHost, long retrievedRoundTripTime) {
		this.retrievedOffsetToHost = retrievedOffsetToHost;
		this.retrievedRoundTripTime = retrievedRoundTripTime;
	}

	@Override
	public void process() {
		realOffsetToHost = retrievedOffsetToHost;
		roundTripTime = retrievedRoundTripTime;
		log.trace("RTT: {}", roundTripTime);
	}

	@Override
	public ReliableMode getReliableMode() {
		return ReliableMode.UNRELIABLE;
	}
}
