package de.legoshi.parkourcalc.core.ports;

import java.nio.file.Path;

/** Open-file picker for the Import .tas flow. Blocks until pick or cancel. */
public interface FilePickerPort {

    /** Returns the picked path, or null on cancel / failure. Filter: *.tas. */
    Path pickTasFile();
}
