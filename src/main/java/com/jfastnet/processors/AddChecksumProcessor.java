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

package com.jfastnet.processors;

import com.jfastnet.messages.Message;
import com.jfastnet.messages.features.ChecksumFeature;

/** @author Klaus Pfeiffer <klaus@allpiper.com> */
public class AddChecksumProcessor implements IMessageSenderPreProcessor {

	/** WIP! */
	private byte[] salt = "".getBytes();

	@Override
	public Message beforeCongestionControl(Message message) {
		return message;
	}

	@Override
	public Message beforeSend(Message message) {
		ChecksumFeature checksumFeature = message.getFeatures().get(ChecksumFeature.class);
		if (checksumFeature != null) {
			checksumFeature.calculate(message);
			// FIXME needs two times of serialisation
			message.payload = message.getConfig().serialiser.serialise(message);
		}
		return message;
	}
}
