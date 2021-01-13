package com.example.actors;

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.stream.alpakka.mongodb.javadsl.MongoSink;
import akka.stream.javadsl.Source;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class MongoDbSink extends AbstractBehavior<MongoDbSink.Command> {

    private final MongoClient client;
    private final MongoDatabase db;
    private final MongoCollection<Disaster> disastersColl;

    public interface Command{}

    public class WriteDisaster implements Command {
        private List<Disaster> disasters;
        public WriteDisaster(List<Disaster> disasters) {
            this.disasters = disasters;
        }
        public List<Disaster> getDisasters() {
            return disasters;
        }
    }

    static Behavior<Command> create() {
        return Behaviors.setup(MongoDbSink::new);
    }

    private MongoDbSink(ActorContext<Command> context) {
        super(context);

        PojoCodecProvider codecProvider = PojoCodecProvider.builder().register(Disaster.class).build();
        CodecRegistry codecRegistry = CodecRegistries.fromProviders(codecProvider, new ValueCodecProvider());

        client = MongoClients.create("mongodb://localhost:27017");
        db = client.getDatabase("MongoSourceTest");
        disastersColl = db.getCollection("disasters", Disaster.class).withCodecRegistry(codecRegistry);
    }

    // TODO: save in mongo
    @Override
    public Receive<Command> createReceive() {
        getContext().getLog().info("Mongo db write");
        return newReceiveBuilder()
                .onMessage(WriteDisaster.class, this::onWriteDisasters)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<MongoDbSink.Command> onWriteDisasters(WriteDisaster writeDisasters) {
        getContext().getLog().info("DisasterNasaSourceActor onReadDisasters");
        final CompletionStage<Done> completion =
                Source.from(writeDisasters.getDisasters()).runWith(MongoSink.insertOne(disastersColl),
                        getContext().getSystem());
        return this;
    }

    private MongoDbSink onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }
}
