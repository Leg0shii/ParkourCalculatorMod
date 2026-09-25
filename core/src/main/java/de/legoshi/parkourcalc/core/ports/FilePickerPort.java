package de.legoshi.parkourcalc.core.ports;

import java.nio.file.Path;

/** Open-file picker for the Import .json flow. Blocks until pick or cancel. */
public interface FilePickerPort {

    /** Returns the picked path, or null on cancel / failure. Filter: *.json. */
    Path pickJsonFile();

    default Path pickFolder() {
        return null;
    }

    default boolean supportsFolderPick() {
        return false;
    }
}
