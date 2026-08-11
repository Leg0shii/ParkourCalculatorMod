package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AllowDamageSettingTest {

    @Test
    public void defaultsToOff() {
        assertFalse(new Settings().allowDamage);
    }

    @Test
    public void persistsAcrossSaveAndLoad() throws Exception {
        Path file = Files.createTempDirectory("pkc-allowdamage").resolve("settings.json");

        Settings saved = new Settings();
        saved.allowDamage = true;
        SettingsIO.save(file, saved);

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);

        assertTrue(loaded.allowDamage);
    }

    @Test
    public void resetRestoresOff() {
        Settings s = new Settings();
        s.allowDamage = true;
        s.reset();
        assertFalse(s.allowDamage);
    }
}
