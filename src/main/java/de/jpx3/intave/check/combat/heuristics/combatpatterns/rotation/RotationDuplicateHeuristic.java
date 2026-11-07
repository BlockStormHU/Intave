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
 * <p>EntityPlayerSP#onUpdateWalkingPlayer (1.8) and LocalPlayer#sendPosition (1.9+) only include a
 * rotation when yaw or pitch differ from the last reported values, so a client never repeats one. Exceptions: riding sends the
 * rotation every tick, and a teleport confirmation (always a position + look packet) carries the
 * new rotation without updating the last reported one, so the following tick may repeat it once. Aura, scaffold and silent-aim
 * modules that send their own rotation every tick repeat it while the target holds still.
 */
public final class RotationDuplicateHeuristic extends ClassicHeuristic<RotationDuplicateHeuristic.RotationDuplicateMeta> {
  private static final int WINDOW_TICKS = 100;
  private static final int REQUIRED_DUPLICATES = 3;
  private static final int VEHICLE_COOLDOWN_TICKS = 5;
  private static final int TELEPORT_COOLDOWN_TICKS = 3;
  // Under high latency, setbacks are resent and stack up; their confirmations repeat the rotation,
  // and an unrecognized resend can arrive well after the teleport was queued
  private static final long TELEPORT_PAUSE_MILLIS = 2000;

  public RotationDuplicateHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_DUPLICATE, RotationDuplicateMeta.class);
  }

  @PacketSubscription(
    // MovementDispatcher marks teleport confirmations at LOW priority
    priority = ListenerPriority.NORMAL,
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
    // A teleport confirmation repeats the rotation the server set, and since it does not update the
    // client's last reported rotation, the next tick may repeat that rotation once more (look-only
    // when standing still). Both the exact confirmation mark and any still pending teleport start
    // the cooldown.
    boolean confirmation = movement.isTeleportConfirmationPacket;
    // Intave's own setbacks are sent past the outbound listeners, so a confirmation or a still
    // pending teleport is the only sign of them; both pause the check like a sent teleport.
    if (confirmation || !movement.pendingTeleports.get().isEmpty()) {
      meta.lastTeleportSent = System.currentTimeMillis();
    }
    if (confirmation) {
      meta.confirmationCooldown = TELEPORT_COOLDOWN_TICKS;
    } else if (meta.confirmationCooldown > 0) {
      meta.confirmationCooldown--;
    }
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
    if (!duplicate || confirmation || meta.confirmationCooldown > 0 || !checkable(user, meta, lookOnly)) {
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

  @PacketSubscription(
    packetsOut = de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION
  )
  public void sentTeleport(User user) {
    metaOf(user).lastTeleportSent = System.currentTimeMillis();
  }

  private static boolean checkable(User user, RotationDuplicateMeta meta, boolean lookOnly) {
    if (System.currentTimeMillis() - meta.lastTeleportSent < TELEPORT_PAUSE_MILLIS) {
      return false;
    }
    MovementMetadata movement = user.meta().movement();
    // Every client version sends a rotation only when it changed (riding excepted), so the rule
    // holds natively and across protocol translation.
    boolean clientChecked = !user.meta().abilities().ignoringMovementPackets()
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
    private int confirmationCooldown;
    private volatile long lastTeleportSent;
  }
}
