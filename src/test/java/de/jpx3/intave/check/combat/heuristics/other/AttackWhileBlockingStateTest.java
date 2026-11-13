package de.jpx3.intave.check.combat.heuristics.other;

import de.jpx3.intave.check.combat.heuristics.other.AttackWhileBlockingHeuristic.BlockingStateMeta;
import de.jpx3.intave.check.combat.heuristics.other.AttackWhileBlockingHeuristic.SlotReplacement;
import de.jpx3.intave.share.PlayerHand;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class AttackWhileBlockingStateTest {
  private static final int HELD_SLOT = 0;
  private static final int MAIN_SLOT = 36 + HELD_SLOT;
  private static final int OFF_SLOT = 45;

  @Test
  void replacementSentBeforeTheUseArrivedStillEndsTheBlock() {
    // 1.8 block-hitting with ping: the server sends the sword durability update, then the use packet
    // the client sent before receiving it arrives, then the client applies the update and stops
    // blocking silently. The update is only applied on acknowledgement, after the use packet.
    BlockingStateMeta meta = new BlockingStateMeta();
    SlotReplacement durabilityUpdate = SlotReplacement.of(0, HELD_SLOT, items(MAIN_SLOT, Material.DIAMOND_SWORD));
    meta.startBlocking(PlayerHand.MAIN_HAND, Material.DIAMOND_SWORD);
    meta.applyReplacement(durabilityUpdate, true);
    meta.endTick();
    assertFalse(meta.blocking());
    assertNull(meta.attack(true));
  }

  @Test
  void attackWhileBlockingIsDetectedOncePerTick() {
    BlockingStateMeta meta = new BlockingStateMeta();
    meta.startBlocking(PlayerHand.MAIN_HAND, Material.DIAMOND_SWORD);
    assertEquals("attacked while blocking", meta.attack(false));
    assertNull(meta.attack(false), "second attack in the same tick");
    meta.endTick();
    assertEquals("attacked while blocking", meta.attack(false));
  }

  @Test
  void attackInTheReleaseTickNeedsVisibleTickEnds() {
    BlockingStateMeta meta = new BlockingStateMeta();
    meta.startBlocking(PlayerHand.MAIN_HAND, Material.DIAMOND_SWORD);
    meta.release();
    assertNull(meta.attack(false), "tick ends not visible");
    assertEquals("attacked in the same tick as unblocking", meta.attack(true));

    meta.startBlocking(PlayerHand.MAIN_HAND, Material.DIAMOND_SWORD);
    meta.release();
    meta.endTick();
    assertNull(meta.attack(true), "attack in the tick after the release");
  }

  @Test
  void releaseWithoutBlockingIsNoUnblockTick() {
    BlockingStateMeta meta = new BlockingStateMeta();
    meta.release();
    assertNull(meta.attack(true));
  }

  @Test
  void modernClientsKeepBlockingWhenTheSameItemTypeIsResent() {
    BlockingStateMeta meta = new BlockingStateMeta();
    meta.startBlocking(PlayerHand.OFF_HAND, Material.SHIELD);
    meta.applyReplacement(SlotReplacement.of(0, HELD_SLOT, items(OFF_SLOT, Material.SHIELD)), false);
    assertTrue(meta.blocking());
    meta.applyReplacement(SlotReplacement.of(0, HELD_SLOT, items(OFF_SLOT, Material.AIR)), false);
    assertFalse(meta.blocking());
  }

  @Test
  void updatesOfTheOtherHandDoNotEndTheBlock() {
    BlockingStateMeta meta = new BlockingStateMeta();
    meta.startBlocking(PlayerHand.OFF_HAND, Material.SHIELD);
    meta.applyReplacement(SlotReplacement.of(0, HELD_SLOT, items(MAIN_SLOT, Material.DIAMOND_SWORD)), true);
    assertTrue(meta.blocking());
  }

  @Test
  void anotherContainerEndsTheBlock() {
    BlockingStateMeta meta = new BlockingStateMeta();
    meta.startBlocking(PlayerHand.MAIN_HAND, Material.DIAMOND_SWORD);
    meta.applyReplacement(SlotReplacement.of(5, HELD_SLOT, items(0, Material.STONE)), false);
    assertFalse(meta.blocking());
  }

  @Test
  void onlyHandSlotsAreReplacements() {
    assertNull(SlotReplacement.of(0, HELD_SLOT, items(5, Material.IRON_HELMET)));
    SlotReplacement playerInventory = SlotReplacement.of(-2, 3, items(3, Material.STONE));
    assertNotNull(playerInventory);
    assertTrue(playerInventory.mainHandReplaced);
    assertFalse(playerInventory.offHandReplaced);
    assertEquals(Material.STONE, playerInventory.mainHandType);
    assertTrue(SlotReplacement.of(-2, 3, items(40, Material.SHIELD)).offHandReplaced);
  }

  @Test
  void emptiedSlotCountsAsAir() {
    Map<Integer, ItemStack> items = new HashMap<>();
    items.put(MAIN_SLOT, null);
    assertEquals(Material.AIR, SlotReplacement.of(0, HELD_SLOT, items).mainHandType);
  }

  private static Map<Integer, ItemStack> items(int slot, Material type) {
    Map<Integer, ItemStack> items = new HashMap<>();
    items.put(slot, new ItemStack(type));
    return items;
  }
}
