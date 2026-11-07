package me.luisgamedev.betterhorses.listeners;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class FoliaBlockTraitReloadTest {
    @Test void queuedFireAndIceRespectNewWorldAndTraitRestrictions() throws Exception {
        for (boolean fire : new boolean[]{false, true}) {
            for (int change = 0; change < 3; change++) {
                try (Blocks f = new Blocks(fire)) {
                    f.queue();
                    if (change == 0) f.blacklist();
                    if (change == 1) f.fixture.config.set("traits." + f.trait + ".enabled", false);
                    if (change == 2) f.fixture.config.set("traits.enabled", false);
                    f.fixture.advance(1);
                    assertEquals(0, f.blockReads, f.trait + " read blocks after restriction " + change);
                    assertTrue(f.fixture.events.isEmpty());
                    assertEquals(fire ? Material.AIR : Material.WATER, f.material);
                }
            }
        }
    }

    @Test void protectionCallbackCanDisableIceBeforeTheBlockCommit() throws Exception {
        for (boolean blacklist : new boolean[]{false, true}) {
            try (Blocks f = new Blocks(false)) {
                f.callback = event -> {
                    if (event instanceof EntityChangeBlockEvent) {
                        if (blacklist) f.blacklist();
                        else f.fixture.config.set("traits.frosthooves.enabled", false);
                    }
                };
                f.queue();
                f.fixture.advance(1);
                assertEquals(1, f.fixture.events.size());
                assertEquals(Material.WATER, f.material);
            }
        }
    }

    @Test void aCancelledOrReplacedWaterBlockIsNeverFrozen() throws Exception {
        for (boolean cancelled : new boolean[]{false, true}) {
            try (Blocks f = new Blocks(false)) {
                f.callback = event -> {
                    if (event instanceof EntityChangeBlockEvent frost) {
                        if (cancelled) frost.setCancelled(true);
                        else f.material = Material.DIAMOND_BLOCK;
                    }
                };
                f.queue(); f.fixture.advance(1);
                assertEquals(cancelled ? Material.WATER : Material.DIAMOND_BLOCK, f.material);
            }
        }
    }

    @Test void acceptedIceStillFormsAndRestoresAfterRestrictionsChange() throws Exception {
        try (Blocks f = new Blocks(false)) {
            f.queue(); f.fixture.advance(1);
            assertEquals(Material.FROSTED_ICE, f.material);
            f.blacklist();
            f.fixture.config.set("traits.frosthooves.enabled", false);
            f.fixture.advance(300);
            assertEquals(Material.WATER, f.material);
        }
    }

    private static final class Blocks implements AutoCloseable {
        final Fixture fixture = new Fixture();
        final Field policy = field(WorldAccessPolicy.class, "snapshot");
        final Object originalPolicy = policy.get(null);
        final boolean fire;
        final String trait;
        Material material;
        int blockReads;
        Consumer<Event> callback = event -> { };

        Blocks(boolean fire) throws Exception {
            this.fire = fire;
            trait = fire ? "hellmare" : "frosthooves";
            material = fire ? Material.AIR : Material.WATER;
            fixture.config.set("traits." + trait + ".enabled", true);
            fixture.config.set("traits." + trait + ".radius", 0);
            fixture.config.set("traits." + trait + ".cooldown", 0);
            WorldAccessPolicy.reload(fixture.plugin);
            BlockData ice = proxy(BlockData.class, (target, method, args) -> method.getName().equals("getMaterial")
                    ? Material.FROSTED_ICE : zero(method.getReturnType()));
            World[] world = {null};
            Block block = proxy(Block.class, (target, method, args) -> switch (method.getName()) {
                case "getType" -> { blockReads++; yield material; }
                case "setType" -> { material = (Material) args[0]; yield null; }
                case "getWorld" -> world[0];
                case "getLocation" -> new Location(world[0], 0, -1, 0);
                default -> zero(method.getReturnType());
            });
            Block ground = proxy(Block.class, (target, method, args) -> switch (method.getName()) {
                case "getType" -> { blockReads++; yield Material.STONE; }
                case "getRelative" -> block;
                default -> zero(method.getReturnType());
            });
            World previousWorld = fixture.world;
            world[0] = proxy(World.class, (target, method, args) -> switch (method.getName()) {
                case "getBlockAt" -> fire ? ground : block;
                case "equals" -> target == args[0];
                case "hashCode" -> System.identityHashCode(target);
                default -> method.invoke(previousWorld, args);
            });
            field(Fixture.class, "world").set(fixture, world[0]);
            Server previousServer = Bukkit.getServer();
            PluginManager manager = proxy(PluginManager.class, (target, method, args) -> {
                if (method.getName().equals("callEvent")) {
                    fixture.events.add((Event) args[0]); callback.accept((Event) args[0]);
                }
                return zero(method.getReturnType());
            });
            RegionScheduler regions = proxy(RegionScheduler.class, (target, method, args) -> {
                if (!method.getName().equals("runDelayed")) return zero(method.getReturnType());
                @SuppressWarnings("unchecked") Consumer<ScheduledTask> work = (Consumer<ScheduledTask>) args[2];
                Task task = fixture.addTask(null, (Long) args[3], 0);
                task.action = () -> work.accept(task.paper);
                return task.paper;
            });
            Server server = proxy(Server.class, (target, method, args) -> switch (method.getName()) {
                case "getPluginManager" -> manager;
                case "getRegionScheduler" -> regions;
                case "createBlockData" -> ice;
                default -> method.invoke(previousServer, args);
            });
            fixture.serverField.set(null, server);
            field(JavaPlugin.class, "server").set(fixture.plugin, server);
        }

        void queue() {
            if (fire) {
                TraitRegistry.activateHellmare(fixture.player.handle, fixture.horse.handle);
                fixture.advance(1); // The entity task queues block-region work for the following tick.
            } else TraitRegistry.activateFrostHooves(fixture.player.handle, fixture.horse.handle);
            assertFalse(fixture.tasks.isEmpty());
        }

        void blacklist() {
            fixture.config.set("world-filter.worlds", List.of("world"));
            WorldAccessPolicy.reload(fixture.plugin);
        }

        @Override public void close() throws Exception {
            try { fixture.close(); }
            finally { policy.set(null, originalPolicy); }
        }
    }
}
