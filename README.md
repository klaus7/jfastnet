# JFastNet
Fast, reliable and easy UDP messaging for Java. Designed for games.

# Maven

The dependency for your POM:
```xml
<dependency>
    <groupId>com.jfastnet</groupId>
    <artifactId>jfastnet</artifactId>
    <version>0.1.2</version>
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
[Click to see full sample code of HelloWorld.java](https://github.com/klaus7/jfastnet/blob/master/src/test/java/com/jfastnet/examples/HelloWorld.java)

# Documentation
The documentation is still a work-in-progress.

The most important classes to look for in the beginning are the `Config` and the `Message` class. The JavaDoc there should provide you with the basic configuration possibilities of the library.

## Reliable sending
There are currently two ways you can use to send a message in a reliable way. Sending the message unreliably is of course also an option.

1. Acknowledge packet
2. Sequence number

### Acknowledge packet
The receiver of a message with reliable mode set to `ACK_PACKET` will send an acknowledge packet to the other end upon receipt of the message.
As long as the sender of the prior mentioned message doesn't receive an acknowledge packet it will keep resending the message.

 Attribute | Value
 --------- |:---:
 Reliable  | yes
 Ordered   | no

### Sequence number
The receiver of a message with reliable mode set to `SEQUENCE_NUMBER` will do nothing as long as the messages arrive in the expected order.
But if a message with an id greater than expected is received, the receiver will stop processing the messages and send a `RequestSeqIdsMessage` to the other end.
Processing will not continue until all required messages are received.

 Attribute | Value
 --------- |:---:
 Reliable  | yes
 Ordered   | yes

It's usually advisable to use sequence numbers, as there will be less overhead and also the ordered delivery is guaranteed.

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
