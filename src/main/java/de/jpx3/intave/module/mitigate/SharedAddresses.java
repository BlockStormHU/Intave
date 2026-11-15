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

package de.jpx3.intave.module.mitigate;

import java.net.InetAddress;

/**
 * Detects addresses that cannot identify a single player.
 *
 * <p>Behind a proxy without IP forwarding every player connects from the proxy's
 * loopback, container or LAN address. Treating that address as a player identity
 * turns one kick into a kick of the whole server.
 */
public final class SharedAddresses {
  private SharedAddresses() {
  }

  public static boolean isShared(InetAddress address) {
    if (address == null) {
      return true;
    }
    if (address.isLoopbackAddress()
      || address.isAnyLocalAddress()
      || address.isSiteLocalAddress()
      || address.isLinkLocalAddress()) {
      return true;
    }
    byte[] raw = address.getAddress();
    // IPv6 unique local addresses (fc00::/7), the IPv6 counterpart of private ranges
    return raw.length == 16 && (raw[0] & 0xFE) == 0xFC;
  }
}
