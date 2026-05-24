package de.legoshi.parkourcalc.forge.core.io;

import de.legoshi.parkourcalc.core.ports.SystemBridgePort;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** OS-shell-backed bridge for both Forge loaders. Spawns the platform's file manager /
 *  browser via ProcessBuilder. No AWT/Swing. */
public final class OsSystemBridge implements SystemBridgePort {

    @Override
    public void openFolder(Path folder) {
        if (folder == null) return;
        runAsync(new Runnable() {
            @Override
            public void run() {
                if (!openFolderNow(folder)) {
                    System.err.println("[ParkourCalculator] Failed to open folder: " + folder);
                }
            }
        });
    }

    @Override
    public void openUrl(final String url) {
        if (url == null || url.isEmpty()) return;
        runAsync(new Runnable() {
            @Override
            public void run() {
                if (!openUrlNow(url)) {
                    System.err.println("[ParkourCalculator] Failed to open URL: " + url);
                }
            }
        });
    }

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r, "ParkourCalculator-SystemBridge");
        t.setDaemon(true);
        t.start();
    }

    private static boolean openFolderNow(Path folder) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", folder.toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", folder.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", folder.toString()).start();
            }
            return true;
        } catch (IOException t) {
            return false;
        }
    }

    private static boolean openUrlNow(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
            return true;
        } catch (IOException t) {
            return false;
        }
    }
}
