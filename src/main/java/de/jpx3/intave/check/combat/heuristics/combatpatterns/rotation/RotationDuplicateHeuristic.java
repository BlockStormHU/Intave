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
import de.jpx3.intave.user.meta.ProtocolMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects rotation packets that repeat the previous rotation exactly (silent rotations).
 *
 * <p>EntityPlayerSP#onUpdateWalkingPlayer (1.8) and LocalPlayer#sendPosition (1.9+) only include a
 * rotation when yaw or pitch differ from the last reported values, so a client never repeats one.
 * Exceptions, all of which send a rotation without updating the last reported one, so the next
 * tick may repeat it:
 * <ul>
 *   <li>riding sends the rotation every tick;</li>
 *   <li>a teleport confirmation (always a position + look packet);</li>
 *   <li>1.21.2+: the answer to a server rotation (ClientboundPlayerRotationPacket);</li>
 *   <li>1.17 to 1.20.6: MultiPlayerGameMode#useItem sends a position + look packet before every
 *   item use, so these clients are not checked at all.</li>
 * </ul>
 * Aura, scaffold and silent-aim modules that send their own rotation every tick repeat it while
 * the target holds still.
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
    long now = System.currentTimeMillis();
    meta.tick(movement.isInVehicle(), movement.isTeleportConfirmationPacket, !movement.pendingTeleports.get().isEmpty(), now);
    if (!reader.hasRotation()) {
      return;
    }
    boolean duplicate = meta.repeats(reader.yaw(), reader.pitch());
    // Teleport confirmations always carry a position (C06), so a repeated look-only packet (C05)
    // cannot be one, even while a teleport is still queued as pending.
    boolean lookOnly = !reader.hasMovement();
    if (!duplicate || meta.quiet(lookOnly, now) || !checkable(user, lookOnly)) {
      return;
    }
    String details = meta.countDuplicate();
    if (details != null) {
      flag(user, "repeated identical rotations", details);
    }
  }

  @PacketSubscription(
    // PLAYER_ROTATION only resolves on 1.21.2+ servers
    packetsOut = {
      de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION,
      de.jpx3.intave.module.linker.packet.PacketId.Server.PLAYER_ROTATION
    }
  )
  public void sentTeleportOrRotation(User user) {
    metaOf(user).serverMovedView(System.currentTimeMillis());
  }

  private static boolean checkable(User user, boolean lookOnly) {
    if (repeatsRotationOnItemUse(user.meta().protocol().protocolVersion())
      || user.meta().abilities().ignoringMovementPackets()) {
      return false;
    }
    // Apart from the exceptions above, clients send a rotation only when it changed, so the rule
    // holds natively and across protocol translation.
    MovementMetadata movement = user.meta().movement();
    return lookOnly || (!movement.awaitTeleport && movement.ticksPast(TELEPORT) > 3);
  }

  /**
   * 1.17 to 1.20.6 clients send the current rotation before every item use (MultiPlayerGameMode#useItem)
   * without updating the last reported rotation, so each use repeats a rotation once.
   */
  static boolean repeatsRotationOnItemUse(int protocolVersion) {
    return protocolVersion >= ProtocolMetadata.VER_1_17 && protocolVersion <= ProtocolMetadata.VER_1_20_5;
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

    /** Advances one movement packet. */
    void tick(boolean inVehicle, boolean confirmation, boolean teleportPending, long nowMillis) {
      tick++;
      if (inVehicle) {
        vehicleCooldown = VEHICLE_COOLDOWN_TICKS;
      } else if (vehicleCooldown > 0) {
        vehicleCooldown--;
      }
      // A teleport confirmation repeats the rotation the server set, and since it does not update the
      // client's last reported rotation, the next tick may repeat that rotation once more (look-only
      // when standing still). Both the exact confirmation mark and any still pending teleport start
      // the cooldown.
      // Intave's own setbacks are sent past the outbound listeners, so a confirmation or a still
      // pending teleport is the only sign of them; both pause the check like a sent teleport.
      if (confirmation || teleportPending) {
        lastTeleportSent = nowMillis;
      }
      if (confirmation) {
        confirmationCooldown = TELEPORT_COOLDOWN_TICKS;
      } else if (confirmationCooldown > 0) {
        confirmationCooldown--;
      }
      if (teleportPending) {
        teleportCooldown = TELEPORT_COOLDOWN_TICKS;
      } else if (teleportCooldown > 0) {
        teleportCooldown--;
      }
    }

    /** Records a rotation; true if it repeats the previous one exactly. */
    boolean repeats(float yaw, float pitch) {
      boolean duplicate = hasLast
        && Float.floatToIntBits(yaw) == Float.floatToIntBits(lastYaw)
        && Float.floatToIntBits(pitch) == Float.floatToIntBits(lastPitch);
      hasLast = true;
      lastYaw = yaw;
      lastPitch = pitch;
      return duplicate;
    }

    /** Whether a recent teleport, server rotation or vehicle explains a repeated rotation. */
    boolean quiet(boolean lookOnly, long nowMillis) {
      return nowMillis - lastTeleportSent < TELEPORT_PAUSE_MILLIS
        || confirmationCooldown > 0
        || vehicleCooldown > 0
        || !lookOnly && teleportCooldown > 0;
    }

    /** A teleport or (1.21.2+) rotation was sent to the client. */
    void serverMovedView(long nowMillis) {
      lastTeleportSent = nowMillis;
    }

    /** Counts an unexplained repetition; the flag details once enough fell into one window, else null. */
    String countDuplicate() {
      if (tick - windowStart > WINDOW_TICKS) {
        windowStart = tick;
        duplicates = 0;
      }
      if (++duplicates < REQUIRED_DUPLICATES) {
        return null;
      }
      String details = duplicates + " in " + (tick - windowStart + 1) + " ticks";
      duplicates = 0;
      windowStart = tick;
      return details;
    }
  }
}
