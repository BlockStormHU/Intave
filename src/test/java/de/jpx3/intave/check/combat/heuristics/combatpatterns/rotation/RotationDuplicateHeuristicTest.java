package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.RotationDuplicateHeuristic.RotationDuplicateMeta;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.RotationDuplicateHeuristic.repeatsRotationOnItemUse;
import static de.jpx3.intave.user.meta.ProtocolMetadata.*;
import static org.junit.jupiter.api.Assertions.*;

final class RotationDuplicateHeuristicTest {
  private static final long NOW = 100_000;

  @Test
  void clientsFrom117To1206RepeatTheirRotationOnItemUse() {
    // MultiPlayerGameMode#useItem sends a position + look packet first (checked in the 1.17,
    // 1.18.2, 1.20.4 and 1.20.6 clients; absent in 1.16.5 and 1.21.1)
    assertTrue(repeatsRotationOnItemUse(VER_1_17));
    assertTrue(repeatsRotationOnItemUse(VER_1_20_1));
    assertTrue(repeatsRotationOnItemUse(VER_1_20_5));
  }

  @Test
  void otherClientsAreChecked() {
    assertFalse(repeatsRotationOnItemUse(VER_1_8));
    assertFalse(repeatsRotationOnItemUse(754)); // 1.16.4 - 1.16.5
    assertFalse(repeatsRotationOnItemUse(VER_1_21));
    assertFalse(repeatsRotationOnItemUse(VER_1_21_2));
    assertFalse(repeatsRotationOnItemUse(775)); // 26.1
  }

  @Test
  void onlyExactRepeatsCount() {
    RotationDuplicateMeta meta = new RotationDuplicateMeta();
    assertFalse(meta.repeats(10.0F, 5.0F), "first rotation");
    assertTrue(meta.repeats(10.0F, 5.0F));
    assertFalse(meta.repeats(10.000001F, 5.0F));
    assertFalse(meta.repeats(10.000001F, -0.0F));
    assertFalse(meta.repeats(10.000001F, 0.0F), "-0 and +0 differ bitwise, as the client compares");
  }

  @Test
  void threeRepeatsInsideTheWindowFlag() {
    RotationDuplicateMeta meta = new RotationDuplicateMeta();
    meta.tick(false, false, false, NOW);
    assertNull(meta.countDuplicate());
    meta.tick(false, false, false, NOW);
    assertNull(meta.countDuplicate());
    meta.tick(false, false, false, NOW);
    assertEquals("3 in 4 ticks", meta.countDuplicate()); // the first window starts at tick 0
    meta.tick(false, false, false, NOW);
    assertNull(meta.countDuplicate(), "count restarts after a flag");
  }

  @Test
  void repeatsSpreadBeyondTheWindowDoNotFlag() {
    RotationDuplicateMeta meta = new RotationDuplicateMeta();
    for (int round = 0; round < 10; round++) {
      for (int i = 0; i < 60; i++) {
        meta.tick(false, false, false, NOW);
      }
      assertNull(meta.countDuplicate());
    }
  }

  @Test
  void serverRotationPausesTheCheck() {
    RotationDuplicateMeta meta = new RotationDuplicateMeta();
    meta.serverMovedView(NOW);
    assertTrue(meta.quiet(true, NOW + 1_999));
    assertFalse(meta.quiet(true, NOW + 2_000));
  }

  @Test
  void teleportConfirmationQuietsTheFollowingTicks() {
    RotationDuplicateMeta meta = new RotationDuplicateMeta();
    meta.tick(false, true, false, NOW);
    long later = NOW + 10_000;
    meta.tick(false, false, false, later);
    meta.tick(false, false, false, later);
    assertTrue(meta.quiet(true, later), "cooldown left");
    meta.tick(false, false, false, later);
    assertFalse(meta.quiet(true, later));
  }

  @Test
  void pendingTeleportOnlyQuietsPositionPackets() {
    RotationDuplicateMeta meta = new RotationDuplicateMeta();
    meta.tick(false, false, true, NOW);
    long later = NOW + 10_000;
    meta.tick(false, false, false, later);
    assertTrue(meta.quiet(false, later));
    assertFalse(meta.quiet(true, later), "a look-only packet is no teleport confirmation");
  }

  @Test
  void ridingQuietsTheCheckUntilTheCooldownRanOut() {
    RotationDuplicateMeta meta = new RotationDuplicateMeta();
    meta.tick(true, false, false, NOW);
    for (int i = 0; i < 4; i++) {
      meta.tick(false, false, false, NOW + 10_000);
      assertTrue(meta.quiet(true, NOW + 10_000));
    }
    meta.tick(false, false, false, NOW + 10_000);
    assertFalse(meta.quiet(true, NOW + 10_000));
  }
}
