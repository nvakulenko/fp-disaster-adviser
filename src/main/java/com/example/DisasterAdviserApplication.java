package com.example;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.Route;
import akka.actor.typed.ActorSystem;
import akka.japi.Pair;
import akka.stream.CompletionStrategy;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import com.example.actors.*;
import com.example.actors.GoogleCalendarSource;
import com.example.actors.WSActor;
import com.example.actors.MongoDbActor;

import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.handleWebSocketMessages;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

//#main-class
public class DisasterAdviserApplication {
    // #start-http-server
    static void startHttpServer(Route route, ActorSystem<?> system) {
        CompletionStage<ServerBinding> futureBinding =
                Http.get(system).newServerAt("localhost", 8080).bind(route);

        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}/",
                        address.getHostString(),
                        address.getPort());
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
                system.terminate();
            }
        });
    }
    // #start-http-server

    public static void main(String[] args) throws Exception {
        // min
        // 1 - NASA API <- (pull) - Disaster Extractor - OUT -> DB (Mongo Docker)
        //                                             - OUT -> Web Socket -> (PUSH) Final App client
        // 2 - Client subscribes his google calendar to events
        // Client - (subscribe email Web Socket)| -> Event checker         -> Google calendar
        // 3 -                       Web socket | <- DB <- Check disasters <- Google calendar event

        // max
        ActorSystem<DisasterNasaSource.Command> disasterSystem =
                ActorSystem.create(DisasterNasaSource.create(), "disaster-system");
        ActorSystem<MongoDbActor.Command> mongoDisasterSink =
                ActorSystem.create(MongoDbActor.create(), "mongo-system");
        disasterSystem.tell(new DisasterNasaSource.ReadDisasters(mongoDisasterSink));

//        LocationToPointMapper.GeocodingLocation locationPoint = new LocationToPointMapper.GeocodingLocation();
//        locationPoint.features = new ArrayList<>();
//        LocationToPointMapper.GeocodingFeature geocodingFeature = new LocationToPointMapper.GeocodingFeature();
//        geocodingFeature.geometry = new LocationToPointMapper.GeocodingGeometry();
//        geocodingFeature.geometry.coordinates = new Double[]{148.42, -5.525};
//        locationPoint.features.add(geocodingFeature);
//        mongoDisasterSink.tell(new MongoDbActor.GetDisasterByLocation(locationPoint));

        ActorSystem<GoogleCalendarSource.Command> calendarSystem =
                ActorSystem.create(GoogleCalendarSource.create(), "calendar-system");

        ActorSystem<LocationToPointMapper.Command> locationSystem =
                ActorSystem.create(LocationToPointMapper.create(), "location-system");
        locationSystem.tell(new LocationToPointMapper.GetPointByLocation("Lviv", null));

        ActorSystem<WSActor.Command> wssystem =
                ActorSystem.create(WSActor.create(), "ws");

        Materializer mat = Materializer.matFromSystem(wssystem);

        Source<Message, ActorRef> someSource = Source.actorRef(
                elem -> {
                    if (!(elem instanceof TextMessage)) return Optional.of(CompletionStrategy.immediately());
                    else return Optional.empty();
                },
                elem -> Optional.empty(),
                8,
                OverflowStrategy.dropNew()
        );

        Pair<ActorRef, Source<Message, NotUsed>> actorRefSourcePair = someSource.preMaterialize(wssystem);

        someSource.runWith(Sink.ignore(), wssystem);
        actorRefSourcePair.second().runWith(Sink.ignore(), wssystem);

        Flow<Message, Message, NotUsed> otherFlow =
                Flow.fromSinkAndSource(
                        Sink.foreach(msg -> {
                            calendarSystem.tell(new GoogleCalendarSource.ReadEvents(actorRefSourcePair.first(), msg.asTextMessage().getStrictText()));
                        }),
                        actorRefSourcePair.second()
                );

        Route route = path("calendar", () ->
                handleWebSocketMessages(otherFlow)
        );

        startHttpServer(route, wssystem);
    }
}


