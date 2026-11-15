package de.jpx3.intave.player.collider;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LowBlockStepTest {
  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.8.8"));
  }

  /** A 1.8 player walking into a low block steps onto it within the same tick. */
  @Test
  void walkingIntoLowBlockStepsUp() {
    assertStepsOnto("SNOW", 0.125);
    assertStepsOnto("OAK_TRAPDOOR", 0.1875);
    assertStepsOnto("STONE_SLAB", 0.5);
    assertStepsOnto("WHITE_CARPET", 0.0625);
  }

  /**
   * Sprint-jumping into a two block high tunnel: vanilla's second step variant (box widened by the
   * horizontal motion) is capped by the ceiling, fits under it and wins on distance, so the jump turns
   * into a slide into the tunnel instead of rising and stopping at the edge.
   */
  @Test
  void jumpIntoTunnelSlidesUnderTheCeiling() {
    Position position = Position.of(0.5D, 1.0D, 0.3019D);
    User user = user(position, Material.GLASS, 1.0D, 3, 1);
    SimulationResult result = Colliders.collision(
      user, user.meta().movement().mutableView(), new Motion(0.0D, 0.42D, 0.537D), false,
      position.getX(), position.getY(), position.getZ()
    );
    assertEquals(0.0D, result.offsetMotion().motionY, 1.0E-9);
    assertEquals(0.537D, result.offsetMotion().motionZ, 1.0E-9);
  }

  private static void assertStepsOnto(String materialName, double height) {
    Material material = Material.valueOf(materialName);
    Position position = Position.of(0.5D, 1.0D, 0.6942D);
    User user = user(position, material, height);
    MovementMetadata environment = user.meta().movement();
    SimulationResult result = Colliders.collision(
      user, environment.mutableView(), new Motion(0.0D, -0.0784D, 0.2158D), false,
      position.getX(), position.getY(), position.getZ()
    );
    assertEquals(height, result.offsetMotion().motionY, 1.0E-9, materialName);
    assertEquals(0.2158D, result.offsetMotion().motionZ, 1.0E-9, materialName);
  }

  private static User user(Position position, Material low, double height) {
    return user(position, low, height, 1, 1);
  }

  private static User user(Position position, Material low, double height, int blockY, int blockZ) {
    MockFullBlockStaticPlane plane = MockFullBlockStaticPlane.createWithHorizontalPlaneAt(0);
    BlockCache cache = (BlockCache) Proxy.newProxyInstance(
      BlockCache.class.getClassLoader(), new Class[] {BlockCache.class},
      (proxy, method, args) -> {
        if (args != null && args.length >= 3 && args[0] instanceof Integer && args[1] instanceof Integer && args[2] instanceof Integer) {
          int x = (Integer) args[0], y = (Integer) args[1], z = (Integer) args[2];
          if (y == blockY && z >= blockZ && z <= blockZ + 3 && x >= -2 && x <= 2) {
            BlockShape shape = BoundingBox.fromBounds(x, y, z, x + 1, y + height, z + 1);
            switch (method.getName()) {
              case "collisionShapeAt":
              case "outlineShapeAt":
                return shape;
              case "typeAt":
                return low;
              case "stateAt":
                return new BlockState(shape, shape, low, 0);
              default:
            }
          }
        }
        return method.invoke(plane, args);
      }
    );
    World world = FakeWorldFactory.createWorld((methodName, _) -> switch (methodName) {
      case "isChunkLoaded", "isChunkInUse" -> true;
      case "isThundering", "hasStorm" -> false;
      default -> null;
    });
    Location location = position.toLocation(world);
    Player player = FakePlayerFactory.createPlayer((methodName, _) -> switch (methodName) {
      case "getWorld" -> world;
      case "getLocation" -> location;
      case "getUniqueId" -> UUID.randomUUID();
      default -> null;
    });
    User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
      case "blockCache" -> cache;
      case "protocolVersion" -> VER_1_8;
      default -> null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    MovementMetadata environment = user.meta().movement();
    environment.updateMovement(position, Rotation.zero());
    environment.setVerifiedLastPosition(position, "low block step test");
    environment.setLastPosition(position);
    environment.setPose(Pose.STANDING);
    environment.onGround = true;
    environment.setLastOnGround(true);
    environment.setStepHeight(0.6F);
    return user;
  }
}
