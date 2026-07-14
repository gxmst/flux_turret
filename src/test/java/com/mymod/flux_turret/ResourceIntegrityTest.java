package com.mymod.flux_turret;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mymod.flux_turret.block.entity.RedstoneControlMode;
import com.mymod.flux_turret.block.entity.TargetingMode;
import com.mymod.flux_turret.block.entity.TurretAccessMode;
import com.mymod.flux_turret.block.entity.TurretStatus;
import com.mymod.flux_turret.item.TurretUpgradeType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                for (String placeholder : placeholders(entry.getValue().getAsString())) {
                    assertTrue(placeholder.endsWith("s"),
                            entry.getKey() + " uses unsupported Minecraft placeholder " + placeholder);
                }
                assertEquals(placeholders(entry.getValue().getAsString()),
                        placeholders(translated.get(entry.getKey()).getAsString()),
                        language + " placeholders for " + entry.getKey());
            }
        }
    }

    @Test
    void dynamicTranslationFamiliesAreComplete() throws IOException {
        JsonObject english = readObject("assets/flux_turret/lang/en_us.json");
        for (TargetingMode mode : TargetingMode.values()) {
            assertTrue(english.has(mode.getTranslationKey()), mode.getTranslationKey());
        }
        for (RedstoneControlMode mode : RedstoneControlMode.values()) {
            assertTrue(english.has(mode.getTranslationKey()), mode.getTranslationKey());
        }
        for (TurretAccessMode mode : TurretAccessMode.values()) {
            assertTrue(english.has(mode.getTranslationKey()), mode.getTranslationKey());
        }
        for (TurretStatus status : TurretStatus.values()) {
            assertTrue(english.has(status.getTranslationKey()), status.getTranslationKey());
        }
        for (TurretUpgradeType type : TurretUpgradeType.values()) {
            assertTrue(english.has(type.getDescriptionKey()), type.getDescriptionKey());
            assertTrue(english.has("item.flux_turret." + type.getId() + "_module"), type.getId());
        }
        for (TurretUpgradeType.Slot slot : TurretUpgradeType.Slot.values()) {
            String key = "tooltip.flux_turret.upgrade_module.slot." + slot.name().toLowerCase();
            assertTrue(english.has(key), key);
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

    @Test
    void energyCrystalRecipesUseStateAwareSerializers() throws IOException {
        assertEquals("flux_turret:redstone_charged_crystal",
                readObject("data/flux_turret/recipes/energy_crystal_from_crafting.json")
                        .get("type").getAsString());
        assertEquals("flux_turret:furnace_charged_crystal",
                readObject("data/flux_turret/recipes/energy_crystal_from_smelting.json")
                        .get("type").getAsString());
        assertEquals("flux_turret:empowered_energy_crystal",
                readObject("data/flux_turret/recipes/empowered_energy_crystal.json")
                        .get("type").getAsString());
    }

    @Test
    void upgradeModuleTagAndRecoveryRecipeStayInSync() throws IOException {
        Set<String> expected = Set.of(
                "flux_turret:armor_piercing_rounds_module",
                "flux_turret:fire_rounds_module",
                "flux_turret:slow_rounds_module",
                "flux_turret:chain_jump_module",
                "flux_turret:emp_slow_module",
                "flux_turret:overload_burst_module",
                "flux_turret:focused_beam_module",
                "flux_turret:refraction_beam_module",
                "flux_turret:remote_support_module",
                "flux_turret:seismic_shock_module",
                "flux_turret:armor_break_module",
                "flux_turret:cluster_shells_module");
        JsonObject tag = readObject("data/flux_turret/tags/items/upgrade_modules.json");
        Set<String> actual = new HashSet<>();
        tag.getAsJsonArray("values").forEach(value -> actual.add(value.getAsString()));
        assertEquals(expected, actual);

        JsonObject recipe = readObject("data/flux_turret/recipes/recycle_upgrade_module.json");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("flux_turret:upgrade_modules",
                recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("minecraft:amethyst_shard",
                recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(2, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void guideAdvancementsFormTheExpectedProgression() throws IOException {
        JsonObject crystal = readObject("data/flux_turret/advancements/guide/energy_crystal.json");
        assertEquals("minecraft:story/root", crystal.get("parent").getAsString());
        assertEquals(2, crystal.getAsJsonArray("requirements").get(0).getAsJsonArray().size());

        JsonObject turret = readObject("data/flux_turret/advancements/guide/turret.json");
        assertEquals("flux_turret:guide/energy_crystal", turret.get("parent").getAsString());
        assertEquals("flux_turret:psychic_beacon",
                turret.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());

        JsonObject beacon = readObject("data/flux_turret/advancements/guide/psychic_beacon.json");
        assertEquals("flux_turret:guide/turret", beacon.get("parent").getAsString());

        JsonObject module = readObject("data/flux_turret/advancements/guide/upgrade_module.json");
        assertEquals("flux_turret:guide/psychic_beacon", module.get("parent").getAsString());
        JsonObject modulePredicate = module.getAsJsonObject("criteria")
                .getAsJsonObject("has_upgrade_module")
                .getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("flux_turret:upgrade_modules", modulePredicate.get("tag").getAsString());
        assertEquals("flux_turret:recycle_upgrade_module",
                module.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
    }

    @Test
    void psychicBeaconRewardTablesAreDataDrivenAndDoNotContainModulesOrCrystals() throws IOException {
        for (int tier = 1; tier <= 4; tier++) {
            JsonObject table = readObject(
                    "data/flux_turret/loot_tables/chests/psychic_beacon_tier" + tier + ".json");
            assertEquals("minecraft:chest", table.get("type").getAsString());
            String json = table.toString();
            assertTrue(!json.contains("_module") && !json.contains("energy_crystal"),
                    "tier " + tier + " must leave module/crystal rewards to beacon code");
        }
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
