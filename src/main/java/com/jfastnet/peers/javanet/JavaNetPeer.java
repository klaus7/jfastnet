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

package com.jfastnet.peers.javanet;

import com.jfastnet.*;
import com.jfastnet.messages.Message;
import com.jfastnet.peers.CongestionControl;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class JavaNetPeer implements IPeer {

	private final Config config;
	private final State state;

	private CongestionControl<DatagramPacket> congestionControl;

	private DatagramSocket socket;
	private Thread receiveThread;

	public JavaNetPeer(Config config, State state) {
		this.config = config;
		this.state = state;
	}

	@Override
	public boolean start() {
		try {
			stop();
			createSocket();
			congestionControl = new CongestionControl<>(new ConfigStateContainer(config, state), this::socketSend);
			receiveThread = new Thread(new MessageReceivingRunnable());
			receiveThread.setName("JavaNetPeer-receiver");
			receiveThread.start();
		} catch (Exception e) {
			log.error("Couldn't start peer.", e);
			return false;
		}
		return true;
	}

	private void createSocket() throws SocketException {
		socket = new DatagramSocket(config.bindPort);
		socket.setSendBufferSize(config.socketSendBufferSize);
		socket.setReceiveBufferSize(config.socketReceiveBufferSize);
	}

	@Override
	public void stop() {
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
			socket = null;
			if (receiveThread != null && receiveThread.isAlive()) {
				receiveThread.join(3000);
				if (receiveThread.isAlive()) {
					log.warn("Receiver thread should be destroyed by now.");
				}
			}
			receiveThread = null;
		} catch (Exception e) {
			log.error("Closing of socket failed.", e);
		}
	}

	@Override
	public boolean createPayload(Message message) {
		byte[] data = getByteArray(message);
		message.payload = data;
		return data != null;
	}

	@Override
	public boolean send(Message message) {
		if (message.payload == null) {
			log.error("Payload of message {} may not be null.", message.toString());
			return false;
		}
		if (message.socketAddressRecipient == null) {
			log.error("Recipient of message {} may not be null.", message.toString());
			return false;
		}
		byte[] payload = (byte[]) message.payload;
		if (config.trackData) {
			int frame = 0;
			config.netStats.getData().add(
					new NetStats.Line(true, message.getSenderId(), frame, System.currentTimeMillis(), message.getClass(), (payload).length));
		}

		log.trace("Send message: {}", message);
		log.trace("Payload length: {}", payload.length);
		DatagramPacket datagramPacket = new DatagramPacket(payload, payload.length, message.socketAddressRecipient);
		congestionControl.send(message, datagramPacket);
		return true;
	}

	private void socketSend(DatagramPacket datagramPacket) {
		if (socket == null || socket.isClosed()) {
			log.warn("Couldn't send message: Socket is closed!");
			return;
		}
		try {
			socket.send(datagramPacket);
		} catch (IOException e) {
			log.error("Couldn't send message.", e);
		}
	}

	public byte[] getByteArray(Message message) {
		byte[] data = config.serialiser.serialise(message);
		log.trace("Message size of {} is {}", message, data != null ? data.length : -1);
		return data;
	}

	public void receive(DatagramPacket packet) {
		Message message = config.serialiser.deserialise(packet.getData(), packet.getOffset(), packet.getLength());
		if (message == null) {
			return;
		}
		log.trace("Received message: {}", message);
		if (config.debug.simulateLossOfPacket()) {
			// simulated N % loss rate
			log.warn("DEBUG: simulated loss of packet: {}", message);
			return;
		}

		trackData(message);

		message.socketAddressSender = new InetSocketAddress(packet.getAddress(), packet.getPort());

		message.setConfig(config);
		message.setState(state);

		message.getFeatures().resolve();

		message = message.beforeReceive();

		// Let the controller receive the message.
		// Processors are called there.
		config.internalReceiver.receive(message);
	}

	private void trackData(Message message) {
		if (config.trackData) {
			int frame = 0;
			config.netStats.getData().add(
					new NetStats.Line(false, message.getSenderId(), frame, message.getTimestamp(), message.getClass(), ((byte[]) message.payload).length));
		}
	}

	@Override
	public void process() {
		congestionControl.process();
	}

	private class MessageReceivingRunnable implements Runnable {
		@Override
		public void run() {
			log.info("Start receiver thread {} for senderId {}", Thread.currentThread().getId(), config.senderId);
			final byte[] receiveData = new byte[config.socketReceiveBufferSize];
			final DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			while(true) {
				try {
					if (socket == null || socket.isClosed()) {
						log.info("Receiving socket closed.");
						break;
					}
					socket.receive(receivePacket);
					receive(receivePacket);
				} catch (Exception e) {
					if (socket != null && !socket.isClosed()) {
						log.warn("Error receiving packet.", e);
					} else {
						log.warn("Error receiving packet. Socket is already closed!");
					}
				}
			}
			log.info("Receiver thread {} ended", Thread.currentThread().getId());
		}
	}
}
