package de.jpx3.intave.check.world.placementanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlacementArrivalsTest {
  @Test
  void usesTheEventTimeWithoutAPacket() {
    PlacementArrivals arrivals = new PlacementArrivals();
    assertEquals(1_000, arrivals.placedAt(1_000));
  }

  @Test
  void lagBurstKeepsThePacketSpacing() {
    PlacementArrivals arrivals = new PlacementArrivals();
    // client places every 300 ms, the server thread stalls and handles all events at t=2000
    arrivals.packetReceived(1_000);
    arrivals.packetReceived(1_300);
    arrivals.packetReceived(1_600);
    assertEquals(1_000, arrivals.placedAt(2_000));
    assertEquals(1_300, arrivals.placedAt(2_000));
    assertEquals(1_600, arrivals.placedAt(2_000));
  }

  @Test
  void neverReturnsATimeAfterTheEvent() {
    PlacementArrivals arrivals = new PlacementArrivals();
    arrivals.packetReceived(5_000);
    assertEquals(4_000, arrivals.placedAt(4_000));
  }

  @Test
  void dropsTheOldestPacketsWhenEventsNeverArrive() {
    PlacementArrivals arrivals = new PlacementArrivals();
    for (int i = 0; i < 100; i++) {
      arrivals.packetReceived(i);
    }
    assertEquals(84, arrivals.placedAt(10_000));
  }
}
