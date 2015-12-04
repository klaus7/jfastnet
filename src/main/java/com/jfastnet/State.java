package com.jfastnet;

import lombok.Setter;
import lombok.experimental.Accessors;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Setter
@Accessors(chain = true)
public class State {

	/** Are we the host? Server sets this to true on creation. */
	public boolean isHost;

	/** The server holds track of its clients. */
	public Map<Integer, InetSocketAddress> clients = new ConcurrentHashMap<>();

}
