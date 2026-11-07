package de.jpx3.intave.check.world.breakspeedlimiter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CompletionDurationToleranceTest {
  @Test
  void stableConnectionKeepsTheOriginalTolerance() {
    assertEquals(100, CompletionDurationCheck.toleratedEarlyFinish(0));
    assertEquals(100, CompletionDurationCheck.toleratedEarlyFinish(-5));
  }

  @Test
  void toleranceGrowsWithJitter() {
    assertEquals(160, CompletionDurationCheck.toleratedEarlyFinish(30));
  }

  @Test
  void jitterAllowanceIsCapped() {
    assertEquals(350, CompletionDurationCheck.toleratedEarlyFinish(1_000));
  }
}
