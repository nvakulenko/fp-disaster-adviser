package com.example.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class WSActor extends AbstractBehavior<WSActor.Command> {

    public interface Command{}

    public static Behavior<Command> create() {
        return Behaviors.setup(WSActor::new);
    }

    private WSActor(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return null;
    }
}