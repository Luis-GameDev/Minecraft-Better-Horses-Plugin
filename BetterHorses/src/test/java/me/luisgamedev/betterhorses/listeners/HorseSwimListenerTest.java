package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Horse;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class HorseSwimListenerTest {
    @Test void existingRidersStartOnConstructionAndReloadDoesNotDuplicateTasks() throws Exception {
        try (Harness h = new Harness(true)) {
            h.f.advance(2);
            assertEquals(1, h.repeatingTasks());
            h.listener.onConfigurationReload();
            h.listener.onMount(new EntityMountEvent(h.f.player.handle, h.horse));
            h.listener.onMount(new EntityMountEvent(h.f.player.handle, h.horse));
            h.f.advance(2);
            assertEquals(1, h.repeatingTasks());
        }
    }

    @Test void waterBuoyancyUsesActualMovementWithoutChangingAttributesOrData() throws Exception {
        try (Harness h = new Harness(true)) {
            h.f.advance(2);
            h.water = true;
            h.x = .3;
            h.f.horse.velocity = new Vector(7, 8, 9);
            h.f.advance(1);
            assertTrue(h.f.horse.velocity.getX() > 0);
            assertTrue(h.f.horse.velocity.getX() <= .1);
            assertTrue(h.f.horse.velocity.getY() > 0);
            assertEquals(0, h.attributeReads);
            assertTrue(h.f.horse.values.isEmpty());
        }
    }

    @Test void tinyHorseFloatsAtHalfItsOwnHeight() throws Exception {
        try (Harness h = new Harness(true)) {
            h.height = .1;
            h.y = 63 + 8.0 / 9.0 - .05;
            h.water = true;
            h.f.advance(3);
            assertEquals(0, h.velocityWrites);
            assertEquals(0.0, h.f.horse.velocity.getY(), 1.0E-12,
                    "A tiny horse at its waterline must not be pushed below the surface");
        }
    }

    @Test void tinyHorseStillGetsAssistanceWhenItSinksBelowItsScaledMargin() throws Exception {
        try (Harness h = new Harness(true)) {
            h.height = .1;
            h.y = 63 + 8.0 / 9.0 - .05 - .02;
            h.water = true;
            h.f.advance(3);
            assertTrue(h.velocityWrites > 0);
            assertTrue(h.f.horse.velocity.getY() > 0,
                    "A fixed surface margin must not let a tiny mount sink");
        }
    }

    @Test void fallingHorseCanDipBeforeItStartsRising() throws Exception {
        try (Harness h = new Harness(true)) {
            h.f.advance(2);
            h.water = true;
            h.y -= 2;
            h.f.advance(1);
            assertTrue(h.f.horse.velocity.getY() < 0, "Water entry must not snap a falling mount upward");
            h.f.advance(1);
            assertTrue(h.f.horse.velocity.getY() > 0, "Buoyancy must recover once downward movement stops");
        }
    }

    @Test void oneBlockWaterPreservesNativeMovementAndDeepWaterResumesAssistance() throws Exception {
        try (Harness h = new Harness(true)) {
            h.floorY = 62;
            h.y = 63.02;
            h.f.advance(2);
            h.water = true;
            h.x = .3;
            h.y -= .01;
            Vector walking = new Vector(.35, -.02, .18);
            h.f.horse.velocity = walking.clone();
            h.f.advance(1);
            assertEquals(0, h.velocityWrites, "A solid floor beneath one water block needs no buoyancy");
            assertEquals(walking, h.f.horse.velocity);

            Vector jumping = new Vector(.4, .5, .2);
            h.f.horse.velocity = jumping.clone();
            h.y += .3;
            h.x += .3;
            h.f.advance(1);
            assertEquals(0, h.velocityWrites);
            assertEquals(jumping, h.f.horse.velocity, "Swimming must not clamp a native jump");

            h.floorY = 61;
            h.y = 63;
            h.x += .3;
            h.f.advance(1);
            assertEquals(1, h.velocityWrites, "Moving into two-block-deep water resumes assistance");
            assertTrue(h.f.horse.velocity.getX() > 0 && h.f.horse.velocity.getX() <= .1);
            h.f.advance(1);
            assertTrue(h.f.horse.velocity.getY() > 0);
        }
    }

    @Test void tinyHorseOnPartialFloorStillGetsAssistanceWhenWaterContinuesAboveTheProbe() throws Exception {
        try (Harness h = new Harness(true)) {
            h.height = .1;
            h.floorY = 62;
            h.floorHeight = .9375;
            h.y = 62.9375;
            h.waterTopY = 65;
            h.water = true;
            h.f.advance(3);
            assertTrue(h.velocityWrites > 0,
                    "The bounded water probe must not mistake deep water for shallow footing");
            assertTrue(h.f.horse.velocity.getY() > 0);
        }
    }

    @Test void waterAboveAnAirGapDoesNotAffectThePoolAroundTheHorse() throws Exception {
        try (Harness h = new Harness(true)) {
            h.floorY = 62;
            h.y = 63.8;
            h.waterTopY = 65;
            h.dryLayers = Set.of(64);
            h.water = true;
            h.f.advance(3);
            assertEquals(0, h.velocityWrites,
                    "An unrelated overhead pool must not turn shallow water into deep water");
        }
    }

    @Test void risingInDeepWaterPreservesNativeJumpAndHorizontalVelocity() throws Exception {
        try (Harness h = new Harness(true)) {
            h.y = 61;
            h.f.advance(2);
            h.water = true;
            h.y += .15;
            h.x += .3;
            Vector nativeMotion = new Vector(.35, .5, .1);
            h.f.horse.velocity = nativeMotion.clone();
            h.f.advance(1);
            assertEquals(0, h.velocityWrites);
            assertEquals(nativeMotion, h.f.horse.velocity);
        }
    }

    @Test void descendingAtTheSurfacePreservesNativeBobbing() throws Exception {
        try (Harness h = new Harness(true)) {
            h.y = 63 + 8.0 / 9.0 - .75 + .02;
            h.f.advance(2);
            h.water = true;
            h.y -= .04;
            h.x += .3;
            Vector nativeMotion = new Vector(.35, -.03, .1);
            h.f.horse.velocity = nativeMotion.clone();
            h.f.advance(1);
            assertEquals(0, h.velocityWrites);
            assertEquals(nativeMotion, h.f.horse.velocity);
        }
    }

    @Test void dryMovementAndTeleportDoNotProduceAnImpulse() throws Exception {
        try (Harness h = new Harness(true)) {
            h.f.advance(2);
            Vector original = new Vector(.5, -.3, .7);
            h.f.horse.velocity = original.clone();
            h.x = .3;
            h.f.advance(1);
            assertEquals(original, h.f.horse.velocity);
            assertEquals(0, h.velocityWrites);
            h.water = true;
            h.x += 100;
            h.f.advance(1);
            assertEquals(original, h.f.horse.velocity);
            assertEquals(0, h.velocityWrites);
        }
    }

    @Test void disabledSettingCreatesNoRepeatingWork() throws Exception {
        try (Harness h = new Harness(false)) {
            h.listener.onMount(new EntityMountEvent(h.f.player.handle, h.horse));
            h.f.advance(2);
            assertEquals(0, h.repeatingTasks());
            assertEquals(0, h.velocityWrites);
        }
    }

    @Test void worldFilterAndDisablingStopExistingWork() throws Exception {
        try (Harness h = new Harness(true)) {
            h.f.advance(2);
            h.f.config.set("world-filter.worlds", List.of("world"));
            WorldAccessPolicy.reload(h.f.plugin);
            h.water = true;
            h.f.advance(1);
            assertEquals(0, h.repeatingTasks());
            assertEquals(0, h.velocityWrites);
            h.f.config.set("world-filter.worlds", List.of());
            WorldAccessPolicy.reload(h.f.plugin);
            h.listener.onConfigurationReload();
            h.f.advance(2);
            assertEquals(1, h.repeatingTasks());
            h.f.config.set("settings.horse-can-swim", false);
            h.f.advance(1);
            assertEquals(0, h.repeatingTasks());
        }
    }

    @Test void dismountAndShutdownReleaseAllTracking() throws Exception {
        try (Harness h = new Harness(true)) {
            h.f.advance(2);
            h.f.player.vehicle = null;
            h.f.advance(1);
            assertEquals(0, h.repeatingTasks());
            h.f.player.vehicle = h.horse;
            h.listener.onMount(new EntityMountEvent(h.f.player.handle, h.horse));
            assertEquals(1, h.repeatingTasks());
            h.listener.shutdown();
            h.listener.onConfigurationReload();
            h.listener.onMount(new EntityMountEvent(h.f.player.handle, h.horse));
            h.f.advance(2);
            assertEquals(0, h.repeatingTasks());
            assertTrue(((Map<?, ?>) field(HorseSwimListener.class, "horses").get(h.listener)).isEmpty());
        }
    }

    static final class Harness implements AutoCloseable {
        final Fixture f = new Fixture();
        final World world;
        final Horse horse;
        final HorseSwimListener listener;
        double x, y = 63, height = 1.6, width = 1.4, floorHeight = 1.0;
        int floorY = -64, waterTopY = 63;
        Set<Integer> dryLayers = Set.of();
        Levelled sourceWater;
        boolean water;
        int velocityWrites, attributeReads;

        Harness(boolean enabled) throws Exception {
            f.config.set("settings.horse-can-swim", enabled);
            f.config.set("world-filter.worlds", List.of());
            WorldAccessPolicy.reload(f.plugin);
            sourceWater = proxy(Levelled.class, (target, method, args) ->
                    method.getName().equals("getLevel") ? 0 : zero(method.getReturnType()));
            world = proxy(World.class, (target, method, args) -> switch (method.getName()) {
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "getBlockAt" -> block((Integer) args[0], (Integer) args[1], (Integer) args[2]);
                default -> delegate(method, f.world, args);
            });
            horse = proxy(Horse.class, (target, method, args) -> switch (method.getName()) {
                case "getLocation" -> new Location(world, x, y, 0);
                case "getWorld" -> world;
                case "getHeight" -> height;
                case "getWidth" -> width;
                case "getBoundingBox" -> new BoundingBox(x - width / 2, y, -width / 2,
                        x + width / 2, y + height, width / 2);
                case "isInWater" -> water;
                case "getPassengers" -> f.player.vehicle == target ? List.of(f.player.handle) : List.of();
                case "getAttribute" -> { attributeReads++; yield delegate(method, f.horse.handle, args); }
                case "setVelocity" -> { velocityWrites++; yield delegate(method, f.horse.handle, args); }
                default -> delegate(method, f.horse.handle, args);
            });
            f.player.vehicle = horse;
            Server original = Bukkit.getServer();
            Server server = proxy(Server.class, (target, method, args) ->
                    method.getName().equals("getOnlinePlayers") ? List.of(f.player.handle) : delegate(method, original, args));
            f.serverField.set(null, server);
            field(JavaPlugin.class, "server").set(f.plugin, server);
            listener = new HorseSwimListener(f.plugin);
        }

        Block block(int blockX, int blockY, int blockZ) {
            boolean solid = blockY <= floorY;
            boolean wet = !solid && blockY <= waterTopY && !dryLayers.contains(blockY);
            double collisionHeight = blockY == floorY ? floorHeight : 1.0;
            List<BoundingBox> boxes = solid
                    ? List.of(new BoundingBox(0, 0, 0, 1, collisionHeight, 1)) : List.of();
            VoxelShape shape = proxy(VoxelShape.class, (target, method, args) -> switch (method.getName()) {
                case "getBoundingBoxes" -> boxes;
                case "overlaps" -> boxes.stream().anyMatch(box -> box.overlaps((BoundingBox) args[0]));
                default -> zero(method.getReturnType());
            });
            return proxy(Block.class, (target, method, args) -> switch (method.getName()) {
                case "getType" -> solid ? Material.STONE : wet ? Material.WATER : Material.AIR;
                case "getBlockData" -> wet ? sourceWater : null;
                case "getCollisionShape" -> shape;
                case "getBoundingBox" -> solid
                        ? new BoundingBox(blockX, blockY, blockZ, blockX + 1, blockY + collisionHeight, blockZ + 1)
                        : new BoundingBox();
                case "getX" -> blockX;
                case "getY" -> blockY;
                case "getZ" -> blockZ;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, blockX, blockY, blockZ);
                default -> zero(method.getReturnType());
            });
        }

        long repeatingTasks() { return f.tasks.stream().filter(task -> !task.cancelled && task.period > 0).count(); }

        @Override public void close() throws Exception {
            listener.shutdown();
            f.config.set("world-filter.worlds", List.of());
            WorldAccessPolicy.reload(f.plugin);
            f.close();
        }
    }

    static Object delegate(Method method, Object receiver, Object[] args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }
}
