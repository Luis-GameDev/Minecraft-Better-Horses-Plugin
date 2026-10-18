package me.luisgamedev.betterhorses.shaders;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Compiles nested configuration into independent, additive shader rules. Each
 * rule requires every ancestor condition; sibling rules never replace one another.
 */
public final class ShaderRuleEngine {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_RULES = 4096;
    private static final int MAX_NODES = 65536;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern SHADER_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Set<String> STATES = Set.of("mounted", "unmounted", "sneaking", "sprinting",
            "swimming", "gliding", "flying", "onfire", "frozen");
    private static final Set<String> GROUPS = Set.of("gender", "mounttype", "traited", "world", "biome",
            "weather", "environment", "gamemode", "permission", "placeholder", "owner", "growth", "neutered");
    private static final Map<String, Set<String>> VALUES = Map.of(
            "gender", Set.of("male", "female"),
            "mounttype", Set.of("horse", "skeletonhorse", "skeletonhorses", "zombiehorse", "zombiehorses",
                    "camel", "camels", "llama", "llamas", "mule", "mules", "donkey", "donkeys"),
            "weather", Set.of("dry", "rain", "thunder"),
            "environment", Set.of("normal", "nether", "theend", "custom"),
            "gamemode", Set.of("survival", "creative", "adventure", "spectator"),
            "owner", Set.of("self", "other", "none"),
            "growth", Set.of("baby", "adult"),
            "neutered", Set.of("yes", "no", "true", "false"));

    private final List<Rule> rules;
    private final Set<String> conditions;

