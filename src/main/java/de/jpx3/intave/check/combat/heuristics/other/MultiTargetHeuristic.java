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

package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ClientTickBoundary;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.RepeatedEvidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects attacks on two different entities within one client tick.
 *
 * <p>The client resolves its crosshair target once per tick (1.8 and modern alike) and every
 * attack click of that tick goes to it, so two different targets within one tick come from multi
 * or switch aura. Only checked where tick ends are visible (see {@link ClientTickBoundary}). The
 * check compares the order of the client's own packets, so latency and jitter cannot cause it.
 */
public final class MultiTargetHeuristic extends ClassicHeuristic<MultiTargetHeuristic.MultiTargetMeta> {
  private static final int NO_TARGET = Integer.MIN_VALUE;

  public MultiTargetHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.MULTI_TARGET, MultiTargetMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void receiveAttack(User user, EntityUseReader reader) {
    if (!reader.isAttackPacket() || !checkable(user)) {
      return;
    }
    MultiTargetMeta meta = metaOf(user);
    int entityId = reader.entityId();
    if (meta.targetThisTick == NO_TARGET) {
      meta.targetThisTick = entityId;
      return;
    }
    if (meta.targetThisTick != entityId && !meta.recordedThisTick) {
      meta.recordedThisTick = true;
      if (meta.evidence.record(System.currentTimeMillis())) {
        flag(user, "attacked multiple entities in one tick",
          "entities " + meta.targetThisTick + ", " + entityId + ", " + meta.evidence.count() + " times");
        user.nerf(AttackNerfStrategy.CANCEL, "multi-target");
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovement(User user) {
    if (ClientTickBoundary.endedByMovement(user)) {
      endTick(metaOf(user));
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = CLIENT_TICK_END
  )
  public void receiveTickEnd(User user) {
    if (ClientTickBoundary.endedByTickEnd(user)) {
      endTick(metaOf(user));
    }
  }

  private static void endTick(MultiTargetMeta meta) {
    meta.targetThisTick = NO_TARGET;
    meta.recordedThisTick = false;
  }

  private static boolean checkable(User user) {
    return ClientTickBoundary.exact(user)
      && !user.meta().abilities().ignoringMovementPackets()
      && !user.meta().movement().isInVehicle()
      && user.meta().movement().ticksPast(TELEPORT) > 1;
  }

  public static final class MultiTargetMeta extends CheckCustomMetadata {
    private int targetThisTick = NO_TARGET;
    private boolean recordedThisTick;
    private final RepeatedEvidence evidence = new RepeatedEvidence(2, 60_000);
  }
}
