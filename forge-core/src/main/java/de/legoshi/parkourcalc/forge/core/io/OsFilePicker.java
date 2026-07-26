package de.legoshi.parkourcalc.forge.core.io;

import de.legoshi.parkourcalc.core.ports.FilePickerPort;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** AWT FileDialog-based picker; Forge has no TinyFileDialogs on LWJGL2. */
public final class OsFilePicker implements FilePickerPort {

    @Override
    public Path pickJsonFile() {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("[ParkourCalculator] File picker unavailable: headless JVM");
            return null;
        }
        AtomicReference<Path> result = new AtomicReference<>();
        try {
            EventQueue.invokeAndWait(() -> result.set(showDialog()));
        } catch (Exception e) {
            System.err.println("[ParkourCalculator] File picker failed: " + e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
        return result.get();
    }

    private static Path showDialog() {
        Frame parent = new Frame();
        parent.setUndecorated(true);
        parent.setSize(1, 1);
        parent.setLocation(-2000, -2000);
        parent.setAlwaysOnTop(true);
        parent.setVisible(true);
        FileDialog dialog = new FileDialog(parent, "Import .json", FileDialog.LOAD);
        dialog.setFile("*.json");
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        dialog.setMultipleMode(false);
        dialog.setVisible(true);
        String file = dialog.getFile();
        String dir = dialog.getDirectory();
        dialog.dispose();
        parent.dispose();
        if (file == null) return null;
        return dir == null ? Paths.get(file) : Paths.get(dir, file);
    }
}
