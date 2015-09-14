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

package com.jfastnet.idprovider;

import com.jfastnet.messages.Message;
import com.jfastnet.util.NullsafeHashMap;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** <b>Attention!</b> This won't work with Lockstepping, because the
 * lockstepping impl requires the same id for messages on all clients.
 * @author Klaus Pfeiffer <klaus@allpiper.com> */
public class ClientIdReliableModeIdProvider implements IIdProvider {

	private Map<Integer, Map<Message.ReliableMode, AtomicLong>> idMap = new NullsafeHashMap<Integer, Map<Message.ReliableMode, AtomicLong>>() {
		@Override
		protected Map<Message.ReliableMode, AtomicLong> newInstance() {
			return new NullsafeHashMap<Message.ReliableMode, AtomicLong>() {
				@Override
				protected AtomicLong newInstance() {
					return new AtomicLong();
				}
			};
		}
	};

	@Override
	public long getFor(Message message) {
		return idMap.get(message.getReceiverId()).get(message.getReliableMode()).incrementAndGet();
	}

	@Override
	public long getLastIdFor(Message message) {
		return idMap.get(message.getReceiverId()).get(message.getReliableMode()).get();
	}

	@Override
	public int compare(Message m1, Message m2) {

		// compare by player id first
		int compare = Integer.compare(m1.getReceiverId(), m2.getReceiverId());
		if (compare != 0) return compare;

		// compare by reliable mode
		compare = Integer.compare(m1.getReliableMode().ordinal(), m2.getReliableMode().ordinal());
		if (compare != 0) return compare;

		// by id
		compare = Long.compare(m1.getMsgId(), m2.getMsgId());
		if (compare != 0) return compare;

		return compare;
	}

	@Override
	public boolean resolveEveryClientMessage() {
		return true;
	}
}
