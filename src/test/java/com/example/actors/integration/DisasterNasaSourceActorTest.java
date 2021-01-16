package com.example.actors.integration;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.example.actors.DisasterNasaSource;
import junit.framework.TestCase;
import org.junit.ClassRule;
import org.junit.Test;

public class DisasterNasaSourceActorTest extends TestCase {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testDisasterNasa() {
        TestProbe<DisasterNasaSource.ReadDisasters> probe =
                testKit.createTestProbe(DisasterNasaSource.ReadDisasters.class);
        ActorRef<DisasterNasaSource.Command> disasterActor = testKit.spawn(DisasterNasaSource.create());
        //disasterActor.tell(new DisasterNasaSource.ReadDisasters());

        // TODO: Check if disaster was passed futher
        //DisasterNasaSourceActor.Command response = probe.receiveMessage();
        //assertEquals();
    }
}