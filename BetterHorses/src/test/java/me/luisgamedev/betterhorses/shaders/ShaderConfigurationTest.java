package me.luisgamedev.betterhorses.shaders;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.CollectionNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class ShaderConfigurationTest {
    @Test void acceptsAndSavesTheNestedBraceExampleAsNativeYaml() throws Exception {
        String input = """
                postprocessing-shaders:
                  enabled: false
                  rules: {
                    Mounted: {
                      default: "mounted",
                      Gender: {Male: "maleshader", Female: "femaleshader"},
                      Mounttype: {
                        Skeleton_Horse: {
                          Traited: {
                            default: "shadertraited",
                            Hellmare: "fireshader",
                            Frosthooves: {
                              default: "testshader",
                              Placeholder: {"%isCold%": "coldonfrosthoovesshader"}
                            }
                          }
                        }
                      },
                      Placeholder: {"%inCombat%": "mountedandincombatshader"}
                    }
                  }
                """;
        String saved = save(load(input));
        assertEquals(values(input), values(saved));
        assertFlow(child(child(compose(saved), "postprocessing-shaders"), "rules"));
        assertTrue(saved.contains("rules: {"));
    }

    @Test void formatsEveryNestedRuleCollectionAndPreservesOtherBlockSections() throws Exception {
        YamlConfiguration config = load("""
                # configuration header
                outside:
                  # unchanged comment
                  nested: {value: "Hallo ö"}
                  empty: {}
                postprocessing-shaders:
                  rules:
                    Mounted:
                      default: [mounted, second]
                      Mounttype:
                        Skeleton_Horse:
                          Traited: {Frosthooves: frost}
                # following comment
                following:
                  nested: {value: end}
                """);
        String normal = config.saveToString();
        String saved = save(config);
        Node document = compose(saved);
        assertFlow(child(child(document, "postprocessing-shaders"), "rules"));
        assertEquals(DumperOptions.FlowStyle.BLOCK, ((MappingNode) child(document, "outside")).getFlowStyle());
        assertEquals(DumperOptions.FlowStyle.BLOCK, ((MappingNode) child(child(document, "outside"), "nested")).getFlowStyle());
        assertEquals(DumperOptions.FlowStyle.BLOCK, ((MappingNode) child(document, "following")).getFlowStyle());
        assertEquals(normal.substring(0, normal.indexOf("postprocessing-shaders:")),
                saved.substring(0, saved.indexOf("postprocessing-shaders:")));
        assertEquals(normal.substring(normal.indexOf("# following comment")),
                saved.substring(saved.indexOf("# following comment")));
        assertEquals(values(normal), values(saved));
    }

    @Test void preservesLiteralSelectorsResourceKeysAndActionLists() throws Exception {
        YamlConfiguration config = load("""
                postprocessing-shaders:
                  rules:
                    Mounted:
                      default: [custom:first, custom:second]
                      Permission:
                        betterhorses.admin: privileged
                      Placeholder:
                        '%world.name%':
                          'true': cold
                """);
        String saved = save(config);
        assertEquals(values(config.saveToString()), values(saved));
        assertTrue(saved.contains("betterhorses.admin"));
        assertTrue(saved.contains("%world.name%"));
        Path file = Files.createTempFile("betterhorses-literal-rules-", ".yml");
        try {
            Files.writeString(file, saved);
            ConfigurationSection mounted = ShaderConfiguration.loadRules(file.toFile()).getConfigurationSection("Mounted");
            assertEquals("privileged", mounted.getConfigurationSection("Permission").getString("betterhorses.admin"));
            assertEquals("cold", mounted.getConfigurationSection("Placeholder")
                    .getConfigurationSection("%world.name%").getString("true"));
            assertEquals(java.util.List.of("custom:first", "custom:second"), mounted.getStringList("default"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test void preservesEmptyRulesAndCreatesTheParentDirectory() throws Exception {
        String saved = save(load("postprocessing-shaders: {enabled: false, rules: {}}\n"));
        assertTrue(saved.contains("rules: {}"));
        assertFlow(child(child(compose(saved), "postprocessing-shaders"), "rules"));
    }

    @Test void preservesCommentedRulesThroughTheNormalBukkitFallback() throws Exception {
        for (String rules : java.util.List.of("""
                  rules:
                    Mounted:
                      # explanation
                      default: mounted
                """, """
                  rules:
                    Mounted:
                      default: mounted # inline explanation
                """)) {
            YamlConfiguration config = load("postprocessing-shaders:\n" + rules);
            assertEquals(config.saveToString(), save(config));
        }
    }

    @Test void supportsFlatLiteralRulesKeysWithoutChangingTheOtherSections() throws Exception {
        YamlConfiguration config = load("""
                other: {nested: value}
                postprocessing-shaders.rules:
                  Mounted: {default: mounted}
                """);
        String saved = save(config);
        assertFlow(child(compose(saved), "postprocessing-shaders.rules"));
        assertEquals(DumperOptions.FlowStyle.BLOCK, ((MappingNode) child(compose(saved), "other")).getFlowStyle());
        assertEquals(values(config.saveToString()), values(saved));
    }

    @Test void leavesMissingAndScalarRulesInNormalBukkitFormat() throws Exception {
        for (String input : java.util.List.of("", "other: value\n", "postprocessing-shaders: disabled\n",
                "postprocessing-shaders: {rules: disabled}\n", "postprocessing-shaders: {rules: null}\n")) {
            YamlConfiguration config = load(input);
            assertEquals(config.saveToString(), save(config));
        }
    }

    @Test void rejectsHybridBraceSyntaxWithoutOverwritingTheOriginalFile() throws Exception {
        String malformed = """
                postprocessing-shaders:
                  rules: {
                    Mounted:
                      default: "blur"
                  }
                """;
        Path original = Files.createTempFile("betterhorses-malformed-rules-", ".yml");
        try {
            Files.writeString(original, malformed);
            assertThrows(InvalidConfigurationException.class, () -> ShaderConfiguration.loadRules(original.toFile()));
            assertThrows(InvalidConfigurationException.class,
                    () -> ShaderConfiguration.preserveRules(load("postprocessing-shaders: {rules: {}}\n"), original.toFile()));
            assertEquals(malformed, Files.readString(original));
        } finally {
            Files.deleteIfExists(original);
        }
    }

    private static YamlConfiguration load(String input) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u0001');
        config.options().parseComments(true);
        config.loadFromString(input);
        return config;
    }

    private static String save(YamlConfiguration config) throws Exception {
        Path directory = Files.createTempDirectory("betterhorses-brace-rules-");
        Path target = directory.resolve("nested/config.yml");
        try {
            ShaderConfiguration.saveWithBraces(config, target.toFile());
            return Files.readString(target);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static Object values(String yaml) {
        return new Yaml().load(yaml);
    }

    private static Node compose(String yaml) {
        return new Yaml().compose(new StringReader(yaml));
    }

    private static Node child(Node node, String key) {
        if (node instanceof MappingNode mapping) {
            for (NodeTuple entry : mapping.getValue()) {
                if (entry.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key)) return entry.getValueNode();
            }
        }
        return null;
    }

    private static void assertFlow(Node node) {
        assertTrue(node instanceof CollectionNode<?>);
        assertEquals(DumperOptions.FlowStyle.FLOW, ((CollectionNode<?>) node).getFlowStyle());
        if (node instanceof MappingNode mapping) {
            for (NodeTuple entry : mapping.getValue()) {
                if (entry.getValueNode() instanceof CollectionNode<?>) assertFlow(entry.getValueNode());
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node value : sequence.getValue()) if (value instanceof CollectionNode<?>) assertFlow(value);
        }
    }
}
