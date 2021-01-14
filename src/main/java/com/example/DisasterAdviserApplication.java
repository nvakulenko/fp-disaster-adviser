package com.example;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.typed.Behavior;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ws.BinaryMessage;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.Route;
import akka.actor.typed.ActorSystem;
import akka.japi.Pair;
import akka.stream.CompletionStrategy;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.impl.ActorRefSource;
import akka.stream.javadsl.*;
import com.example.actors.DisasterNasaSource;
import com.example.actors.GoogleCalendarSource;
import com.example.actors.WSActor;

import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.handleWebSocketMessages;
//import akka.actor.ActorSystem;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;
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
//            ActorSystem.create("disaster-system", DisasterNasaSource.create());
        disasterSystem.tell(new DisasterNasaSource.ReadDisasters());

        ActorSystem<GoogleCalendarSource.Command> calendarSystem =
                ActorSystem.create(GoogleCalendarSource.create(), "calendar-system");

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


