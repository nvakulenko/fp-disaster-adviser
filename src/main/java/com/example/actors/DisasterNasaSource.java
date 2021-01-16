package com.example.actors;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.alpakka.json.javadsl.JsonReader;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import scala.concurrent.ExecutionContextExecutor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

// Actor: Source of disasters is Nasa API
public class DisasterNasaSource extends AbstractBehavior<DisasterNasaSource.Command> {

    public interface Command{}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NasaDisasterEvent {
        public NasaDisasterEvent() {}
        public String id;
        public String title;
        public String closed;
        public List<DisasterNasaSource.NasaDisasterCategories> categories;
        public List<DisasterNasaSource.NasaDisasterGeometry> geometry;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NasaDisasterCategories {
        public NasaDisasterCategories() {}
        public String id;
        public String title;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NasaDisasterGeometry {
        public NasaDisasterGeometry(){}
        public String date;
        public String type;
        public BigDecimal[] coordinates;
    }

    public static final class ReadDisasters implements Command {
        final ActorRef<MongoDbSink.Command> replyTo;
        public ReadDisasters(ActorRef<MongoDbSink.Command> replyTo) {
            this.replyTo = replyTo;
        }
    }

    private static final String NASA_URI = "https://eonet.sci.gsfc.nasa.gov/api/v3/events";
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

    private Behavior<Command> onReadDisasters(final ReadDisasters readDisasters) {
        getContext().getLog().info("DisasterNasaSourceActor onReadDisasters");

        Unmarshaller<ByteString, NasaDisasterEvent> unmarshaller =
                Jackson.byteStringUnmarshaller(NasaDisasterEvent.class);
        ActorSystem<Void> system = getContext().getSystem();

        CompletionStage<Done> completion =
                Source.single(HttpRequest.GET(NASA_URI)) // : HttpRequest
                        .mapAsync(1, http::singleRequest) // : HttpResponse
                        .flatMapConcat(this::extractEntityData)
                        .via(JsonReader.select("$.events[*]"))
                        .mapAsync(1, r -> unmarshaller.unmarshal(r, system))
                        .runWith(Sink.foreach(in ->
                                    //System.out.println("id = " + in.id + " cat =" + in.categories.size());
                            readDisasters.replyTo.tell(new MongoDbSink.WriteDisaster(in)))
                        , system);
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

    private DisasterNasaSource onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }
}
