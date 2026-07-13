package com.mymod.flux_turret;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResourceIntegrityTest {
    private static final String[] LANGUAGES = {"en_us", "zh_cn", "es_es", "ja_jp"};
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]");

    @Test
    void translationsHaveMatchingKeysAndPlaceholders() throws IOException {
        JsonObject english = readObject("assets/flux_turret/lang/en_us.json");
        for (String language : LANGUAGES) {
            JsonObject translated = readObject("assets/flux_turret/lang/" + language + ".json");
            assertEquals(english.keySet(), translated.keySet(), language + " key set");
            for (Map.Entry<String, JsonElement> entry : english.entrySet()) {
                assertEquals(placeholders(entry.getValue().getAsString()),
                        placeholders(translated.get(entry.getKey()).getAsString()),
                        language + " placeholders for " + entry.getKey());
            }
        }
    }

    @Test
    void psychicBeaconRecipeMatchesTheRegisteredItem() throws IOException {
        JsonObject recipe = readObject("data/flux_turret/recipes/psychic_beacon.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("flux_turret:psychic_beacon",
                recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(3, recipe.getAsJsonArray("pattern").size());
    }

    private static List<String> placeholders(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static JsonObject readObject(String path) throws IOException {
        InputStream stream = ResourceIntegrityTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
