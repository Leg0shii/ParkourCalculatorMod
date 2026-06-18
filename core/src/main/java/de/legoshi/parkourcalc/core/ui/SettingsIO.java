package de.legoshi.parkourcalc.core.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistence for Settings using Gson. The JSON key for each value is the Java
 * field name discovered via reflection: there is no separate string-to-field
 * mapping to drift. Renaming a Settings field is therefore a backward-incompatible
 * change to existing config files. API is restricted to the Gson 2.2.4 subset since
 * Minecraft 1.8.9 ships that version on the classpath.
 */
public final class SettingsIO {

    private SettingsIO() {}

    public static void load(Path file, Settings settings) {
        if (!Files.exists(file)) return;

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) return;
            json = root.getAsJsonObject();
        } catch (Exception e) {
            return;
        }

        Gson gson = new Gson();
        for (Field field : Settings.class.getDeclaredFields()) {
            if (!isPersistable(field)) continue;
            JsonElement element = json.get(jsonKey(field));
            if (element == null || element.isJsonNull()) continue;
            try {
                mergeField(gson, field, settings, element);
            } catch (Exception ignored) {
            }
        }

        clampScaleIndex(settings);
        normalizeTickInfoStats(settings);
    }

    public static void save(Path file, Settings settings) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(settings, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isPersistable(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers);
    }

    private static String jsonKey(Field field) {
        SerializedName annotation = field.getAnnotation(SerializedName.class);
        return annotation != null ? annotation.value() : field.getName();
    }

    private static void mergeField(Gson gson, Field field, Settings settings, JsonElement element) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type.isArray() && type.getComponentType().isPrimitive()) {
            Object deserialized = gson.fromJson(element, type);
            Object existing = field.get(settings);
            if (deserialized != null && existing != null) {
                int copyLength = Math.min(Array.getLength(deserialized), Array.getLength(existing));
                System.arraycopy(deserialized, 0, existing, 0, copyLength);
            }
        } else {
            field.set(settings, gson.fromJson(element, type));
        }
    }

    private static void clampScaleIndex(Settings settings) {
        if (settings.scaleIndex == Settings.AUTO_SCALE_INDEX) return;
        if (settings.scaleIndex < 0 || settings.scaleIndex >= Settings.PRESET_SCALES.length) {
            settings.scaleIndex = Settings.DEFAULT_SCALE_INDEX;
        }
    }

    private static void normalizeTickInfoStats(Settings settings) {
        if (settings.tickInfoStats == null) {
            settings.tickInfoStats = TickInfoConfig.defaultConfig(Settings.defaultTickInfoPrecision());
            return;
        }
        settings.tickInfoStats.normalize(
                Settings.defaultTickInfoPrecision(),
                Settings.MIN_STAT_PRECISION,
                Settings.MAX_STAT_PRECISION);
    }
}
