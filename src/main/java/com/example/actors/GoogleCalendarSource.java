package com.example;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class GoogleCalendarEventsSource extends AbstractBehavior<String> {

    static Behavior<String> create() {
        return Behaviors.setup(GoogleCalendarEventsSource::new);
    }

    private GoogleCalendarEventsSource(ActorContext<String> context) {
        super(context);
    }

    // TODO: Get Google Events here and send to Analyzer if there some disasters on event places
    @Override
    public Receive<String> createReceive() {
        return null;
    }
}
