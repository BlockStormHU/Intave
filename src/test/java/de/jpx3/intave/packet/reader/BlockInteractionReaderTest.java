package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockInteractionReaderTest {
  @Test
  void legacyServersAcceptBothProtocolLibNamesOfTheOldPlacePacket() {
    assertTrue(BlockInteractionReader.isLegacyBlockPlace(PacketType.Play.Client.BLOCK_PLACE, false));
    assertTrue(BlockInteractionReader.isLegacyBlockPlace(PacketType.Play.Client.USE_ITEM_ON, false));
    assertFalse(BlockInteractionReader.isLegacyBlockPlace(PacketType.Play.Client.USE_ITEM, false));
  }

  @Test
  void modernBlockClicksAreNoLegacyPlacements() {
    // ProtocolLib 5: BLOCK_PLACE is a clone of USE_ITEM_ON and equal to it
    assertTrue(PacketType.Play.Client.USE_ITEM_ON.equals(PacketType.Play.Client.BLOCK_PLACE));
    assertFalse(BlockInteractionReader.isLegacyBlockPlace(PacketType.Play.Client.USE_ITEM_ON, true));
    assertFalse(BlockInteractionReader.isLegacyBlockPlace(PacketType.Play.Client.USE_ITEM, true));
  }
}
