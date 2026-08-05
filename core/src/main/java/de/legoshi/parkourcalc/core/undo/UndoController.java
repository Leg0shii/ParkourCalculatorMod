package de.legoshi.parkourcalc.core.undo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class UndoController {

    public static final long POLL_INTERVAL_NANOS = 250_000_000L;
    public static final long DEFAULT_BYTE_BUDGET = 16L << 20;
    public static final int DEFAULT_MAX_ENTRIES = 4096;

    private final Supplier<String> snapshotSource;
    private final Consumer<String> restorer;
    private final long byteBudget;
    private final int maxEntries;

    private final List<byte[]> entries = new ArrayList<>();
    private int cursor = -1;
    private long totalBytes;
    private String currentJson;
    private String pendingJson;
    private boolean polled;
    private long lastPollNanos;
    private UndoJournal journal;

    public UndoController(Supplier<String> snapshotSource, Consumer<String> restorer) {
        this(snapshotSource, restorer, DEFAULT_BYTE_BUDGET, DEFAULT_MAX_ENTRIES);
    }

    public UndoController(Supplier<String> snapshotSource, Consumer<String> restorer, long byteBudget, int maxEntries) {
        this.snapshotSource = snapshotSource;
        this.restorer = restorer;
        this.byteBudget = byteBudget;
        this.maxEntries = maxEntries;
    }

    public void tick(long nowNanos) {
        if (polled && nowNanos - lastPollNanos < POLL_INTERVAL_NANOS) return;
        polled = true;
        lastPollNanos = nowNanos;
        String json = snapshotSource.get();
        if (json == null) return;
        if (currentJson == null) {
            commit(json);
            pendingJson = null;
            return;
        }
        if (json.equals(currentJson)) {
            pendingJson = null;
            return;
        }
        if (json.equals(pendingJson)) {
            commit(json);
            pendingJson = null;
            return;
        }
        pendingJson = json;
    }

    public boolean undo() {
        if (!flushLive()) return false;
        if (cursor <= 0) return false;
        String json = decompress(entries.get(cursor - 1));
        if (json == null) return false;
        cursor--;
        applyRestore(json);
        return true;
    }

    public boolean redo() {
        if (!flushLive()) return false;
        if (cursor >= entries.size() - 1) return false;
        String json = decompress(entries.get(cursor + 1));
        if (json == null) return false;
        cursor++;
        applyRestore(json);
        return true;
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor >= 0 && cursor < entries.size() - 1;
    }

    public void onDocumentReplaced(UndoJournal newJournal) {
        entries.clear();
        cursor = -1;
        totalBytes = 0;
        currentJson = null;
        pendingJson = null;
        journal = newJournal;
        if (journal != null) {
            for (byte[] record : journal.read(byteBudget, maxEntries)) {
                entries.add(record);
                totalBytes += record.length;
            }
            cursor = entries.size() - 1;
        }
        String live = snapshotSource.get();
        if (live == null) return;
        if (cursor >= 0 && live.equals(decompress(entries.get(cursor)))) {
            currentJson = live;
            return;
        }
        commit(live);
    }

    public void bindJournal(UndoJournal newJournal) {
        if (newJournal == null) return;
        if (journal != null && journal.samePath(newJournal)) return;
        journal = newJournal;
        if (!journal.rewrite(entries)) journal = null;
    }

    public void unbindIf(UndoJournal other) {
        if (journal != null && journal.samePath(other)) journal = null;
    }

    private boolean flushLive() {
        String live = snapshotSource.get();
        if (live == null) return false;
        if (currentJson == null || !live.equals(currentJson)) {
            commit(live);
        }
        pendingJson = null;
        return true;
    }

    private void applyRestore(String json) {
        restorer.accept(json);
        currentJson = snapshotSource.get();
        pendingJson = null;
    }

    private void commit(String json) {
        boolean truncated = cursor < entries.size() - 1;
        while (entries.size() - 1 > cursor) {
            totalBytes -= entries.remove(entries.size() - 1).length;
        }
        byte[] record = compress(json);
        entries.add(record);
        totalBytes += record.length;
        cursor = entries.size() - 1;
        currentJson = json;
        while (entries.size() > 1 && (totalBytes > byteBudget || entries.size() > maxEntries)) {
            totalBytes -= entries.remove(0).length;
            cursor--;
        }
        if (journal == null) return;
        boolean ok = truncated ? journal.rewrite(entries) : journal.append(record);
        if (ok && journal.sizeBytes() > byteBudget * 2) {
            ok = journal.rewrite(entries);
        }
        if (!ok) journal = null;
    }

    private static byte[] compress(String json) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, json.length() / 8));
            GZIPOutputStream gz = new GZIPOutputStream(bos);
            gz.write(json.getBytes(StandardCharsets.UTF_8));
            gz.close();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decompress(byte[] record) {
        try {
            GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(record));
            ByteArrayOutputStream out = new ByteArrayOutputStream(record.length * 8);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
