package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class HitDistanceReachSettingTest {

    @Test
    public void defaultsToSurvival() {
        Settings s = new Settings();
        assertEquals(Settings.HIT_DISTANCE_REACH_SURVIVAL, s.hitDistanceReachMode);
        assertEquals(3.0f, s.hitDistanceCustomReach, 0.0f);
    }

    @Test
    public void persistsAcrossSaveAndLoad() throws Exception {
        Path file = Files.createTempDirectory("pkc-reach").resolve("settings.json");

        Settings saved = new Settings();
        saved.hitDistanceReachMode = Settings.HIT_DISTANCE_REACH_CUSTOM;
        saved.hitDistanceCustomReach = 6.25f;
        SettingsIO.save(file, saved);

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);

        assertEquals(Settings.HIT_DISTANCE_REACH_CUSTOM, loaded.hitDistanceReachMode);
        assertEquals(6.25f, loaded.hitDistanceCustomReach, 0.0f);
    }

    @Test
    public void migratesLegacyGameReachFlag() throws Exception {
        Path dir = Files.createTempDirectory("pkc-reach-legacy");
        Path on = dir.resolve("on.json");
        Path off = dir.resolve("off.json");
        Files.write(on, "{\"hitDistanceGameReach\": true}".getBytes(StandardCharsets.UTF_8));
        Files.write(off, "{\"hitDistanceGameReach\": false}".getBytes(StandardCharsets.UTF_8));

        Settings loadedOn = new Settings();
        SettingsIO.load(on, loadedOn);
        assertEquals(Settings.HIT_DISTANCE_REACH_GAME_MODE, loadedOn.hitDistanceReachMode);

        Settings loadedOff = new Settings();
        SettingsIO.load(off, loadedOff);
        assertEquals(Settings.HIT_DISTANCE_REACH_SURVIVAL, loadedOff.hitDistanceReachMode);
    }

    @Test
    public void clampsOutOfRangeValues() throws Exception {
        Path file = Files.createTempDirectory("pkc-reach-clamp").resolve("settings.json");
        Files.write(file, "{\"hitDistanceReachMode\": 9, \"hitDistanceCustomReach\": 99.0}".getBytes(StandardCharsets.UTF_8));

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);

        assertEquals(Settings.HIT_DISTANCE_REACH_SURVIVAL, loaded.hitDistanceReachMode);
        assertEquals(Settings.MAX_HIT_DISTANCE_CUSTOM_REACH, loaded.hitDistanceCustomReach, 0.0f);
    }

    @Test
    public void resetRestoresDefaults() {
        Settings s = new Settings();
        s.hitDistanceReachMode = Settings.HIT_DISTANCE_REACH_CREATIVE;
        s.hitDistanceCustomReach = 8.0f;
        s.reset();
        assertEquals(Settings.HIT_DISTANCE_REACH_SURVIVAL, s.hitDistanceReachMode);
        assertEquals(3.0f, s.hitDistanceCustomReach, 0.0f);
    }
}
