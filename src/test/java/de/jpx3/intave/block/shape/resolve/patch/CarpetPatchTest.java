package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CarpetPatchTest {
  @Test
  void restoresCarpetCollisionWhenTheServerRemovedIt() {
    BlockShape patched = new CarpetPatch().collisionPatch(
      null, null, 3, 64, 7, Material.WHITE_CARPET, 0, BlockShapes.emptyShape()
    );
    assertEquals(0.0D, patched.min(Direction.Axis.Y_AXIS), 1.0E-9);
    assertEquals(0.0625D, patched.max(Direction.Axis.Y_AXIS), 1.0E-9);
    assertEquals(1.0D, patched.max(Direction.Axis.X_AXIS) - patched.min(Direction.Axis.X_AXIS), 1.0E-9);
  }

  @Test
  void keepsTheVanillaCarpetShape() {
    BlockShape vanilla = BoundingBox.originFrom(0.0F, 0.0F, 0.0F, 1.0F, 0.0625F, 1.0F);
    BlockShape patched = new CarpetPatch().collisionPatch(null, null, 0, 0, 0, Material.WHITE_CARPET, 0, vanilla);
    assertEquals(vanilla.max(Direction.Axis.Y_AXIS), patched.max(Direction.Axis.Y_AXIS), 1.0E-9);
    assertTrue(new CarpetPatch().appliesTo(Material.WHITE_CARPET));
  }

  @Test
  void keepsTheServerShapeOfPaleMossCarpet() {
    // the hanging parts above the base carpet have no collision box
    BlockShape hanging = BlockShapes.emptyShape();
    Material paleMoss = Material.valueOf("PALE_MOSS_CARPET");
    assertTrue(new CarpetPatch().appliesTo(paleMoss));
    assertSame(hanging, new CarpetPatch().collisionPatch(null, null, 0, 65, 0, paleMoss, 0, hanging));
  }
}
