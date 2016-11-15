/*******************************************************************************
 * Copyright 2016 Klaus Pfeiffer - klaus@allpiper.com
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

package com.jfastnet.state;

import com.jfastnet.Config;
import com.jfastnet.peers.CongestionControl;
import com.jfastnet.processors.ReliableModeSequenceProcessor;
import lombok.ToString;

import java.util.SortedSet;
import java.util.TreeSet;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@ToString(of = "qualityFactor")
public class NetworkQuality {

	/** 1f => Best, 0f => Worst quality */
	public float qualityFactor = 1f;

	private int countRequestedMessages = 0;
	private SortedSet<Long> missingMessageTimestamps = new TreeSet<>();

	private final Config config;
	private final long consideredTimeFrameInMs;

	public NetworkQuality(Config config) {
		this.config = config;
		CongestionControl.CongestionControlConfig congestionControlConfig = config.getAdditionalConfig(CongestionControl.CongestionControlConfig.class);
		consideredTimeFrameInMs = congestionControlConfig.consideredTimeFrameInMs;
	}

	public void requestedMissingMessages(int size, long timeStamp) {
		countRequestedMessages += size;
		for (int i = 0; i < size; i++) {
			missingMessageTimestamps.add(timeStamp);
		}
	}

	void calculateQuality() {
		final long currentTimestamp = config.timeProvider.get();
		missingMessageTimestamps.removeIf(timestamp -> timestamp < currentTimestamp - consideredTimeFrameInMs);
		int missingMessagesInTimeFrame = missingMessageTimestamps.size();
		ReliableModeSequenceProcessor.ProcessorConfig processorConfig = config.getAdditionalConfig(ReliableModeSequenceProcessor.ProcessorConfig.class);
		float maximumNumberOfRequestedMessages = consideredTimeFrameInMs / (float) processorConfig.requestMissingIdsIntervalMs * processorConfig.maximumMissingIdsRequestCount;
		float missingFactor = Math.min(1f, missingMessagesInTimeFrame / maximumNumberOfRequestedMessages);
		this.qualityFactor = 1f - missingFactor;
	}

}
