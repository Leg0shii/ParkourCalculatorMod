package de.legoshi.parkourcalc.core.io;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Open-folder / open-URL helper for the Help/Settings menus. Uses java.awt.Desktop, or a
 *  loader-installed opener where AWT is unavailable (modern MC clients force java.awt.headless). */
public final class OsSystemBridge {

    private static volatile Consumer<Path> folderOpener;
    private static volatile Consumer<String> urlOpener;

    public static void setPlatformOpeners(Consumer<Path> folder, Consumer<String> url) {
        folderOpener = folder;
        urlOpener = url;
    }

    /** Open a folder in the OS file explorer. No-op if the platform cannot. */
    public void openFolder(Path folder) {
        if (folder == null) return;
        runAsync(() -> {
            if (!openFolderNow(folder)) {
                System.err.println("[ParkourCalculator] Failed to open folder: " + folder);
            }
        });
    }

    /** Open a URL in the default browser. No-op if the platform cannot. */
    public void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        runAsync(() -> {
            if (!openUrlNow(url)) {
                System.err.println("[ParkourCalculator] Failed to open URL: " + url);
            }
        });
    }

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r, "ParkourCalculator-SystemBridge");
        t.setDaemon(true);
        t.start();
    }

    private static boolean openFolderNow(Path folder) {
        Consumer<Path> opener = folderOpener;
        if (opener != null) {
            try {
                opener.accept(folder);
                return true;
            } catch (RuntimeException t) {
                return false;
            }
        }
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) return false;
            desktop.open(folder.toFile());
            return true;
        } catch (Exception t) {
            return false;
        }
    }

    private static boolean openUrlNow(String url) {
        Consumer<String> opener = urlOpener;
        if (opener != null) {
            try {
                opener.accept(url);
                return true;
            } catch (RuntimeException t) {
                return false;
            }
        }
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
            desktop.browse(new URI(url));
            return true;
        } catch (Exception t) {
            return false;
        }
    }
}
