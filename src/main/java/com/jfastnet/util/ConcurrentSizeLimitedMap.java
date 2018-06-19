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

package com.jfastnet.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class ConcurrentSizeLimitedMap<K extends Comparable<K>, V> extends ConcurrentHashMap<K, V> {

	/** Maximum number of entries in this map. */
	private int maximumSize;

	/** How many items get deleted if we are above maximum. */
	private int deleteCount;

	private volatile int size = 0;

	public ConcurrentSizeLimitedMap() {}

	public ConcurrentSizeLimitedMap(int maximumSize) {
		this.maximumSize = maximumSize;
		this.deleteCount = Math.max(1, maximumSize / 10);
	}

	public ConcurrentSizeLimitedMap(int maximumSize, int deleteCount) {
		this.maximumSize = maximumSize;
		this.deleteCount = deleteCount;
	}

	@Override
	public V put(K key, V value) {
		V put = super.put(key, value);
		if (put == null) {
			size++;
		}
		checkSize();
		return put;
	}

	private void checkSize() {
		if (size > maximumSize) {
			List<K> keys = new ArrayList<>(keySet());
			Collections.sort(keys);
			int removed = 0;
			for (int i = 0; i < keys.size() && removed < deleteCount; i++) {
				K key = keys.get(i);
				remove(key);
				size--;
				removed++;
			}
		}
	}
}
