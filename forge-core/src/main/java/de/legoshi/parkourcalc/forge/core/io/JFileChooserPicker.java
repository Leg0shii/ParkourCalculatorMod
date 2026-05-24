package de.legoshi.parkourcalc.forge.core.io;

import de.legoshi.parkourcalc.core.ports.FilePickerPort;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared Swing JFileChooser-based picker for both Forge loaders. invokeAndWait keeps the
 * dialog on the EDT and blocks the game thread until the user picks or cancels. The MC
 * client freezes during this; v1.3.0 accepts that, async polling is a future refactor.
 */
public final class JFileChooserPicker implements FilePickerPort {

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
