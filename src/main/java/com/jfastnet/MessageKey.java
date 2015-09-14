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

package com.jfastnet;

import com.jfastnet.messages.Message;
import lombok.ToString;

/** Reliable mode / ClientID / MessageID - Key
 * @author Klaus Pfeiffer <klaus@allpiper.com> */
@ToString
public class MessageKey implements Comparable<MessageKey>{
	public Message.ReliableMode reliableMode;
	public int clientId;
	public long messageId;

	public MessageKey(Message.ReliableMode reliableMode, int clientId, long messageId) {
		this.reliableMode = reliableMode;
		this.clientId = clientId;
		this.messageId = messageId;
	}

	public static MessageKey newKey(Message.ReliableMode reliableMode, int clientId, long msgId) {
		return new MessageKey(reliableMode, clientId, msgId);
	}

	@Override
	public int compareTo(MessageKey o) {
		int reliableCompare = Integer.compare(reliableMode.ordinal(), o.reliableMode.ordinal());
		if (reliableCompare != 0) {
			return reliableCompare;
		}
		int clientCompare = Integer.compare(clientId, o.clientId);
		if (clientCompare != 0) {
			return clientCompare;
		}
		return Long.compare(messageId, o.messageId);
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof MessageKey)) return false;
		final MessageKey other = (MessageKey) o;
		if (!other.canEqual((Object) this)) return false;
		final Object this$reliableMode = this.reliableMode;
		final Object other$reliableMode = other.reliableMode;
		if (this$reliableMode == null ? other$reliableMode != null : !this$reliableMode.equals(other$reliableMode))
			return false;
		if (this.clientId != other.clientId) return false;
		if (this.messageId != other.messageId) return false;
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final Object $reliableMode = this.reliableMode;
		result = result * PRIME + ($reliableMode == null ? 0 : $reliableMode.hashCode());
		result = result * PRIME + this.clientId;
		final long $messageId = this.messageId;
		result = result * PRIME + (int) ($messageId >>> 32 ^ $messageId);
		return result;
	}

	protected boolean canEqual(Object other) {return other instanceof MessageKey;}
}
