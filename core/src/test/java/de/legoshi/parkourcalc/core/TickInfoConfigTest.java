package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.SettingsIO;
import de.legoshi.parkourcalc.core.ui.TickInfoConfig;
import de.legoshi.parkourcalc.core.ui.TickInfoStat;
import de.legoshi.parkourcalc.core.ui.TickInfoStatSetting;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TickInfoConfigTest {

    @Test
    public void defaultConfigMatchesLegacyStatSetOrderAndPrecision() {
        TickInfoConfig config = TickInfoConfig.defaultConfig(Settings.defaultTickInfoPrecision());

        TickInfoStat[] expected = TickInfoStat.values();
        assertEquals("one setting per stat", expected.length, config.stats.size());
        for (int i = 0; i < expected.length; i++) {
            TickInfoStatSetting setting = config.stats.get(i);
            assertEquals("order matches enum declaration order", expected[i].id(), setting.id);
            assertTrue("every stat enabled by default", setting.enabled);
            assertEquals("every stat at the legacy default precision",
                    Settings.defaultTickInfoPrecision(), setting.decimals);
        }
        assertEquals(Arrays.asList(expected), config.enabledInOrder());
    }

    @Test
    public void freshSettingsCarriesTheDefaultConfig() {
        Settings settings = new Settings();
        assertNotNull(settings.tickInfoStats);
        assertEquals(TickInfoStat.values().length, settings.tickInfoStats.stats.size());
        assertEquals(Arrays.asList(TickInfoStat.values()), settings.tickInfoStats.enabledInOrder());
    }

    @Test
    public void moveReordersWithInsertBeforeSemantics() {
        TickInfoConfig config = TickInfoConfig.defaultConfig(5);
        String first = config.stats.get(0).id;
        String second = config.stats.get(1).id;

        config.move(0, 2);
        assertEquals(second, config.stats.get(0).id);
        assertEquals(first, config.stats.get(1).id);

        List<String> before = ids(config);
        config.move(1, 1);
        config.move(1, 2);
        assertEquals(before, ids(config));

        config.move(-1, 0);
        config.move(0, config.stats.size() + 5);
        assertEquals(before, ids(config));
    }

    @Test
    public void disablingHidesAStatButKeepsItInTheConfig() {
        TickInfoConfig config = TickInfoConfig.defaultConfig(5);
        int total = config.stats.size();

        config.find(TickInfoStat.SNEAKING).enabled = false;
        config.find(TickInfoStat.COLLISION).enabled = false;

        List<TickInfoStat> visible = config.enabledInOrder();
        assertEquals(total - 2, visible.size());
        assertFalse(visible.contains(TickInfoStat.SNEAKING));
        assertFalse(visible.contains(TickInfoStat.COLLISION));
        assertTrue("disabled stats are retained for re-enabling", config.stats.size() == total);
    }

    @Test
    public void roundTripPreservesEnabledDecimalsAndOrder() throws Exception {
        Settings original = new Settings();
        TickInfoConfig config = original.tickInfoStats;

        config.find(TickInfoStat.YAW).enabled = false;
        config.find(TickInfoStat.MOTION).enabled = false;
        config.find(TickInfoStat.POSITION).decimals = 2;
        config.find(TickInfoStat.SPEED).decimals = 9;
        config.move(0, config.stats.size());
        List<String> savedOrder = ids(config);

        Path file = Files.createTempFile("pkc-tickinfo", ".json");
        SettingsIO.save(file, original);

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);

        TickInfoConfig out = loaded.tickInfoStats;
        assertEquals("order preserved across save/load", savedOrder, ids(out));
        assertFalse(out.find(TickInfoStat.YAW).enabled);
        assertFalse(out.find(TickInfoStat.MOTION).enabled);
        assertTrue(out.find(TickInfoStat.POSITION).enabled);
        assertEquals(2, out.find(TickInfoStat.POSITION).decimals);
        assertEquals(9, out.find(TickInfoStat.SPEED).decimals);
        assertEquals(config.enabledInOrder(), out.enabledInOrder());
    }

    @Test
    public void oldSettingsFileWithoutTickInfoStatsLoadsDefault() throws Exception {
        Path file = Files.createTempFile("pkc-old-settings", ".json");
        Files.write(file, "{\"tickInfoPrecision\":4,\"scaleIndex\":1}".getBytes(StandardCharsets.UTF_8));

        Settings loaded = new Settings();
        SettingsIO.load(file, loaded);

        assertNotNull(loaded.tickInfoStats);
        assertEquals("missing key falls back to the full default config",
                Arrays.asList(TickInfoStat.values()), loaded.tickInfoStats.enabledInOrder());
    }

    @Test
    public void normalizeDropsUnknownIdsAppendsMissingAndClampsDecimals() {
        TickInfoConfig config = new TickInfoConfig();
        config.stats = new ArrayList<>();
        config.stats.add(new TickInfoStatSetting("a_removed_stat", true, 5));
        config.stats.add(new TickInfoStatSetting(TickInfoStat.YAW.id(), false, 999));
        config.stats.add(new TickInfoStatSetting(TickInfoStat.YAW.id(), true, 3));
        config.stats.add(new TickInfoStatSetting(TickInfoStat.POSITION.id(), true, -7));

        config.normalize(Settings.defaultTickInfoPrecision(), Settings.MIN_STAT_PRECISION, Settings.MAX_STAT_PRECISION);

        assertEquals(null, TickInfoStat.byId("a_removed_stat"));
        assertEquals(1, countId(config, TickInfoStat.YAW.id()));
        assertFalse("first YAW occurrence (disabled) is the one kept", config.find(TickInfoStat.YAW).enabled);

        assertEquals(Settings.MAX_STAT_PRECISION, config.find(TickInfoStat.YAW).decimals);
        assertEquals(Settings.MIN_STAT_PRECISION, config.find(TickInfoStat.POSITION).decimals);

        assertEquals(TickInfoStat.values().length, config.stats.size());
        assertEquals(TickInfoStat.YAW.id(), config.stats.get(0).id);
        assertEquals(TickInfoStat.POSITION.id(), config.stats.get(1).id);
        for (TickInfoStat stat : TickInfoStat.values()) {
            assertEquals("each stat present exactly once after normalize", 1, countId(config, stat.id()));
        }
        assertTrue(config.find(TickInfoStat.TICK).enabled);
        assertEquals(Settings.defaultTickInfoPrecision(), config.find(TickInfoStat.TICK).decimals);
    }

    @Test
    public void normalizeRebuildsFromNullOrEmpty() {
        TickInfoConfig config = new TickInfoConfig();
        config.stats = null;
        config.normalize(Settings.defaultTickInfoPrecision(), Settings.MIN_STAT_PRECISION, Settings.MAX_STAT_PRECISION);
        assertEquals(Arrays.asList(TickInfoStat.values()), config.enabledInOrder());
    }

    private static List<String> ids(TickInfoConfig config) {
        List<String> result = new ArrayList<>();
        for (TickInfoStatSetting setting : config.stats) result.add(setting.id);
        return result;
    }

    private static int countId(TickInfoConfig config, String id) {
        int n = 0;
        for (TickInfoStatSetting setting : config.stats) {
            if (id.equals(setting.id)) n++;
        }
        return n;
    }
}
