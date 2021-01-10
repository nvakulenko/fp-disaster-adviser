package com.example;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

// Actor: Source of disasters is Nasa API
public class DisasterNasaSource extends AbstractBehavior<DisasterNasaSource.Command> {

    public interface Command{}

    public static final class ReadDisasters implements Command {
    }

    private static final String NASA_URI = "https://eonet.sci.gsfc.nasa.gov/api/v3/events";

    public static Behavior<Command> create() {
        return Behaviors.setup(DisasterNasaSource::new);
    }

    private DisasterNasaSource(ActorContext<DisasterNasaSource.Command> context) {
        super(context);
        getContext().getLog().info("DisasterNasaSourceActor created");
    }

    // Get Disasters from NASA API here and send to Mongo db
    @Override
    public Receive<Command> createReceive() {
        getContext().getLog().info("DisasterNasaSourceActor createReceive");
        return newReceiveBuilder()
                .onMessage(ReadDisasters.class, this::onReadDisasters)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<Command> onReadDisasters(ReadDisasters readDisasters) {
        getContext().getLog().info("DisasterNasaSourceActor onReadDisasters");
        return this;
    }

    private DisasterNasaSource onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }
}
