package me.luisgamedev.betterhorses.shaders;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShaderEffectStateTest {
    @Test void enablesEveryMatchingEffectOnceInDeterministicOrder() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects();
        state.synchronize(List.of("mounted", "fire", "cold", "fire"), effects);
        assertEquals(List.of("cold", "fire", "mounted"), effects.keys);
        assertEquals(3, effects.additions.size());
        state.synchronize(List.of("fire", "mounted", "cold"), effects);
        assertEquals(3, effects.additions.size());
        assertTrue(effects.removals.isEmpty());
    }

    @Test void preservesPreexistingEffectsIncludingMatchingSharedKeys() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects("other:weather", "mounted");
        state.synchronize(List.of("mounted", "fire"), effects);
        assertEquals(List.of("other:weather", "mounted", "fire"), effects.keys);
        state.synchronize(List.of(), effects);
        assertEquals(List.of("other:weather", "mounted"), effects.keys);
        assertEquals(List.of("fire"), effects.removals);
    }

    @Test void removesOnlyConditionsThatNoLongerMatch() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects("external");
        state.synchronize(List.of("fire", "mounted", "cold"), effects);
        state.synchronize(List.of("mounted", "cold"), effects);
        assertEquals(List.of("external", "cold", "mounted"), effects.keys);
        assertEquals(List.of("fire"), effects.removals);
    }

    @Test void restoresAnExternallyRemovedMatchingEffect() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects();
        state.synchronize(List.of("mounted"), effects);
        effects.keys.remove("mounted");
        state.synchronize(List.of("mounted"), effects);
        assertEquals(List.of("mounted", "mounted"), effects.additions);
        state.clear(effects);
        assertTrue(effects.keys.isEmpty());
    }

    @Test void reordersOnlyOwnedEffectsWhenNewConditionsAddEarlierKeys() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects("external:first");
        state.synchronize(List.of("mounted"), effects);
        effects.keys.add("external:last");
        state.synchronize(List.of("fire", "mounted"), effects);
        assertEquals(List.of("external:first", "external:last", "fire", "mounted"), effects.keys);
        assertFalse(effects.removals.contains("external:first"));
        assertFalse(effects.removals.contains("external:last"));
        int additions = effects.additions.size();
        int removals = effects.removals.size();
        state.synchronize(List.of("mounted", "fire"), effects);
        assertEquals(additions, effects.additions.size());
        assertEquals(removals, effects.removals.size());
    }

    @Test void clearsSuccessfulAddsAfterALaterAdditionThrows() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects("external");
        effects.throwOnAdd = "mounted";
        assertThrows(ReflectiveOperationException.class,
                () -> state.synchronize(List.of("fire", "mounted"), effects));
        assertEquals(List.of("external", "fire"), effects.keys);
        state.clear(effects);
        assertEquals(List.of("external"), effects.keys);
    }

    @Test void retainsOwnershipWhenRemovalThrowsAndRetriesCleanup() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects("external");
        state.synchronize(List.of("fire", "mounted"), effects);
        effects.throwOnRemove = "mounted";
        assertThrows(ReflectiveOperationException.class, () -> state.clear(effects));
        assertEquals(List.of("external", "mounted"), effects.keys);
        effects.throwOnRemove = null;
        state.clear(effects);
        assertEquals(List.of("external"), effects.keys);
    }

    @Test void doesNotClaimAnUnsuccessfulAddition() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects();
        effects.rejectAdd = "mounted";
        state.synchronize(List.of("mounted"), effects);
        // An external provider adds the same effect after our failed attempt.
        effects.keys.add("mounted");
        state.clear(effects);
        assertEquals(List.of("mounted"), effects.keys);
        assertTrue(effects.removals.isEmpty());
    }

    @Test void retainsOwnershipWhenRemovalReturnsFalse() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects();
        state.synchronize(List.of("mounted"), effects);
        effects.rejectRemove = "mounted";
        state.clear(effects);
        assertEquals(List.of("mounted"), effects.keys);
        effects.rejectRemove = null;
        state.clear(effects);
        assertTrue(effects.keys.isEmpty());
    }

    @Test void recoversWhenReorderingFailsAfterASuccessfulRemoval() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects("external");
        state.synchronize(List.of("mounted"), effects);
        effects.throwOnRemove = "fire";
        assertThrows(ReflectiveOperationException.class,
                () -> state.synchronize(List.of("fire", "mounted"), effects));
        assertEquals(List.of("external", "fire"), effects.keys);
        effects.throwOnRemove = null;
        state.synchronize(List.of("mounted", "fire"), effects);
        assertEquals(List.of("external", "fire", "mounted"), effects.keys);
        state.clear(effects);
        assertEquals(List.of("external"), effects.keys);
    }

    @Test void forgetsOwnershipForEffectsAlreadyRemovedByAnotherProvider() throws Exception {
        ShaderEffectState state = new ShaderEffectState();
        FakeEffects effects = new FakeEffects();
        state.synchronize(List.of("mounted"), effects);
        effects.keys.clear();
        state.clear(effects);
        effects.keys.add("mounted");
        state.clear(effects);
        assertEquals(List.of("mounted"), effects.keys);
        assertTrue(effects.removals.isEmpty());
    }

    private static final class FakeEffects implements ShaderEffectState.Effects {
        private final List<String> keys = new ArrayList<>();
        private final List<String> additions = new ArrayList<>();
        private final List<String> removals = new ArrayList<>();
        private String throwOnAdd;
        private String throwOnRemove;
        private String rejectAdd;
        private String rejectRemove;

        private FakeEffects(String... initial) {
            keys.addAll(Arrays.asList(initial));
        }

        @Override public List<String> values() {
            return new ArrayList<>(keys);
        }

        @Override public boolean add(String key) throws ReflectiveOperationException {
            if (key.equals(throwOnAdd)) {
                throw new ReflectiveOperationException("addition failed");
            }
            if (key.equals(rejectAdd) || keys.contains(key)) {
                return false;
            }
            keys.add(key);
            additions.add(key);
            return true;
        }

        @Override public boolean remove(String key) throws ReflectiveOperationException {
            if (key.equals(throwOnRemove)) {
                throw new ReflectiveOperationException("removal failed");
            }
            if (key.equals(rejectRemove)) {
                return false;
            }
            boolean removed = keys.remove(key);
            if (removed) {
                removals.add(key);
            }
            return removed;
        }
    }
}
