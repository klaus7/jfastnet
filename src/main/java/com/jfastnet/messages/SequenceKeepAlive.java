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

package com.jfastnet.messages;

/** A keep alive message, so we can be sure that the last message also gets
 * retrieved. Without sending this message one peer could be stuck, because
 * it never retrieves the last message and also has no way of detecting,
 * because a message with an higher id is not sent.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
public class SequenceKeepAlive extends Message implements IDontFrame {}
