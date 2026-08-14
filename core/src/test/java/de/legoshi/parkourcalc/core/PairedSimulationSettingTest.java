package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PairedSimulationSettingTest {

    @Test
    public void defaultsToOff() {
        Settings s = new Settings();
        assertFalse(s.pairedSimulation);
        assertTrue(s.viewServerEvents);
    }

    @Test
    public void persistsAcrossSaveAndLoad() throws Exception {
        Path file = Files.createTempDirectory("pkc-paired").resolve("settings.json");

        Settings saved = new Settings();
        saved.pairedSimulation = true;
        saved.viewServerEvents = false;
        SettingsIO.save(file, saved);

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);

        assertTrue(loaded.pairedSimulation);
        assertFalse(loaded.viewServerEvents);
    }

    @Test
    public void resetRestoresDefaults() {
        Settings s = new Settings();
        s.pairedSimulation = true;
        s.viewServerEvents = false;
        s.reset();
        assertFalse(s.pairedSimulation);
        assertTrue(s.viewServerEvents);
    }
}
