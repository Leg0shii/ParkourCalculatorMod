package de.legoshi.parkourcalc.core.ports;

import java.nio.file.Path;

/** OS-level actions (open folder, open URL) for the Help/Settings menus. */
public interface SystemBridgePort {

    /** Open a folder in the OS file explorer. No-op if the platform cannot. */
    void openFolder(Path folder);

    /** Open a URL in the default browser. No-op if the platform cannot. */
    void openUrl(String url);
}
