package com.example.integration;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import junit.framework.TestCase;
import org.junit.ClassRule;
import org.junit.Test;

public class DisasterNasaSourceActorTest extends TestCase {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void testDisasterNasa() {
        TestProbe<DisasterNasaSourceActor.ReadDisasters> probe =
                testKit.createTestProbe(DisasterNasaSourceActor.ReadDisasters.class);
        ActorRef<DisasterNasaSourceActor.Command> disasterActor = testKit.spawn(DisasterNasaSourceActor.create());
        disasterActor.tell(new DisasterNasaSourceActor.ReadDisasters());
        //DisasterNasaSourceActor.Command response = probe.receiveMessage();
        //assertEquals();

    }

}