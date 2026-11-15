package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

final class CarpetPatch extends BlockShapePatch {
  @Override
  protected BlockShape collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    // Clients always collide with a 1/16 carpet. Some 1.8 forks remove the server-side box
    // (Carbon PREVENT-CARPET-STUCK-GLITCH), which made every step onto a carpet a physics flag.
    // 1.7 clients are handled by CarpetCollisionModifier.
    // Pale moss carpet (1.21.4+) is no plain carpet: only its base state collides, the hanging
    // parts on top of it have no collision box, so the server's shape is kept.
    return PALE_MOSS_CARPET.equals(type.name()) ? shape : CARPET;
  }

  private static final String PALE_MOSS_CARPET = "PALE_MOSS_CARPET";

  private static final BlockShape CARPET = BoundingBox.originFrom(0.0F, 0.0F, 0.0F, 1.0F, 0.0625F, 1.0F);

  @Override
  public boolean appliesTo(Material material) {
    return material.name().contains("CARPET");
  }
}
