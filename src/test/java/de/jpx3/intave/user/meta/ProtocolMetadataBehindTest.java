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

package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.version.ProtocolVersionConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProtocolMetadataBehindTest {
  @AfterEach
  void reset() {
    MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));
  }

  @Test
  void clientOnTheServersProtocolIsNeverBehind() {
    // protocol 775 is labelled 26.1.1, the server is 26.1.2
    assertBehind("26.1.2", VER_26_1_1, false);
    assertBehind("1.21.11", VER_1_21_11, false);
    assertBehind("1.8.8", VER_1_8, false);
  }

  @Test
  void olderProtocolsAreBehindAndNewerOnesAreNot() {
    assertBehind("26.1.2", VER_1_21_11, true);
    assertBehind("26.1.2", VER_1_8, true);
    assertBehind("1.8.8", VER_26_1_1, false);
  }

  private static void assertBehind(String server, int client, boolean expected) {
    MinecraftVersion serverVersion = new MinecraftVersion(server);
    int serverProtocol = ProtocolVersionConverter.exactProtocolVersionBy(serverVersion);
    MinecraftVersion clientVersion = new MinecraftVersion(ProtocolVersionConverter.versionByProtocolVersion(client));
    assertEquals(expected, ProtocolMetadata.behind(client, clientVersion, serverProtocol, serverVersion),
      "client " + client + " (" + clientVersion + ") on " + server + " (" + serverProtocol + ")");
  }
}
