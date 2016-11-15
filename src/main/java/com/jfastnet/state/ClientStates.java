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

package com.jfastnet.state;

import com.jfastnet.Config;
import com.jfastnet.ISimpleProcessable;
import lombok.ToString;

import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@ToString
public class ClientStates implements ISimpleProcessable {

	private final ClientState emptyClientState;
	private final Config config;

	private Map<Integer, InetSocketAddress> clientAddressMap = new ConcurrentHashMap<>();
	private Map<Integer, ClientState> clientStateMap = new ConcurrentHashMap<>();

	public ClientStates(Config config) {
		this.config = config;
		emptyClientState = new ClientState(config);
	}

	public ClientState getById(int id) {
		return clientStateMap.getOrDefault(id, emptyClientState);
	}

	public boolean hasAddress(InetSocketAddress socketAddress) {
		return clientAddressMap.containsValue(socketAddress);
	}

	public ClientState getBySocketAddress(InetSocketAddress socketAddress) {
		Integer id = getIdBySocketAddress(socketAddress);
		if (id != null) {
			return getById(id);
		}
		return null;
	}

	public Integer getIdBySocketAddress(InetSocketAddress socketAddress) {
		Optional<Map.Entry<Integer, InetSocketAddress>> socketAddressOptional = clientAddressMap.entrySet().stream()
				.filter(entry -> entry.getValue().equals(socketAddress))
				.findFirst();
		if (socketAddressOptional.isPresent()) {
			return socketAddressOptional.get().getKey();
		}
		return null;
	}

	public int newClientId() {
		Integer maximumId = clientStateMap.keySet().stream().max(Comparator.naturalOrder()).orElse(0);
		int clientId = maximumId == null ? 1 : maximumId + 1;
		return clientId;
	}

	public boolean hasId(int clientId) {
		return clientStateMap.containsKey(clientId);
	}

	public void put(int clientId, InetSocketAddress socketAddress) {
		clientAddressMap.put(clientId, socketAddress);
		clientStateMap.put(clientId, new ClientState(config, socketAddress));
	}

	public int size() {
		return clientStateMap.size();
	}

	public Set<Map.Entry<Integer, InetSocketAddress>> addressEntrySet() {
		return clientAddressMap.entrySet();
	}

	public void remove(int clientId) {
		clientAddressMap.remove(clientId);
		clientStateMap.remove(clientId);
	}

	public Set<Integer> idSet() {
		return clientStateMap.keySet();
	}

	@Override
	public void process() {
		clientStateMap.forEach((id, clientState) -> clientState.getNetworkQuality().calculateQuality());
	}

}
