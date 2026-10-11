package me.luisgamedev.betterhorses.shaders;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostEffectAccessTest {
    @Test void acceptsOnlyTheSupportedMinecraftVersionFamily() {
        for (String version : List.of("26.3", "26.3.1", "26.3.0-R0.1-SNAPSHOT", "26.3-R0.1-SNAPSHOT",
                "26.3.build.157-beta", "26.3.build.158-stable", "26.3.local-SNAPSHOT",
                "26.3.1.build.1-beta")) {
            assertTrue(PostEffectAccess.detect(FakePlayer.class, version).isPresent(), version);
        }
        for (String version : List.of("1.20.6-R0.1-SNAPSHOT", "26.2-R0.1-SNAPSHOT", "26.30",
                "26.3x", "26.4", "27.3", "26.3-snapshot-3", "prefix26.3", "26.3 ", "",
                "26.30.build.157-beta", "26.2.build.157-beta", "26.4.build.157-beta",
                "26.3.build.beta", "26.3.build.157-beta-extra", "26.3.local-SNAPSHOT-extra")) {
            assertTrue(PostEffectAccess.detect(FakePlayer.class, version).isEmpty(), version);
        }
        assertTrue(PostEffectAccess.detect(FakePlayer.class, null).isEmpty());
        assertTrue(PostEffectAccess.detect(null, "26.3").isEmpty());
    }

    @Test void detectsMissingOrIncompatibleMethodsWithoutLoadingNewServerClasses() {
        for (Class<?> playerType : List.of(Object.class, StaticPlayer.class, PrimitivePlayer.class,
                BadValuesPlayer.class, BadRemovePlayer.class, BadFactoryPlayer.class,
                BadStringPlayer.class, BadUpdatePlayer.class, AmbiguousPlayer.class)) {
            assertTrue(PostEffectAccess.detect(playerType, "26.3").isEmpty(), playerType.getName());
        }
    }

    @Test void usesTheServersKeyFactoryAndPreservesAllEffectsAndTheirOrder() throws Exception {
        PostEffectAccess access = PostEffectAccess.detect(FakePlayer.class, "26.3").orElseThrow();
        FakePlayer player = new FakePlayer();
        player.effects.add(ServerKey.key("other:weather"));
        assertTrue(access.add(player, "betterhorses:mounted"));
        assertTrue(access.add(player, "betterhorses:fire"));
        assertEquals(List.of("other:weather", "betterhorses:mounted", "betterhorses:fire"), access.values(player));
        assertTrue(access.remove(player, "betterhorses:mounted"));
        assertEquals(List.of("other:weather", "betterhorses:fire"), access.values(player));
        assertEquals(ServerKeyImpl.class, player.effects.keys.get(1).getClass());
    }

    @Test void returnsTheServersAddAndRemoveResults() throws Exception {
        PostEffectAccess access = PostEffectAccess.detect(FakePlayer.class, "26.3").orElseThrow();
        FakePlayer player = new FakePlayer();
        assertTrue(access.add(player, "mounted"));
        assertFalse(access.add(player, "minecraft:mounted"));
        assertFalse(access.remove(player, "betterhorses:unknown"));
        assertTrue(access.remove(player, "minecraft:mounted"));
        assertTrue(access.values(player).isEmpty());
    }

    @Test void returnsAnImmutableSnapshot() throws Exception {
        PostEffectAccess access = PostEffectAccess.detect(FakePlayer.class, "26.3").orElseThrow();
        FakePlayer player = new FakePlayer();
        access.add(player, "mounted");
        List<String> before = access.values(player);
        assertThrows(UnsupportedOperationException.class, () -> before.add("other:effect"));
        access.add(player, "fire");
        assertEquals(List.of("minecraft:mounted"), before);
    }

    @Test void supportsTheInitialApiWithoutUpdate() throws Exception {
        PostEffectAccess access = PostEffectAccess.detect(InitialPlayer.class, "26.3").orElseThrow();
        InitialPlayer player = new InitialPlayer();
        assertTrue(access.add(player, "mounted"));
        access.flush(player);
        assertEquals(List.of("minecraft:mounted"), access.values(player));
    }

    @Test void flushesOnlyWhenRequestedAndTheOptionalApiExists() throws Exception {
        PostEffectAccess access = PostEffectAccess.detect(FakePlayer.class, "26.3").orElseThrow();
        FakePlayer player = new FakePlayer();
        access.add(player, "mounted");
        access.values(player);
        access.remove(player, "mounted");
        assertEquals(0, player.effects.updates);
        access.flush(player);
        assertEquals(1, player.effects.updates);
    }

    @Test void propagatesFactoryAndServerFailuresAsReflectionFailures() throws Exception {
        PostEffectAccess access = PostEffectAccess.detect(FakePlayer.class, "26.3").orElseThrow();
        FakePlayer player = new FakePlayer();
        assertThrows(InvocationTargetException.class, () -> access.add(player, "invalid key"));
        player.effects.fail = true;
        assertThrows(InvocationTargetException.class, () -> access.values(player));
        assertThrows(InvocationTargetException.class, () -> access.add(player, "mounted"));
        assertThrows(InvocationTargetException.class, () -> access.remove(player, "mounted"));
        assertThrows(InvocationTargetException.class, () -> access.flush(player));
    }

    @Test void rejectsInvalidRuntimeValuesBeforeTheyReachTheEffectState() throws Exception {
        PostEffectAccess access = PostEffectAccess.detect(InvalidValuesPlayer.class, "26.3").orElseThrow();
        InvalidValuesPlayer player = new InvalidValuesPlayer();
        assertThrows(ReflectiveOperationException.class, () -> access.values(player));
        player.effects.invalidValues = null;
        assertThrows(ReflectiveOperationException.class, () -> access.values(player));
        player.effects.invalidValues = List.of((ServerKey) () -> null);
        assertThrows(ReflectiveOperationException.class, () -> access.values(player));
    }

    @Test void rejectsNullEffectHandles() {
        PostEffectAccess access = PostEffectAccess.detect(NullPlayer.class, "26.3").orElseThrow();
        NullPlayer player = new NullPlayer();
        assertThrows(ReflectiveOperationException.class, () -> access.values(player));
        assertThrows(ReflectiveOperationException.class, () -> access.add(player, "mounted"));
        assertThrows(ReflectiveOperationException.class, () -> access.remove(player, "mounted"));
        assertThrows(ReflectiveOperationException.class, () -> access.flush(player));
    }

    public interface ServerKey {
        static ServerKey key(String value) {
            if (value.contains(" ")) throw new IllegalArgumentException("invalid key");
            return new ServerKeyImpl(value.contains(":") ? value : "minecraft:" + value);
        }
        String asString();
    }

    // A non-public implementation proves reflection uses the public API key type.
    private record ServerKeyImpl(String asString) implements ServerKey { }

    public static class InitialEffects {
        final List<ServerKey> keys = new ArrayList<>();
        boolean fail;

        public List<ServerKey> values() { check(); return List.copyOf(keys); }
        public boolean add(ServerKey key) {
            check();
            if (keys.stream().anyMatch(existing -> existing.asString().equals(key.asString()))) return false;
            keys.add(key);
            return true;
        }
        public boolean remove(ServerKey key) {
            check();
            return keys.removeIf(existing -> existing.asString().equals(key.asString()));
        }
        public void clear() { throw new AssertionError("must preserve other effects"); }
        public void set(List<ServerKey> keys) { throw new AssertionError("must preserve other effects"); }
        void check() { if (fail) throw new IllegalStateException("server failure"); }
    }

    public static final class FakeEffects extends InitialEffects {
        int updates;
        public void update() { check(); updates++; }
    }

    public static final class FakePlayer {
        final FakeEffects effects = new FakeEffects();
        public FakeEffects postEffects() { return effects; }
    }

    public static final class InitialPlayer {
        final InitialEffects effects = new InitialEffects();
        public InitialEffects postEffects() { return effects; }
    }

    public static final class NullPlayer {
        public FakeEffects postEffects() { return null; }
    }

    public static final class InvalidValuesEffects extends InitialEffects {
        @SuppressWarnings("unchecked")
        List<ServerKey> invalidValues = (List<ServerKey>) (List<?>) List.of("wrong key type");
        @Override public List<ServerKey> values() { return invalidValues; }
    }

    public static final class InvalidValuesPlayer {
        final InvalidValuesEffects effects = new InvalidValuesEffects();
        public InvalidValuesEffects postEffects() { return effects; }
    }

    public static final class StaticPlayer {
        public static FakeEffects postEffects() { return new FakeEffects(); }
    }
    public static final class PrimitivePlayer {
        public int postEffects() { return 0; }
    }
    public static final class BadValuesEffects {
        public String values() { return ""; }
        public boolean add(ServerKey key) { return false; }
        public boolean remove(ServerKey key) { return false; }
    }
    public static final class BadValuesPlayer {
        public BadValuesEffects postEffects() { return null; }
    }
    public static final class BadRemoveEffects {
        public List<ServerKey> values() { return List.of(); }
        public boolean add(ServerKey key) { return false; }
        public void remove(ServerKey key) { }
    }
    public static final class BadRemovePlayer {
        public BadRemoveEffects postEffects() { return null; }
    }
    public static final class BadFactoryKey {
        public static String key(String value) { return value; }
        public String asString() { return ""; }
    }
    public static final class BadFactoryEffects {
        public List<BadFactoryKey> values() { return List.of(); }
        public boolean add(BadFactoryKey key) { return false; }
        public boolean remove(BadFactoryKey key) { return false; }
    }
    public static final class BadFactoryPlayer {
        public BadFactoryEffects postEffects() { return null; }
    }
    public static final class BadStringKey {
        public static BadStringKey key(String value) { return new BadStringKey(); }
        public int asString() { return 0; }
    }
    public static final class BadStringEffects {
        public List<BadStringKey> values() { return List.of(); }
        public boolean add(BadStringKey key) { return false; }
        public boolean remove(BadStringKey key) { return false; }
    }
    public static final class BadStringPlayer {
        public BadStringEffects postEffects() { return null; }
    }
    public static final class BadUpdateEffects extends InitialEffects {
        public boolean update() { return false; }
    }
    public static final class BadUpdatePlayer {
        public BadUpdateEffects postEffects() { return null; }
    }
    public static final class SecondKey {
        public static SecondKey key(String value) { return new SecondKey(); }
        public String asString() { return ""; }
    }
    public static final class AmbiguousEffects extends InitialEffects {
        public boolean add(SecondKey key) { return false; }
        public boolean remove(SecondKey key) { return false; }
    }
    public static final class AmbiguousPlayer {
        public AmbiguousEffects postEffects() { return null; }
    }
}
