package de.jpx3.intave.module.mitigate;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SharedAddressesTest {
  @Test
  void proxyAndLocalAddressesAreShared() throws UnknownHostException {
    for (String address : new String[] {
      "127.0.0.1", "0.0.0.0", "10.2.2.2", "172.18.0.1", "192.168.1.20", "169.254.3.4", "::1", "fd12:3456::1", "fe80::1"
    }) {
      assertTrue(SharedAddresses.isShared(InetAddress.getByName(address)), address);
    }
    assertTrue(SharedAddresses.isShared(null));
  }

  @Test
  void publicAddressesIdentifyAPlayer() throws UnknownHostException {
    for (String address : new String[] {"37.221.209.202", "8.8.8.8", "172.32.0.1", "2a00:1450:4001::1"}) {
      assertFalse(SharedAddresses.isShared(InetAddress.getByName(address)), address);
    }
  }
}
