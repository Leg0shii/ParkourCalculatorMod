package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.SystemBridgePort;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/** java.awt.Desktop-backed bridge for Fabric. Same shape as the Forge bridge. */
public final class FabricSystemBridge implements SystemBridgePort {

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
