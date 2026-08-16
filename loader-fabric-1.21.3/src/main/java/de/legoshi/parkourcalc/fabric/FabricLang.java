package de.legoshi.parkourcalc.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class FabricLang {

    private static final String LANG_PATH = "/assets/parkourcalculator/lang/en_us.json";
    private static final Map<String, String> ENTRIES = load();

    private FabricLang() {
    }

    public static String get(String key) {
        return ENTRIES.get(key);
    }

    private static Map<String, String> load() {
        try (InputStream in = FabricLang.class.getResourceAsStream(LANG_PATH)) {
            if (in == null) {
                return Collections.emptyMap();
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
            return map;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
