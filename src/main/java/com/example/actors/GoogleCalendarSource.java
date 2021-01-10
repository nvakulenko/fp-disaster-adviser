package com.example.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class GoogleCalendarSource extends AbstractBehavior<GoogleCalendarSource.Command> {

    public interface Command{}

    static Behavior<Command> create() {
        return Behaviors.setup(GoogleCalendarSource::new);
    }

    private GoogleCalendarSource(ActorContext<GoogleCalendarSource.Command> context) {
        super(context);
    }

    // TODO: Get Google Events here and send to Analyzer if there some disasters on event places
    @Override
    public Receive<Command> createReceive() {
        return null;
    }
}
