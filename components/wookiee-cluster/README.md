# Wookiee - Distributed Messaging (Cluster Component)

For Configuration information see [Cluster Config](docs/config.md)

# Overview

With the benefits of distributed cluster of nodes, developers can take advantage of Publish-Subscribe or Send-Subscribe
messaging patterns. These patterns have the advantage that the sender of messages has no knowledge of the receivers.
The sender simply uses a 'topic' to send messages to instead of having to know that actual address of the receiver.

This pattern allows for our systems to scale by adding additional receivers without the need to change logic or
re-deploy with the knowledge of new receivers.

For a working example see [Wookiee - Cluster Example](../../examples/example-cluster)

## Basic Usage

The system is designed so that a developer simply needs to apply a trait (interface) on an actor in order to have full
access to this functionality. Wookiee is responsible for managing the peer to peer communication and thus
the developer of the service does not need to handle any of that information. The act of 'publishing' a message
guarantees that the message will be sent to all subscribers, while 'sending' a message will round-robin to only one of
the subscribers.

![](https://webtrends.jiveon.com/servlet/JiveServlet/showImage/102-29731-8-12519/temp.png)

## Sending/Receiving Messages

In order to take full benefit of the functionality one simply needs to apply the trait `MessagingAdapter` and
then start interacting with the system. When an actor subscribes to a topic it will then receive instances of
`com.webtrends.communication.Message` which is the container that consists of the topic, sender, and
actual message content that was sent or published/sent.

```scala

import com.webtrends.communication.MessagingAdapter
import com.webtrends.communication.Message

// Define the actor and have it apply the MessagingAdapter trait
class MyReceiverActor extends Actor with ActorLoggingAdapter with MessagingAdapter {
    def preStart: Unit = {
        // Subscribe to a specific topic to receive messages from other nodes in the cluster
        subscribe("cluster-topic", self /* I am the receiver, but could be another ActorRef */, false /* this is a subscription for messages from other cluster nodes */)
        // Subscribe to a specific topic to receive messages from other nodes in the cluster and the sender expects a response
        subscribe("cluster-topic-with-response", self /* I am the receiver, but could be another ActorRef */, false /* this is a subscription for messages from other cluster nodes */)
        // Subscribe to a specific topic to receive messages from only within this node
        subscribe("internal-topic", self /* I am the receiver, but could be another ActorRef */, true/* this is a subscription for messages from only this node*/)
    }
    def postStop: Unit = {
        // Unsubscribe from the given topics
        unsubscribe("cluster-topic", self /* I am the receiver, but could be another ActorRef */)
        unsubscribe("cluster-topic-with-response", self /* I am the receiver, but could be another ActorRef */)
        unsubscribe("internal-topic", self /* I am the receiver, but could be another ActorRef */)
        // Alternatively, you can unsubscribe from several topics in one call
        //unsubscribeFromMany(Seq("cluster-topic","cluster-topic-with-response", "internal-topic"), self)
    }
    def receive = {
        // The received message is wrapped and includes the senders information as well as the topic and actual message
        case Message("cluster-topic", msg) => // Do something with the message now
        case Message("cluster-topic-with-response", msg) => sender() ! "an answer"
    }
}

// Define the actor and have it apply the MessageServiceAdaptor trait
class MySenderActor extends Actor with ActorLoggingAdapter with MessagingAdapter {
    def receive = {
        case "send-external-message" =>
            // We receive a hypothetical message that tells us to send off a message for a given topic
            send("cluster-topic", SomeMessage("somedata"), false /* I wanted this message to not be handled locally */)
        case "send-external-message-with-response" =>
            // We receive a hypothetical message that tells us to send off a message for a given topic and uses a future to handle the response
            sendWithFuture("cluster-topic", SomeMessage("somedata"), false /* I wanted this message to not be handled locally */).onComplete {
                case Success(answer) => // Do something
                case Failure(fail) => // Handle error
            }
        case "send-internal-message" =>
            // We receive a hypothetical message that tells us to send off a message for a given topic
            send("internal-topic", SomeMessage("somedata"), true /* I wanted this message to be handled locally */)
    }
}

```

## Message Subscription Events
The ability to receive events for message subscription changes are also available through the trait
`MessageSubscriptionEventAdapter`. If this trait is applied to an actor then the ability to register/unregister
for events becomes available.

```scala

import com.webtrends.communication.MessageSubscriptionEventAdapter

// Define the actor and apply the MessageSubscriptionEventAdapter
class MyEventHandlerActor extends Actor with ActorLoggingAdapter with MessageSubscriptionEventAdapter {

    def preStart: Unit = {
        // Register for events.This will register for add and remove events, but you can also register for a specific event (e.g. classOf[SubscriptionAddedEvent]).
        register(self, classOf[MessageSubscriptionEvent])
    }

    def postStop: Unit = {
        // Unregister for the events. This follows the same pattern as registered that is described above
        unregister(self, classOf[MessageSubscriptionEvent])
    }

    def receive = {
        case SubscriptionAddedEvent(topic, subscriber /* ActorRef */) =>
        // An added subscription event occurred
        case SubscriptionRemovedEvent(topic, subscriber /* ActorRef */) =>
        // A removed subscription event occurred
    }
}

```