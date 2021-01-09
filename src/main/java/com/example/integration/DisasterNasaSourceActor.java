package com.example.integration;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

// Actor: Source of disasters is Nasa API
public class DisasterNasaSourceActor extends AbstractBehavior<String> {

    private static final String NASA_URI = "https://eonet.sci.gsfc.nasa.gov/api/v3/events";

    static Behavior<String> create() {
        return Behaviors.setup(DisasterNasaSourceActor::new);
    }

    private DisasterNasaSourceActor(ActorContext<String> context) {
        super(context);
    }

    // Get Disasters from NASA API here and send to Mongo db
    @Override
    public Receive<String> createReceive() {
        return null;
    }
}
