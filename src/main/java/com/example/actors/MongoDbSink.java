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

import java.util.concurrent.CompletionStage;

public class MongoDbSink extends AbstractBehavior<MongoDbSink.Command> {

    private final MongoClient client;
    private final MongoDatabase db;
    private final MongoCollection<DisasterEntity> disastersColl;

    public interface Command{}

    public static class WriteDisaster implements Command {
        public DisasterNasaSource.NasaDisasterEvent disaster;
        public WriteDisaster(DisasterNasaSource.NasaDisasterEvent disaster) {
            this.disaster = disaster;
        }
        public DisasterNasaSource.NasaDisasterEvent getDisaster() {
            return disaster;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(MongoDbSink::new);
    }

    private MongoDbSink(ActorContext<Command> context) {
        super(context);

        PojoCodecProvider codecProvider = PojoCodecProvider.builder().register(DisasterEntity.class).build();
        CodecRegistry codecRegistry = CodecRegistries.fromProviders(codecProvider, new ValueCodecProvider());

        client = MongoClients.create("mongodb://localhost:27017");
        db = client.getDatabase("MongoSourceTest");
        disastersColl = db.getCollection("disasters", DisasterEntity.class).withCodecRegistry(codecRegistry);
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
        getContext().getLog().info("MongoDbSink onReadDisasters");

        DisasterNasaSource.NasaDisasterEvent disaster = writeDisasters.getDisaster();
        getContext().getLog().info("Disaster ID = " + disaster.id);

        DisasterEntity disasterEntity = new DisasterEntity();
        disasterEntity.setNasaId(disaster.id);
        disasterEntity.setTitle(disaster.title);
        disasterEntity.setClosed(disaster.closed);
//        disasterEntity.setCategories(disaster.categories);
//        disasterEntity.setGeometry(disaster.geometry);

        CompletionStage<Done> completion =
                Source.single(disasterEntity)
                        .runWith(MongoSink.insertOne(disastersColl), getContext().getSystem());
        completion
                .thenAccept(
                        done -> {
                            System.out.println("Done write to db!");
                        });
        return this;
    }

    private MongoDbSink onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }
}
