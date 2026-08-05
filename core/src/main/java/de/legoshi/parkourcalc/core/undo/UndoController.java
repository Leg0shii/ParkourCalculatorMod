package de.legoshi.parkourcalc.core.undo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class UndoController<T> {

    public static final long POLL_INTERVAL_NANOS = 250_000_000L;
    public static final long DEFAULT_BYTE_BUDGET = 16L << 20;
    public static final int DEFAULT_MAX_ENTRIES = 4096;

    private static ExecutorService worker;

    private final Supplier<String> signatureSource;
    private final Supplier<T> snapshotSource;
    private final Function<T, String> serializer;
    private final Consumer<String> restorer;
    private final long byteBudget;
    private final int maxEntries;

    private final List<byte[]> entries = new ArrayList<>();
    private int cursor = -1;
    private long totalBytes;
    private String currentJson;
    private String currentSignature;
    private String pendingSignature;
    private boolean polled;
    private long lastPollNanos;
    private UndoJournal journal;
    private volatile boolean journalDead;
    private Future<CaptureResult> pendingCapture;
    private String pendingCaptureSignature;

    private static final class CaptureResult {
        final String json;
        final byte[] record;

        CaptureResult(String json, byte[] record) {
            this.json = json;
            this.record = record;
        }
    }

    public UndoController(Supplier<String> signatureSource, Supplier<T> snapshotSource,
                          Function<T, String> serializer, Consumer<String> restorer) {
        this(signatureSource, snapshotSource, serializer, restorer, DEFAULT_BYTE_BUDGET, DEFAULT_MAX_ENTRIES);
    }

    public UndoController(Supplier<String> signatureSource, Supplier<T> snapshotSource,
                          Function<T, String> serializer, Consumer<String> restorer,
                          long byteBudget, int maxEntries) {
        this.signatureSource = signatureSource;
        this.snapshotSource = snapshotSource;
        this.serializer = serializer;
        this.restorer = restorer;
        this.byteBudget = byteBudget;
        this.maxEntries = maxEntries;
    }

    public void tick(long nowNanos) {
        if (polled && nowNanos - lastPollNanos < POLL_INTERVAL_NANOS) return;
        polled = true;
        lastPollNanos = nowNanos;
        harvestCapture(false);
        if (pendingCapture != null) return;
        String sig = signatureSource.get();
        if (sig == null) return;
        if (currentSignature == null) {
            beginCapture(sig);
            pendingSignature = null;
            return;
        }
        if (sig.equals(currentSignature)) {
            pendingSignature = null;
            return;
        }
        if (sig.equals(pendingSignature)) {
            beginCapture(sig);
            pendingSignature = null;
            return;
        }
        pendingSignature = sig;
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

    public void awaitPendingCapture() {
        harvestCapture(true);
        drainWorker();
    }

    public void onDocumentReplaced(UndoJournal newJournal) {
        harvestCapture(true);
        drainWorker();
        entries.clear();
        cursor = -1;
        totalBytes = 0;
        currentJson = null;
        currentSignature = null;
        pendingSignature = null;
        journal = newJournal;
        journalDead = false;
        if (journal != null) {
            for (byte[] record : journal.read(byteBudget, maxEntries)) {
                entries.add(record);
                totalBytes += record.length;
            }
            cursor = entries.size() - 1;
        }
        if (cursor >= 0) currentJson = decompress(entries.get(cursor));
        String sig = signatureSource.get();
        if (sig == null) return;
        T dto = snapshotSource.get();
        if (dto == null) return;
        String live = serializer.apply(dto);
        if (live == null) return;
        if (currentJson != null && live.equals(currentJson)) {
            currentSignature = sig;
            return;
        }
        commit(live, compress(live), sig);
    }

    public void bindJournal(UndoJournal newJournal) {
        if (newJournal == null) return;
        if (journal != null && journal.samePath(newJournal)) return;
        harvestCapture(true);
        journal = newJournal;
        journalDead = false;
        List<byte[]> copy = new ArrayList<>(entries);
        worker().execute(() -> {
            if (!newJournal.rewrite(copy)) journalDead = true;
        });
    }

    public void unbindIf(UndoJournal other) {
        if (journal != null && journal.samePath(other)) {
            drainWorker();
            journal = null;
        }
    }

    private boolean flushLive() {
        harvestCapture(true);
        String sig = signatureSource.get();
        if (sig == null) return false;
        if (currentSignature == null || !sig.equals(currentSignature)) {
            if (!beginCapture(sig)) return false;
            harvestCapture(true);
        }
        pendingSignature = null;
        return true;
    }

    private boolean beginCapture(String sig) {
        T dto = snapshotSource.get();
        if (dto == null) return false;
        String topJson = currentJson;
        pendingCaptureSignature = sig;
        pendingCapture = worker().submit(() -> {
            String json = serializer.apply(dto);
            if (json == null) throw new IllegalStateException("null snapshot json");
            if (json.equals(topJson)) return new CaptureResult(json, null);
            return new CaptureResult(json, compress(json));
        });
        return true;
    }

    private void harvestCapture(boolean block) {
        if (pendingCapture != null && (block || pendingCapture.isDone())) {
            try {
                CaptureResult r = pendingCapture.get(30, TimeUnit.SECONDS);
                if (r.record == null) {
                    currentJson = r.json;
                    currentSignature = pendingCaptureSignature;
                } else {
                    commit(r.json, r.record, pendingCaptureSignature);
                }
            } catch (Exception ignored) {
            }
            pendingCapture = null;
            pendingCaptureSignature = null;
        }
        if (journalDead) {
            journal = null;
            journalDead = false;
        }
    }

    private void applyRestore(String json) {
        restorer.accept(json);
        currentJson = json;
        currentSignature = signatureSource.get();
        pendingSignature = null;
    }

    private void commit(String json, byte[] record, String sig) {
        boolean truncated = cursor < entries.size() - 1;
        while (entries.size() - 1 > cursor) {
            totalBytes -= entries.remove(entries.size() - 1).length;
        }
        entries.add(record);
        totalBytes += record.length;
        cursor = entries.size() - 1;
        currentJson = json;
        currentSignature = sig;
        while (entries.size() > 1 && (totalBytes > byteBudget || entries.size() > maxEntries)) {
            totalBytes -= entries.remove(0).length;
            cursor--;
        }
        UndoJournal target = journal;
        if (target == null) return;
        List<byte[]> copy = new ArrayList<>(entries);
        long budget = byteBudget;
        worker().execute(() -> {
            boolean ok = truncated ? target.rewrite(copy) : target.append(record);
            if (ok && target.sizeBytes() > budget * 2) ok = target.rewrite(copy);
            if (!ok) journalDead = true;
        });
    }

    private void drainWorker() {
        if (worker == null) return;
        try {
            worker.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static ExecutorService worker() {
        if (worker == null) {
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "pkc-undo-writer");
                t.setDaemon(true);
                return t;
            });
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                executor.shutdown();
                try {
                    executor.awaitTermination(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }, "pkc-undo-flush"));
            worker = executor;
        }
        return worker;
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
