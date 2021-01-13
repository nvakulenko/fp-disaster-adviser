package com.example.actors;

import akka.Done;
import akka.actor.ActorSelection;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.alpakka.json.javadsl.JsonReader;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import scala.concurrent.ExecutionContextExecutor;
import static akka.pattern.PatternsCS.pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

// Actor: Source of disasters is Nasa API
public class DisasterNasaSource extends AbstractBehavior<DisasterNasaSource.Command> {

    public interface Command{}

    public static final class ReadDisasters implements Command {
    }

    private static final String NASA_URI = "https://eonet.sci.gsfc.nasa.gov/api/v3/events?status=open";
    final Http http = Http.get(getContext().getSystem());
    final ExecutionContextExecutor dispatcher = getContext().getExecutionContext();

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
//        CompletionStage<HttpResponse> response =
//                Http.get(getContext().getSystem()).singleRequest(HttpRequest.GET(NASA_URI));
//
//        //ActorRef<MongoDbSink.Command> actorRef = getContext().getSystem().systemActorOf(MongoDbSink.create(), "nasa-mongo-db", null);
//        response
//                .thenAccept(
//                        done -> {
//                            System.out.println("Done!");
//                            System.out.println(done.entity());
//                        });

        CompletionStage<Done> completion =
                Source.single(HttpRequest.GET(NASA_URI)) // : HttpRequest
                        .mapAsync(1, http::singleRequest) // : HttpResponse
                        .flatMapConcat(this::extractEntityData)
                        .via(JsonReader.select("$.events[*]"))
                        .runWith(Sink.foreach(bs -> System.out.println(bs.utf8String())), getContext().getSystem());

//        .mapAsync(1, http::singleRequest) // : HttpResponse
//                .flatMapConcat(this::extractEntityData) // : ByteString
//                .via(CsvParsing.lineScanner()) // : List<ByteString>
//                .via(CsvToMap.toMap()) // : Map<String, ByteString>
//                .map(this::cleanseCsvData) // : Map<String, String>
//                .map(this::toJson) // : String
//                .map(elem ->
//                        new ProducerRecord<String, String>(
//                                "topic1", elem) // : Kafka ProducerRecord
//                )
        completion
                .thenAccept(
                        done -> {
                            System.out.println("Done!");
                        });
        return this;
    }

    private Source<ByteString, ?> extractEntityData(HttpResponse httpResponse) {
        if (httpResponse.status() == StatusCodes.OK) {
            return httpResponse.entity().getDataBytes();
        } else {
            return Source.failed(new RuntimeException("illegal response " + httpResponse));
        }
    }

//    @Override
//    public Receive createReceive() {
//        return receiveBuilder()
//                .match(String.class, url -> pipe(fetch(url), dispatcher).to(self()))
//                .build();
//    }
//
//    CompletionStage<HttpResponse> fetch(String url) {
//        return http.singleRequest(HttpRequest.create(url));
//    }

    private DisasterNasaSource onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }
}
