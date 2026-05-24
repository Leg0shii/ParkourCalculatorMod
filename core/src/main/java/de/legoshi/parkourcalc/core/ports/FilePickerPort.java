package de.legoshi.parkourcalc.core.ports;

import java.nio.file.Path;

/**
 * Open-file picker for the Import .tas flow. Loader-side implementations show an OS
 * dialog and block the calling thread until the user picks or cancels. v1.3.0 accepts
 * a brief stall during the picker; if it becomes an issue, switch to an async API.
 */
public interface FilePickerPort {

    /** Returns the picked path, or null on cancel / failure. Filter: *.tas. */
    Path pickTasFile();
}
