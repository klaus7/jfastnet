/*******************************************************************************
 * Copyright 2015 Klaus Pfeiffer - klaus@allpiper.com
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

import com.jfastnet.events.EventLog;
import com.jfastnet.idprovider.IIdProvider;
import com.jfastnet.messages.MessagePart;
import com.jfastnet.processors.IMessageReceiverPostProcessor;
import com.jfastnet.processors.IMessageReceiverPreProcessor;
import com.jfastnet.processors.IMessageSenderPostProcessor;
import com.jfastnet.processors.IMessageSenderPreProcessor;
import com.jfastnet.state.ClientStates;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
@Getter
@Accessors(chain = true)
public class State {

	/** UDP peer system to use. (e.g. KryoNetty) */
	private IPeer udpPeer;

	/** Provides the message ids. */
	public IIdProvider idProvider;

	/** Are we the host? Server sets this to true on creation. */
	@Setter
	private boolean isHost;

	/** The server holds track of its clients. */
	private final ClientStates clientStates;

	/** Client will only receive messages, if connected. */
	public volatile boolean connected = false;
	public volatile boolean connectionFailed = false;

	private final EventLog eventLog;

	/** Stacked messages can be temporarily disabled. e.g. if the packet size
	 * of the stacked messages is too big. */
	@Setter
	private boolean enableStackedMessages = true;

	/** Used for receiving bigger messages. Only one byte array buffer may
	 * be processed at any given time.
	 * Key: channel id; value: (key: message part position; value: message part) */
	private SortedMap<Long, SortedMap<Integer, MessagePart>> byteArrayBufferMap = new TreeMap<>();

	/** Map of all added processors. */
	private final Map<Class, Object> processorMap = new HashMap<>();

	/** List of systems that need to be processed every tick. */
	private final List<ISimpleProcessable> processables = new ArrayList<>();
	private final List<IMessageSenderPreProcessor> messageSenderPreProcessors = new ArrayList<>();
	private final List<IMessageSenderPostProcessor> messageSenderPostProcessors = new ArrayList<>();
	private final List<IMessageReceiverPreProcessor> messageReceiverPreProcessors = new ArrayList<>();
	private final List<IMessageReceiverPostProcessor> messageReceiverPostProcessors = new ArrayList<>();

	@Getter
	private Config config;

	public State(Config config) {
		this.config = config;
		this.eventLog = new EventLog(config, this);
		createIdProvider(config);
		createUdpPeer(config);
		createProcessors(config);
		clientStates = new ClientStates(config);
	}

	private void createIdProvider(Config config) {
		try {
			Constructor[] constructors = config.idProviderClass.getConstructors();
			// Search full-blown constructor
			for (Constructor constructor : constructors) {
				Class[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length == 2 && parameterTypes[0] == Config.class && parameterTypes[1] == State.class) {
					idProvider = (IIdProvider) constructor.newInstance(config, this);
				}
			}
			if (idProvider == null) {
				// Try default constructor
				idProvider = config.idProviderClass.newInstance();
			}
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			log.error("Couldn't create id provider {}", config.udpPeerClass, e);
		}
	}

	private void createUdpPeer(Config config) {
		try {
			Constructor[] constructors = config.udpPeerClass.getConstructors();
			// Search full-blown constructor
			for (Constructor constructor : constructors) {
				Class[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length == 2 && parameterTypes[0] == Config.class && parameterTypes[1] == State.class) {
					udpPeer = (IPeer) constructor.newInstance(config, this);
				}
			}
			if (udpPeer == null) {
				// Try default constructor
				udpPeer = config.udpPeerClass.newInstance();
			}
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			log.error("Couldn't create udp peer {}", config.udpPeerClass, e);
		}
	}

	private void createProcessors(Config config) {
		config.processorClasses.forEach(processorClass -> {
			try {
				Constructor[] constructors = processorClass.getConstructors();
				// Search full-blown constructor
				for (Constructor constructor : constructors) {
					Class[] parameterTypes = constructor.getParameterTypes();
					if (parameterTypes.length == 2 && parameterTypes[0] == Config.class && parameterTypes[1] == State.class) {
						addProcessor(constructor.newInstance(config, this));
						return;
					}
				}
				// Try default constructor
				addProcessor(processorClass.newInstance());
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
				log.error("Couldn't create processor object {}", processorClass, e);
			}
		});
	}

	public void addProcessor(Object processor) {
		processorMap.put(processor.getClass(), processor);
		if (processor instanceof ISimpleProcessable) {
			ISimpleProcessable processable = (ISimpleProcessable) processor;
			processables.add(processable);
		}
		if (processor instanceof IMessageSenderPreProcessor) {
			IMessageSenderPreProcessor messageSenderPreProcessor = (IMessageSenderPreProcessor) processor;
			messageSenderPreProcessors.add(messageSenderPreProcessor);
		}
		if (processor instanceof IMessageSenderPostProcessor) {
			IMessageSenderPostProcessor messageSenderPostProcessor = (IMessageSenderPostProcessor) processor;
			messageSenderPostProcessors.add(messageSenderPostProcessor);
		}
		if (processor instanceof IMessageReceiverPreProcessor) {
			IMessageReceiverPreProcessor messageReceiverPreProcessor = (IMessageReceiverPreProcessor) processor;
			messageReceiverPreProcessors.add(messageReceiverPreProcessor);
		}
		if (processor instanceof IMessageReceiverPostProcessor) {
			IMessageReceiverPostProcessor messageReceiverPostProcessor = (IMessageReceiverPostProcessor) processor;
			messageReceiverPostProcessors.add(messageReceiverPostProcessor);
		}
	}

	public <E> E getProcessorOf(Class<E> clazz) {
		return (E) processorMap.get(clazz);
	}
}
