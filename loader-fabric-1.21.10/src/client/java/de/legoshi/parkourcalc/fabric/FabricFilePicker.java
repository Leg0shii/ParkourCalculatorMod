package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.FilePickerPort;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Swing JFileChooser-based picker for Fabric. Same shape as the Forge picker. */
public final class FabricFilePicker implements FilePickerPort {

    @Override
    public Path pickTasFile() {
        AtomicReference<Path> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Import .tas");
                chooser.setFileFilter(new FileNameExtensionFilter("TAS files (*.tas)", "tas"));
                chooser.setMultiSelectionEnabled(false);
                int choice = chooser.showOpenDialog(null);
                if (choice == JFileChooser.APPROVE_OPTION) {
                    File f = chooser.getSelectedFile();
                    if (f != null) result.set(f.toPath());
                }
            });
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ignored) {
        }
        return result.get();
    }
}
