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

package com.jfastnet.idprovider;

import com.jfastnet.messages.Message;
import com.jfastnet.util.NullsafeHashMap;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Ids begin with 1. Every reliable mode has its stream of ids.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class ReliableModeIdProvider implements IIdProvider {

	private Map<Message.ReliableMode, AtomicLong> idMap = new NullsafeHashMap<Message.ReliableMode, AtomicLong>() {
		@Override
		protected AtomicLong newInstance() {
			return new AtomicLong();
		}
	};

	@Override
	public long createIdFor(Message message) {
		final long newId = idMap.get(message.getReliableMode()).incrementAndGet();
		log.trace("Created new id {} for {}", newId, message);
		return newId;
	}

	@Override
	public long getLastIdFor(Message message) {
		return idMap.get(message.getReliableMode()).get();
	}

	@Override
	public long stepBack(Message message) {
		return idMap.get(message.getReliableMode()).decrementAndGet();
	}

	@Override
	public int compare(Message m1, Message m2) {

		// compare by reliable mode at first
		int compare = Integer.compare(m1.getReliableMode().ordinal(), m2.getReliableMode().ordinal());
		if (compare != 0) return compare;

		// second by id
		compare = Long.compare(m1.getMsgId(), m2.getMsgId());
		if (compare != 0) return compare;

		return compare;
	}
}
