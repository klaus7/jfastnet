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

package com.jfastnet.messages.features;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class MessageFeatures implements Serializable, MessageFeature {

	private Set<MessageFeature> features = new HashSet<>();

	private transient Map<Class, MessageFeature> featureMap = new HashMap<>();

	public void resolve() {
		featureMap = new HashMap<>();
		for (MessageFeature feature : features) {
			featureMap.put(feature.getClass(), feature);
		}
	}

	public void add(MessageFeature feature) {
		features.add(feature);
		featureMap.put(feature.getClass(), feature);
	}

	@SuppressWarnings("unchecked")
	public <T>T get(Class<T> featureClass) {
		return (T) featureMap.get(featureClass);
	}

	public static class Immutable extends MessageFeatures {
		@Override
		public void add(MessageFeature feature) {
			throw new UnsupportedOperationException(
					"Add your own messageFeatures field with a getter to use " +
					"additional features! This is done to spare precious payload" +
					" size for messages that don't need additional features." +
					"See com.jfastnet.messages.features.ChecksumFeatureTest.ChecksumTestMsg " +
					"for an example.");
		}
	}
}
