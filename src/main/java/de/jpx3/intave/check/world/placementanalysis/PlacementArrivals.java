package de.jpx3.intave.check.world.placementanalysis;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Remembers when block placement packets arrived on the network thread.
 * <p>
 * {@code BlockPlaceEvent} runs on the server thread, so after a server lag spike several delayed
 * placements fire back to back and look like impossibly fast building. Timing placements by the
 * moment their packets arrived keeps the measured speed independent of server-side stalls.
 * <p>
 * Not every click places a block (opening a chest or pressing a button with a block in hand), so
 * a packet is matched to its event by the clicked block, which the event reports as the block
 * placed against. Unmatched packets are dropped: events fire in packet order, so a packet older
 * than the matched one will never get an event.
 */
final class PlacementArrivals {
  private static final int MAX_PENDING = 16;
  /**
   * Server stalls longer than this are not compensated; the event time is used instead, so a
   * stale click can never shorten a measured placement interval by more than this.
   */
  static final long MAX_AGE_MILLIS = 3_000;

  private final ArrayDeque<Arrival> pending = new ArrayDeque<>();

  synchronized void packetReceived(long arrivalMillis, int clickedX, int clickedY, int clickedZ) {
    if (pending.size() >= MAX_PENDING) {
      pending.pollFirst();
    }
    pending.addLast(new Arrival(arrivalMillis, clickedX, clickedY, clickedZ));
  }

  /**
   * @param eventMillis the time the matching {@code BlockPlaceEvent} fired
   * @param againstX    x of the block the event placed against
   * @param againstY    y of the block the event placed against
   * @param againstZ    z of the block the event placed against
   * @return the arrival time of the oldest unmatched placement packet that clicked that block, or
   * {@code eventMillis} if there is none. Never later than {@code eventMillis}.
   */
  synchronized long placedAt(long eventMillis, int againstX, int againstY, int againstZ) {
    while (!pending.isEmpty() && eventMillis - pending.peekFirst().millis > MAX_AGE_MILLIS) {
      pending.pollFirst();
    }
    int index = 0;
    for (Arrival arrival : pending) {
      if (arrival.clicked(againstX, againstY, againstZ)) {
        Iterator<Arrival> iterator = pending.iterator();
        for (int i = 0; i <= index; i++) {
          iterator.next();
          iterator.remove();
        }
        return Math.min(arrival.millis, eventMillis);
      }
      index++;
    }
    return eventMillis;
  }

  private static final class Arrival {
    private final long millis;
    private final int x, y, z;

    private Arrival(long millis, int x, int y, int z) {
      this.millis = millis;
      this.x = x;
      this.y = y;
      this.z = z;
    }

    private boolean clicked(int x, int y, int z) {
      return this.x == x && this.y == y && this.z == z;
    }
  }
}
