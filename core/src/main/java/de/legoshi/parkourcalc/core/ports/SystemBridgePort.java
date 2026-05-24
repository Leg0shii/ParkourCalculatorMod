package de.legoshi.parkourcalc.core.ports;

import java.nio.file.Path;

/**
 * OS-level actions the Help / Settings menus invoke. Loader-side adapters use
 * java.awt.Desktop. Implementations should fail silently on unsupported environments.
 */
public interface SystemBridgePort {

    /** Open a folder in the OS file explorer. No-op if the platform cannot. */
    void openFolder(Path folder);

    /** Open a URL in the default browser. No-op if the platform cannot. */
    void openUrl(String url);
}
