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
import com.example.actors.entity.DisasterEntity;
import com.example.actors.entity.ResponseItem;

import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.handleWebSocketMessages;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

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

    public static void main(String[] args) {
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
//        disasterSystem.tell(new DisasterNasaSource.ReadDisasters(mongoDisasterSink));

        ActorSystem<GoogleCalendarSource.Command> calendarSystem =
                ActorSystem.create(GoogleCalendarSource.create(), "calendar-system");

        ActorSystem<LocationToPointMapper.Command> locationSystem =
                ActorSystem.create(LocationToPointMapper.create(), "location-system");

        ActorSystem<WSActor.Command> wssystem =
                ActorSystem.create(WSActor.create(), "ws");

        Materializer mat = Materializer.matFromSystem(wssystem);

        Source<ResponseItem, ActorRef> responseItemSource = Source.actorRef(
                elem -> {
                    if (!(elem instanceof ResponseItem))
                        return Optional.of(CompletionStrategy.immediately());
                    else return Optional.empty();
                },
                elem -> Optional.empty(),
                8,
                OverflowStrategy.dropNew()
        );


        Pair<ActorRef, Source<ResponseItem, NotUsed>> calendarActorRefSourcePair = responseItemSource.preMaterialize(wssystem);
        Pair<ActorRef, Source<ResponseItem, NotUsed>> locationActorRefSourcePair = responseItemSource.preMaterialize(wssystem);
        Pair<ActorRef, Source<ResponseItem, NotUsed>> disasterActorRefSourcePair = responseItemSource.preMaterialize(wssystem);


        responseItemSource.runWith(Sink.ignore(), wssystem);
        calendarActorRefSourcePair.second().runWith(Sink.foreach(item -> locationSystem.tell(new LocationToPointMapper.GetPointByLocation(item, locationActorRefSourcePair.first()))), wssystem);
        locationActorRefSourcePair.second().runWith(Sink.foreach(item -> mongoDisasterSink.tell(new MongoDbActor.GetDisasterByLocation(item, disasterActorRefSourcePair.first()))), wssystem);
        disasterActorRefSourcePair.second().runWith(Sink.ignore(), wssystem);

        Flow<Message, Message, NotUsed> otherFlow =
                Flow.fromSinkAndSource(
                        Sink.foreach(msg -> calendarSystem.tell(new GoogleCalendarSource.ReadEvents(calendarActorRefSourcePair.first(), msg.asTextMessage().getStrictText()))),
                        disasterActorRefSourcePair.second()
                                .map(resp -> {
                                    String title = resp.item.summary != null ? resp.item.summary : resp.item.id;
                                    String message = "[" + resp.id + "] Your event: " + title + " at " + resp.item.location;
                                    int size = resp.disasterEntities.size();
                                    if (size == 1) {
                                        message += " is dagerous. Details: " + resp.disasterEntities.get(0).getTitle();

                                    } else if (size > 1) {
                                        String details = resp.disasterEntities.stream().map(DisasterEntity::getTitle).collect(Collectors.joining("\n"));
                                        message += " is dangerous(" + resp.disasterEntities.size() + " events found). Details: \n" + details;
                                    } else {
                                        message += " is safe.";
                                    }
                                    return TextMessage.create(message);
                                })
                );

        Route route = path("calendar", () ->
                handleWebSocketMessages(otherFlow)
        );

        startHttpServer(route, wssystem);
    }
}


