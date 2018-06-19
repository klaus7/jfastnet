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

package com.jfastnet;

import com.jfastnet.messages.Message;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** Reliable mode / ClientID / MessageID - Key
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@ToString
@EqualsAndHashCode
public class MessageKey {
	public final long messageId;
	public final Message.ReliableMode reliableMode;
	public final int clientId;

	public MessageKey(Message.ReliableMode reliableMode, int clientId, long messageId) {
		this.reliableMode = reliableMode;
		this.clientId = clientId;
		this.messageId = messageId;
	}

	public static MessageKey newKey(Message.ReliableMode reliableMode, int clientId, long msgId) {
		return new MessageKey(reliableMode, clientId, msgId);
	}
}
