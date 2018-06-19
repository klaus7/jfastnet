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

package com.jfastnet.messages;

/** A message that needs to be received in a particular order has to implement
 * this interface and respect the conditions.
 * <br>Usually a better way is to use a reliable sequence instead.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
public interface IOrderedMessage {

	/** @return the message id this message is based on. The message with this
	 * id has to be received, before this message can be processed. From this
	 * it follows, that the message this message is based on has to be a
	 * reliable udp message. Otherwise you could run into a kind of
	 * "udp processing deadlock". */
	long getBasedOnMessageId();
}
