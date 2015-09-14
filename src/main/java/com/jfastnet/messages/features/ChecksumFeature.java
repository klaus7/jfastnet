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

import com.jfastnet.Config;
import com.jfastnet.messages.Message;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.zip.CRC32;

/** @author Klaus Pfeiffer <klaus@allpiper.com> */
@Slf4j
public class ChecksumFeature implements MessageFeature {

	// TODO make configurable
	public static final byte[] salt = "".getBytes();

	/** Checksum of message. */
	@Getter
	private long crcValue;

	/** Must be called after the payload was created. */
	public void calculate(Message message) {
		CRC32 crc32 = message.getConfig().serialiser.getChecksum(message, salt);
		crcValue = crc32.getValue();
	}

	public boolean check(Message message) {
		long crcValue = this.crcValue;
		// Set CRC value to 0 so we can correctly compare both checksum'd messages
		this.crcValue = 0L;
		Config config = message.getConfig();
		message.payload = config.serialiser.serialise(message);
		CRC32 crc32 = config.serialiser.getChecksum(message, salt);
		log.trace("Sent CRC: {}, Calculated CRC: {}", crcValue, crc32.getValue());
		this.crcValue = crcValue;
		return crcValue == crc32.getValue();
	}

}
