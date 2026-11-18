package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Shared entity/item encoding of the growth clock and its last availability. */
public final class HorseGrowthData {
    private HorseGrowthData() { }

    public enum Availability {
        LOADED, UNLOADED, DESPAWNED, MOUNTED;

        public boolean accrues(FileConfiguration config) {
            return switch (this) {
                case LOADED -> true;
                case UNLOADED -> config.getBoolean("horse-growth-settings.grow-while-unloaded", false);
                case DESPAWNED -> config.getBoolean("horse-growth-settings.grow-while-despawned", false);
                case MOUNTED -> false;
            };
        }
    }

    public static GrowthState read(PersistentDataContainer data, int initialStage, long now, long intervalMillis) {
        Integer storedStage = data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        int stage = storedStage == null ? initialStage : storedStage;
        Long elapsed = data.get(BetterHorseKeys.GROWTH_PROGRESS_MILLIS, PersistentDataType.LONG);
        Long anchor = data.get(BetterHorseKeys.GROWTH_UPDATED_AT, PersistentDataType.LONG);
        if (elapsed != null) return new GrowthState(stage, elapsed, anchor == null ? now : anchor);

        // The old timestamp was the last stage boundary, not the last observation.
        // Reconcile it once using its original wall-clock meaning to retain existing
        // partial progress. Historical loaded/unloaded periods were not recorded;
        // the new availability policies apply after this first reconciliation.
        return fromLegacy(stage, anchor, now, intervalMillis);
    }

    static GrowthState fromLegacy(int stage, Long anchor, long now, long intervalMillis) {
        long previous = anchor == null || anchor > now ? now : anchor;
        return GrowthState.initial(stage, previous).advance(now, intervalMillis, true);
    }

    public static void write(PersistentDataContainer data, GrowthState state, Availability availability) {
        data.set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, state.stage());
        data.set(BetterHorseKeys.GROWTH_PROGRESS_MILLIS, PersistentDataType.LONG, state.elapsedMillis());
        data.set(BetterHorseKeys.GROWTH_UPDATED_AT, PersistentDataType.LONG, state.updatedAt());
        data.set(BetterHorseKeys.GROWTH_AVAILABILITY, PersistentDataType.STRING, availability.name());
    }

    public static Availability availability(PersistentDataContainer data) {
        String stored = data.get(BetterHorseKeys.GROWTH_AVAILABILITY, PersistentDataType.STRING);
        if (stored != null) {
            try { return Availability.valueOf(stored); }
            catch (IllegalArgumentException ignored) { }
        }
        return Availability.UNLOADED;
    }

    public static boolean hasProgress(PersistentDataContainer data) {
        return data.has(BetterHorseKeys.GROWTH_STAGE)
                || data.has(BetterHorseKeys.GROWTH_PROGRESS_MILLIS)
                || data.has(BetterHorseKeys.GROWTH_UPDATED_AT)
                || data.has(BetterHorseKeys.GROWTH_AVAILABILITY);
    }

    public static void clear(PersistentDataContainer data) {
        data.remove(BetterHorseKeys.GROWTH_STAGE);
        data.remove(BetterHorseKeys.GROWTH_PROGRESS_MILLIS);
        data.remove(BetterHorseKeys.GROWTH_UPDATED_AT);
        data.remove(BetterHorseKeys.GROWTH_AVAILABILITY);
    }

    /** Copy the clock without advancing it; the receiving lifecycle chooses its policy. */
    public static void copy(PersistentDataContainer source, PersistentDataContainer target) {
        copy(source, target, BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        copy(source, target, BetterHorseKeys.GROWTH_PROGRESS_MILLIS, PersistentDataType.LONG);
        copy(source, target, BetterHorseKeys.GROWTH_UPDATED_AT, PersistentDataType.LONG);
        copy(source, target, BetterHorseKeys.GROWTH_AVAILABILITY, PersistentDataType.STRING);
    }

    private static <P, C> void copy(PersistentDataContainer source, PersistentDataContainer target,
                                   org.bukkit.NamespacedKey key, PersistentDataType<P, C> type) {
        C value = source.get(key, type);
        if (value == null) target.remove(key);
        else target.set(key, type, value);
    }
}
