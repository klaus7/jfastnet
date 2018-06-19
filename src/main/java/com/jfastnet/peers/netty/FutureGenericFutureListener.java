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

package com.jfastnet.peers.netty;

import com.jfastnet.messages.Message;
import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
@Accessors(chain = true)
public class FutureGenericFutureListener implements GenericFutureListener<Future<? super Void>> {

	String action;
	Object callerObj;
	Object messageObj;

	@Setter
	String msgType;

	public FutureGenericFutureListener() {}

	public FutureGenericFutureListener(String msgType) {
		this.msgType = msgType;
	}

	public FutureGenericFutureListener(String action, Object callerObj, Object messageObj) {
		this.action = action;
		this.callerObj = callerObj;
		this.messageObj = messageObj;
	}

	@Override
	public void operationComplete(final Future<? super Void> future) throws Exception {
		if (!future.isSuccess()) {
			if (callerObj != null) {
				log.error(
						"Operation failed for '{}'. Caller: {}, Message: {}, toString: {}",
						new Object[] { action, callerObj.getClass().getSimpleName(), messageObj.getClass().getSimpleName(),
										messageObj.toString(), future.cause() }
					);
				if (messageObj instanceof Message) {
					Message message = (Message) messageObj;
					if (message.payload instanceof ByteBuf) {
						ByteBuf payload = (ByteBuf) message.payload;
						int writerIndex = payload.writerIndex();
						log.error("Size of failed message: {}", writerIndex);
					}
				}
			} else if (msgType == null) {
				log.error("Operation failed", future.cause());
			} else {
				log.error("Operation failed for {}", msgType, future.cause());
			}
		}
	}
}
