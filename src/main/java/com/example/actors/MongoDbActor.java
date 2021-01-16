package com.example.actors;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.stream.Materializer;
import akka.stream.alpakka.mongodb.javadsl.MongoSink;
import akka.stream.alpakka.mongodb.javadsl.MongoSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.example.actors.entity.CategoryEntity;
import com.example.actors.entity.DisasterEntity;
import com.example.actors.entity.LocationEntity;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class MongoDbActor extends AbstractBehavior<MongoDbActor.Command> {

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

    public static class GetDisasterByLocation implements Command {
        public LocationToPointMapper.GeocodingLocation locationPoint;
        public GetDisasterByLocation(LocationToPointMapper.GeocodingLocation locationPoint) {
            this.locationPoint = locationPoint;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(MongoDbActor::new);
    }

    private MongoDbActor(ActorContext<Command> context) {
        super(context);

        PojoCodecProvider codecProvider = PojoCodecProvider.builder()
                .register(DisasterEntity.class)
                .register(LocationEntity.class)
                .register(CategoryEntity.class)
                .register(Document.class)
                .build();

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
                .onMessage(GetDisasterByLocation.class, this::onGetDisastersByLocation)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<MongoDbActor.Command> onWriteDisasters(WriteDisaster writeDisasters) {
        getContext().getLog().info("MongoDbSink onWriteDisasters for " + writeDisasters.disaster.id);

        DisasterNasaSource.NasaDisasterEvent disaster = writeDisasters.getDisaster();
        // for each Point we create separate Disaster entity in BD - for easier querying
        List<DisasterEntity> entities = disaster.geometry.stream()
                .filter(in -> "Point".equals(in.type)) // take into account just "Point" locations
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
        locationEntity.setCoordinates(Arrays.asList(in.coordinates.clone()));

        disasterEntity.setLocation(locationEntity);

        disasterEntity.setCategories(disaster.categories.stream().map(category -> {
            CategoryEntity categoryEntity = new CategoryEntity();
            categoryEntity.setId(category.id);
            categoryEntity.setTitle(category.title);
            return categoryEntity;
        }).collect(Collectors.toList()));
        return disasterEntity;
    }

    private MongoDbActor onPostStop() {
        getContext().getLog().info("DisasterNasaSourceActor stopped");
        return this;
    }

    private Behavior<MongoDbActor.Command> onGetDisastersByLocation(GetDisasterByLocation getDisasterByLocation)
            throws InterruptedException, ExecutionException, TimeoutException {
        LocationToPointMapper.GeocodingLocation locationPoint = getDisasterByLocation.locationPoint;

        final Source<DisasterEntity, NotUsed> source =
                MongoSource.create(disastersColl.find(DisasterEntity.class));
        final CompletionStage<List<DisasterEntity>> rows =
                source.runWith(Sink.seq(), Materializer.matFromSystem(getContext().getSystem()));
        List<DisasterEntity> disasters = rows.toCompletableFuture().get(5L, TimeUnit.SECONDS);
        List<DisasterEntity> disastersNearEvent = disasters.stream()
                    .filter(in -> in.getClosed() == null)
                    .filter(in -> isNearEvent(in.getLocation(), locationPoint))
                    .collect(Collectors.toList());
        return this;
    }

    private boolean isNearEvent(LocationEntity location, LocationToPointMapper.GeocodingLocation locationPoint) {
        List<Double> coordinates = location.getCoordinates();
        Double disasterLatitude = coordinates.get(0);
        Double disasterLongitude = coordinates.get(1);

        Double[] geometry = locationPoint.features.get(0).geometry.coordinates;
        Double eventLatitude = geometry[0];
        Double eventLongitude = geometry[1];

        // in meters
        double distance = distance(disasterLatitude, eventLatitude, disasterLongitude, eventLongitude, 0.0, 0.0);
        return distance < 10000;
    }

    /**
     * Calculate distance between two points in latitude and longitude taking
     * into account height difference. If you are not interested in height
     * difference pass 0.0. Uses Haversine method as its base.
     *
     * lat1, lon1 Start point lat2, lon2 End point el1 Start altitude in meters
     * el2 End altitude in meters
     * @returns Distance in Meters
     */
    public static double distance(double lat1, double lat2, double lon1,
                                  double lon2, double el1, double el2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = el1 - el2;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }

}
