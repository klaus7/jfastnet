# Changelog

The API of this library is subject to change.

## 0.3.9 (unreleased)

* Upgraded all dependencies to their latest stable versions:
.* Netty 4.1.42.Final -> 4.2.16.Final (peer now uses the `MultiThreadIoEventLoopGroup` API introduced in Netty 4.2)
.* Kryo 5.5.0 -> 5.6.2
.* Apache Commons Collections 4.1 -> 4.5.0
.* SLF4J 1.6.6 -> 2.0.18
.* Lombok 1.18.30 -> 1.18.46
.* Javassist 3.18.2-GA -> 3.32.0-GA
* Replaced the hard dependency on the EOL Log4j 1.x binding (`slf4j-log4j12`) with `slf4j-api` only;
  downstream users can now choose their own SLF4J binding. Tests log via `slf4j-simple`.
* Migrated the test suite from JUnit 4 to JUnit 5 (Jupiter) and Hamcrest 1.3 to 3.0
* Upgraded all Maven build plugins to current versions

## 0.3.3

* Missing receiver id led to server sending specific message to all clients (only with ACK reliable mode)

## 0.3.2

* added empty constructors to fix de-/serialising

## 0.3.1

* When compression fails, message will be sent uncompressed
* Congestion Control
* don't split up resent messages
* added CompressedMessage for convenient sending of single compressed messages
* fixed bug in ReliableModeAckProcessor when messages got sent to all clients from the server
* configurable maximumNumberOfResentMessagesPerCheck in ReliableModeAckProcessor added
* StackedMessageProcessor bugfix: lastAckMessageIdMap was not set correctly on re-join

## 0.3.0

* Crucial bugfixes for stacked message processing
* Added events and an event queue to notify other components of critical events
.* RequestedMessageNotInLogEvent
.* DisabledStackedMessagesEvent
* Exceptions
* Improved message log
* Added processor config for the ReliableModeSequenceProcessor

## 0.2.4

* Added context to message processing

## 0.2.3

* No auto splitting of unreliable messages -> splitted messages must be sent reliably

## 0.2.2

* Fail safe if compression failed

## 0.2.1

* TimerSyncMessage missing MessageFeatures bugfix

## 0.2.0

* Stackable messages (new reliable sending mode, where all unacknowledged messages get stacked onto the most recent message)
* Cleaner separation of config and state
* Individual configs for the processors
* Faster message log

## 0.1.5

* auto split bugfix
