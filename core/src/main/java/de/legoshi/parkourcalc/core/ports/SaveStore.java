package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.save.WorldDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface SaveStore {

    Path getSaveDir();

    WorldDescriptor getWorldDescriptor();

    String getModVersion();

    String getMcVersion();

    List<SaveInfo> list();

    boolean exists(String name);

    String read(String name) throws IOException;

    void write(String name, String contents) throws IOException;

    /** Best-effort move to OS trash; loaders unable to reach it may fall back to plain delete or return false. */
    boolean moveToRecycleBin(String name);
}
