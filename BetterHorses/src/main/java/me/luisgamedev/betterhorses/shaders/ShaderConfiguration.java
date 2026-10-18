package me.luisgamedev.betterhorses.shaders;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.CollectionNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Shader selectors are literal YAML keys, including dots in placeholders and permissions. */
public final class ShaderConfiguration {
    private ShaderConfiguration() { }

    static ConfigurationSection loadRules(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u0001');
        config.load(file);
        ConfigurationSection feature = config.getConfigurationSection("postprocessing-shaders");
        ConfigurationSection rules = feature == null ? null : feature.getConfigurationSection("rules");
        return rules == null ? config.getConfigurationSection("postprocessing-shaders.rules") : rules;
    }

    /** Preserve literal selector keys when Bukkit's normal config updater writes new defaults. */
    public static void preserveRules(YamlConfiguration updated, File original)
            throws IOException, InvalidConfigurationException {
        ConfigurationSection rules = loadRules(original);
        if (rules != null) updated.set("postprocessing-shaders.rules", rules);
    }

    /** Save shader rule collections with braces while keeping other configuration sections in block form. */
    public static void saveWithBraces(YamlConfiguration config, File file) throws IOException {
        String contents = formatRules(config, config.saveToString());
        Files.createDirectories(file.toPath().toAbsolutePath().getParent());
        Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
    }

    private static String formatRules(YamlConfiguration config, String original) {
        try {
            LoaderOptions loader = new LoaderOptions();
            loader.setProcessComments(true);
            DumperOptions dumper = new DumperOptions();
            dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dumper.setPrettyFlow(false);
            dumper.setProcessComments(true);
            dumper.setIndent(config.options().indent());
            dumper.setWidth(config.options().width());
            Yaml yaml = new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
            Node document = yaml.compose(new StringReader(original));
            Node rules = child(child(document, "postprocessing-shaders"), "rules");
            if (rules == null) rules = child(document, "postprocessing-shaders.rules");
            if (!(rules instanceof CollectionNode<?>) || hasComments(rules, identitySet())) return original;

            useFlowStyle(rules, identitySet());
            StringWriter writer = new StringWriter();
            yaml.serialize(document, writer);
            String formatted = writer.toString();
            // Formatting is optional. Never save it if the emitted document changes any values.
            return Objects.equals(yaml.load(original), yaml.load(formatted)) ? formatted : original;
        } catch (RuntimeException exception) {
            // SnakeYAML versions differ in flow-comment support. Bukkit's output is the safe fallback.
            return original;
        }
    }

    private static Node child(Node parent, String key) {
        if (parent instanceof MappingNode mapping) {
            for (NodeTuple entry : mapping.getValue()) {
                if (entry.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key)) {
                    return entry.getValueNode();
                }
            }
        }
        return null;
    }

    private static boolean hasComments(Node node, Set<Node> seen) {
        if (!seen.add(node)) return false;
        if (node.getBlockComments() != null && !node.getBlockComments().isEmpty()
                || node.getInLineComments() != null && !node.getInLineComments().isEmpty()
                || node.getEndComments() != null && !node.getEndComments().isEmpty()) return true;
        if (node instanceof MappingNode mapping) {
            for (NodeTuple entry : mapping.getValue()) {
                if (hasComments(entry.getKeyNode(), seen) || hasComments(entry.getValueNode(), seen)) return true;
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node value : sequence.getValue()) if (hasComments(value, seen)) return true;
        }
        return false;
    }

    private static void useFlowStyle(Node node, Set<Node> seen) {
        if (!seen.add(node)) return;
        if (node instanceof CollectionNode<?> collection) collection.setFlowStyle(DumperOptions.FlowStyle.FLOW);
        if (node instanceof MappingNode mapping) {
            for (NodeTuple entry : mapping.getValue()) {
                useFlowStyle(entry.getKeyNode(), seen);
                useFlowStyle(entry.getValueNode(), seen);
            }
        } else if (node instanceof SequenceNode sequence) {
            for (Node value : sequence.getValue()) useFlowStyle(value, seen);
        }
    }

    private static Set<Node> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
