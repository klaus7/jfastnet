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

package com.jfastnet.messages.comparators;

import com.jfastnet.messages.Message;

import java.util.Comparator;

/** Higher priority messages will come first when used this comparator.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
public class MessageExecutionPriorityComparator implements Comparator<Message> {

	public static final MessageExecutionPriorityComparator INSTANCE = new MessageExecutionPriorityComparator();

	@Override
	public int compare(Message o1, Message o2) {
		return -Integer.compare(o1.executionPriority(), o2.executionPriority());
	}

}