    private ShaderRuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
        TreeSet<String> dependencies = new TreeSet<>();
        for (Rule rule : rules) {
            for (Condition condition : rule.conditions) dependencies.add(condition.name);
        }
        conditions = Collections.unmodifiableSet(dependencies);
    }

    public static ShaderRuleEngine compile(ConfigurationSection configuration, String namespace,
                                           Consumer<String> warning) {
        Compiler compiler = new Compiler(namespace, warning);
        if (configuration != null && compiler.validNamespace) {
            compiler.body(configuration, List.of(), "rules", 0);
        }
        compiler.reportWarnings();
        return new ShaderRuleEngine(compiler.rules);
    }

    public boolean isEmpty() { return rules.isEmpty(); }

    /** Normalized condition names used by valid compiled shader rules. */
    public Set<String> conditions() { return conditions; }

    /** Sorted output makes evaluation independent of configuration nesting and sibling order. */
    public Set<String> evaluate(ShaderContext context) {
        TreeSet<String> shaders = new TreeSet<>();
        for (Rule rule : rules) {
            boolean matches = true;
            for (Condition condition : rule.conditions) {
                if (!condition.predicate.test(context)) {
                    matches = false;
                    break;
                }
            }
            if (matches) shaders.add(rule.shader);
        }
        return Collections.unmodifiableSet(shaders);
    }

    private record Condition(String name, Predicate<ShaderContext> predicate) { }
    private record Rule(List<Condition> conditions, String shader) { }

    private static final class Compiler {
        private final String namespace;
        private final Consumer<String> warning;
        private final boolean validNamespace;
        private final List<Rule> rules = new ArrayList<>();
        private final Set<String> warnings = new LinkedHashSet<>();
        private int nodes;

        private Compiler(String namespace, Consumer<String> warning) {
            this.namespace = namespace == null ? "" : namespace.trim();
            this.warning = warning == null ? ignored -> { } : warning;
            validNamespace = NAMESPACE.matcher(this.namespace).matches();
            if (!validNamespace) warn("default-namespace", "must be a lowercase resource namespace");
        }

        private void body(Object value, List<Condition> conditions, String path, int depth) {
            if (!visit(path, depth)) return;
            if (value instanceof String shader) {
                shader(shader, conditions, path);
            } else if (value instanceof List<?> list) {
                for (int i = 0; i < list.size() && !exhausted(); i++) {
                    body(list.get(i), conditions, path + "[" + i + "]", depth + 1);
                }
            } else {
                Map<String, Object> map = mapping(value);
                if (map == null) {
                    warn(path, "expected a shader key, list, or nested conditions");
                    return;
                }
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (exhausted()) break;
                    String name = normalize(entry.getKey());
                    String childPath = path + "/" + entry.getKey();
                    if (name.equals("default")) body(entry.getValue(), conditions, childPath, depth + 1);
                    else condition(name, entry.getValue(), conditions, childPath, depth + 1);
                }
            }
        }

        private void condition(String condition, Object value, List<Condition> ancestors,
                               String path, int depth) {
            if (!visit(path, depth)) return;
            if (STATES.contains(condition)) {
                body(value, append(ancestors, condition, context -> context.matches(condition, "true")), path, depth);
                return;
            }
            if (!GROUPS.contains(condition)) {
                warn(path, "unknown condition");
                return;
            }
            if (condition.equals("traited")) {
                traited(value, append(ancestors, "traited", context -> context.matches("traited", "default")), path, depth);
                return;
            }
            Map<String, Object> selectors = mapping(value);
            if (selectors == null) {
                warn(path, "condition requires a map of values");
                return;
            }
            for (Map.Entry<String, Object> entry : selectors.entrySet()) {
                if (exhausted()) break;
                String selector = entry.getKey().trim();
                String childPath = path + "/" + entry.getKey();
                if (!visit(childPath, depth + 1)) continue;
                if (condition.equals("placeholder")) {
                    placeholder(selector, entry.getValue(), ancestors, childPath, depth + 1);
                } else if (validValue(condition, selector)) {
                    String expected = condition.equals("neutered") && normalize(selector).equals("true") ? "yes"
                            : condition.equals("neutered") && normalize(selector).equals("false") ? "no" : selector;
                    body(entry.getValue(), append(ancestors, condition, context -> context.matches(condition, expected)),
                            childPath, depth + 1);
                } else warn(childPath, "unsupported value for " + condition);
            }
        }

        private void traited(Object value, List<Condition> ancestors, String path, int depth) {
            Map<String, Object> map = mapping(value);
            if (map == null) {
                body(value, ancestors, path, depth + 1);
                return;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (exhausted()) break;
                String name = normalize(entry.getKey());
                String childPath = path + "/" + entry.getKey();
                if (!visit(childPath, depth + 1)) continue;
                if (name.equals("default")) body(entry.getValue(), ancestors, childPath, depth + 1);
                else if (STATES.contains(name) || GROUPS.contains(name)) {
                    condition(name, entry.getValue(), ancestors, childPath, depth + 1);
                } else if (!entry.getKey().isBlank() && !name.equals("none")) {
                    body(entry.getValue(), append(ancestors, "traited", context -> context.matches("traited", entry.getKey())),
                            childPath, depth + 1);
                } else warn(childPath, "expected a trait name or nested condition");
            }
        }

        private void placeholder(String expression, Object value, List<Condition> ancestors,
                                 String path, int depth) {
            if (!visit(path, depth)) return;
            if (expression.length() < 3 || !expression.startsWith("%") || !expression.endsWith("%")) {
                warn(path, "placeholder keys must be quoted expressions such as '%isCold%'");
                return;
            }
            Predicate<ShaderContext> truthy = context -> truthy(context.placeholder(expression), expression);
            Map<String, Object> map = mapping(value);
            if (map == null) {
                body(value, append(ancestors, "placeholder", truthy), path, depth + 1);
                return;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (exhausted()) break;
                String name = normalize(entry.getKey());
                String childPath = path + "/" + entry.getKey();
                if (!visit(childPath, depth + 1)) continue;
                if (name.equals("default")) {
                    body(entry.getValue(), append(ancestors, "placeholder", truthy), childPath, depth + 1);
                } else if (STATES.contains(name) || GROUPS.contains(name)) {
                    condition(name, entry.getValue(), append(ancestors, "placeholder", truthy), childPath, depth + 1);
                } else {
                    String expected = entry.getKey().trim();
                    if (expected.isEmpty()) {
                        warn(childPath, "placeholder comparisons cannot use an empty value");
                        continue;
                    }
                    body(entry.getValue(), append(ancestors, "placeholder", context -> {
                        String actual = resolved(context.placeholder(expression), expression);
                        return !actual.isEmpty() && actual.equalsIgnoreCase(expected);
                    }), childPath, depth + 1);
                }
            }
        }

        private void shader(String value, List<Condition> conditions, String path) {
            String shader = value.trim();
            if (!shader.contains(":")) shader = namespace + ":" + shader;
            if (!SHADER_KEY.matcher(shader).matches()) {
                warn(path, "invalid shader resource key '" + value + "'");
                return;
            }
            if (rules.size() >= MAX_RULES) {
                warn("rules", "exceeded the maximum of " + MAX_RULES + " shader rules");
                return;
            }
            rules.add(new Rule(List.copyOf(conditions), shader));
        }

        private boolean visit(String path, int depth) {
            if (depth > MAX_DEPTH) {
                warn(path, "exceeded the maximum nesting depth of " + MAX_DEPTH);
                return false;
            }
            if (++nodes > MAX_NODES) {
                warn("rules", "exceeded the maximum number of configuration nodes");
                return false;
            }
            return true;
        }

        private boolean exhausted() {
            if (rules.size() >= MAX_RULES) {
                warn("rules", "reached the maximum of " + MAX_RULES + " shader rules");
                return true;
            }
            return nodes > MAX_NODES;
        }

        private boolean validValue(String condition, String value) {
            if (value.isBlank()) return false;
            Set<String> allowed = VALUES.get(condition);
            return allowed == null || allowed.contains(normalize(value));
        }

        private void warn(String path, String message) {
            // Keep malformed configurations from producing an unbounded console message.
            if (warnings.size() < 16) warnings.add(path + ": " + message);
        }

        private void reportWarnings() {
            if (!warnings.isEmpty()) warning.accept("Ignored invalid postprocessing shader configuration: "
                    + String.join("; ", warnings));
        }
    }

    private static List<Condition> append(List<Condition> conditions, String name,
                                          Predicate<ShaderContext> next) {
        List<Condition> copy = new ArrayList<>(conditions);
        copy.add(new Condition(name, next));
        return copy;
    }

    private static Map<String, Object> mapping(Object value) {
        if (value instanceof ConfigurationSection section) return section.getValues(false);
        if (!(value instanceof Map<?, ?> map)) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
        return result;
    }

    private static boolean truthy(String value, String expression) {
        return Set.of("true", "yes", "on", "1").contains(resolved(value, expression).toLowerCase(Locale.ROOT));
    }

    private static String resolved(String value, String expression) {
        if (value == null || value.trim().equals(expression)) return "";
        return value.trim();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }
}
