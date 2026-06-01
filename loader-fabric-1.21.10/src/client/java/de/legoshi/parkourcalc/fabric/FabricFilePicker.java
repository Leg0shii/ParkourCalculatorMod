package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.FilePickerPort;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.nio.file.Paths;

/** LWJGL3 TinyFileDialogs picker. Invokes the OS-native file dialog (GetOpenFileName on
 *  Windows, NSOpenPanel on macOS, zenity/kdialog on Linux). No AWT/Swing. */
public final class FabricFilePicker implements FilePickerPort {

    @Override
    public Path pickTasFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json"));
            filters.flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import .json",
                    null,
                    filters,
                    "TAS files (*.json)",
                    false
            );
            return (result == null || result.isEmpty()) ? null : Paths.get(result);
        } catch (Throwable t) {
            System.err.println("[ParkourCalculator] File picker failed: " + t);
            return null;
        }
    }
}
