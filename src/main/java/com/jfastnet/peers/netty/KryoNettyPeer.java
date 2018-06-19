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

import com.jfastnet.Config;
import com.jfastnet.IPeer;
import com.jfastnet.NetStats;
import com.jfastnet.messages.Message;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class KryoNettyPeer implements IPeer {

	ChannelHandler channelHandler;

	Channel channel;
	EventLoopGroup group;

	Config config;
	private Random debugRandom = new Random();

	public KryoNettyPeer(Config config, ChannelHandler channelHandler) {
		this.config = config;
		this.channelHandler = channelHandler;
	}

	public KryoNettyPeer(Config config) {
		this.config = config;
	}

	@Override
	public boolean start() {
		group = new NioEventLoopGroup();
		try {
			Bootstrap b = new Bootstrap();
			b.group(group)
					.channel(NioDatagramChannel.class)
					.option(ChannelOption.SO_BROADCAST, true)
					.option(ChannelOption.SO_SNDBUF, config.socketSendBufferSize)
					.option(ChannelOption.SO_RCVBUF, config.socketReceiveBufferSize)
					.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(config.receiveBufferAllocator))
					.handler(channelHandler != null ? channelHandler : new UdpHandler());

			channel = b.bind(config.bindPort).sync().channel();

		} catch (Exception e) {
			log.error("Couldn't start server.", e);
			return false;
		}
		return true;
	}

	@Override
	public void stop() {
		try {
			if (channel != null) {
				channel.close().await();
			}
		} catch (Exception e) {
			log.error("Closing of channel failed.", e);
		} finally {
			if (group != null) {
				group.shutdownGracefully();
			}
		}
	}

	@Override
	public boolean createPayload(Message message) {
		ByteBuf data = getByteBuf(message);
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
		if (config.trackData) {
			int frame = 0;
			config.netStats.getData().add(
					new NetStats.Line(true, message.getSenderId(), frame, message.getTimestamp(), message.getClass(), ((ByteBuf)message.payload).writerIndex()));
		}

		channel.writeAndFlush(new DatagramPacket((ByteBuf) message.payload, message.socketAddressRecipient))
				.addListener(new FutureGenericFutureListener("writeAndFlush", KryoNettyPeer.this, message));
		return true;
	}

	public ByteBuf getByteBuf(Message message) {
		ByteBuf data = Unpooled.buffer();
		config.serialiser.serialiseWithStream(message, new ByteBufOutputStream(data));

		int length = data.writerIndex();
		log.trace("Message size of {} is {}", message, length);
//		if (length > config.maximumUdpPacketSize) {
//			log.error("Message {} exceeds maximum size of {}! Size is {} byte", new Object[]{message, config.maximumUdpPacketSize, length});
//			return null;
//		}
		return data.retain();
	}

	public void receive(ChannelHandlerContext ctx, DatagramPacket packet) {
		ByteBuf content = packet.content();
		Message message = config.serialiser.deserialiseWithStream(new ByteBufInputStream(content));
		if (message == null) {
			return;
		}
		// TODO set config and state
		message.payload = content;
		if (message.getFeatures() != null) {
			message.getFeatures().resolve();
		}

		if (config.debug.simulateLossOfPacket()) {
			// simulated N % loss rate
			log.warn("DEBUG: simulated loss of packet: {}", message);
			return;
		}

		message.socketAddressSender = packet.sender();
		message.socketAddressRecipient = packet.recipient();

		if (config.trackData) {
			int frame = 0;
			config.netStats.getData().add(
					new NetStats.Line(false, message.getSenderId(), frame, message.getTimestamp(), message.getClass(), ((ByteBuf)message.payload).writerIndex()));
		}

		// Let the controller receive the message.
		// Processors are called there.
		config.internalReceiver.receive(message);
	}

	@Override
	public void process() {}

	public class UdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
			receive(ctx, packet);
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
			ctx.flush();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)	throws Exception {
			log.error("UdpHandler error.", cause);
			ctx.close();
			// We don't close the channel because we can keep serving requests.
		}
	}

}
