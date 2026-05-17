package de.legoshi.parkourcalc.core.save;

import de.legoshi.parkourcalc.core.ports.SaveStore;

import java.io.IOException;
import java.nio.charset.Charset;
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
 * Filesystem-backed SaveStore. Files live as {@code <saveDir>/<name>.json}; deletes route
 * to {@code <saveDir>/.trash/<name>_<epochMs>.json} (Java 8 has no portable OS-trash hook).
 */
public final class FileSystemSaveStore implements SaveStore {

    private static final String EXTENSION = ".json";
    private static final String TRASH_DIR = ".trash";
    private static final String TMP_SUFFIX = ".tmp";
    private static final Charset CHARSET = Charset.forName("UTF-8");

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

    @Override
    public Path getSaveDir() {
        return saveDir;
    }

    @Override
    public WorldDescriptor getWorldDescriptor() {
        return worldSupplier == null ? null : worldSupplier.get();
    }

    @Override
    public String getModVersion() {
        return modVersion;
    }

    @Override
    public String getMcVersion() {
        return mcVersion;
    }

    @Override
    public List<SaveInfo> list() {
        if (!Files.isDirectory(saveDir)) return Collections.emptyList();
        List<SaveInfo> infos = new ArrayList<SaveInfo>();
        Set<String> present = new HashSet<String>();
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

    @Override
    public boolean exists(String name) {
        return Files.isRegularFile(saveDir.resolve(name + EXTENSION));
    }

    @Override
    public String read(String name) throws IOException {
        Path file = saveDir.resolve(name + EXTENSION);
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, CHARSET);
    }

    @Override
    public void write(String name, String contents) throws IOException {
        Files.createDirectories(saveDir);
        Path target = saveDir.resolve(name + EXTENSION);
        Path tmp = saveDir.resolve(name + EXTENSION + TMP_SUFFIX);
        Files.write(tmp, contents.getBytes(CHARSET));
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public boolean moveToRecycleBin(String name) {
        Path file = saveDir.resolve(name + EXTENSION);
        if (!Files.exists(file)) return false;
        Path trash = saveDir.resolve(TRASH_DIR);
        try {
            Files.createDirectories(trash);
            Path target = trash.resolve(name + "_" + System.currentTimeMillis() + EXTENSION);
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

}
