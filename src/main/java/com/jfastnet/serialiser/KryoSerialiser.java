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

package com.jfastnet.serialiser;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.jfastnet.config.SerialiserConfig;
import com.jfastnet.messages.Message;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Blazing fast kryo serialiser. Strongly recommended!
 * @author Klaus Pfeiffer - klaus@allpiper.com */
@Slf4j
public class KryoSerialiser implements ISerialiser{

	private final SerialiserConfig config;

	private Output output = new Output(128, 1048576);

	private ThreadLocal<Kryo> kryos;

	private Kryo kryo;

	private CRC32 crc = new CRC32();

	public KryoSerialiser(SerialiserConfig config, ThreadLocal<Kryo> kryos) {
		this.config = config;
		this.kryos = kryos;
	}

	public KryoSerialiser(SerialiserConfig config, Kryo kryo) {
		this.config = config;
		this.kryo = kryo;
	}

	@Override
	public byte[] serialise(Message message) {
		try {
			output = new Output(128, 1048576);
//			output.clear(); // FIXME see https://github.com/EsotericSoftware/kryo/issues/312
			getKryo().writeClassAndObject(output, message);
			output.flush();
			return output.toBytes();
		} catch (Exception e) {
			log.error("Couldn't create output byte array.", e);
			if (output != null) {
				log.error("Output position: {}, Buffer length: {}", output.position(), output.getBuffer().length);
			}
		} finally {
			output.close();
		}
		return null;
	}

	@Override
	public Message deserialise(byte[] byteArray, int offset, int length) {
		try (Input input = new Input(byteArray, offset, length)) {
			Message message = (Message) getKryo().readClassAndObject(input);
			if (message != null) {
				message.payload = byteArray;
				return message;
			}
		} catch (Exception e) {
			log.error("Couldn't stream from byte array.", e);
		}
		return null;
	}

	@Override
	public void serialiseWithStream(Message message, OutputStream _outputStream) {
		if (config.useBasicCompression) {
			// It usually requires more bytes if used with deflater. Depends on the size.
			_outputStream = new DeflaterOutputStream(_outputStream);
		}
		try (OutputStream outputStream = _outputStream) {
			Output output = new Output(outputStream);
			getKryo().writeClassAndObject(output, message);
			output.close();
		} catch (IOException e) {
			log.error("Couldn't stream to byte buffer.", e);
		}
	}

	@Override
	public Message deserialiseWithStream(InputStream _is) {
		if (this.config.useBasicCompression) {
			_is = new InflaterInputStream(_is);
		}
		try (InputStream is = _is) {
			Input input = new Input(is);
			Message message = (Message) getKryo().readClassAndObject(input);
			if (message != null) {
				//message.payload = content;
				return message;
			}
		} catch (EOFException e) {
			log.error("EOFException", e);
		} catch (IOException e) {
			log.error("Couldn't stream from byte buffer.", e);
		}
		return null;
	}

	@Override
	public CRC32 getChecksum(Message message, byte[] salt) {
		Object output = message.payload;
		crc.reset();
		if (output instanceof byte[]) {
			byte[] bytes = (byte[]) output;
			crc.update(bytes);
			crc.update(salt);
		} else {
			if (output == null) {
				log.error("Payload was null.");
			} else {
				log.error("Type '{}' not supported", output.getClass());
			}
			return null;
		}
		return crc;
	}

	private Kryo getKryo() {
		return kryos != null ? kryos.get() : this.kryo;
	}
}
