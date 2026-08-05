package de.legoshi.parkourcalc.core.undo;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UndoControllerTest {

    private static final long STEP = UndoController.POLL_INTERVAL_NANOS + 50_000_000L;

    private static final class Doc {
        String value;

        Doc(String value) {
            this.value = value;
        }
    }

    private long now = 1L;

    private static UndoController controller(Doc doc) {
        return new UndoController(() -> doc.value, json -> doc.value = json);
    }

    private void settle(UndoController u) {
        now += STEP;
        u.tick(now);
        now += STEP;
        u.tick(now);
    }

    @Test
    public void commitsAfterStablePollsAndRoundTrips() {
        Doc doc = new Doc("a");
        UndoController u = controller(doc);
        settle(u);
        assertFalse(u.canUndo());

        doc.value = "b";
        settle(u);
        assertTrue(u.canUndo());

        assertTrue(u.undo());
        assertEquals("a", doc.value);
        assertTrue(u.canRedo());
        assertTrue(u.redo());
        assertEquals("b", doc.value);
    }

    @Test
    public void coalescesWhileValueKeepsChanging() {
        Doc doc = new Doc("a");
        UndoController u = controller(doc);
        settle(u);

        doc.value = "b1";
        now += STEP;
        u.tick(now);
        doc.value = "b2";
        now += STEP;
        u.tick(now);
        doc.value = "b3";
        now += STEP;
        u.tick(now);
        assertFalse(u.canUndo());

        now += STEP;
        u.tick(now);
        assertTrue(u.canUndo());
        assertTrue(u.undo());
        assertEquals("a", doc.value);
    }

    @Test
    public void undoFlushesPendingEdit() {
        Doc doc = new Doc("a");
        UndoController u = controller(doc);
        settle(u);

        doc.value = "b";
        assertTrue(u.undo());
        assertEquals("a", doc.value);
        assertTrue(u.redo());
        assertEquals("b", doc.value);
    }

    @Test
    public void newEditDropsRedoTail() {
        Doc doc = new Doc("a");
        UndoController u = controller(doc);
        settle(u);
        doc.value = "b";
        settle(u);
        assertTrue(u.undo());
        assertEquals("a", doc.value);

        doc.value = "c";
        settle(u);
        assertFalse(u.canRedo());
        assertFalse(u.redo());
        assertEquals("c", doc.value);
        assertTrue(u.undo());
        assertEquals("a", doc.value);
    }

    @Test
    public void identicalSnapshotsAreDeduped() {
        Doc doc = new Doc("a");
        UndoController u = controller(doc);
        settle(u);
        settle(u);
        settle(u);
        assertFalse(u.canUndo());
        assertFalse(u.undo());
    }

    @Test
    public void byteBudgetEvictsOldestEntries() {
        Doc doc = new Doc("v0");
        UndoController u = new UndoController(() -> doc.value, json -> doc.value = json, 300L, 1000);
        settle(u);
        for (int i = 1; i <= 20; i++) {
            doc.value = "v" + i;
            settle(u);
        }
        int undos = 0;
        while (u.undo()) {
            undos++;
        }
        assertTrue("eviction keeps the stack under the budget, undos=" + undos, undos < 19);
        assertTrue("some undo depth survives", undos >= 1);
        assertFalse("the oldest entry was evicted", "v0".equals(doc.value));
    }

    @Test
    public void journalPersistsAcrossControllers() throws Exception {
        Path dir = Files.createTempDirectory("pkc-undo-journal");
        Path file = dir.resolve("run.undo");

        Doc doc = new Doc("a");
        UndoController u1 = controller(doc);
        u1.onDocumentReplaced(new UndoJournal(file));
        doc.value = "b";
        settle(u1);
        doc.value = "c";
        settle(u1);

        Doc doc2 = new Doc("c");
        UndoController u2 = controller(doc2);
        u2.onDocumentReplaced(new UndoJournal(file));
        assertTrue(u2.canUndo());
        assertTrue(u2.undo());
        assertEquals("b", doc2.value);
        assertTrue(u2.undo());
        assertEquals("a", doc2.value);
        assertFalse(u2.undo());
    }

    @Test
    public void journalAdoptionPushesDivergedDocument() throws Exception {
        Path dir = Files.createTempDirectory("pkc-undo-diverge");
        Path file = dir.resolve("run.undo");

        Doc doc = new Doc("a");
        UndoController u1 = controller(doc);
        u1.onDocumentReplaced(new UndoJournal(file));
        doc.value = "b";
        settle(u1);

        Doc doc2 = new Doc("x");
        UndoController u2 = controller(doc2);
        u2.onDocumentReplaced(new UndoJournal(file));
        assertEquals("x", doc2.value);
        assertTrue(u2.undo());
        assertEquals("b", doc2.value);
        assertTrue(u2.undo());
        assertEquals("a", doc2.value);
    }

    @Test
    public void journalToleratesTruncatedTail() throws Exception {
        Path dir = Files.createTempDirectory("pkc-undo-trunc");
        Path file = dir.resolve("run.undo");

        Doc doc = new Doc("a");
        UndoController u1 = controller(doc);
        u1.onDocumentReplaced(new UndoJournal(file));
        doc.value = "b";
        settle(u1);

        Files.write(file, new byte[] { 0, 0, 3, -24, 1, 2, 3 }, StandardOpenOption.APPEND);

        Doc doc2 = new Doc("b");
        UndoController u2 = controller(doc2);
        u2.onDocumentReplaced(new UndoJournal(file));
        assertTrue(u2.undo());
        assertEquals("a", doc2.value);
    }

    @Test
    public void truncationAfterUndoRewritesJournal() throws Exception {
        Path dir = Files.createTempDirectory("pkc-undo-rewrite");
        Path file = dir.resolve("run.undo");

        Doc doc = new Doc("a");
        UndoController u1 = controller(doc);
        u1.onDocumentReplaced(new UndoJournal(file));
        doc.value = "b";
        settle(u1);
        doc.value = "c";
        settle(u1);
        assertTrue(u1.undo());
        assertEquals("b", doc.value);
        doc.value = "d";
        settle(u1);

        Doc doc2 = new Doc("d");
        UndoController u2 = controller(doc2);
        u2.onDocumentReplaced(new UndoJournal(file));
        assertTrue(u2.undo());
        assertEquals("b", doc2.value);
        assertTrue(u2.undo());
        assertEquals("a", doc2.value);
        assertFalse("the dropped redo tail must not resurface", u2.undo());
    }
}
