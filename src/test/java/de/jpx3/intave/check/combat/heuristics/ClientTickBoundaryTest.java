package de.jpx3.intave.check.combat.heuristics;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClientTickBoundaryTest {
  @AfterEach
  void reset() {
    MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));
  }

  @Test
  void legacyClientOnLegacyServerEndsTicksWithMovement() {
    assertBoundary("1.8.8", VER_1_8, true, true, false);
  }

  @Test
  void modernClientOnLegacyServerHasNoVisibleTickEnds() {
    assertBoundary("1.8.8", VER_1_12, false, false, false);
    assertBoundary("1.8.8", VER_1_21_2, false, false, false);
  }

  @Test
  void tickEndPacketsNeedClientAndServerFrom1212() {
    assertBoundary("26.1.2", VER_1_21_2, true, false, true);
    assertBoundary("26.1.2", VER_1_21, false, false, false);
    assertBoundary("1.21.1", VER_1_21_2, false, false, false);
  }

  @Test
  void legacyClientOnModernServerLosesItsFlyingPackets() {
    assertBoundary("26.1.2", VER_1_8, false, false, false);
  }

  @Test
  void repeatedEvidenceNeedsRepetitionInsideTheWindow() {
    RepeatedEvidence evidence = new RepeatedEvidence(2, 1000);
    assertEquals(false, evidence.record(0));
    assertEquals(false, evidence.record(5000));
    assertEquals(true, evidence.record(5500));
    assertEquals(true, evidence.record(5900));
  }

  private static void assertBoundary(String server, int client, boolean exact, boolean movement, boolean tickEnd) {
    MinecraftVersion.setCurrent(new MinecraftVersion(server));
    User user = user(client);
    String label = "client " + client + " on " + server;
    assertEquals(exact, ClientTickBoundary.exact(user), label);
    assertEquals(movement, ClientTickBoundary.endedByMovement(user), label);
    assertEquals(tickEnd, ClientTickBoundary.endedByTickEnd(user), label);
  }

  private static User user(int protocolVersion) {
    World world = FakeWorldFactory.createWorld((methodName, _) -> null);
    Location location = new Location(world, 0, 64, 0);
    Player player = FakePlayerFactory.createPlayer((methodName, _) -> switch (methodName) {
      case "getWorld" -> world;
      case "getLocation" -> location;
      case "getUniqueId" -> UUID.randomUUID();
      default -> null;
    });
    return UserFactory.createTestUserFor(player, (usr, key) -> "protocolVersion".equals(key) ? protocolVersion : null);
  }
}
