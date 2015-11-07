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

import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Statistics about the network traffic.
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class NetStats {
	public static final DateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS");

	private static final String CSV_SEPARATOR = ";";

	private List<Line> data = new ArrayList<>();

	/** Reliable sequence resent messages. */
	public AtomicLong resentMessages = new AtomicLong();

	/** Reliable sequence sent messages. */
	public AtomicLong sentMessages = new AtomicLong();
	public AtomicLong requestedMissingMessages = new AtomicLong();

	public synchronized List<Line> getData() {
		return data;
	}

	public void clear() {
		data = new ArrayList<>();
	}

	public void writeLog() {
		log.info("Sent sequenced messages:    {}", sentMessages);
		log.info("Resent sequenced messages:  {}", resentMessages);
		log.info("Requested missing messages: {}", requestedMissingMessages);
		log.info("Lost percentage:            {} %", String.format("%.2f", ((float) resentMessages.get() / sentMessages.get()) * 100));
	}

	public void write() {
		String filename = "netstats-" + FILE_DATE_FORMAT.format(new Date()) + ".csv";
		log.info("Write netstats to {}", filename);
		try {
			FileWriter fw = new FileWriter(filename);
			// write header
			fw.write("sent;timestamp;frame;clientid;class;size");
			fw.write("\n");
			synchronized (data) {
				for (Line line : new ArrayList<>(getData())) {
					fw.write(String.valueOf(line.sent));
					fw.write(CSV_SEPARATOR);
					fw.write(String.valueOf(line.timestamp));
					fw.write(CSV_SEPARATOR);
					fw.write(String.valueOf(line.frame));
					fw.write(CSV_SEPARATOR);
					fw.write(String.valueOf(line.clientId));
					fw.write(CSV_SEPARATOR);
					fw.write(line.clazz.getSimpleName());
					fw.write(CSV_SEPARATOR);
					fw.write(String.valueOf(line.byteBufSize));
					fw.write("\n");
				}
			}
			fw.flush();
		} catch (Exception e) {
			log.error("Couldn't write netstats file.", e);
		}
	}

	public static class Line {
		public boolean sent;
		public int clientId;
		public int frame;
		public long timestamp;
		public Class clazz;
		public int byteBufSize;

		public Line(boolean sent, int clientId, int frame, long timestamp, Class clazz, int byteBufSize) {
			this.sent = sent;
			this.clientId = clientId;
			this.frame = frame;
			this.timestamp = timestamp;
			this.clazz = clazz;
			this.byteBufSize = byteBufSize;
		}
	}
}
