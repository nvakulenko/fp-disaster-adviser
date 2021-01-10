package com.example.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class MongoDbSink extends AbstractBehavior<MongoDbSink.Command> {

    public interface Command{}

    static Behavior<Command> create() {
        return Behaviors.setup(MongoDbSink::new);
    }

    private MongoDbSink(ActorContext<Command> context) {
        super(context);
    }

    // TODO: save in mongo
    @Override
    public Receive<Command> createReceive() {
        // print to console for now
        return null;
    }
}
