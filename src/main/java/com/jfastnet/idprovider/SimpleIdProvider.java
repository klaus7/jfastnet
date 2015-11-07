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

import java.util.concurrent.atomic.AtomicLong;

/** Provides a new id for every message. Usually not advisable to use this
 * provider.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
public class SimpleIdProvider implements IIdProvider {

	AtomicLong id = new AtomicLong();

	@Override
	public long getFor(Message message) {
		return id.incrementAndGet();
	}

	@Override
	public long getLastIdFor(Message message) {
		return id.get();
	}

	@Override
	public int compare(Message m1, Message m2) {
		return Long.compare(m1.getMsgId(), m2.getMsgId());
	}
}
