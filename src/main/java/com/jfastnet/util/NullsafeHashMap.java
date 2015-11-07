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

package com.jfastnet.util;

import java.util.HashMap;

/** If on "get" the map is null, a new object is inserted.
 * @author Klaus Pfeiffer - klaus@allpiper.com
 * @param <E> key for this map containing another Object of entities and their ids.
 * @param <F> value of this map */
public abstract class NullsafeHashMap<E, F> extends HashMap<E, F> {

	public NullsafeHashMap() {}

	public NullsafeHashMap(int componentsInitialCapacity) {
		super(componentsInitialCapacity);
	}

	@Override
	public F get(final Object key) {
		F map = super.get(key);
		if (map == null) {
			map = newInstance();
			put((E) key, map);
		}
		return map;
	}

	/** If on get it is null, this object is created. Returning null would
	 * imitate the usual HashMap behavior. */
	protected abstract F newInstance();
}
