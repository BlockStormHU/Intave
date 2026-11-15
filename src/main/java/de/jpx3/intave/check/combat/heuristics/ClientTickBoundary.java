/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;

/**
 * Where the end of a client tick is visible in the packet stream.
 *
 * <p>Clients up to 1.8 end every tick with a movement packet (when the server is 1.8 too, so no
 * protocol translation drops them). Clients from 1.21.2 send a dedicated tick end packet (when the
 * server understands it). Between those, or across protocol translation, ticks without movement
 * are invisible, so checks that compare events within one tick must not run.
 */
public final class ClientTickBoundary {
  private ClientTickBoundary() {
  }

  private static final long TICK_END_FRESHNESS_MILLIS = 1000;

  /**
   * Whether every client tick end can be observed for this user. Tick end packets must actually
   * arrive: a modified client or a proxy that drops them would otherwise merge many ticks into one.
   */
  public static boolean exact(User user) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.emptyFlyingPacketsAreExplicitlySent()) {
      return true;
    }
    return protocol.sendsClientTickEnd()
      && System.currentTimeMillis() - protocol.lastClientTickEnd < TICK_END_FRESHNESS_MILLIS;
  }

  /** Records a received client tick end packet. */
  public static void observeTickEnd(User user) {
    user.meta().protocol().lastClientTickEnd = System.currentTimeMillis();
  }

  /** Whether a movement packet from this user ends a client tick. */
  public static boolean endedByMovement(User user) {
    return user.meta().protocol().emptyFlyingPacketsAreExplicitlySent();
  }

  /** Whether a client tick end packet from this user ends a client tick. */
  public static boolean endedByTickEnd(User user) {
    return user.meta().protocol().sendsClientTickEnd();
  }
}
