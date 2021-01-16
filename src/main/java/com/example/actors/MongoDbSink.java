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
import com.example.actors.entity.CategoryEntity;
import com.example.actors.entity.DisasterEntity;
import com.example.actors.entity.LocationEntity;
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
import java.util.stream.Collectors;

public class MongoDbSink extends AbstractBehavior<MongoDbSink.Command> {

    private final MongoClient client;
    private final MongoDatabase db;
    private final MongoCollection<DisasterEntity> disastersColl;

    public interface Command {
    }

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
        db = client.getDatabase("disasters-application");
        disastersColl = db.getCollection("disasters", DisasterEntity.class).withCodecRegistry(codecRegistry);
    }

    @Override
    public Receive<Command> createReceive() {
        getContext().getLog().info("Mongo db write");
        return newReceiveBuilder()
                .onMessage(WriteDisaster.class, this::onWriteDisasters)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<MongoDbSink.Command> onWriteDisasters(WriteDisaster writeDisasters) {
        getContext().getLog().info("MongoDbSink onWriteDisasters for " + writeDisasters.disaster.id);

        DisasterNasaSource.NasaDisasterEvent disaster = writeDisasters.getDisaster();
        // for each Point we create separate Disaster entity in BD - for easier querying
        List<DisasterEntity> entities = disaster.geometry.stream()
                .filter(in -> in.type != null && "Point".equals(in.type)) // take into account just "Point" locations
                .map(in -> getDisasterEntity(disaster, in))
                .collect(Collectors.toList());

        CompletionStage<Done> completion =
                Source.from(entities)
                        .runWith(MongoSink.insertOne(disastersColl), getContext().getSystem());
        completion.thenAccept(done -> System.out.println("Done write to db!"));
        return this;
    }

    private DisasterEntity getDisasterEntity(DisasterNasaSource.NasaDisasterEvent disaster, DisasterNasaSource.NasaDisasterGeometry in) {
        DisasterEntity disasterEntity = new DisasterEntity();
        disasterEntity.setNasaId(disaster.id);
        disasterEntity.setTitle(disaster.title);
        disasterEntity.setClosed(disaster.closed);

        LocationEntity locationEntity = new LocationEntity();
        locationEntity.setType(in.type);
        locationEntity.setCoordinates(in.coordinates);

        disasterEntity.setCategories(disaster.categories.stream().map(category -> {
            CategoryEntity categoryEntity = new CategoryEntity();
            categoryEntity.setId(category.id);
            categoryEntity.setTitle(category.title);
            return categoryEntity;
        }).collect(Collectors.toList()));
        return disasterEntity;
    }

    private MongoDbSink onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }
}
