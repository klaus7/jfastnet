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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** Sent from the client to the server.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class ConnectRequest extends Message implements IDontFrame {

	/** Dummy message only used to retrieve the last reliable id. */
	public static final Message DUMMY = new Message() {
		@Override
		public ReliableMode getReliableMode() {
			return ReliableMode.SEQUENCE_NUMBER;
		}
	};

	@Getter
	@Setter
	int clientId;

	public ConnectRequest() {}

	public ConnectRequest(int clientId) {
		this.clientId = clientId;
	}

	@Override
	public ReliableMode getReliableMode() {
		return ReliableMode.ACK_PACKET;
	}

	@Override
	public void process() {
		final ConnectResponse connectResponse = new ConnectResponse(getMsgId(), clientId);
		DUMMY.setReceiverId(clientId);
		connectResponse.lastReliableSeqId = getConfig().idProvider.getLastIdFor(DUMMY);
		log.info("Last reliable ID: {} - send to {}", connectResponse.lastReliableSeqId, clientId);
		connectResponse.setReceiverId(clientId);
		getConfig().sender.send(connectResponse);
	}
}
