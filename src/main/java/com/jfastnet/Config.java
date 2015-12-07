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

import com.esotericsoftware.kryo.Kryo;
import com.jfastnet.config.SerialiserConfig;
import com.jfastnet.idprovider.ClientIdReliableModeIdProvider;
import com.jfastnet.idprovider.IIdProvider;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.MessagePart;
import com.jfastnet.peers.javanet.JavaNetPeer;
import com.jfastnet.processors.*;
import com.jfastnet.serialiser.ISerialiser;
import com.jfastnet.serialiser.KryoSerialiser;
import com.jfastnet.time.ITimeProvider;
import com.jfastnet.time.SystemTimeProvider;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Configure JFastNet with this configuration class. We don't care much about
 * visibility here, because it's only used for configuration of the system and
 * access to the fields look much cleaner without the setter/getter boilerplate.
 *
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Setter
@Accessors(chain = true)
public class Config {

	/** Message receiver that will simply call process on the message. */
	public static final IMessageReceiver DEFAULT_MESSAGE_RECEIVER = Message::process;

	public static final List<Class> DEFAULT_MESSAGE_PROCESSORS = new ArrayList<>();
	static {
		// add default processors in the order in which they get called
		DEFAULT_MESSAGE_PROCESSORS.add(AddChecksumProcessor.class);
		DEFAULT_MESSAGE_PROCESSORS.add(DiscardWrongChecksumMessagesHandler.class);
		DEFAULT_MESSAGE_PROCESSORS.add(MessageLogProcessor.class);
		DEFAULT_MESSAGE_PROCESSORS.add(StackedMessageProcessor.class);
		DEFAULT_MESSAGE_PROCESSORS.add(ReliableModeAckProcessor.class);
		DEFAULT_MESSAGE_PROCESSORS.add(ReliableModeSequenceProcessor.class);
		DEFAULT_MESSAGE_PROCESSORS.add(DiscardMessagesHandler.class);
	}

	/** Hostname or IP address. */
	public String host = "127.0.0.1";

	/** Port number to accept or request new UDP connections on. */
	public int port = 0;

	/** On client this can be 0 so a free port is automatically used. */
	public int bindPort = 0;

	/** Optional configured sender id. */
	public int senderId;

	/** Consumer is called when a new client id is retrieved through the
	 * ConnectResponse message. */
	public Consumer<Integer> newSenderIdConsumer = id -> {};

	/** Set to true if you want that a CSV file is created after every run with
	 * data about all the sent and received messages and their data size. */
	public boolean trackData = false;

	/** Collected data. Only used if trackData is set to true. */
	public NetStats netStats = new NetStats();

	/** Used for the timestamp for new messages. */
	public ITimeProvider timeProvider = new SystemTimeProvider();

	/** Provides the message ids. */
	public Class<? extends IIdProvider> idProviderClass = ClientIdReliableModeIdProvider.class;

	/** JFastNet internal message sender. */
	public IMessageSender internalSender;

	/** JFastNet internal message receiver for received messages. */
	public IMessageReceiver internalReceiver;

	/** Configure an external receiver for incoming messages. Must be thread-safe. */
	public IMessageReceiver externalReceiver = DEFAULT_MESSAGE_RECEIVER;

	/** UDP peer system to use. (e.g. KryoNetty) */
	public Class<? extends IPeer> udpPeerClass = JavaNetPeer.class;

	/** Serialisation system. Some peers require specific serialisation
	 * return types. */
	public ISerialiser serialiser = new KryoSerialiser(new SerialiserConfig(), new Kryo());

	/** Compress MessagePart messages. */
	public boolean compressBigMessages = false;

	/** Required for the reliable sequence mode. Interval in ms. */
	public int keepAliveInterval = 3000;

	/** If keepalive messages can be stacked. */
	public boolean stackKeepAliveMessages = false;

	/** After X received stacked messages we send an ack packet. */
	public int stackedMessagesAckThreshold = 7;

	/** Time in ms when peer considers other side as not reachable. */
	public int timeoutThreshold = keepAliveInterval * 6; //2;

	// BEGIN server config
	/** All client ids that are expected to join. */
	public List<Integer> expectedClientIds = new ArrayList<>();

	/** Map of clients that are required to connect.
	 * Key: client id, value: true if connected. */
	public Map<Integer, Boolean> requiredClients = new ConcurrentHashMap<>();

	/** Time that has to be passed to consider a received connect request as
	 * new. */
	public int timeSinceLastConnectRequest = 3000;

	/** Called by the server. */
	public IServerHooks serverHooks = new IServerHooks() {};
	// END server config

	// BEGIN client config
	/** Time in ms the client tries to connect to the server. */
	public int connectTimeout = 5000;
	// END client config

	/** Packets above this size will log an error or will be automatically
	 * splitted into multiple messages. */
	public int maximumUdpPacketSize = 1024;

	/** Automatically split too big messages into multiple smaller messages. */
	public boolean autoSplitTooBigMessages = true;

	public int messageQueueThreshold = 37;
	//SocketOption send buffer SO_SNDBUF
	public int socketSendBufferSize = 131072; //65536;
	public int socketReceiveBufferSize = 65536;

	public int receiveBufferAllocator = 65536;

	/** Maximum number of ids to request when not in sync anymore. */
	public int maximumRequestAbsentIds = 5;

	/** Delay in ms between sending of queued messages. */
	public int queuedMessagesDelay = 50;

	/** Set to true if you want to simulate packet loss or an otherwise
	 * rough environment. */
	public boolean debug = false;

	/** Specify percentage of lost packages from 0 to 100 where 100 means
	 * every packet. */
	public int debugLostPackagePercentage = 1;

	/** List of all added processors. */
	public List<Class> processorClasses = DEFAULT_MESSAGE_PROCESSORS;

	public Predicate<Message> messageLogReceiveFilter = new MessageLog.NoMessagesPredicate();
	public Predicate<Message> messageLogSendFilter = new MessageLog.ReliableMessagesPredicate();

}
