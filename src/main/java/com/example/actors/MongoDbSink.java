package com.example;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class MongoDbSinkActor extends AbstractBehavior<String> {
    static Behavior<String> create() {
        return Behaviors.setup(MongoDbSinkActor::new);
    }

    private MongoDbSinkActor(ActorContext<String> context) {
        super(context);
    }

    // TODO: save in mongo
    @Override
    public Receive<String> createReceive() {
        // print to console for now
        return null;
    }
}
