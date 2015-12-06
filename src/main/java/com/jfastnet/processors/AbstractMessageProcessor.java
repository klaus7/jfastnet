package com.jfastnet.processors;

import com.jfastnet.Config;
import com.jfastnet.State;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public abstract class AbstractMessageProcessor {

	public Config config;

	public State state;

	public AbstractMessageProcessor(Config config, State state) {
		assert config != null : "Config may not be null!";
		assert state != null : "State may not be null!";
		this.config = config;
		this.state = state;
	}
}
