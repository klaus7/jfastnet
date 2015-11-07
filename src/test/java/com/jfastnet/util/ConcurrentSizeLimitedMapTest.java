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

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class ConcurrentSizeLimitedMapTest {

	@Test
	public void testConcurrentSizeLimitedMap() {
		Map<Integer, Integer> map = new com.jfastnet.util.ConcurrentSizeLimitedMap(3);
		map.put(1, 1);
		map.put(2, 2);
		map.put(3, 3);
		log.info("map: " + Arrays.toString(map.keySet().toArray()));
		assertThat(map.size(), is(3));
		assertThat(map.get(1), is(1));
		assertThat(map.get(3), is(3));

		map.put(4, 4);
		map.put(5, 5);
		log.info("map: " + Arrays.toString(map.keySet().toArray()));
		assertThat(map.size(), is(3));
		assertThat(map.get(3), is(3));
	}
}