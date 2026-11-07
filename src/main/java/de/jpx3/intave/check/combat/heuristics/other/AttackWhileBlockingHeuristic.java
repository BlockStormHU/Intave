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
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.ClientTickBoundary;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.combat.heuristics.RepeatedEvidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.HELD_ITEM_SLOT_OUT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_COOLDOWN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_SLOT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.WINDOW_ITEMS;

/**
 * Detects attacks while blocking, or in the tick the block was released (autoblock aura).
 *
 * <p>While the client uses an item, Minecraft#runTick (1.8) and Minecraft#handleKeybinds (1.9+)
 * drain attack clicks without attacking. Releasing the item happens in that same input pass, so
 * the earliest legitimate attack after a release is in the next tick. Within a tick, attack clicks
 * are handled before use clicks, so "attack, then block" stays legitimate.
 *
 * <p>Tracked blocking items always start on use and never finish by themselves: the sword when
 * both client and server are 1.8, the shield in either hand when both are 1.9+. Mixed versions are
 * skipped, since protocol translation lets the client and the server disagree about blocking. The
 * client can stop silently when its item is replaced, its hands swapped or its shield put on
 * cooldown, so those end the tracked block (missing a flag rather than raising a wrong one). Only
 * the order of the client's own packets is compared, so latency cannot cause a detection.
 */
public final class AttackWhileBlockingHeuristic extends ClassicHeuristic<AttackWhileBlockingHeuristic.BlockingStateMeta> {
  public AttackWhileBlockingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_WHILE_BLOCKING, BlockingStateMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    // ProtocolLib reports the legacy use packet under one of these names depending on its version
    packetsIn = {
      BLOCK_PLACE, USE_ITEM, USE_ITEM_ON
    }
  )
  public void receiveUse(User user, BlockInteractionReader reader) {
    if (!reader.isDirectItemUseRequest()) {
      return;
    }
    ItemStack item = user.meta().inventory().itemInHand(reader.hand());
    if (startsBlocking(user, item)) {
      metaOf(user).blocking = true;
    }
  }

  private static boolean startsBlocking(User user, ItemStack item) {
    if (item == null) {
      return false;
    }
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.emptyFlyingPacketsAreExplicitlySent()) {
      // 1.8 client on a 1.8 server: sword blocking
      return ItemProperties.isSwordItem(item);
    }
    if (protocol.combatUpdate() && MinecraftVersions.VER1_9_0.atOrAbove()) {
      return "SHIELD".equals(item.getType().name());
    }
    return false;
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = BLOCK_DIG
  )
  public void receiveDig(User user, BlockDigReader reader) {
    BlockingStateMeta meta = metaOf(user);
    EnumWrappers.PlayerDigType action = reader.action();
    if (action == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM && meta.blocking) {
      meta.blocking = false;
      meta.releasedThisTick = true;
    } else if (action == EnumWrappers.PlayerDigType.SWAP_HELD_ITEMS) {
      meta.blocking = false;
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
      SET_SLOT, WINDOW_ITEMS, HELD_ITEM_SLOT_OUT, RESPAWN, SET_COOLDOWN
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
    if (meta.recordedThisTick) {
      return;
    }
    String message;
    if (meta.blocking) {
      message = "attacked while blocking";
    } else if (meta.releasedThisTick && ClientTickBoundary.exact(user)) {
      message = "attacked in the same tick as unblocking";
    } else {
      return;
    }
    meta.recordedThisTick = true;
    if (meta.evidence.record(System.currentTimeMillis())) {
      flag(user, message, meta.evidence.count() + " times");
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
    if (user.meta().movement().ticksPast(TELEPORT) <= 1) {
      meta.blocking = false;
    }
    // a movement packet always belongs to a later input pass than the release before it
    endTick(meta);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = CLIENT_TICK_END
  )
  public void receiveTickEnd(User user) {
    endTick(metaOf(user));
  }

  private static void endTick(BlockingStateMeta meta) {
    meta.releasedThisTick = false;
    meta.recordedThisTick = false;
  }

  private static boolean checkable(User user) {
    return !user.meta().abilities().ignoringMovementPackets()
      && user.meta().movement().ticksPast(TELEPORT) > 1;
  }

  public static final class BlockingStateMeta extends CheckCustomMetadata {
    private boolean blocking;
    private boolean releasedThisTick;
    private boolean recordedThisTick;
    private final RepeatedEvidence evidence = new RepeatedEvidence(2, 60_000);
  }
}
