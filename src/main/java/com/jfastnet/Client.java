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

import com.jfastnet.messages.*;
import com.jfastnet.messages.features.TimestampFeature;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.locks.ReentrantLock;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class Client extends PeerController {

	/** Time of the last received message. */
	@Getter
	private long lastReceivedMessageTime;

	/** If not set it is retrieved from Config.senderId */
	@Setter(AccessLevel.PACKAGE)
	private int clientId;

	/** Timestamp of last keep alive check. */
	private long lastKeepAliveCheck;

	/** Timestamp of sent connect request. */
	private long connectRequestTimestamp;

	/** Address of the server. */
	private InetSocketAddress serverSocketAddress;

	private ReentrantLock connectLock = new ReentrantLock();

	public Client(Config config) {
		super(config);
		state.setHost(false);
		clientId = config.senderId;
	}

	@Override
	public void process() {
		super.process();
		sendKeepAliveSequencePacket();
	}

	public void sendKeepAliveSequencePacket() {
		if (state.isConnected()) {
			long currentTime = config.timeProvider.get();
			if (lastKeepAliveCheck + config.keepAliveInterval < currentTime) {
				lastKeepAliveCheck = currentTime;
				send(new SequenceKeepAlive());
			}
		}
	}

	@Override
	public boolean start() {
		boolean start = super.start();
		serverSocketAddress = new InetSocketAddress(config.host, config.port);
		if (start) {
			log.info("Say hello to server at {}:{}.", config.host, config.port);
			send(new ConnectRequest(clientId));
			connectRequestTimestamp = config.timeProvider.get();
		}
		lastReceivedMessageTime = config.timeProvider.get();
		return start;
	}

	/** Wait until connect response is received. */
	public void blockingWaitUntilConnected() {
		process();
		connectLock.lock();
		try {
			try {
				final int processTimeInterval = 100;
				while (!state.connected && config.timeProvider.get() - connectRequestTimestamp < config.connectTimeout) {
					connectLock.unlock();
					process();
					Thread.sleep(processTimeInterval);
					connectLock.lock();
				}
			} catch (InterruptedException e) {
				connectLock.lock();
				log.error("Wait for connection interrupted.", e);
			}
			if (state.connected) {
				log.info("Connection established!");
			} else {
				log.error("Connection failed!");
				state.connectionFailed = true;
				stop();
			}
		} finally {
			connectLock.unlock();
		}
	}

	@Override
	public boolean send(Message message) {
		// Keep alive only has to be sent, when no other messages are sent
		lastKeepAliveCheck = config.timeProvider.get();
		message.setSenderId(config.senderId);
		message.socketAddressRecipient = serverSocketAddress;
		return super.send(message);
	}

	@Override
	public void receive(Message message) {
		InetSocketAddress senderAddress = message.getSocketAddressSender();
		if (senderAddress == null || !senderAddress.equals(serverSocketAddress)) {
			log.warn("Message not from server {}, but from {}! {}", new Object[]{serverSocketAddress, senderAddress, message});
			return;
		}
		log.trace("Received message: {}", message);

		lastReceivedMessageTime = System.currentTimeMillis();
		if (message instanceof ConnectResponse && !state.connected) {
			connectLock.lock();
			try {
				if (!state.connectionFailed) {
					ConnectResponse connectResponse = (ConnectResponse) message;
					connectResponse.setLastReliableSeqIdInSequenceProcessor();
					state.connected = true;
					clientId = connectResponse.getClientId();
					log.info("Set client id to {}", clientId);
					config.setSenderId(clientId);
					config.newSenderIdConsumer.accept(clientId);
				} else {
					log.warn("ConnectResponse received, but connection failed already!");
				}
			} finally {
				connectLock.unlock();
			}
		}
		if (state.connected) {
			if (message instanceof TimerSyncMessage) {
				// received timer sync message
				// also used for the heart beat
				if (clientId != 0) {
					TimerSyncMessage timerSyncAction = new TimerSyncMessage(clientId);
					TimestampFeature timestampFeature = new TimestampFeature();
					timestampFeature.timestamp = System.currentTimeMillis();
					timerSyncAction.getFeatures().add(timestampFeature);
					send(timerSyncAction);
				}
			} else {
				super.receive(message);
			}
		}
		if (message instanceof LeaveConfirmationResponse) {
			log.info("Received LeaveConfirmationResponse");
			super.receive(message);
			this.state.getUdpPeer().stop();
		}
	}

	/** @return true if timeout for last received message reached. */
	public boolean noResponseFromServer() {
		return config.timeProvider.get() - lastReceivedMessageTime > config.timeoutThreshold;
	}

	public boolean isConnected() {
		return state.connected;
	}
}
