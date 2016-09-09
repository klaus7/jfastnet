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

import com.jfastnet.ISimpleProcessable;
import com.jfastnet.messages.features.MessageFeatures;
import com.jfastnet.messages.features.TimestampFeature;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/** This message is sent from the server to the client and then back to the
 * server. It is used to measure the round trip time.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class TimerSyncMessage extends Message implements IInstantServerProcessable, IDontFrame {

	/* -- */
	public static volatile boolean started = false;
	public static ISimpleProcessable initiateSynchronousStart;
	private static final Object checkStartedLock = new Object();
	/* -- */

	/** Client ID returning the sync request. */
	private int clientId;

	/** System timestamp when last sync action was requested. Only set by server. */
	@Setter @Getter
	private static long lastTimestamp;

	@Getter
	private static Map<Integer, Long> roundTrip = new HashMap<>();

	/** System playerIndex plus timestampDifferenceToHost equals host time. */
	@Getter
	private static Map<Integer, Long> timestampDifferenceToHost = new HashMap<>();

	@Getter
	private MessageFeatures features = new MessageFeatures();

	public TimerSyncMessage(int clientId) {
		this.clientId = clientId;
		getFeatures().add(new TimestampFeature());
	}

	/*
	 * IMPORTANT: the server processes this method!
	 */
	@Override
	public void process(Object context) {
		/*
		 * The host sends out this action, which gets immediately returned (without processing) 
		 * by the clients and received by the server,
		 * who then calculates the roundtrips for all clients. 
		 */
		long retrievedTs = getTimestamp();
		long sysTs = System.currentTimeMillis();
		long roundTripTime = sysTs - lastTimestamp;

		log.trace("RTT for player {}: {} ms", clientId, roundTripTime);
		roundTrip.put(clientId, roundTripTime);

		long offsetToHost = -(retrievedTs - sysTs - (roundTripTime / 2));
		timestampDifferenceToHost.put(clientId, offsetToHost);

		// send information back to client, so he can compute actions accordingly
		ClientTimerSyncMessage clientTimerSyncMessage = new ClientTimerSyncMessage(offsetToHost, roundTripTime);
		clientTimerSyncMessage.setReceiverId(clientId);
		getConfig().internalSender.send(clientTimerSyncMessage);

		synchronized (checkStartedLock) {
			if (!started && getConfig().requiredClients.size() > 0) {
				Map<Integer, Boolean> requiredClients = getConfig().requiredClients;
				boolean allReady = true;
				for (Boolean ready : requiredClients.values()) {
					if (!ready) {
						allReady = false;
						break;
					}
				}
				if (allReady) {
					started = true;
					initiateSynchronousStart.process();
				}
			}
		}
	}

	@Override
	public ReliableMode getReliableMode() {
		return ReliableMode.UNRELIABLE;
	}
}
