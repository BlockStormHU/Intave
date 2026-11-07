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

package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects rotation packets that repeat the previous rotation exactly (silent rotations).
 *
 * <p>EntityPlayerSP#onUpdateWalkingPlayer only includes a rotation when yaw or pitch differ from
 * the last reported values, so a 1.8 client never repeats one. Exceptions: riding sends the
 * rotation every tick, and a teleport confirmation (always a position + look packet) carries the
 * new rotation without updating the last reported one, so the following tick may repeat it once. Aura, scaffold and silent-aim
 * modules that send their own rotation every tick repeat it while the target holds still.
 */
public final class RotationDuplicateHeuristic extends ClassicHeuristic<RotationDuplicateHeuristic.RotationDuplicateMeta> {
  private static final int WINDOW_TICKS = 100;
  private static final int REQUIRED_DUPLICATES = 3;
  private static final int VEHICLE_COOLDOWN_TICKS = 5;
  private static final int TELEPORT_COOLDOWN_TICKS = 3;

  public RotationDuplicateHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_DUPLICATE, RotationDuplicateMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovement(User user, PlayerMoveReader reader) {
    RotationDuplicateMeta meta = metaOf(user);
    MovementMetadata movement = user.meta().movement();
    meta.tick++;
    if (movement.isInVehicle()) {
      meta.vehicleCooldown = VEHICLE_COOLDOWN_TICKS;
    } else if (meta.vehicleCooldown > 0) {
      meta.vehicleCooldown--;
    }
    // Teleport confirmations repeat the rotation the server set (absolute or unchanged), and the
    // next tick may repeat it once more. Confirmations are matched after this listener runs, so
    // a pending teleport is still queued here.
    if (!movement.pendingTeleports.get().isEmpty()) {
      meta.teleportCooldown = TELEPORT_COOLDOWN_TICKS;
    } else if (meta.teleportCooldown > 0) {
      meta.teleportCooldown--;
    }
    if (!reader.hasRotation()) {
      return;
    }
    float yaw = reader.yaw();
    float pitch = reader.pitch();
    boolean duplicate = meta.hasLast
      && Float.floatToIntBits(yaw) == Float.floatToIntBits(meta.lastYaw)
      && Float.floatToIntBits(pitch) == Float.floatToIntBits(meta.lastPitch);
    meta.hasLast = true;
    meta.lastYaw = yaw;
    meta.lastPitch = pitch;
    // Teleport confirmations always carry a position (C06), so a repeated look-only packet (C05)
    // cannot be one, even while a teleport is still queued as pending.
    boolean lookOnly = !reader.hasMovement();
    if (!duplicate || !checkable(user, meta, lookOnly)) {
      return;
    }
    if (meta.tick - meta.windowStart > WINDOW_TICKS) {
      meta.windowStart = meta.tick;
      meta.duplicates = 0;
    }
    if (++meta.duplicates >= REQUIRED_DUPLICATES) {
      flag(user, "repeated identical rotations", meta.duplicates + " in " + (meta.tick - meta.windowStart + 1) + " ticks");
      meta.duplicates = 0;
      meta.windowStart = meta.tick;
    }
  }

  private static boolean checkable(User user, RotationDuplicateMeta meta, boolean lookOnly) {
    MovementMetadata movement = user.meta().movement();
    boolean clientChecked = user.meta().protocol().emptyFlyingPacketsAreExplicitlySent()
      && !user.meta().abilities().ignoringMovementPackets()
      && meta.vehicleCooldown == 0;
    if (!clientChecked) {
      return false;
    }
    return lookOnly || (meta.teleportCooldown == 0 && !movement.awaitTeleport && movement.ticksPast(TELEPORT) > 3);
  }

  public static final class RotationDuplicateMeta extends CheckCustomMetadata {
    private boolean hasLast;
    private float lastYaw, lastPitch;
    private long tick, windowStart;
    private int duplicates;
    private int vehicleCooldown;
    private int teleportCooldown;
  }
}
