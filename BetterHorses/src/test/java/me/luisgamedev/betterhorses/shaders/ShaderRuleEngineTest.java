package me.luisgamedev.betterhorses.shaders;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderRuleEngineTest {
    @Test void matchesAllExampleShadersIncludingConditionalFrosthooves() throws Exception {
        ShaderRuleEngine engine = compile("""
                Mounted:
                  default: mounted
                  Gender:
                    Male: maleshader
                    Female: femaleshader
                  Mounttype:
                    Skeleton_Horse:
                      Traited:
                        default: shadertraited
                        Hellmare: fireshader
                        Frosthooves:
                          default: testshader
                          Placeholder:
                            '%isCold%': coldonfrosthoovesshader
                  Placeholder:
                    '%inCombat%': mountedandincombatshader
                """);
        Facts facts = new Facts().state("mounted").value("gender", "Male")
                .value("mounttype", "Skeleton_Horse").value("traited", "Frosthooves")
                .placeholder("%isCold%", "true").placeholder("%inCombat%", "true");
        assertEquals(keys("mounted", "maleshader", "shadertraited", "testshader",
                "coldonfrosthoovesshader", "mountedandincombatshader"), engine.evaluate(facts));
        facts.placeholder("%isCold%", "false");
        assertEquals(keys("mounted", "maleshader", "shadertraited", "testshader", "mountedandincombatshader"),
                engine.evaluate(facts));
        facts.value("traited", "Hellmare");
        assertEquals(keys("mounted", "maleshader", "shadertraited", "fireshader", "mountedandincombatshader"),
                engine.evaluate(facts));
        facts.value("mounttype", "Horse");
        assertEquals(keys("mounted", "maleshader", "mountedandincombatshader"), engine.evaluate(facts));
        facts.state("mounted", false);
        assertEquals(Set.of(), engine.evaluate(facts));
    }

    @Test void swappingEveryAncestorConditionPreservesTheSameResult() throws Exception {
        List<String> clauses = List.of("Mounted", "Mounttype|Skeleton_Horse", "Traited|Frosthooves",
                "Gender|Female", "Placeholder|%isCold%");
        List<List<String>> orders = new ArrayList<>();
        permutations(new ArrayList<>(clauses), 0, orders);
        List<ShaderRuleEngine> engines = new ArrayList<>();
        for (List<String> order : orders) engines.add(compile(nested(order, "matched")));
        assertEquals(120, engines.size());
        for (int mask = 0; mask < 32; mask++) {
            Facts facts = new Facts().state("mounted", (mask & 1) != 0)
                    .value("mounttype", (mask & 2) != 0 ? "Skeleton_Horse" : "Horse")
                    .value("traited", (mask & 4) != 0 ? "Frosthooves" : "Hellmare")
                    .value("gender", (mask & 8) != 0 ? "Female" : "Male")
                    .placeholder("%isCold%", (mask & 16) != 0 ? "true" : "false");
            Set<String> expected = mask == 31 ? keys("matched") : Set.of();
            for (ShaderRuleEngine engine : engines) assertEquals(expected, engine.evaluate(facts));
        }
    }

    @Test void traitedDefaultRequiresATraitAndCanWrapOtherConditions() throws Exception {
        ShaderRuleEngine first = compile("""
                Traited:
                  default: anytrait
                  Mounttype:
                    Skeleton_Horse: skeletontrait
                """);
        ShaderRuleEngine second = compile("""
                Traited:
                  default: anytrait
                Mounttype:
                  Skeleton_Horse:
                    Traited:
                      default: skeletontrait
                """);
        Facts facts = new Facts().value("mounttype", "Skeleton_Horse");
        for (String trait : List.of("", "none", "Hellmare", "Frosthooves")) {
            facts.value("traited", trait);
            Set<String> expected = trait.isEmpty() || trait.equals("none") ? Set.of() : keys("anytrait", "skeletontrait");
            assertEquals(expected, first.evaluate(facts));
            assertEquals(expected, second.evaluate(facts));
        }
    }

    @Test void duplicateKeysListsAndIndependentRuleMapsRemainAdditiveAndSorted() throws Exception {
        ShaderRuleEngine engine = compile("""
                default:
                  - custom:z
                  - custom:a
                  - custom:z
                  - Mounted:
                      default: [custom:a, custom:b]
                  - Sneaking: custom:c
                Mounted:
                  default: custom:z
                """);
        Set<String> actual = engine.evaluate(new Facts().state("mounted").state("sneaking"));
        assertEquals(List.of("custom:a", "custom:b", "custom:c", "custom:z"), new ArrayList<>(actual));
        assertEquals(Set.of("custom:a", "custom:z"), engine.evaluate(new Facts()));
    }

    @Test void placeholdersSupportTruthyDefaultsAndSpecificResolvedValues() throws Exception {
        ShaderRuleEngine engine = compile("""
                Placeholder:
                  '%condition%':
                    default: truthy
                    'true': explicittrue
                    'false': explicitfalse
                    cold: explicitcold
                    Mounted: truthyandmounted
                """);
        Facts facts = new Facts().state("mounted");
        assertEquals(Set.of(), engine.evaluate(facts));
        facts.placeholder("%condition%", "%condition%");
        assertEquals(Set.of(), engine.evaluate(facts));
        facts.placeholder("%condition%", "true");
        assertEquals(keys("truthy", "explicittrue", "truthyandmounted"), engine.evaluate(facts));
        for (String result : List.of("yes", "on", "1", "  YES  ")) {
            facts.placeholder("%condition%", result);
            assertEquals(keys("truthy", "truthyandmounted"), engine.evaluate(facts));
        }
        facts.placeholder("%condition%", "false");
        assertEquals(keys("explicitfalse"), engine.evaluate(facts));
        facts.placeholder("%condition%", "Cold");
        assertEquals(keys("explicitcold"), engine.evaluate(facts));
        facts.placeholder("%condition%", "unrelated");
        assertEquals(Set.of(), engine.evaluate(facts));
    }

    @Test void literalDottedPermissionsAndPlaceholderExpressionsArePreserved() throws Exception {
        ShaderRuleEngine engine = compile("""
                Permission:
                  betterhorses.shaders.special:
                    Placeholder:
                      '%weather.cold%': custom:dotted
                """);
        Facts facts = new Facts().value("permission", "betterhorses.shaders.special")
                .placeholder("%weather.cold%", "true");
        assertEquals(Set.of("custom:dotted"), engine.evaluate(facts));
        facts.value("permission", "betterhorses.shaders.other");
        assertEquals(Set.of(), engine.evaluate(facts));
    }

    @Test void usefulStateAndValueConditionsUseIndependentAncestorChecks() throws Exception {
        List<String> conditions = List.of("Unmounted", "Sneaking", "Sprinting", "Swimming", "Gliding", "Flying",
                "OnFire", "Frozen", "World|world", "Biome|SNOWY_PLAINS", "Weather|Thunder",
                "Environment|THE_END", "GameMode|Survival", "Permission|example.permission",
                "Owner|Other", "Growth|Baby", "Neutered|Yes");
        for (String condition : conditions) {
            ShaderRuleEngine engine = compile(nested(List.of(condition), "state"));
            Facts facts = new Facts();
            String[] parts = condition.split("\\|", 2);
            if (parts.length == 1) facts.state(parts[0]);
            else facts.value(parts[0], parts[1]);
            assertEquals(keys("state"), engine.evaluate(facts));
            assertEquals(Set.of(), engine.evaluate(new Facts()));
        }
        ShaderRuleEngine booleans = compile("""
                Neutered:
                  true: neutered
                  false: fertile
                """);
        assertEquals(keys("neutered"), booleans.evaluate(new Facts().value("neutered", "Yes")));
        assertEquals(keys("fertile"), booleans.evaluate(new Facts().value("neutered", "No")));
    }

    @Test void invalidBranchesFailClosedWhileValidSiblingsStillWork() throws Exception {
        List<String> warnings = new ArrayList<>();
        ShaderRuleEngine engine = compile("""
                Mounted: valid
                UnknownCondition: bad
                Gender:
                  Robot: badgender
                Mounttype:
                  Unicorn: badmount
                Weather:
                  Tornado: badweather
                Placeholder:
                  unquotedexpression: badplaceholder
                Owner: notamap
                default: [INVALID, custom:valid, 'bad namespace:bad', 42]
                """, "minecraft", warnings);
        assertEquals(Set.of("minecraft:valid", "custom:valid"), engine.evaluate(new Facts().state("mounted")));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("UnknownCondition"));
        assertTrue(warnings.get(0).contains("invalid shader resource key"));
        assertTrue(warnings.get(0).contains("unsupported value"));
    }

    @Test void namespaceValidationAndEmptyRulesFailClosed() throws Exception {
        List<String> warnings = new ArrayList<>();
        ShaderRuleEngine engine = compile("Mounted: mounted", "custom_namespace", warnings);
        assertEquals(Set.of("custom_namespace:mounted"), engine.evaluate(new Facts().state("mounted")));
        assertFalse(engine.isEmpty());
        assertTrue(warnings.isEmpty());
        ShaderRuleEngine invalid = compile("Mounted: mounted", "Bad Namespace", warnings);
        assertTrue(invalid.isEmpty());
        assertEquals(Set.of(), invalid.evaluate(new Facts().state("mounted")));
        assertEquals(1, warnings.size());
        assertTrue(ShaderRuleEngine.compile(null, "minecraft", warnings::add).isEmpty());
        assertTrue(compile("{}").isEmpty());
    }

    @Test void depthAndRuleLimitsBoundCompilationWithoutEnablingInvalidBranches() throws Exception {
        String deep = nested(java.util.Collections.nCopies(40, "Mounted"), "too_deep");
        List<String> warnings = new ArrayList<>();
        ShaderRuleEngine engine = compile(deep + "Sprinting: valid\n", "minecraft", warnings);
        assertEquals(keys("valid"), engine.evaluate(new Facts().state("mounted").state("sprinting")));
        assertTrue(warnings.get(0).contains("maximum nesting depth"));
        StringBuilder yaml = new StringBuilder("default:\n");
        for (int i = 0; i < 4100; i++) yaml.append("  - custom:shader_").append(i).append('\n');
        warnings.clear();
        Set<String> result = compile(yaml.toString(), "minecraft", warnings).evaluate(new Facts());
        assertEquals(4096, result.size());
        assertFalse(result.contains("custom:shader_4096"));
        assertTrue(warnings.get(0).contains("4096"));
    }

    private static ShaderRuleEngine compile(String yaml) throws Exception {
        List<String> warnings = new ArrayList<>();
        ShaderRuleEngine engine = compile(yaml, "minecraft", warnings);
        assertEquals(List.of(), warnings);
        return engine;
    }

    private static ShaderRuleEngine compile(String yaml, String namespace, List<String> warnings) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().pathSeparator('\u0001');
        configuration.loadFromString(yaml);
        return ShaderRuleEngine.compile(configuration, namespace, warnings::add);
    }

    private static Set<String> keys(String... keys) {
        TreeSet<String> namespaced = new TreeSet<>();
        for (String key : keys) namespaced.add("minecraft:" + key);
        return namespaced;
    }

    private static String nested(List<String> conditions, String shader) {
        StringBuilder yaml = new StringBuilder();
        int indent = 0;
        for (String condition : conditions) {
            String[] parts = condition.split("\\|", 2);
            yaml.append(" ".repeat(indent)).append(parts[0]).append(":\n");
            indent += 2;
            if (parts.length > 1) {
                yaml.append(" ".repeat(indent)).append('\'').append(parts[1]).append("':\n");
                indent += 2;
            }
        }
        return yaml.append(" ".repeat(indent)).append("default: ").append(shader).append('\n').toString();
    }

    private static void permutations(List<String> values, int index, List<List<String>> result) {
        if (index == values.size()) {
            result.add(List.copyOf(values));
            return;
        }
        for (int i = index; i < values.size(); i++) {
            java.util.Collections.swap(values, index, i);
            permutations(values, index + 1, result);
            java.util.Collections.swap(values, index, i);
        }
    }

    private static final class Facts implements ShaderContext {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, String> placeholders = new HashMap<>();

        Facts state(String condition) { return state(condition, true); }
        Facts state(String condition, boolean enabled) { return value(condition, Boolean.toString(enabled)); }
        Facts value(String condition, String value) { values.put(normalize(condition), value); return this; }
        Facts placeholder(String expression, String value) { placeholders.put(expression, value); return this; }

        @Override public boolean matches(String condition, String value) {
            String actual = values.get(normalize(condition));
            if (condition.equals("traited") && value.equals("default")) {
                return actual != null && !actual.isBlank() && !actual.equalsIgnoreCase("none");
            }
            return actual != null && normalize(actual).equals(normalize(value));
        }

        @Override public String placeholder(String expression) { return placeholders.getOrDefault(expression, ""); }

        private static String normalize(String value) {
            return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        }
    }
}
