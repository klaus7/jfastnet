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

package com.jfastnet.peers.javanet;

import com.jfastnet.Config;
import com.jfastnet.IPeer;
import com.jfastnet.NetStats;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.MessagePart;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class JavaNetPeer implements IPeer {

	Config config;
	private Random debugRandom = new Random();

	private DatagramSocket socket;

	private Thread receiveThread;

	public JavaNetPeer(Config config) {
		this.config = config;
	}

	@Override
	public boolean start() {
		try {
			socket = new DatagramSocket(config.bindPort);
			socket.setSendBufferSize(config.socketSendBufferSize);
			socket.setReceiveBufferSize(config.socketReceiveBufferSize);

			receiveThread = new Thread(new MessageReceivingRunnable());
			receiveThread.start();
		} catch (Exception e) {
			log.error("Couldn't start server.", e);
			return false;
		}
		return true;
	}

	@Override
	public void stop() {
		try {
			if (socket != null) {
				socket.close();
			}
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

		if (payload.length > config.maximumUdpPacketSize) {
			if (config.autoSplitTooBigMessages) {
				final List<MessagePart> parts = MessagePart.createFromMessage(config, 0, message, config.maximumUdpPacketSize - Message.MESSAGE_HEADER_SIZE, message.getReliableMode());
				if (parts != null) {
					parts.forEach(this::queue);
					return true;
				} else {
					log.error("Message {} exceeds configured maximumUdpPacketSize of {}. Payload size is {}.",
							new Object[]{message, config.maximumUdpPacketSize, payload.length});
					log.error("Parts couldn't be created for message {}", message);
				}
			} else {
				// Write error message, but still try to send the message.
				// OS could prevent too big messages from being sent.
				log.error("Message {} exceeds configured maximumUdpPacketSize of {}. Payload size is {}.",
						new Object[]{message, config.maximumUdpPacketSize, payload.length});
			}
		}

		try {
			log.trace("Payload length: {}", payload.length);
			socket.send(new DatagramPacket(payload, payload.length, message.socketAddressRecipient));
		} catch (IOException e) {
			log.error("Couldn't send message.", e);
			return false;
		}
		return true;
	}

	public byte[] getByteArray(Message message) {
		byte[] data = config.serialiser.serialise(message);
		log.trace("Message size of {} is {}", message, data != null ? data.length : -1);
		return data;
	}

	public void receive(DatagramPacket packet) {
		Message message = config.serialiser.deserialise(config, packet.getData(), packet.getOffset(), packet.getLength());
		if (message == null) {
			return;
		}
		if (message.getFeatures() != null) {
			message.getFeatures().resolve();
		}

		if (config.debug && debugRandom.nextInt(100) < config.debugLostPackagePercentage) {
			// simulated N % loss rate
			log.warn("DEBUG: simulated loss of packet: {}", message);
			return;
		}

		message.socketAddressSender = new InetSocketAddress(packet.getAddress(), packet.getPort());

		if (config.trackData) {
			int frame = 0;
			config.netStats.getData().add(
					new NetStats.Line(false, message.getSenderId(), frame, message.getTimestamp(), message.getClass(), ((byte[]) message.payload).length));
		}

		message.beforeExternalReceive();

		// Let the controller receive the message.
		// Processors are called there.
		config.receiver.receive(message);
	}

	@Override
	public void process() {}

	private class MessageReceivingRunnable implements Runnable {
		@Override
		public void run() {
			byte[] receiveData = new byte[config.socketReceiveBufferSize];
			while(true) {
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					if (socket.isClosed()) {
						log.info("Receiving socket closed.");
						break;
					}
					socket.receive(receivePacket);
					receive(receivePacket);
				} catch (IOException e) {
					if (!socket.isClosed()) {
						log.warn("Error receiving packet.", e);
					}
				}
			}
		}
	}
}
