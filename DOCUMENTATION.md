# JFastNet Documentation

Welcome to the JFastNet documentation! This document provides a comprehensive overview of the JFastNet library, from basic usage to advanced features.

## Table of Contents

* [Getting Started](#getting-started)
* [Configuration](#configuration)
* [Messages](#messages)
* [Examples](#examples)

---

## Getting Started

This section will guide you through the process of setting up a simple client and server and sending messages between them.

### 1. Add JFastNet to your project

If you are using Maven, add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.jfastnet</groupId>
    <artifactId>jfastnet</artifactId>
    <version>0.3.8</version>
</dependency>
```

### 2. Create a Message

First, you need to create a message class that extends `com.jfastnet.messages.GenericMessage`. This class will contain the data you want to send over the network.

```java
public class PrintMessage extends GenericMessage {

    /** no-arg constructor required for serialization. */
    private PrintMessage() {}

    public PrintMessage(Object object) { super(object); }

    @Override
    public void process(Object context) {
        System.out.println(object);
    }
}
```

The `process` method is where you define the logic that will be executed when the message is received.

### 3. Create a Server and a Client

Next, you need to create a server and a client. The `Config` class is used to configure the server and client.

```java
Server server = new Server(new Config().setBindPort(15150));
Client client = new Client(new Config().setPort(15150));

server.start();
client.start();
client.blockingWaitUntilConnected();
```

In this example, the server is configured to listen on port `15150`, and the client is configured to connect to the server on the same port.

### 4. Send Messages

Now you can send messages between the server and the client.

```java
server.send(new PrintMessage("Hello Client!"));
client.send(new PrintMessage("Hello Server!"));
```

### 5. Putting it all together

Here is the complete code for the `HelloWorld` example:

```java
import com.jfastnet.Client;
import com.jfastnet.Config;
import com.jfastnet.Server;
import com.jfastnet.messages.GenericMessage;

import java.util.concurrent.atomic.AtomicInteger;

public class HelloWorld {

    private static final AtomicInteger received = new AtomicInteger(0);

    public static class PrintMessage extends GenericMessage {

        /** no-arg constructor required for serialization. */
        private PrintMessage() {}

        PrintMessage(Object object) { super(object); }

        @Override
        public void process(Object context) {
            System.out.println(object);
            received.incrementAndGet();
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

        while (received.get() < 2) Thread.sleep(100);

        client.stop();
        server.stop();
    }
}
```

This example demonstrates the basic usage of JFastNet. In the following sections, we will explore the more advanced features of the library.

---

## Configuration

JFastNet provides a `Config` class that allows you to customize the behavior of the client and server. You can create a `Config` object and pass it to the `Server` or `Client` constructor.

```java
Config config = new Config();
config.setPort(15150);
Server server = new Server(config);
```

The following is a list of the available configuration options:

### General

| Option | Type | Default | Description |
|---|---|---|---|
| `host` | `String` | `"127.0.0.1"` | The hostname or IP address to connect to (client) or bind to (server). |
| `port` | `int` | `0` | The port number to connect to (client). |
| `bindPort` | `int` | `0` | The port number to bind to. On the client, you can leave this at `0` to automatically use a free port. |
| `senderId` | `int` | `0` | An optional sender ID. `0` is reserved for the server. |
| `udpPeerClass` | `Class<? extends IPeer>` | `JavaNetPeer.class` | The UDP peer system to use. |
| `trackData` | `boolean` | `false` | Set to `true` to create a CSV file with data about all sent and received messages. |
| `timeProvider` | `ITimeProvider` | `SystemTimeProvider.class` | The time provider used for message timestamps. |
| `idProviderClass` | `Class<? extends IIdProvider>` | `ClientIdReliableModeIdProvider.class` | The provider for message IDs. |
| `serialiser` | `ISerialiser` | `KryoSerialiser` | The serialization system to use. |
| `compressBigMessages` | `boolean` | `false` | Set to `true` to compress large messages. |
| `keepAliveInterval` | `int` | `3000` | The interval in milliseconds for sending keep-alive messages. Required for the reliable sequence mode. |
| `stackKeepAliveMessages` | `boolean` | `false` | Set to `true` to stack keep-alive messages. |
| `timeoutThreshold` | `int` | `keepAliveInterval * 6` | The time in milliseconds after which a peer is considered unreachable. |
| `maximumUdpPacketSize` | `int` | `1024` | The maximum UDP packet size. Packets larger than this will be logged or split. |
| `autoSplitTooBigMessages` | `boolean` | `true` | Set to `true` to automatically split large messages into smaller ones. |
| `messageQueueThreshold` | `int` | `37` | The message queue threshold. |
| `socketSendBufferSize` | `int` | `131072` | The socket send buffer size (SO_SNDBUF). |
| `socketReceiveBufferSize` | `int` | `65536` | The socket receive buffer size (SO_RCVBUF). |
| `receiveBufferAllocator` | `int` | `65536` | The receive buffer allocator size. |
| `eventLogSize` | `int` | `4096` | The maximum size of the event log queue. |
| `queuedMessagesDelay` | `int` | `50` | The delay in milliseconds between sending queued messages. |

### Server

| Option | Type | Default | Description |
|---|---|---|---|
| `expectedClientIds` | `List<Integer>` | `new ArrayList<>()` | A list of all client IDs that are expected to join. |
| `requiredClients` | `Map<Integer, Boolean>` | `new ConcurrentHashMap<>()` | A map of clients that are required to connect. |
| `timeSinceLastConnectRequest` | `int` | `3000` | The time that has to pass to consider a received connect request as new. |
| `serverHooks` | `IServerHooks` | `new IServerHooks() {}` | Callbacks for server events. |

### Client

| Option | Type | Default | Description |
|---|---|---|---|
| `connectTimeout` | `int` | `5000` | The time in milliseconds the client tries to connect to the server. |

### Debugging

The `Config` class also has a `Debug` inner class that provides options for simulating packet loss and other network issues.

| Option | Type | Default | Description |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Set to `true` to enable debug mode. |
| `discardNextPacket` | `boolean` | `false` | Set to `true` to discard the next received packet. |
| `lostPacketsPercentage` | `int` | `1` | The percentage of lost packets (0-100). |

---

## Messages

The `Message` class is the base class for all messages in JFastNet. It provides a number of features that allow you to control how messages are sent and processed.

### Creating Custom Messages

To create a custom message, you need to extend the `Message` class (or a subclass like `GenericMessage`) and implement the `process` method.

```java
public class MyMessage extends Message<MyContext> {

    private String text;

    public MyMessage(String text) {
        this.text = text;
    }

    @Override
    public void process(MyContext context) {
        context.doSomethingWithMessage(text);
    }
}
```

### Reliability Modes

JFastNet provides three reliability modes for sending messages:

*   **`UNRELIABLE`**: The message is sent once and is not guaranteed to arrive. This is the fastest mode, but should only be used for non-essential data.
*   **`ACK_PACKET`**: The receiver sends an acknowledgment packet to the sender when it receives the message. If the sender does not receive an acknowledgment within a certain time, it will resend the message. This guarantees that the message will arrive, but it is not guaranteed to arrive in order.
*   **`SEQUENCE_NUMBER`**: The sender assigns a sequence number to each message. The receiver checks the sequence numbers and requests any missing messages. This guarantees that messages will arrive in order.

You can specify the reliability mode for a message by overriding the `getReliableMode` method:

```java
@Override
public ReliableMode getReliableMode() {
    return ReliableMode.ACK_PACKET;
}
```

### Advanced Features

The `Message` class provides a number of other features that you can use to control how messages are sent and processed.

| Method | Return Type | Description |
|---|---|---|
| `stackable()` | `boolean` | Return `true` to allow this message to be stacked with other messages. This can improve performance by reducing the number of packets sent over the network. Only use in conjunction with `SEQUENCE_NUMBER`. |
| `broadcast()` | `boolean` | Return `true` to broadcast the message to all clients when it is received by the server. |
| `sendBroadcastBackToSender()` | `boolean` | Return `true` if the message should be sent back to the original sender when broadcasting. |
| `getDiscardableKey()` | `Object` | Return a unique key for this message. If a message with the same key is received again, it will be discarded. |
| `getTimeOut()` | `int` | Return a timeout in milliseconds. If the message is not received within this time, it will be discarded. |

---

## Examples

The following examples demonstrate how to use JFastNet in different scenarios.

*   [HelloWorld.java](src/test/java/com/jfastnet/examples/HelloWorld.java): A simple example that shows how to set up a client and server and send messages between them.
