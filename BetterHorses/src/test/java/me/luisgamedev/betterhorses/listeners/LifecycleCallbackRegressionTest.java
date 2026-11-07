package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseAbilityUseEvent;
import me.luisgamedev.betterhorses.api.events.BetterHorseNeuterEvent;
import me.luisgamedev.betterhorses.inspection.HorseNeuteringService;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleCallbackRegressionTest {
    @Test void aCancelledGestureIsIgnoredButANormalGestureStillActivates() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(BetterHorseKeys.TRAIT, "dashboost");
            PlayerSwapHandItemsEvent blocked = new PlayerSwapHandItemsEvent(f.player.handle, null, null);
            blocked.setCancelled(true);
            new TraitActivationListener().onTraitKeyPressed(blocked);
            assertTrue(f.events.isEmpty());
            assertTrue(f.tasks.isEmpty());
            assertEquals(.2, f.horse.attributes.get(AttributeResolver.generic("MOVEMENT_SPEED")).getValue(), 1e-12);
            PlayerSwapHandItemsEvent accepted = new PlayerSwapHandItemsEvent(f.player.handle, null, null);
            new TraitActivationListener().onTraitKeyPressed(accepted);
            assertTrue(accepted.isCancelled());
            assertEquals(.3, f.horse.attributes.get(AttributeResolver.generic("MOVEMENT_SPEED")).getValue(), 1e-12);
        }
    }

    @Test void abilityCallbackStateChangesAbortBeforeBonusOrCooldown() throws Exception {
        for (int change = 0; change < 4; change++) {
            try (Fixture f = new Fixture()) {
                f.horse.values.put(BetterHorseKeys.TRAIT, "dashboost");
                int kind = change;
                intercept(f, event -> {
                    if (!(event instanceof BetterHorseAbilityUseEvent)) return;
                    if (kind == 0) f.player.permission = false;
                    if (kind == 1) enabled(f, false);
                    if (kind == 2) f.player.vehicle = null;
                    if (kind == 3) f.config.set("traits.enabled", false);
                });
                PlayerSwapHandItemsEvent input = new PlayerSwapHandItemsEvent(f.player.handle, null, null);
                new TraitActivationListener().onTraitKeyPressed(input);
                assertTrue(input.isCancelled());
                assertEquals(.2, f.horse.attributes.get(AttributeResolver.generic("MOVEMENT_SPEED")).getValue(), 1e-12);
                assertFalse(f.horse.values.containsKey(new NamespacedKey("betterhorses", "ability_cooldown_dashboost")));
            }
        }
    }

    @Test void redirectedAbilityUsesTheSelectedTraitsPermission() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(BetterHorseKeys.TRAIT, "dashboost");
            Player original = f.player.handle;
            Player player = proxy(Player.class, (target, method, args) -> method.getName().equals("hasPermission")
                    ? !args[0].equals("betterhorses.trait.ghosthorse") : method.invoke(original, args));
            intercept(f, event -> {
                if (event instanceof BetterHorseAbilityUseEvent ability) ability.setTraitKey("ghosthorse");
            });
            new TraitActivationListener().onTraitKeyPressed(new PlayerSwapHandItemsEvent(player, null, null));
            assertNull(f.player.effect(PotionEffectType.INVISIBILITY));
            assertNull(f.horse.effect(PotionEffectType.INVISIBILITY));
            assertTrue(f.tasks.isEmpty());
        }
    }

    @Test void disablingPluginDuringNeuterOrWearPreservesFertilityAndTool() throws Exception {
        for (boolean duringWear : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.config.set("horse-neutering.enabled", true);
                f.config.set("horse-neutering.shears-enabled", true);
                f.held = new Shears();
                Player player = neuteringPlayer(f);
                intercept(f, event -> {
                    if ((!duringWear && event instanceof BetterHorseNeuterEvent)
                            || (duringWear && event instanceof PlayerItemDamageEvent)) enabled(f, false);
                });
                assertFalse(new HorseNeuteringService(f.plugin).neuter(player, f.horse.handle, EquipmentSlot.HAND));
                assertFalse(f.horse.values.containsKey(BetterHorseKeys.NEUTERED));
                assertEquals(0, ((Shears) f.held).damage);
                assertEquals(duringWear ? 2 : 1, f.events.size());
            }
        }
    }

    @Test void acceptedShearsInteractionStillNeutersAndWearsTheTool() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("horse-neutering.enabled", true);
            f.config.set("horse-neutering.shears-enabled", true);
            f.held = new Shears();
            assertTrue(new HorseNeuteringService(f.plugin).neuter(neuteringPlayer(f), f.horse.handle, EquipmentSlot.HAND));
            assertEquals((byte) 1, f.horse.values.get(BetterHorseKeys.NEUTERED));
            assertEquals(1, ((Shears) f.held).damage);
        }
    }

    private static Player neuteringPlayer(Fixture f) {
        Player original = f.player.handle;
        return proxy(Player.class, (target, method, args) -> switch (method.getName()) {
            case "isSneaking" -> true;
            case "getGameMode" -> GameMode.SURVIVAL;
            default -> method.invoke(original, args);
        });
    }

    private static void enabled(Fixture f, boolean enabled) {
        try { field(JavaPlugin.class, "isEnabled").setBoolean(f.plugin, enabled); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
    }

    private static void intercept(Fixture f, Consumer<Event> callback) throws Exception {
        Server original = Bukkit.getServer();
        PluginManager manager = proxy(PluginManager.class, (target, method, args) -> {
            if (method.getName().equals("callEvent")) { f.events.add((Event) args[0]); callback.accept((Event) args[0]); }
            return zero(method.getReturnType());
        });
        f.serverField.set(null, proxy(Server.class, (target, method, args) -> method.getName().equals("getPluginManager")
                ? manager : method.invoke(original, args)));
    }

    private static final class Shears extends ItemStack {
        int damage;
        Shears() { super(Material.SHEARS); }
        @Override public Shears clone() { Shears copy = new Shears(); copy.damage = damage; return copy; }
        @Override public ItemMeta getItemMeta() {
            int[] value = {damage};
            return proxy(Damageable.class, (target, method, args) -> switch (method.getName()) {
                case "getDamage" -> value[0];
                case "setDamage" -> { value[0] = (int) args[0]; yield null; }
                default -> zero(method.getReturnType());
            });
        }
        @Override public boolean setItemMeta(ItemMeta meta) { damage = ((Damageable) meta).getDamage(); return true; }
        @Override public boolean equals(Object other) { return other instanceof Shears shears && shears.damage == damage; }
        @Override public int hashCode() { return damage; }
    }
}
