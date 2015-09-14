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

package com.jfastnet.idprovider;

import com.jfastnet.messages.Message;

import java.util.Comparator;

/** Provides the id for messages. It depends on the use case which provider
 * you should choose.
 * @author Klaus Pfeiffer <klaus@allpiper.com> */
public interface IIdProvider extends Comparator<Message> {

	/** Get new id for message. */
	long getFor(Message message);

	/** Get last created id for message type. */
	long getLastIdFor(Message message);

	/** Return true, if broadcasted messages should resolve the message id
	 * again after setting the correct receiver id.
	 * <br/>
	 * Otherwise every client receives the same id for a particular message,
	 * which is required for the lock stepping extension implementation. */
	default boolean resolveEveryClientMessage() {
		return false;
	}
}
