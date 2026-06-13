package de.legoshi.parkourcalc.core.save;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SaveSubfolderTest {

    private static FileSystemSaveStore storeAt(Path dir) {
        return new FileSystemSaveStore(dir, "test", "1.8.9", () -> null);
    }

    @Test
    public void sanitizeRelativeKeepsBareNamesAndCleansSegments() {
        assertEquals("run", SaveIO.sanitizeRelative("run"));
        assertEquals("maps/spawn/run", SaveIO.sanitizeRelative("maps/spawn/run"));
        assertEquals("maps/run", SaveIO.sanitizeRelative("maps\\run"));
        assertEquals("maps/run", SaveIO.sanitizeRelative("/maps//run/"));
        assertEquals("a_b/c", SaveIO.sanitizeRelative("a b/c"));
    }

    @Test
    public void sanitizeRelativeRejectsTraversalAndEmpty() {
        assertNull(SaveIO.sanitizeRelative(null));
        assertNull(SaveIO.sanitizeRelative(""));
        assertNull(SaveIO.sanitizeRelative("   "));
        assertNull(SaveIO.sanitizeRelative("/"));
        assertNull(SaveIO.sanitizeRelative(".."));
        assertNull(SaveIO.sanitizeRelative("../secret"));
        assertNull(SaveIO.sanitizeRelative("maps/../../etc"));
        assertNull(SaveIO.sanitizeRelative("maps/."));
    }

    @Test
    public void writeReadAndExistsTraverseSubfolders() throws IOException {
        FileSystemSaveStore store = storeAt(Files.createTempDirectory("pkc-sub"));
        store.write("maps/spawn/run", "{\"hello\":1}");

        assertTrue(store.exists("maps/spawn/run"));
        assertFalse(store.exists("maps/spawn/missing"));
        assertEquals("{\"hello\":1}", store.read("maps/spawn/run"));
        assertTrue(Files.isRegularFile(store.getSaveDir().resolve("maps/spawn/run.json")));
    }

    @Test
    public void browseListsFoldersAndFilesAndHidesTrash() throws IOException {
        Path dir = Files.createTempDirectory("pkc-browse");
        FileSystemSaveStore store = storeAt(dir);
        store.write("root", "{\"a\":1}");
        store.write("maps/alpha", "{\"a\":1}");
        store.write("maps/beta", "{\"a\":1}");
        store.moveToRecycleBin("root");
        store.write("root", "{\"a\":1}");

        SaveBrowseResult top = store.browse("");
        assertTrue("sub-folder is listed", top.folders.contains("maps"));
        assertFalse(".trash must stay hidden", top.folders.contains(".trash"));
        assertEquals("one .json file at root", 1, top.files.size());
        assertEquals("root", top.files.get(0).name);

        SaveBrowseResult inside = store.browse("maps");
        assertTrue(inside.folders.isEmpty());
        assertEquals(2, inside.files.size());
        List<String> names = new java.util.ArrayList<>();
        for (SaveInfo info : inside.files) names.add(info.name);
        assertTrue(names.contains("alpha"));
        assertTrue(names.contains("beta"));
    }

    @Test
    public void browseRefusesToEscapeBaseDirectory() throws IOException {
        Path base = Files.createTempDirectory("pkc-escape").resolve("saves");
        Files.createDirectories(base);
        Path sibling = base.resolveSibling("outside");
        Files.createDirectories(sibling);
        Files.write(sibling.resolve("secret.json"), "{\"x\":1}".getBytes(StandardCharsets.UTF_8));

        FileSystemSaveStore store = storeAt(base);
        SaveBrowseResult escaped = store.browse("../outside");
        assertNotNull(escaped);
        assertTrue("traversal out of the save dir yields nothing", escaped.folders.isEmpty());
        assertTrue(escaped.files.isEmpty());
    }
}
