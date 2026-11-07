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

import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.HELD_ITEM_SLOT_OUT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_SLOT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.WINDOW_ITEMS;

/**
 * Detects attacks while sword blocking, or in the tick the block was released (autoblock aura).
 *
 * <p>While a 1.8 client uses an item, Minecraft#runTick drains attack clicks without attacking.
 * Releasing the item happens in that same input pass, so the earliest legitimate attack after a
 * release follows the next movement packet. Within a tick, attack clicks are handled before use
 * clicks, so "attack, then block" stays legitimate.
 *
 * <p>Only sword blocking is tracked: it always starts on use and never finishes by itself. The
 * client can also stop silently when its held item is replaced, so any slot update from the
 * server ends the tracked block (missing a flag rather than raising a wrong one).
 */
public final class AttackWhileBlockingHeuristic extends ClassicHeuristic<AttackWhileBlockingHeuristic.BlockingStateMeta> {
  public AttackWhileBlockingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_WHILE_BLOCKING, BlockingStateMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    // ProtocolLib reports the 1.8 use packet under one of these names depending on its version
    packetsIn = {
      BLOCK_PLACE, USE_ITEM, USE_ITEM_ON
    }
  )
  public void receiveUse(User user, BlockInteractionReader reader) {
    if (!reader.isDirectItemUseRequest()) {
      return;
    }
    if (ItemProperties.isSwordItem(user.meta().inventory().heldItem())) {
      metaOf(user).blocking = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = BLOCK_DIG
  )
  public void receiveDig(User user, BlockDigReader reader) {
    BlockingStateMeta meta = metaOf(user);
    if (reader.action() == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM && meta.blocking) {
      meta.blocking = false;
      meta.releasedThisTick = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = HELD_ITEM_SLOT_IN
  )
  public void receiveSlotChange(User user) {
    metaOf(user).blocking = false;
  }

  @PacketSubscription(
    packetsOut = {
      SET_SLOT, WINDOW_ITEMS, HELD_ITEM_SLOT_OUT, RESPAWN
    }
  )
  public void sentInventoryChange(User user) {
    metaOf(user).blocking = false;
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
    BlockingStateMeta meta = metaOf(user);
    if (meta.flaggedThisTick) {
      return;
    }
    if (meta.blocking) {
      meta.flaggedThisTick = true;
      flag(user, "attacked while blocking", "");
      user.nerf(AttackNerfStrategy.CANCEL, "attack-while-blocking");
    } else if (meta.releasedThisTick) {
      meta.flaggedThisTick = true;
      flag(user, "attacked in the same tick as unblocking", "");
      user.nerf(AttackNerfStrategy.CANCEL, "attack-while-blocking");
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovement(User user) {
    BlockingStateMeta meta = metaOf(user);
    meta.releasedThisTick = false;
    meta.flaggedThisTick = false;
    if (user.meta().movement().ticksPast(TELEPORT) <= 1) {
      meta.blocking = false;
    }
  }

  private static boolean checkable(User user) {
    return user.meta().protocol().emptyFlyingPacketsAreExplicitlySent()
      && !user.meta().abilities().ignoringMovementPackets()
      && user.meta().movement().ticksPast(TELEPORT) > 1;
  }

  public static final class BlockingStateMeta extends CheckCustomMetadata {
    private boolean blocking;
    private boolean releasedThisTick;
    private boolean flaggedThisTick;
  }
}
