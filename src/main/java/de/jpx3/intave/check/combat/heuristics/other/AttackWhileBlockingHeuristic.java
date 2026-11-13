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
import de.jpx3.intave.packet.reader.WindowItemReader;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

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
 * client can stop silently when its item is replaced (1.8-1.13: any new stack in the blocking
 * hand, as sword durability updates after hits; 1.14+: another item type), its hands swapped, an
 * item dropped or clicked in the inventory, or a cooldown set, so those end the tracked block
 * (missing a flag rather than raising a wrong one).
 *
 * <p>Server packets that end a block silently are applied when they are sent and again when the
 * client acknowledges them (tick feedback): a use packet the client sent before it received the
 * replacement arrives after the send but before the acknowledgement, and is ended by the second
 * pass. A separate transaction is used rather than bundling with the packet, since bundling
 * cancels and re-sends it, which would collide with PlayerHandTracker's own bundle of the same
 * packet. Only the order of the client's own packets is compared, so latency cannot cause a
 * detection.
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
    PlayerHand hand = reader.hand();
    ItemStack item = user.meta().inventory().itemInHand(hand);
    if (startsBlocking(user, item)) {
      metaOf(user).startBlocking(hand, item.getType());
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
    if (blocksWithShield(protocol)) {
      return "SHIELD".equals(item.getType().name());
    }
    return false;
  }

  private static boolean blocksWithShield(ProtocolMetadata protocol) {
    return protocol.combatUpdate() && MinecraftVersions.VER1_9_0.atOrAbove();
  }

  private static boolean canBlock(User user) {
    ProtocolMetadata protocol = user.meta().protocol();
    return protocol.emptyFlyingPacketsAreExplicitlySent() || blocksWithShield(protocol);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = BLOCK_DIG
  )
  public void receiveDig(User user, BlockDigReader reader) {
    BlockingStateMeta meta = metaOf(user);
    EnumWrappers.PlayerDigType action = reader.action();
    if (action == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM) {
      meta.release();
    } else if (action == EnumWrappers.PlayerDigType.SWAP_HELD_ITEMS
      || action == EnumWrappers.PlayerDigType.DROP_ITEM
      || action == EnumWrappers.PlayerDigType.DROP_ALL_ITEMS) {
      meta.stopBlocking();
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      HELD_ITEM_SLOT_IN, WINDOW_CLICK
    }
  )
  public void receiveInventoryAction(User user) {
    metaOf(user).stopBlocking();
  }

  @PacketSubscription(
    packetsOut = {
      SET_SLOT, WINDOW_ITEMS
    }
  )
  public void sentItems(User user, WindowItemReader reader) {
    BlockingStateMeta meta = metaOf(user);
    if (!mayBlockBeforeReceipt(user, meta)) {
      return;
    }
    SlotReplacement replacement = SlotReplacement.of(
      reader.windowId(), user.meta().inventory().handSlot(), reader.itemMap()
    );
    if (replacement == null || replacement.foreignWindow && !meta.blocking()) {
      // a block started later cannot have been running while another container was open
      return;
    }
    // same rule as InventoryMetadata#applyClientActiveItemReplacement
    boolean anyNewStack = user.meta().protocol().protocolVersion() < ProtocolMetadata.VER_1_14;
    meta.applyReplacement(replacement, anyNewStack);
    user.tickFeedback(() -> meta.applyReplacement(replacement, anyNewStack));
  }

  @PacketSubscription(
    packetsOut = {
      HELD_ITEM_SLOT_OUT, RESPAWN, SET_COOLDOWN
    }
  )
  public void sentInventoryChange(User user) {
    BlockingStateMeta meta = metaOf(user);
    if (mayBlockBeforeReceipt(user, meta)) {
      meta.stopBlocking();
      user.tickFeedback(meta::stopBlocking);
    }
  }

  /**
   * Whether the client may be blocking by the time it receives a packet sent now. Starting a block
   * needs a blocking item in hand already, so everyone else is skipped without extra feedback.
   */
  private static boolean mayBlockBeforeReceipt(User user, BlockingStateMeta meta) {
    if (!canBlock(user)) {
      return false;
    }
    InventoryMetadata inventory = user.meta().inventory();
    return meta.blocking()
      || startsBlocking(user, inventory.heldItem())
      || startsBlocking(user, inventory.offhandItem());
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
    String message = meta.attack(ClientTickBoundary.exact(user));
    if (message != null && meta.evidence.record(System.currentTimeMillis())) {
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
      meta.stopBlocking();
    }
    // a movement packet always belongs to a later input pass than the release before it
    meta.endTick();
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = CLIENT_TICK_END
  )
  public void receiveTickEnd(User user) {
    ClientTickBoundary.observeTickEnd(user);
    metaOf(user).endTick();
  }

  private static boolean checkable(User user) {
    return !user.meta().abilities().ignoringMovementPackets()
      && user.meta().movement().ticksPast(TELEPORT) > 1;
  }

  /**
   * The hand slots a clientbound inventory packet replaces, captured when it is sent. The held slot
   * is read at send time; changing it later sends HELD_ITEM_SLOT, which ends the block anyway.
   */
  static final class SlotReplacement {
    final boolean foreignWindow;
    final boolean mainHandReplaced, offHandReplaced;
    final Material mainHandType, offHandType;

    private SlotReplacement(
      boolean foreignWindow,
      boolean mainHandReplaced, Material mainHandType,
      boolean offHandReplaced, Material offHandType
    ) {
      this.foreignWindow = foreignWindow;
      this.mainHandReplaced = mainHandReplaced;
      this.mainHandType = mainHandType;
      this.offHandReplaced = offHandReplaced;
      this.offHandType = offHandType;
    }

    /**
     * @return the replacement, or null if the packet touches neither hand slot
     */
    static SlotReplacement of(int windowId, int heldSlot, Map<Integer, ItemStack> items) {
      if (windowId != 0 && windowId != -2) {
        // another container is open: its slot numbers are not tracked here
        return new SlotReplacement(true, false, null, false, null);
      }
      int mainSlot = windowId == -2 ? heldSlot : 36 + heldSlot;
      int offSlot = windowId == -2 ? 40 : 45;
      boolean main = items.containsKey(mainSlot);
      boolean off = items.containsKey(offSlot);
      if (!main && !off) {
        return null;
      }
      return new SlotReplacement(
        false,
        main, main ? typeOf(items.get(mainSlot)) : null,
        off, off ? typeOf(items.get(offSlot)) : null
      );
    }

    private static Material typeOf(ItemStack stack) {
      return stack == null || stack.getAmount() == 0 ? Material.AIR : stack.getType();
    }
  }

  public static final class BlockingStateMeta extends CheckCustomMetadata {
    // Written on the client's packet thread and, to end a block, on the sending thread. Volatile
    // and assigned last in startBlocking, so a sender that sees a block also sees its hand and item.
    private volatile boolean blocking;
    private PlayerHand hand;
    private Material itemType;
    private boolean releasedThisTick;
    private boolean recordedThisTick;
    private final RepeatedEvidence evidence = new RepeatedEvidence(2, 60_000);

    void startBlocking(PlayerHand hand, Material itemType) {
      this.hand = hand;
      this.itemType = itemType;
      this.blocking = true;
    }

    void release() {
      if (blocking) {
        blocking = false;
        releasedThisTick = true;
      }
    }

    void stopBlocking() {
      blocking = false;
    }

    boolean blocking() {
      return blocking;
    }

    /**
     * Applies a clientbound inventory update once the client has received it.
     *
     * @param anyNewStack whether any new stack in the blocking hand ends the block (1.8-1.13),
     *                    rather than only another item type (1.14+)
     */
    void applyReplacement(SlotReplacement replacement, boolean anyNewStack) {
      if (!blocking) {
        return;
      }
      if (replacement.foreignWindow) {
        blocking = false;
        return;
      }
      boolean offHand = hand == PlayerHand.OFF_HAND;
      if (!(offHand ? replacement.offHandReplaced : replacement.mainHandReplaced)) {
        return;
      }
      Material type = offHand ? replacement.offHandType : replacement.mainHandType;
      if (anyNewStack || type != itemType) {
        blocking = false;
      }
    }

    /**
     * Records an attack.
     *
     * @param exactTickBoundary whether every client tick end is visible for this client
     * @return the detection message, or null if the attack is legitimate or this tick already counted
     */
    String attack(boolean exactTickBoundary) {
      if (recordedThisTick) {
        return null;
      }
      String message;
      if (blocking) {
        message = "attacked while blocking";
      } else if (releasedThisTick && exactTickBoundary) {
        message = "attacked in the same tick as unblocking";
      } else {
        return null;
      }
      recordedThisTick = true;
      return message;
    }

    void endTick() {
      releasedThisTick = false;
      recordedThisTick = false;
    }
  }
}
