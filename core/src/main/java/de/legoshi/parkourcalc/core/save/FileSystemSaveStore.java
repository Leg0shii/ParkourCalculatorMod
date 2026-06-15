package de.legoshi.parkourcalc.core.save;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Filesystem-backed save store. Files live as {@code <saveDir>/<name>.json}; deletes route
 * to {@code <saveDir>/.trash/<name>_<epochMs>.json} (Java 8 has no portable OS-trash hook).
 */
public final class FileSystemSaveStore {

    private static final String EXTENSION = ".json";
    private static final String TRASH_DIR = ".trash";
    private static final String TMP_SUFFIX = ".tmp";
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private final Path saveDir;
    private final String modVersion;
    private final String mcVersion;
    private final Supplier<WorldDescriptor> worldSupplier;

    private final Map<String, SaveInfo> infoCache = new HashMap<String, SaveInfo>();

    public FileSystemSaveStore(Path saveDir, String modVersion, String mcVersion, Supplier<WorldDescriptor> worldSupplier) {
        this.saveDir = saveDir;
        this.modVersion = modVersion;
        this.mcVersion = mcVersion;
        this.worldSupplier = worldSupplier;
    }

    public Path getSaveDir() {
        return saveDir;
    }

    public WorldDescriptor getWorldDescriptor() {
        return worldSupplier == null ? null : worldSupplier.get();
    }

    public String getModVersion() {
        return modVersion;
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public List<SaveInfo> list() {
        if (!Files.isDirectory(saveDir)) return Collections.emptyList();
        List<SaveInfo> infos = new ArrayList<>();
        Set<String> present = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(saveDir, "*" + EXTENSION)) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) continue;
                String file = p.getFileName().toString();
                String name = file.substring(0, file.length() - EXTENSION.length());
                long mtime;
                try {
                    FileTime ft = Files.getLastModifiedTime(p);
                    mtime = ft.toMillis();
                } catch (IOException e) {
                    mtime = 0L;
                }
                present.add(name);
                SaveInfo cached = infoCache.get(name);
                if (cached == null || cached.lastModifiedMs != mtime) {
                    cached = parseInfo(p, name, mtime);
                    infoCache.put(name, cached);
                }
                infos.add(cached);
            }
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
        infoCache.keySet().retainAll(present);
        return infos;
    }

    private static SaveInfo parseInfo(Path p, String name, long mtime) {
        try {
            SaveFile parsed = SaveIO.parseSafe(new String(Files.readAllBytes(p), CHARSET));
            if (parsed != null) {
                return new SaveInfo(name, mtime, parsed.mcVersion, WorldDescriptor.displayOf(parsed.world));
            }
        } catch (IOException ignored) {
        }
        return new SaveInfo(name, mtime, null, null);
    }

    public boolean exists(String name) {
        Path file = resolveSave(name);
        return file != null && Files.isRegularFile(file);
    }

    public String read(String name) throws IOException {
        Path file = resolveSave(name);
        if (file == null) throw new IOException("Invalid save path: " + name);
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, CHARSET);
    }

    public void write(String name, String contents) throws IOException {
        Path target = resolveSave(name);
        if (target == null) throw new IOException("Invalid save path: " + name);
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path tmp = parent.resolve(target.getFileName() + TMP_SUFFIX);
        Files.write(tmp, contents.getBytes(CHARSET));
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean moveToRecycleBin(String name) {
        Path file = resolveSave(name);
        if (file == null || !Files.exists(file)) return false;
        String flat = name.replace('/', '_');
        Path trash = saveDir.resolve(TRASH_DIR);
        try {
            Files.createDirectories(trash);
            Path target = trash.resolve(flat + "_" + System.currentTimeMillis() + EXTENSION);
            try {
                Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException e) {
                Files.move(file, target);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Path resolveSave(String relPath) {
        return resolveWithin(relPath + EXTENSION);
    }

    private Path resolveWithin(String relPath) {
        if (relPath == null) return null;
        Path base = saveDir.toAbsolutePath().normalize();
        Path resolved = base.resolve(relPath).normalize();
        if (!resolved.startsWith(base)) return null;
        return resolved;
    }

    public SaveBrowseResult browse(String relDir) {
        Path dir = (relDir == null || relDir.isEmpty()) ? saveDir.toAbsolutePath().normalize() : resolveWithin(relDir);
        if (dir == null || !Files.isDirectory(dir)) return SaveBrowseResult.empty();

        List<String> folders = new ArrayList<>();
        List<SaveInfo> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String fileName = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    if (TRASH_DIR.equals(fileName)) continue;
                    folders.add(fileName);
                } else if (Files.isRegularFile(p) && fileName.endsWith(EXTENSION)) {
                    String name = fileName.substring(0, fileName.length() - EXTENSION.length());
                    long mtime;
                    try {
                        mtime = Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                        mtime = 0L;
                    }
                    files.add(parseInfo(p, name, mtime));
                }
            }
        } catch (IOException e) {
            return SaveBrowseResult.empty();
        }
        return new SaveBrowseResult(folders, files);
    }

}
