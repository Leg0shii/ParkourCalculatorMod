package de.legoshi.parkourcalc.forge.core.io;

import de.legoshi.parkourcalc.core.ports.SystemBridgePort;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/** java.awt.Desktop-backed bridge. Shared by both Forge loaders. */
public final class AwtSystemBridge implements SystemBridgePort {

    @Override
    public void openFolder(Path folder) {
        if (folder == null) return;
        if (!Desktop.isDesktopSupported()) return;
        try {
            Desktop.getDesktop().open(folder.toFile());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        if (!Desktop.isDesktopSupported()) return;
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
        }
    }
}
