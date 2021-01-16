package com.example.actors;

import akka.Done;
import akka.actor.ActorRef;
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
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.example.actors.entity.ResponseItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class LocationToPointMapper extends AbstractBehavior<LocationToPointMapper.Command> {

    public interface Command{}

    public static Behavior<LocationToPointMapper.Command> create() {
        return Behaviors.setup(LocationToPointMapper::new);
    }

    private LocationToPointMapper(ActorContext<LocationToPointMapper.Command> context) {
        super(context);
        getContext().getLog().info("DisasterAnalyzer created");
    }

    private static final String GEOCODING_URL = "https://api.mapbox.com/geocoding/v5/mapbox.places/%s.json?access_token=%s&limit=1";
    private static final String GEOCODING_TOKEN = "pk.eyJ1Ijoia2g1NzMiLCJhIjoiY2tqdTZuYW4wMDN2aTJ5bXM0dXpmZGk4ayJ9.s7iXnttMf_piucIU8mkPcg";
    final Http http = Http.get(getContext().getSystem());

    public static final class GetPointByLocation implements LocationToPointMapper.Command {
        public ResponseItem item;
        public final ActorRef replyTo;

        // final ActorRef<XXXXXAnalyzer.Command> replyTo;
        public GetPointByLocation(ResponseItem item, ActorRef replyTo) {
            this.replyTo = replyTo;
            this.item = item;
        }
    }

    @Override
    public Receive<Command> createReceive() {
        getContext().getLog().info("DisasterAnalyzer createReceive");
        return newReceiveBuilder()
                .onMessage(GetPointByLocation.class, this::onGetPointByLocation)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeocodingLocation {
        public GeocodingLocation(){}
        public List<GeocodingFeature> features;
        public Double relevance;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeocodingFeature {
        public GeocodingFeature(){}
        public GeocodingGeometry geometry;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeocodingGeometry {
        public GeocodingGeometry(){}
        public String type;
        public List<Double> coordinates;
    }

    private Behavior<Command> onGetPointByLocation(GetPointByLocation command) {
        // get location coordinates
        String location = URLEncoder.encode(command.item.item.location, StandardCharsets.UTF_8);
        String geocodingUrl = String.format(GEOCODING_URL, location, GEOCODING_TOKEN);

        Unmarshaller<ByteString, LocationToPointMapper.GeocodingLocation> unmarshaller =
                Jackson.byteStringUnmarshaller(LocationToPointMapper.GeocodingLocation.class);
        ActorSystem<Void> system = getContext().getSystem();

        // Calendar -> Location -> Get URL Point -> Point -> Calendar
        // Calendar -> Db
        CompletionStage<Done> completion =
                Source.single(HttpRequest.GET(geocodingUrl)) // : HttpRequest
                        .mapAsync(1, http::singleRequest) // : HttpResponse
                        .flatMapConcat(this::extractEntityData)
                        .mapAsync(1, r -> unmarshaller.unmarshal(r, system))
                        .runWith(Sink.foreach(in -> {
                            ResponseItem responseItem = new ResponseItem(
                                    command.item.id,
                                    command.item.item,
                                    in
                            );
                            command.replyTo.tell(responseItem, ActorRef.noSender());
                        }), system);
        return this;
    }

    private Source<ByteString, ?> extractEntityData(HttpResponse httpResponse) {
        if (httpResponse.status() == StatusCodes.OK) {
            return httpResponse.entity().getDataBytes();
        } else {
            return Source.failed(new RuntimeException("illegal response " + httpResponse));
        }
    }

    private LocationToPointMapper onPostStop() {
        getContext().getLog().info("DisasterAnalyzer stopped");
        return this;
    }

}
