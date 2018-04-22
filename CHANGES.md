# Changelog

The API of this library is subject to change.

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
