package com.example.actors;

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
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.example.actors.entity.ResponseItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GoogleCalendarSource extends AbstractBehavior<GoogleCalendarSource.Command> {

    public interface Command {
    }

    public static final class ReadEvents implements Command {
        final ActorRef replyTo;
        final String calendarId;

        public ReadEvents(ActorRef replyTo, String calendarId) {
            this.replyTo = replyTo;
            this.calendarId = calendarId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoogleCalendarEvent {
        public GoogleCalendarEvent() {
        }

        public String id;
        public String kind;
        public String etag;
        public String summary;
        public String nextSyncToken;
        public List<GoogleCalendarEventItem> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoogleCalendarEventItem {
        public GoogleCalendarEventItem() {
        }

        public String id;
        public String location;
        public String status;
        public String summary;
    }

    public static final String GOOGLE_API = "https://www.googleapis.com";
    public static final String key = "AIzaSyCKYFpTRY-IhXNrsgjRvHwbICk2F3fOt1k";
    final Http http = Http.get(getContext().getSystem());

    public static Behavior<Command> create() {
        return Behaviors.setup(GoogleCalendarSource::new);
    }

    private GoogleCalendarSource(ActorContext<GoogleCalendarSource.Command> context) {
        super(context);
        getContext().getLog().info("GoogleCalendarSource created");
    }

    // TODO: Get Google Events here and send to Analyzer if there some disasters on event places
    @Override
    public Receive<Command> createReceive() {
        getContext().getLog().info("GoogleCalendarSource createReceive");
        return newReceiveBuilder()
                .onMessage(ReadEvents.class, this::onReadEvents)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<GoogleCalendarSource.Command> onReadEvents(ReadEvents readEvents) {
        getContext().getLog().info("GoogleCalendarSource onReadDisasters");
        String id = readEvents.calendarId;
        ActorSystem<Void> system = getContext().getSystem();

        Unmarshaller<HttpEntity, GoogleCalendarEvent> unmarshaller = Jackson.unmarshaller(GoogleCalendarEvent.class);
        AtomicReference<String> nextSyncToken = new AtomicReference<>("");

        Source.tick(Duration.ofSeconds(1), Duration.ofSeconds(1), nextSyncToken)
                .mapAsync(1, token -> {
                    String url = GOOGLE_API + "/calendar/v3/calendars/" + id + "/events?key=" + key;

                    if (token.get().length() != 0) {
                        url += "&syncToken=" + token.get();
                    }

                    HttpRequest httpRequest = HttpRequest.GET(url);

                    return http.singleRequest(httpRequest);
                })
                .mapAsync(1, r -> unmarshaller.unmarshal(r.entity(), system))
                .wireTap(r -> nextSyncToken.set(r.nextSyncToken))
                .map(r -> r.items.stream().filter(item -> item.location != null && item.status.equals("confirmed")).collect(Collectors.toList()))
                .filter(items -> items.size() > 0)
                .runWith(Sink.foreach(response -> response.forEach(item -> {
                    ResponseItem responseItem = new ResponseItem(id, item);
                    readEvents.replyTo.tell(responseItem, ActorRef.noSender());
                })), system);

        return this;
    }

    private GoogleCalendarSource onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }

}
