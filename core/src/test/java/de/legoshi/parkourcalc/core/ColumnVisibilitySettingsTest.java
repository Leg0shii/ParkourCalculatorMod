package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ColumnVisibilitySettingsTest {

    @Test
    public void newColumnsDefaultHiddenLegacyColumnsDefaultVisible() {
        Settings s = new Settings();
        assertTrue(s.showColA);
        assertTrue(s.showColS);
        assertTrue(s.showColD);
        assertTrue(s.showColSprint);
        assertTrue(s.showColSneak);
        assertTrue(s.showColJump);
        assertTrue(s.showColYaw);
        assertFalse(s.showColPitch);
        assertFalse(s.showColLeftClick);
        assertFalse(s.showColRightClick);
    }

    @Test
    public void columnVisibilityPersistsAcrossSaveAndLoad() throws Exception {
        Path file = Files.createTempDirectory("pkc-colvis").resolve("settings.json");

        Settings saved = new Settings();
        saved.showColD = false;
        saved.showColSneak = false;
        saved.showColPitch = true;
        saved.showColLeftClick = true;
        SettingsIO.save(file, saved);

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);

        assertFalse("hidden legacy column must persist", loaded.showColD);
        assertFalse(loaded.showColSneak);
        assertTrue("opted-in new column must persist", loaded.showColPitch);
        assertTrue(loaded.showColLeftClick);
        assertTrue(loaded.showColA);
        assertFalse(loaded.showColRightClick);
    }

    @Test
    public void resetRestoresColumnDefaults() {
        Settings s = new Settings();
        s.showColA = false;
        s.showColPitch = true;
        s.showColRightClick = true;
        s.reset();
        assertTrue(s.showColA);
        assertFalse(s.showColPitch);
        assertFalse(s.showColRightClick);
    }
}
