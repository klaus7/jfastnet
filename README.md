# JFastNet
Fast, reliable and easy UDP messaging for Java. Designed for games.

JFastNet is tolerant towards packet loss and when used in the right way it can
provide your players with a smooth network gaming experience even in bad network conditions.

_The API of this library is subject to change._

# Maven

The dependency for your POM:
```xml
<dependency>
    <groupId>com.jfastnet</groupId>
    <artifactId>jfastnet</artifactId>
    <version>0.3.8</version>
</dependency>
```

# Example code
The following code shows the important parts of a server-client communication:
```java
Server server = new Server(new Config().setBindPort(15150));
Client client = new Client(new Config().setPort(15150));

server.start();
client.start();
client.blockingWaitUntilConnected();

server.send(new PrintMessage("Hello Client!"));
client.send(new PrintMessage("Hello Server!"));
```
[Click to see full sample code of HelloWorld.java](src/test/java/com/jfastnet/examples/HelloWorld.java)

## More examples
Every sending mode has its own runnable example in [`src/test/java/com/jfastnet/examples`](src/test/java/com/jfastnet/examples).
The examples for the reliable modes simulate packet loss with `Config.debug`, so you can watch the recovery mechanisms at work:

| Example | What it shows |
| ------- | ------------- |
| [HelloWorld.java](src/test/java/com/jfastnet/examples/HelloWorld.java) | The minimal server-client setup. |
| [UnreliableExample.java](src/test/java/com/jfastnet/examples/UnreliableExample.java) | `UNRELIABLE`: fire-and-forget position updates. A third of them get lost and nobody minds. |
| [AckPacketExample.java](src/test/java/com/jfastnet/examples/AckPacketExample.java) | `ACK_PACKET`: reliable but unordered. A lost message is resent and processed last, `ackCallback()` tells the sender it arrived. |
| [SequenceNumberExample.java](src/test/java/com/jfastnet/examples/SequenceNumberExample.java) | `SEQUENCE_NUMBER`: reliable and ordered. A lost message is requested and the later ones wait for it. |
| [StackedMessagesExample.java](src/test/java/com/jfastnet/examples/StackedMessagesExample.java) | Stackable messages: the same lossy burst sent plain and stacked, and how often the server has to resend in each case. |
| [BroadcastExample.java](src/test/java/com/jfastnet/examples/BroadcastExample.java) | `broadcast()`: a client's chat message is relayed to all clients, plus sending to a single client. |
| [BigMessageExample.java](src/test/java/com/jfastnet/examples/BigMessageExample.java) | A message 20 times bigger than a packet is split, sent in parts and reassembled, with and without compression. |

Run an example with Maven or start its `main` method from your IDE:
```bash
mvn -q test-compile exec:java -Dexec.mainClass=com.jfastnet.examples.SequenceNumberExample
```

Two things the examples show that HelloWorld doesn't need:

* Call `process()` on the server and on every client regularly, for example once per frame.
  Resending messages, requesting missing ones and sending the parts of big messages all happen there.
* Messages are processed on the receiving thread of the peer, so whatever `process()` touches has to be thread-safe.

[ExamplesTest.java](src/test/java/com/jfastnet/examples/ExamplesTest.java) runs every example as part of the test suite.

# Roadmap

* More documentation

# Documentation

A comprehensive documentation can be found in the [DOCUMENTATION.md](DOCUMENTATION.md) file.

The documentation is still a work-in-progress.

The most important classes to look for in the beginning are the `Config` and the `Message` class. The JavaDoc there should provide you with the basic configuration possibilities of the library.

## Reliable sending
There are currently two ways you can use to send a message in a reliable way. Sending the message unreliably is of course also an option
(see [UnreliableExample.java](src/test/java/com/jfastnet/examples/UnreliableExample.java)).

1. Acknowledge packet
2. Sequence number

Every message picks its mode by overriding `getReliableMode()`; the default is `SEQUENCE_NUMBER`.

### Acknowledge packet
The receiver of a message with reliable mode set to `ACK_PACKET` will send an acknowledge packet to the other end upon receipt of the message.
As long as the sender of the prior mentioned message doesn't receive an acknowledge packet it will keep resending the message.

 Attribute | Value
 --------- |:---:
 Reliable  | yes
 Ordered   | no

Example: [AckPacketExample.java](src/test/java/com/jfastnet/examples/AckPacketExample.java)

### Sequence number
The receiver of a message with reliable mode set to `SEQUENCE_NUMBER` will do nothing as long as the messages arrive in the expected order.
But if a message with an id greater than expected is received, the receiver will stop processing the messages and send a `RequestSeqIdsMessage` to the other end.
Processing will not continue until all required messages are received.

 Attribute | Value
 --------- |:---:
 Reliable  | yes
 Ordered   | yes

It's usually advisable to use sequence numbers, as there will be less overhead and also the ordered delivery is guaranteed.

Example: [SequenceNumberExample.java](src/test/java/com/jfastnet/examples/SequenceNumberExample.java)

### Stacked messages
A message with reliable mode `SEQUENCE_NUMBER` that returns `true` from `stackable()` is not sent on its own.
Instead every packet carries all stackable messages the receiver hasn't acknowledged yet, and the receiver acknowledges them in batches.
If a packet gets lost, the next packet contains the lost messages again, so the receiver never has to request them and doesn't wait for a round trip.
Stacking requires the `ReliableModeIdProvider` (`config.setIdProviderClass(ReliableModeIdProvider.class)`), which hands out the same message ids to all clients.

 Attribute | Value
 --------- |:---:
 Reliable  | yes
 Ordered   | yes

Example: [StackedMessagesExample.java](src/test/java/com/jfastnet/examples/StackedMessagesExample.java)

# Build
Use maven to build JFastNet:
```bash
mvn clean install
```

# Thanks
[Kryo](https://github.com/EsotericSoftware/kryo) is the default serialiser used in JFastNet and is a pleasure to work with! Thanks very much for this awesome library!

[Project Lombok](https://projectlombok.org/) also deserves a mention, as it makes working with Java much more comfortable and the code looks cleaner. Check it out if you don't have already.

# Contact
Post issues to [the issues page](https://github.com/klaus7/jfastnet/issues) or contact me via email at [support@jfastnet.com](mailto:support@jfastnet.com) for other inquiries.
