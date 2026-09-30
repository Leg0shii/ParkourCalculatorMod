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
        rows.add(r0);
        rows.add(r1);
        doc.reference().replace(rows, new boolean[] {true, false}, new boolean[] {true, true});
        doc.reference().setTasFirstTick(26);
        doc.reference().setLanding(new TurnReference.Landing(35, 186.32, Double.NaN, Double.NaN, 873.05));
        doc.touch();
        doc.add(new TurnAttempt(1, 26, new double[] {-12.4, -20.0}, 2, true, true, false, "landed", -0.012, 27, -1, 0, 0, 0));
        doc.add(new TurnAttempt(2, 26, new double[] {-12.4, 0.0}, 1, true, false, true, "input failure", Double.NaN, -1, 27,
                TurnReference.KEY_W, TurnReference.KEY_W | TurnReference.KEY_A, 0));
        return doc;
    }

    private static TurnProfileStore store(Path dir) {
        FileSystemSaveStore fs = new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
        return new TurnProfileStore(() -> fs);
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
        Path file = dir.resolveSibling(TurnProfileStore.DIRECTORY).resolve("d10_j335" + TurnProfileStore.EXTENSION);
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
        TurnAttempt a3 = new TurnAttempt(3, 26, new double[] {-12.0, -20.5}, 2, true, false, false, "short", 0.031, 27, -1, 0, 0, 0);
        doc.add(a3);
        assertTrue(doc.hasPending());
        assertFalse(doc.isReferenceDirty());
        List<TurnAttempt> pending = doc.drainPending();
        assertEquals(1, pending.size());
        assertFalse(doc.hasPending());
        assertTrue(store.append("j1", pending));
        Path file = dir.resolveSibling(TurnProfileStore.DIRECTORY).resolve("j1" + TurnProfileStore.EXTENSION);
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
            doc.add(new TurnAttempt(i + 1, 0, new double[] {0.0}, 1, true, margin <= 0.0, false, "", margin, -1, -1, 0, 0, 0));
        }
        assertEquals(TurnProfileDocument.TOP, doc.top().size());
        doc.add(new TurnAttempt(101, 0, new double[] {0.0}, 1, true, true, false, "", -1.0, -1, -1, 0, 0, 1));
        doc.add(new TurnAttempt(102, 0, new double[] {0.0}, 1, true, false, false, "", 0.2, -1, -1, 0, 0, 2));
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
}
