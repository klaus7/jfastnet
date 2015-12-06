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

import com.jfastnet.processors.ReliableModeSequenceProcessor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Sent from the server to the client to confirm the connection.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class ConnectResponse extends Message implements IDontFrame, IInstantProcessable, IAckMessage {

	/** Last used reliable sequence id on the server. */
	long lastReliableSeqId;

	long connectRequestMsgId;

	/** The client id is sent back from the server to the client. If it was 0
	 * before, the server assigend a new id. */
	@Getter
	int clientId;

	public ConnectResponse(long connectRequestMsgId, int clientId) {
		this.connectRequestMsgId = connectRequestMsgId;
		this.clientId = clientId;
	}

	@Override
	public ReliableMode getReliableMode() {
		return ReliableMode.ACK_PACKET;
	}

	/** process() would be called too late. */
	public void setLastReliableSeqIdInSequenceProcessor() {
		log.info("Connection established! Last reliable sequence id is {}", lastReliableSeqId);
		final Map<Integer, AtomicLong> lastMessageIdMap = getConfig().getProcessorOf(ReliableModeSequenceProcessor.class).getLastMessageIdMap();
		AtomicLong lastId = lastMessageIdMap.get(getSenderId());
		if (lastId == null || lastId.get() == 0L) {
			lastMessageIdMap.put(getSenderId(), new AtomicLong(lastReliableSeqId));
			log.info(" * Last reliable sequence id set to {}", lastReliableSeqId);
		} else {
			log.warn(" * Last reliable sequence id was already set to {}", lastId);
		}
	}

	@Override
	public long getAckId() {
		return connectRequestMsgId;
	}

}
