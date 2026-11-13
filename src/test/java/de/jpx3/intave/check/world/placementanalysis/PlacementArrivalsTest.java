package de.jpx3.intave.check.world.placementanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlacementArrivalsTest {
  @Test
  void usesTheEventTimeWithoutAPacket() {
    PlacementArrivals arrivals = new PlacementArrivals();
    assertEquals(1_000, arrivals.placedAt(1_000, 0, 64, 0));
  }

  @Test
  void lagBurstKeepsThePacketSpacing() {
    PlacementArrivals arrivals = new PlacementArrivals();
    // client bridges every 300 ms, the server thread stalls and handles all events at t=2000
    arrivals.packetReceived(1_000, 0, 64, 0);
    arrivals.packetReceived(1_300, 0, 64, 1);
    arrivals.packetReceived(1_600, 0, 64, 2);
    assertEquals(1_000, arrivals.placedAt(2_000, 0, 64, 0));
    assertEquals(1_300, arrivals.placedAt(2_000, 0, 64, 1));
    assertEquals(1_600, arrivals.placedAt(2_000, 0, 64, 2));
  }

  @Test
  void neverReturnsATimeAfterTheEvent() {
    PlacementArrivals arrivals = new PlacementArrivals();
    arrivals.packetReceived(5_000, 0, 64, 0);
    assertEquals(4_000, arrivals.placedAt(4_000, 0, 64, 0));
  }

  @Test
  void clicksThatPlacedNothingAreNotReplayedAsPlacements() {
    PlacementArrivals arrivals = new PlacementArrivals();
    // ten clicks on a chest 100 ms apart with a block in hand: no placement events
    for (int i = 0; i < 10; i++) {
      arrivals.packetReceived(10_000 + i * 100L, 5, 64, 5);
    }
    // then bridging every 400 ms
    arrivals.packetReceived(11_500, 0, 64, 0);
    arrivals.packetReceived(11_900, 0, 64, 1);
    assertEquals(11_500, arrivals.placedAt(11_520, 0, 64, 0));
    assertEquals(11_900, arrivals.placedAt(11_920, 0, 64, 1));
  }

  @Test
  void eventsForEveryThirdClickKeepTheirOwnTimes() {
    PlacementArrivals arrivals = new PlacementArrivals();
    for (int i = 0; i < 9; i++) {
      arrivals.packetReceived(1_000 + i * 100L, i, 64, 0);
    }
    assertEquals(1_200, arrivals.placedAt(1_210, 2, 64, 0));
    assertEquals(1_500, arrivals.placedAt(1_510, 5, 64, 0));
    assertEquals(1_800, arrivals.placedAt(1_810, 8, 64, 0));
  }

  @Test
  void unmatchedEventUsesTheEventTimeAndKeepsPendingPackets() {
    PlacementArrivals arrivals = new PlacementArrivals();
    arrivals.packetReceived(1_000, 0, 64, 0);
    assertEquals(1_050, arrivals.placedAt(1_050, 9, 9, 9));
    assertEquals(1_000, arrivals.placedAt(1_060, 0, 64, 0));
  }

  @Test
  void staleClicksExpire() {
    PlacementArrivals arrivals = new PlacementArrivals();
    arrivals.packetReceived(1_000, 0, 64, 0);
    long event = 1_000 + PlacementArrivals.MAX_AGE_MILLIS + 1;
    assertEquals(event, arrivals.placedAt(event, 0, 64, 0));
  }

  @Test
  void dropsTheOldestPacketsWhenEventsNeverArrive() {
    PlacementArrivals arrivals = new PlacementArrivals();
    for (int i = 0; i < 100; i++) {
      arrivals.packetReceived(1_000 + i, 0, 64, 0);
    }
    assertEquals(1_084, arrivals.placedAt(1_200, 0, 64, 0));
  }
}
