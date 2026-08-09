package de.legoshi.parkourcalc.core.undo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class UndoJournal {

    private static final int MAGIC = 0x504B4355;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTES = 8;
    private static final int MAX_RECORD_BYTES = 64 << 20;

    private final Path file;
    private long sizeBytes;

    public UndoJournal(Path file) {
        this.file = file;
    }

    public boolean samePath(UndoJournal other) {
        return other != null && file.equals(other.file);
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public List<byte[]> read(long byteBudget, int maxEntries) {
        List<byte[]> all = new ArrayList<>();
        sizeBytes = 0;
        if (!Files.isRegularFile(file)) return all;
        try {
            sizeBytes = Files.size(file);
        } catch (IOException e) {
            return all;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) return all;
            while (true) {
                int len;
                try {
                    len = in.readInt();
                } catch (EOFException e) {
                    break;
                }
                if (len <= 0 || len > MAX_RECORD_BYTES) break;
                byte[] record = new byte[len];
                try {
                    in.readFully(record);
                } catch (EOFException e) {
                    break;
                }
                all.add(record);
            }
        } catch (IOException ignored) {
        }
        int start = all.size();
        long total = 0;
        while (start > 0) {
            long withPrev = total + all.get(start - 1).length;
            int count = all.size() - start + 1;
            if (start < all.size() && (withPrev > byteBudget || count > maxEntries)) break;
            total = withPrev;
            start--;
        }
        return start == 0 ? all : new ArrayList<>(all.subList(start, all.size()));
    }

    public boolean append(byte[] record) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            boolean fresh = !Files.isRegularFile(file);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)))) {
                if (fresh) {
                    out.writeInt(MAGIC);
                    out.writeInt(FORMAT_VERSION);
                    sizeBytes = HEADER_BYTES;
                }
                out.writeInt(record.length);
                out.write(record);
            }
            sizeBytes += 4 + record.length;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean rewrite(List<byte[]> records) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT_VERSION);
                for (byte[] record : records) {
                    out.writeInt(record.length);
                    out.write(record);
                }
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            long total = HEADER_BYTES;
            for (byte[] record : records) {
                total += 4 + record.length;
            }
            sizeBytes = total;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
