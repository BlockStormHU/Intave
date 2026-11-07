package de.jpx3.intave.check.world.placementanalysis;

import java.util.ArrayDeque;

/**
 * Remembers when block placement packets arrived on the network thread.
 * <p>
 * {@code BlockPlaceEvent} runs on the server thread, so after a server lag spike several delayed
 * placements fire back to back and look like impossibly fast building. Timing placements by the
 * moment their packets arrived keeps the measured speed independent of server-side stalls.
 */
final class PlacementArrivals {
  private static final int MAX_PENDING = 16;

  private final ArrayDeque<Long> pending = new ArrayDeque<>();

  synchronized void packetReceived(long arrivalMillis) {
    if (pending.size() >= MAX_PENDING) {
      pending.pollFirst();
    }
    pending.addLast(arrivalMillis);
  }

  /**
   * @param eventMillis the time the matching {@code BlockPlaceEvent} fired
   * @return the arrival time of the oldest unmatched placement packet, or {@code eventMillis} if
   * there is none. Never later than {@code eventMillis}.
   */
  synchronized long placedAt(long eventMillis) {
    Long arrival = pending.pollFirst();
    return arrival == null ? eventMillis : Math.min(arrival, eventMillis);
  }
}
