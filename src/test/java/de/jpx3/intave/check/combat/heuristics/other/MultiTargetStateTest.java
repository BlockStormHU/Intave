package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.heuristics.other.MultiTargetHeuristic.MultiTargetMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MultiTargetStateTest {
  @Test
  void repeatedClicksOnOneTargetAreLegitimate() {
    MultiTargetMeta meta = new MultiTargetMeta();
    assertFalse(meta.attack(7));
    assertFalse(meta.attack(7));
    assertFalse(meta.attack(7));
  }

  @Test
  void secondTargetInOneTickIsDetectedOnce() {
    MultiTargetMeta meta = new MultiTargetMeta();
    assertFalse(meta.attack(7));
    assertTrue(meta.attack(8));
    assertFalse(meta.attack(9), "one detection per tick");
  }

  @Test
  void differentTargetsInConsecutiveTicksAreLegitimate() {
    MultiTargetMeta meta = new MultiTargetMeta();
    assertFalse(meta.attack(7));
    meta.endTick();
    assertFalse(meta.attack(8));
    meta.endTick();
    assertFalse(meta.attack(7));
  }

  @Test
  void entityIdZeroIsATarget() {
    MultiTargetMeta meta = new MultiTargetMeta();
    assertFalse(meta.attack(0));
    assertTrue(meta.attack(1));
  }
}
