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

package com.jfastnet.peers;

import com.jfastnet.ConfigStateContainer;
import com.jfastnet.messages.Message;
import com.jfastnet.state.ClientState;
import com.jfastnet.state.NetworkQuality;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class CongestionControl<T> {

	private final ConfigStateContainer configStateContainer;
	private final Consumer<T> packetSender;

	private final Queue<DelayedPacket> packetQueue = new ArrayDeque<>();

	private long delay;

	public CongestionControl(ConfigStateContainer configStateContainer, Consumer<T> packetSender) {
		this.configStateContainer = configStateContainer;
		this.packetSender = packetSender;
	}

	public void send(Message message, T packet) {
		if (message.isResendMessage()) {
			immediateSend(packet);
			return;
		}
		InetSocketAddress socketAddressRecipient = message.socketAddressRecipient;
		ClientState clientState = configStateContainer.state.getClientStates().getBySocketAddress(socketAddressRecipient);
		float qualityFactor = 1f;
		if (clientState != null) {
			NetworkQuality networkQuality = clientState.getNetworkQuality();
			qualityFactor = networkQuality.qualityFactor;
		}

		if (qualityFactor > 0.9f && packetQueue.isEmpty()) {
			immediateSend(packet);
			delay = 0;
		} else {
			delay = (long) ((1f - qualityFactor) * 1000);

			long sendTimeStamp;
			DelayedPacket lastDelayedPacket = packetQueue.peek();
			if (lastDelayedPacket != null) {
				sendTimeStamp = lastDelayedPacket.sendTimeStamp + delay;
			} else {
				long currentTime = configStateContainer.config.timeProvider.get();
				sendTimeStamp = currentTime + delay;
			}
			packetQueue.add(new DelayedPacket(sendTimeStamp, packet));
		}
	}

	private void immediateSend(T packet) {
		packetSender.accept(packet);
	}

	public void process() {
		long currentTime = configStateContainer.config.timeProvider.get();
		for (DelayedPacket delayedPacket = packetQueue.peek();
			 delayedPacket != null && delayedPacket.sendTimeStamp <= currentTime;
			 delayedPacket = packetQueue.peek()) {

			immediateSend(delayedPacket.packet);
			packetQueue.poll();
		}
	}

	private class DelayedPacket {
		long sendTimeStamp;
		T packet;
		DelayedPacket(long sendTimeStamp, T packet) {
			this.sendTimeStamp = sendTimeStamp;
			this.packet = packet;
		}
	}
}
