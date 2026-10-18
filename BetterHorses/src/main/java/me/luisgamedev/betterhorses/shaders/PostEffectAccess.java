package me.luisgamedev.betterhorses.shaders;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Optional access to Paper's Minecraft 26.3 post-effects API.
 *
 * The effect key type is obtained from the server API itself. This matters because
 * the plugin shades Adventure: its own Key objects are not the server's Key objects.
 * No new Paper or Adventure classes are linked when running on an older server.
 */
public final class PostEffectAccess {
    // Paper's API version includes .build.<number>-<channel>, or .local-SNAPSHOT.
    private static final Pattern SUPPORTED_VERSION = Pattern.compile(
            "26\\.3(?:\\.\\d+)*(?:-R\\d+(?:\\.\\d+)*(?:-[A-Za-z0-9._-]+)?"
                    + "|\\.build\\.\\d+-[a-z]+|\\.local-SNAPSHOT)?");

    private final Method postEffects;
    private final Method values;
    private final Method add;
    private final Method remove;
    private final Method keyFactory;
    private final Method keyString;
    private final Method update;
    private final Class<?> keyType;

    private PostEffectAccess(Method postEffects, Method values, Method add, Method remove,
                             Method keyFactory, Method keyString, Method update) {
        this.postEffects = postEffects;
        this.values = values;
        this.add = add;
        this.remove = remove;
        this.keyFactory = keyFactory;
        this.keyString = keyString;
        this.update = update;
        keyType = add.getParameterTypes()[0];
    }

    /** Returns empty for unsupported versions or an absent/incompatible API. */
    public static Optional<PostEffectAccess> detect(Class<?> playerType, String bukkitVersion) {
        if (playerType == null || bukkitVersion == null
                || !SUPPORTED_VERSION.matcher(bukkitVersion).matches()) {
            return Optional.empty();
        }
        try {
            Method postEffects = playerType.getMethod("postEffects");
            if (!instanceMethod(postEffects) || postEffects.getReturnType().isPrimitive()) {
                return Optional.empty();
            }
            Class<?> effectsType = postEffects.getReturnType();
            Method values = effectsType.getMethod("values");
            if (!instanceMethod(values) || !List.class.isAssignableFrom(values.getReturnType())) {
                return Optional.empty();
            }

            Method add = null;
            Method keyFactory = null;
            Method keyString = null;
            for (Method candidate : effectsType.getMethods()) {
                if (!candidate.getName().equals("add") || !instanceMethod(candidate)
                        || candidate.getParameterCount() != 1 || candidate.getReturnType() != boolean.class) {
                    continue;
                }
                Class<?> keyType = candidate.getParameterTypes()[0];
                Method factory;
                Method string;
                try {
                    factory = keyType.getMethod("key", String.class);
                    string = keyType.getMethod("asString");
                } catch (NoSuchMethodException ignored) {
                    continue;
                }
                if (!Modifier.isStatic(factory.getModifiers())
                        || !keyType.isAssignableFrom(factory.getReturnType())
                        || !instanceMethod(string) || string.getReturnType() != String.class) {
                    continue;
                }
                // An ambiguous overload must not select a different API by accident.
                if (add != null) return Optional.empty();
                add = candidate;
                keyFactory = factory;
                keyString = string;
            }
            if (add == null) return Optional.empty();
            Method remove = effectsType.getMethod("remove", add.getParameterTypes()[0]);
            if (!instanceMethod(remove) || remove.getReturnType() != boolean.class) {
                return Optional.empty();
            }

            Method update = null;
            try {
                update = effectsType.getMethod("update");
                if (!instanceMethod(update) || update.getReturnType() != void.class) {
                    return Optional.empty();
                }
            } catch (NoSuchMethodException ignored) {
                // The initial 26.3 API synchronizes at the end of the player tick.
            }
            return Optional.of(new PostEffectAccess(postEffects, values, add, remove,
                    keyFactory, keyString, update));
        } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    /** Reads all active keys in the order maintained by the server. */
    public List<String> values(Object player) throws ReflectiveOperationException {
        Object result = values.invoke(effects(player));
        if (!(result instanceof List<?> keys)) {
            throw new ReflectiveOperationException("Post-effects values did not return a list");
        }
        List<String> resultKeys = new ArrayList<>(keys.size());
        for (Object key : keys) {
            if (!keyType.isInstance(key)) {
                throw new ReflectiveOperationException("Post-effects values contained an incompatible key");
            }
            Object value = keyString.invoke(key);
            if (!(value instanceof String string)) {
                throw new ReflectiveOperationException("Post-effect key did not return a string");
            }
            resultKeys.add(string);
        }
        return List.copyOf(resultKeys);
    }

    public boolean add(Object player, String key) throws ReflectiveOperationException {
        return (boolean) add.invoke(effects(player), serverKey(key));
    }

    public boolean remove(Object player, String key) throws ReflectiveOperationException {
        return (boolean) remove.invoke(effects(player), serverKey(key));
    }

    /** Used during cleanup; normal updates are synchronized automatically each tick. */
    public void flush(Object player) throws ReflectiveOperationException {
        if (update != null) update.invoke(effects(player));
    }

    private Object effects(Object player) throws ReflectiveOperationException {
        Object result = postEffects.invoke(Objects.requireNonNull(player, "player"));
        if (result == null) throw new ReflectiveOperationException("Player post-effects returned null");
        return result;
    }

    private Object serverKey(String key) throws ReflectiveOperationException {
        Object result = keyFactory.invoke(null, Objects.requireNonNull(key, "key"));
        if (!keyType.isInstance(result)) {
            throw new ReflectiveOperationException("Post-effect key factory returned an incompatible key");
        }
        return result;
    }

    private static boolean instanceMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers());
    }
}
