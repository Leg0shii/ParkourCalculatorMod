package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.save.WorldDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Loader-side persistence + environment queries for the save/load feature.
 * Core defines the shape; each loader implements run-dir lookup, world id,
 * server address, and the recycle-bin semantics for its platform.
 *
 * Names handed to read/write/moveToRecycleBin are the bare save name (no
 * extension, no path separators). Core's SaveIO performs sanitization
 * before any of these methods are called.
 */
public interface SaveStore {

    /** Directory where {@code <name>.json} files live. Created if missing. */
    Path getSaveDir();

    /** Current world context, or null when at title screen / out of world. */
    WorldDescriptor getWorldDescriptor();

    /** {@code mod_version} from gradle.properties at runtime. */
    String getModVersion();

    /** Minecraft version the loader targets (e.g. "1.21.10", "1.12.2", "1.8.9"). */
    String getMcVersion();

    /** Save entries present in {@link #getSaveDir()}, each with name + last-modified epoch ms. */
    List<SaveInfo> list();

    /** Reads the file contents for the given save name. Throws if missing. */
    String read(String name) throws IOException;

    /** Writes JSON contents to {@code <saveDir>/<name>.json}, creating parents. */
    void write(String name, String contents) throws IOException;

    /**
     * Best-effort move of {@code <name>.json} to the OS recycle bin / trash.
     * Returns true on success. Loaders that can't reach the trash may fall back
     * to plain deletion or return false; core treats false as "still present".
     */
    boolean moveToRecycleBin(String name);
}
