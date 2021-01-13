package com.example;

import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.actor.typed.ActorSystem;
import com.example.actors.DisasterNasaSource;
import com.example.actors.GoogleCalendarSource;
import com.example.actors.MongoDbSink;

import java.net.InetSocketAddress;
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
        ActorSystem<MongoDbSink.Command> mongoDisasterSink =
                ActorSystem.create(MongoDbSink.create(), "mongo-write-system");
        disasterSystem.tell(new DisasterNasaSource.ReadDisasters(mongoDisasterSink));

        ActorSystem<GoogleCalendarSource.Command> calendarSystem =
                ActorSystem.create(GoogleCalendarSource.create(), "calendar-system");
        calendarSystem.tell(new GoogleCalendarSource.ReadEvents());
    }
}


