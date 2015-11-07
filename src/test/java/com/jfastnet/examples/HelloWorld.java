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

package com.jfastnet.examples;

import com.jfastnet.Client;
import com.jfastnet.Config;
import com.jfastnet.Server;
import com.jfastnet.messages.GenericMessage;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class HelloWorld {

	public static int received = 0;

	public static class PrintMessage extends GenericMessage {
		public PrintMessage(Object object) { super(object); }
		@Override
		public void process() {
			System.out.println(object);
			received++;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		Server server = new Server(new Config().setBindPort(15150));
		Client client = new Client(new Config().setPort(15150));

		server.start();
		client.start();
		client.blockingWaitUntilConnected();

		server.send(new PrintMessage("Hello Client!"));
		client.send(new PrintMessage("Hello Server!"));

		while (received < 2) Thread.sleep(100);

		client.stop();
		server.stop();
	}

}
