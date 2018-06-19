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

package com.jfastnet.events;

import com.jfastnet.Config;
import com.jfastnet.State;
import lombok.Getter;
import org.apache.commons.collections4.queue.CircularFifoQueue;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class EventLog {

	private final Config config;
	private final State state;

	@Getter
	private CircularFifoQueue<Event> eventQueue;

	public EventLog(Config config, State state) {
		this.config = config;
		this.state = state;
		this.eventQueue = new CircularFifoQueue<>(config.eventLogSize);
	}

	public void add(Event event) {
		eventQueue.add(event);
	}

	public void clear() {
		eventQueue.clear();
	}
}
