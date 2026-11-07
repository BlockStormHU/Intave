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

/**
 * Requires a detection to repeat before it counts, so a single unexplained event (a lost packet,
 * an unusual client) never adds violation level on its own.
 */
public final class RepeatedEvidence {
  private final int required;
  private final long windowMillis;
  private long windowStart;
  private int count;

  public RepeatedEvidence(int required, long windowMillis) {
    this.required = required;
    this.windowMillis = windowMillis;
  }

  /** Records one occurrence; true once {@code required} occurrences fell into one window. */
  public boolean record(long now) {
    if (count == 0 || now - windowStart > windowMillis) {
      windowStart = now;
      count = 0;
    }
    return ++count >= required;
  }

  public int count() {
    return count;
  }
}
