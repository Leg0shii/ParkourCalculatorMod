package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TurnProfileStoreTest {

    private static TurnProfileDocument sample() {
        TurnProfileDocument doc = new TurnProfileDocument();
        List<InputRow> rows = new ArrayList<>();
        InputRow r0 = new InputRow();
        TurnReference.applyKeys(r0, TurnReference.KEY_W | TurnReference.KEY_JUMP);
        r0.setYaw(-12.5f);
        InputRow r1 = new InputRow();
        TurnReference.applyKeys(r1, TurnReference.KEY_W | TurnReference.KEY_A);
        r1.setYaw(-20.25f);
        r0.setOnejumpKeys(true);
        r0.setOnejumpFace(InputRow.ONEJUMP_FACE_CHECK);
        r1.setOnejumpKeys(false);
        r1.setOnejumpFace(InputRow.ONEJUMP_FACE_CHECK);
        rows.add(r0);
        rows.add(r1);
        doc.reference().replace(rows, null);
        doc.reference().setTasFirstTick(26);
        doc.reference().setLanding(new TurnReference.Landing(35, 186.32, Double.NaN, Double.NaN, 873.05));
        doc.touch();
        doc.add(new TurnAttempt(1, 26, new double[] {-12.4, -20.0}, 2, true, true, false, "landed", -0.012, 27, -1, 0, 0, 0, null, null, null, false, Double.NaN, null));
        doc.add(new TurnAttempt(2, 26, new double[] {-12.4, 0.0}, 1, true, false, true, "input failure", Double.NaN, -1, 27,
                TurnReference.KEY_W, TurnReference.KEY_W | TurnReference.KEY_A, 0, null, null, null, false, Double.NaN, null));
        return doc;
    }

    private static TurnProfileStore store(Path dir) {
        FileSystemSaveStore fs = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
        return new TurnProfileStore(() -> fs);
    }

    @Test
    public void theStillFlagAndAPreturnRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-still");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        TurnReference ref = doc.reference();
        ref.row(0).setOnejumpFace(InputRow.ONEJUMP_FACE_STILL);
        TurnReference.applyOptional(ref.row(1), TurnReference.KEY_JUMP | TurnReference.KEY_SPRINT);
        assertTrue(ref.still(ref.row(0)));
        assertFalse(ref.still(ref.row(1)));
        assertEquals(TurnReference.KEY_JUMP | TurnReference.KEY_SPRINT, ref.optionalKeys(ref.row(1)));
        doc.add(new TurnAttempt(3, 26, new double[] {-12.25, 0.0}, 1, true, false, false,
                "tick 27: turned +0.150° (1 px), expected still", Double.NaN, -1, 26, 0, 0, 0, null, null, null, true, 0.15, null));
        assertTrue(store.save("still", ref, doc.attempts()));
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("still", back));
        assertTrue(back.reference().still(back.reference().row(0)));
        assertEquals(TurnReference.KEY_JUMP | TurnReference.KEY_SPRINT, back.reference().optionalKeys(back.reference().row(1)));
        assertEquals(0, back.reference().optionalKeys(back.reference().row(0)));
        assertFalse(back.reference().still(back.reference().row(1)));
        TurnAttempt a = back.attempts().get(2);
        assertTrue(a.turnFailure);
        assertFalse(a.inputFailure);
        assertFalse(a.judged());
        assertEquals(26, a.failTick);
        assertEquals(0.15, a.failTurn, 0.0);
        assertTrue(a.verdict.endsWith("expected still"));
        assertFalse(back.attempts().get(0).turnFailure);
        assertTrue(Double.isNaN(back.attempts().get(0).failTurn));
        assertEquals(1, back.stats().turnFailures);
        TurnReference copy = ref.copy();
        assertTrue(copy.still(copy.row(0)));
    }

    @Test
    public void turnTimingRoundTripsWithoutTheTrace() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-timing");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        doc.add(new TurnAttempt(3, 26, new double[] {-12.4, -20.0}, 2, true, true, false, "landed", -0.02, 27, -1, 0, 0, 0,
                new float[] {0.4f, Float.NaN}, new float[] {0.6f, Float.NaN}, new float[][] {{0f, -12.4f}, null}, false, Double.NaN, null));
        assertTrue(store.save("timed", doc.reference(), doc.attempts()));
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("timed", back));
        assertEquals(3, back.attempts().size());
        assertFalse(back.attempts().get(0).hasTiming());
        TurnAttempt a = back.attempts().get(2);
        assertTrue(a.hasTiming());
        assertNull(a.trace);
        assertEquals(0.4f, a.turnStartAt(26), 0f);
        assertEquals(0.6f, a.turnEndAt(26), 0f);
        assertTrue(Float.isNaN(a.turnStartAt(27)));
        assertTrue(Float.isNaN(a.turnEndAt(27)));
        assertTrue(Float.isNaN(a.turnStartAt(25)));
    }

    @Test
    public void aDocumentRoundTripsThroughOneFilePerOnejump() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        assertTrue(doc.isReferenceDirty());
        assertTrue(doc.hasPending());
        assertTrue(store.save("d10/j335", doc.reference(), doc.attempts()));
        doc.markClean();
        Path file = dir.resolve(TurnProfileStore.DIRECTORY).resolve("d10").resolve("j335" + TurnProfileStore.EXTENSION);
        assertTrue(Files.exists(file));
        assertEquals(3, Files.readAllLines(file).size());

        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("d10/j335", back));
        TurnReference ref = back.reference();
        assertEquals(2, ref.size());
        assertEquals(26, ref.tasFirstTick());
        assertEquals(TurnReference.KEY_W | TurnReference.KEY_JUMP, TurnReference.mask(ref.row(0)));
        assertEquals(-12.5f, ref.row(0).getYaw(), 0.0f);
        assertEquals(-12.5, ref.facings()[0], 0.0);
        assertTrue(ref.checkKeys(ref.row(0)));
        assertFalse(ref.checkKeys(ref.row(1)));
        assertTrue(ref.checkYaw(ref.row(1)));
        assertNotNull(ref.landing());
        assertEquals(35, ref.landing().tick);
        assertEquals(186.32, ref.landing().xLo, 0.0);
        assertTrue(Double.isNaN(ref.landing().xHi));
        assertTrue(Double.isNaN(ref.landing().zLo));
        assertEquals(873.05, ref.landing().zHi, 0.0);
        assertEquals(0.5, ref.landing().margin(185.82, 870.0), 1e-9);
        assertEquals(0.5, ref.landing().margin(190.0, 873.55), 1e-9);
        assertEquals("Z", ref.landing().worstAxis(190.0, 873.55));
        assertTrue(ref.landing().margin(190.0, 870.0) < 0.0);
        assertEquals(2, back.attempts().size());
        TurnAttempt a1 = back.attempts().get(0);
        assertTrue(a1.landed);
        assertEquals(-0.012, a1.margin, 0.0);
        assertEquals(27, a1.worstTick);
        TurnAttempt a2 = back.attempts().get(1);
        assertTrue(a2.inputFailure);
        assertFalse(a2.hasMargin());
        assertEquals(27, a2.failTick);
        assertEquals(TurnReference.KEY_W | TurnReference.KEY_A, a2.expectedKeys);
        assertEquals(2, back.stats().attempts);
        assertEquals(1, back.stats().landings);
        assertEquals(1, back.stats().inputFailures);
        assertEquals(-0.012, back.stats().closest, 0.0);
        assertEquals(3, back.nextNumber());
        assertEquals(1, back.top().size());
        assertFalse(back.isReferenceDirty());
        assertFalse(back.hasPending());
    }

    @Test
    public void newAttemptsAreAppendedAsSingleLines() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        assertTrue(store.save("j1", doc.reference(), doc.attempts()));
        doc.markClean();
        TurnAttempt a3 = new TurnAttempt(3, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.031, 27, -1, 0, 0, 0, null, null, null, false, Double.NaN, null);
        doc.add(a3);
        assertTrue(doc.hasPending());
        assertFalse(doc.isReferenceDirty());
        List<TurnAttempt> pending = doc.drainPending();
        assertEquals(1, pending.size());
        assertFalse(doc.hasPending());
        assertTrue(store.append("j1", pending));
        Path file = dir.resolve(TurnProfileStore.DIRECTORY).resolve("j1" + TurnProfileStore.EXTENSION);
        assertEquals(4, Files.readAllLines(file).size());
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("j1", back));
        assertEquals(3, back.attempts().size());
        assertEquals(0.031, back.attempts().get(2).margin, 0.0);
        assertEquals(2, back.top().size());
        assertEquals(-0.012, back.top().get(0).margin, 0.0);
        assertEquals(0.031, back.top().get(1).margin, 0.0);
        assertFalse(store.append("missing", pending));
    }

    @Test
    public void theTopListKeepsTheTenClosestOnly() {
        TurnProfileDocument doc = new TurnProfileDocument();
        for (int i = 0; i < 100; i++) {
            double margin = (i * 37) % 100 / 100.0 - 0.3;
            doc.add(new TurnAttempt(i + 1, 0, new double[] {0.0}, 1, true, margin <= 0.0, false, "", margin, -1, -1, 0, 0, 0, null, null, null, false, Double.NaN, null));
        }
        assertEquals(TurnProfileDocument.TOP, doc.top().size());
        doc.add(new TurnAttempt(101, 0, new double[] {0.0}, 1, true, true, false, "", -1.0, -1, -1, 0, 0, 1, null, null, null, false, Double.NaN, null));
        doc.add(new TurnAttempt(102, 0, new double[] {0.0}, 1, true, false, false, "", 0.2, -1, -1, 0, 0, 2, null, null, null, false, Double.NaN, null));
        assertEquals(100, doc.stats().attempts);
        assertEquals(1, doc.stats().mouseAttempts);
        assertEquals(1, doc.stats().mouseClears);
        assertEquals(1, doc.stats().inputAttempts);
        assertEquals(0, doc.stats().inputClears);
        assertTrue(doc.top().get(0).margin > -1.0);
        for (int i = 1; i < doc.top().size(); i++) assertTrue(doc.top().get(i - 1).margin <= doc.top().get(i).margin);
        assertEquals(-0.3, doc.top().get(0).margin, 1e-12);
        assertEquals(-0.3, doc.stats().closest, 1e-12);
        doc.clearAttempts();
        assertTrue(doc.top().isEmpty());
        assertTrue(doc.isReferenceDirty());
        assertEquals(0, doc.stats().attempts);
    }

    @Test
    public void aLandingOnlyReferenceWithAxisHeightAndAxisMarginsRoundTrips() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-fast");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = new TurnProfileDocument();
        TurnReference ref = doc.reference();
        ref.setTasFirstTick(40);
        ref.setLanding(new TurnReference.Landing(0, 186.32, Double.NaN, 870.0, 873.05, 65.0));
        ref.setAxis(TurnReference.AXIS_Z);
        assertTrue(ref.isLandingOnly());
        assertFalse(ref.isEmpty());
        TurnAttempt a = new TurnAttempt(1, 0, new double[0], 0, true, false, false, "-0.02 X", 0.02, -1, -1, 0, 0, 0,
                null, null, null, false, Double.NaN, null);
        a.setAxisMargins(0.02, -0.4);
        a.tasFirstTick = 40;
        doc.add(a);
        assertTrue(store.save("fast", ref, doc.attempts()));
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("fast", back));
        TurnReference r = back.reference();
        assertTrue(r.isLandingOnly());
        assertEquals(40, r.tasFirstTick());
        assertEquals(TurnReference.AXIS_Z, r.axis());
        assertEquals(65.0, r.landing().y, 0.0);
        assertTrue(r.landing().hasY());
        assertEquals(870.0, r.landing().zLo, 0.0);
        TurnAttempt b = back.attempts().get(0);
        assertEquals(0.02, b.marginX, 0.0);
        assertEquals(-0.4, b.marginZ, 0.0);
        assertEquals(0.02, b.margin, 0.0);
        assertEquals(0, b.recorded);
        assertFalse(b.landed);
        back.rejudge(TurnReference.AXIS_Z);
        assertEquals(-0.4, b.margin, 0.0);
        assertTrue(b.verdict, b.verdict.startsWith("+0.4") && b.verdict.endsWith(" X"));
        assertEquals(-0.4, back.stats().closest, 0.0);
        assertTrue(back.isReferenceDirty());
        TurnReference legacy = TurnProfileStore.toReference(new TurnProfileStore.HeaderData());
        assertEquals(TurnReference.AXIS_BOTH, legacy.axis());
        assertTrue(legacy.isEmpty());
    }

    @Test
    public void aMissingFileLoadsAnEmptyDocument() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        assertFalse(store.load("nothing", doc));
        assertTrue(doc.reference().isEmpty());
        assertNull(doc.reference().landing());
        assertTrue(doc.attempts().isEmpty());
        assertNull(store.fileFor(null));
        assertEquals(Collections.emptyList(), doc.top());
    }

    @Test
    public void theFailedTickLoadsFromTheOldLostTickKey() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-lost");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        TurnAttempt.Forecast fc = new TurnAttempt.Forecast(new double[] {0.1, 0.2}, new double[] {0.05, 0.1},
                new double[] {0.0, 0.0}, 27, null, null, null, null, null);
        doc.add(new TurnAttempt(3, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.031, 27, -1, 0, 0, 0,
                null, null, null, false, Double.NaN, fc));
        assertTrue(store.save("old", doc.reference(), doc.attempts()));
        Path file = store.fileFor("old");
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.get(3).contains("\"failedTick\":27"));
        assertFalse(lines.get(3).contains("lostTick"));
        lines.set(3, lines.get(3).replace("\"failedTick\":27", "\"lostTick\":27"));
        Files.write(file, lines);
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("old", back));
        assertEquals(27, back.attempts().get(2).failedTick());
    }

    @Test
    public void ordinalsCountPerKindAndMissBandsAndFavouritesPersist() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-fav");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        doc.add(new TurnAttempt(3, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.031, 27, -1, 0, 0, 1, null, null, null, false, Double.NaN, null));
        doc.add(new TurnAttempt(4, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.0004, 27, -1, 0, 0, 0, null, null, null, false, Double.NaN, null));
        doc.add(new TurnAttempt(5, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.00002, 27, -1, 0, 0, 2, null, null, null, false, Double.NaN, null));
        doc.add(new TurnAttempt(6, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.05, 27, -1, 0, 0, 1, null, null, null, false, Double.NaN, null));
        List<TurnAttempt> all = doc.attempts();
        assertEquals(1, all.get(0).ordinal);
        assertEquals(2, all.get(1).ordinal);
        assertEquals(1, all.get(2).ordinal);
        assertEquals(3, all.get(3).ordinal);
        assertEquals(1, all.get(4).ordinal);
        assertEquals(2, all.get(5).ordinal);
        TurnProfileDocument.Stats st = doc.stats();
        assertEquals(3, st.attempts);
        assertEquals(0, st.missBands[0]);
        assertEquals(0, st.missBands[1]);
        assertEquals(1, st.missBands[2]);
        assertEquals(0, st.missBands[3]);
        assertTrue(store.save("fav", doc.reference(), all));
        doc.markClean();
        doc.setFavourite(all.get(3), true);
        doc.setFavourite(all.get(0), true);
        assertEquals(2, doc.favourites().size());
        assertEquals(1, doc.favourites().get(0).number);
        assertEquals(4, doc.favourites().get(1).number);
        assertTrue(doc.isReferenceDirty());
        assertTrue(store.save("fav", doc.reference(), all));
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("fav", back));
        assertEquals(2, back.favourites().size());
        assertTrue(back.attempts().get(3).favourite);
        assertFalse(back.attempts().get(1).favourite);
        assertEquals(3, back.attempts().get(3).ordinal);
        back.setFavourite(back.attempts().get(3), false);
        assertEquals(1, back.favourites().size());
    }

    @Test
    public void theAttemptStateAndTheSolvedTickRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-state");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        TurnAttempt.Forecast fc = new TurnAttempt.Forecast(null, null, null, -1,
                new double[] {1.5, 2.5}, new double[] {3.5, 4.5}, new double[] {0.1, 0.2}, new double[] {0.3, 0.4},
                new boolean[] {true, false});
        TurnAttempt a = new TurnAttempt(3, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.031, 27, -1, 0, 0, 0,
                null, null, null, false, Double.NaN, fc);
        a.solvedOffset = new double[] {0.02, -0.01};
        doc.add(a);
        assertTrue(a.hasState());
        assertFalse(a.hasForecast());
        assertEquals(27, a.failedTick());
        assertTrue(store.save("state", doc.reference(), doc.attempts()));
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("state", back));
        TurnAttempt b = back.attempts().get(2);
        assertTrue(b.hasState());
        assertEquals(2.5, b.forecast.x[1], 0.0);
        assertEquals(0.4, b.forecast.vz[1], 0.0);
        assertTrue(b.forecast.ground[0]);
        assertFalse(b.forecast.ground[1]);
        assertTrue(b.solved());
        assertEquals(0.02, b.solvedOffsetAt(26), 0.0);
        assertEquals(-0.01, b.solvedOffsetAt(27), 0.0);
        assertEquals(27, b.failedTick());
        assertFalse(back.attempts().get(0).solved());
        assertEquals(-1, back.attempts().get(0).failedTick());
    }
    @Test
    public void aBrokenAttemptLineIsSkippedAndTheRestLoads() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-broken");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        doc.add(new TurnAttempt(3, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.031, 27, -1, 0, 0, 0, null, null, null, false, Double.NaN, null));
        assertTrue(store.save("broken", doc.reference(), doc.attempts()));
        Path file = store.fileFor("broken");
        List<String> lines = Files.readAllLines(file);
        lines.set(2, lines.get(2).substring(0, 20));
        Files.write(file, lines);
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("broken", back));
        assertNull(store.lastError());
        assertEquals(1, store.skippedLines());
        assertEquals(2, back.attempts().size());
        assertEquals(1, back.attempts().get(0).number);
        assertEquals(3, back.attempts().get(1).number);
        assertEquals(4, back.nextNumber());
        assertEquals(26, back.reference().tasFirstTick());
    }

    @Test
    public void theReferenceFacingsKeepTheirDoublePrecision() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-facing");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        double exact = -12.123456789012345;
        List<InputRow> rows = new ArrayList<>();
        rows.add(doc.reference().row(0));
        rows.add(doc.reference().row(1));
        doc.reference().replace(rows, new double[] {exact, Double.NaN});
        assertEquals(exact, doc.reference().facings()[0], 0.0);
        assertEquals(exact, doc.reference().facings()[1], 0.0);
        assertEquals((float) exact, doc.reference().row(0).getYaw(), 0f);
        assertNull(doc.reference().row(1).getYaw());
        assertTrue(store.save("facing", doc.reference(), doc.attempts()));
        TurnProfileDocument back = new TurnProfileDocument();
        assertTrue(store.load("facing", back));
        assertEquals(exact, back.reference().facings()[0], 0.0);
        assertTrue(back.reference().sameAs(doc.reference()));
        TurnReference copy = back.reference().copy();
        assertEquals(exact, copy.facing(0), 0.0);
        assertTrue(Double.isNaN(copy.facing(1)));
    }

    @Test
    public void aBrokenHeaderFailsTheLoadWithAnError() throws Exception {
        Path dir = Files.createTempDirectory("pkc-onejump-header");
        TurnProfileStore store = store(dir);
        TurnProfileDocument doc = sample();
        assertTrue(store.save("header", doc.reference(), doc.attempts()));
        Path file = store.fileFor("header");
        List<String> lines = Files.readAllLines(file);
        lines.set(0, "{\"version\":2,\"rows\":[");
        Files.write(file, lines);
        TurnProfileDocument back = sample();
        assertFalse(store.load("header", back));
        assertNotNull(store.lastError());
        assertTrue(back.attempts().isEmpty());
    }
}
