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
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.reader.PlayerActionReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.FLYING_PACKET_ACCURATE;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketPlayerActionToggleHeuristic extends ClassicHeuristic<PacketPlayerActionToggleHeuristic.PacketSprintToggleHeuristicMeta> {
  public PacketPlayerActionToggleHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.SPRINT_TOGGLES, PacketSprintToggleHeuristicMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK
    }
  )
  public void receiveMovementPacket(User user) {
    if (ClientTickBoundary.endedByMovement(user)) {
      metaOf(user).reset();
    }
  }

  @PacketSubscription(
    packetsIn = CLIENT_TICK_END
  )
  public void receiveTickEnd(User user) {
    ClientTickBoundary.observeTickEnd(user);
    if (ClientTickBoundary.endedByTickEnd(user)) {
      metaOf(user).reset();
    }
  }

  @PacketSubscription(
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void receiveEntityAction(User user, PlayerActionReader reader) {
    MetadataBundle meta = user.meta();
    SimulationEnvironment movementData = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    ProtocolMetadata clientData = meta.protocol();
    PunishmentMetadata punishmentData = user.meta().punishment();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(user);

    PlayerAction action = reader.playerAction();

    boolean sprint = action == PlayerAction.START_SPRINTING || action == PlayerAction.STOP_SPRINTING;
    boolean sneak = action.isSneakRelated();
    if (!sprint && !sneak) {
      return;
    }

    if (abilityData.ignoringMovementPackets()) {
      heuristicMeta.reset();
      return;
    }

    if (movementData.ticksPast(TELEPORT) < 10) {
      return;
    }

    boolean flag = sprint
      ? heuristicMeta.sprintTogglesInTick++ >= 1
      : heuristicMeta.sneakTogglesInTick++ >= 1;

    if (flag) {
      // The client sends at most one sprint and one sneak change per tick (EntityPlayerSP /
      // LocalPlayer), so only clients whose tick ends are visible are checked.
      // Two changes in one tick reset sprint for extra knockback (w-tap/super knockback modules).
      boolean alreadyFlagged = sprint ? heuristicMeta.flaggedSprintThisTick : heuristicMeta.flaggedSneakThisTick;
      if (ClientTickBoundary.exact(user) && !alreadyFlagged && !movementData.isInVehicle()) {
        String message = sprint
          ? "sent too many sprint toggles per tick"
          : "sent too many sneak toggles per tick";
        int toggleCount = sprint
          ? heuristicMeta.sprintTogglesInTick
          : heuristicMeta.sneakTogglesInTick;
        if (sprint) {
          heuristicMeta.flaggedSprintThisTick = true;
        } else {
          heuristicMeta.flaggedSneakThisTick = true;
        }
        RepeatedEvidence evidence = sprint ? heuristicMeta.sprintEvidence : heuristicMeta.sneakEvidence;
        if (evidence.record(System.currentTimeMillis())) {
          flag(user, message, "toggles: " + toggleCount + ", " + evidence.count() + " times");
          if (sprint) {
            user.nerf(AttackNerfStrategy.CANCEL, "sprint:toggles");
          }
        }
      }
    } else if (heuristicMeta.threshold > 0) {
      heuristicMeta.threshold -= 0.01;
    }
  }

  public static final class PacketSprintToggleHeuristicMeta extends CheckCustomMetadata {
    public int sprintTogglesInTick;
    public int sneakTogglesInTick;
    public boolean flaggedSprintThisTick, flaggedSneakThisTick;
    private final RepeatedEvidence sprintEvidence = new RepeatedEvidence(2, 60_000);
    private final RepeatedEvidence sneakEvidence = new RepeatedEvidence(2, 60_000);
    public double threshold;

    public void reset() {
      sprintTogglesInTick = 0;
      sneakTogglesInTick = 0;
      flaggedSprintThisTick = false;
      flaggedSneakThisTick = false;
    }
  }
}
