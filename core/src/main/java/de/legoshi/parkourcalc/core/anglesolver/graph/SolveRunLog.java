package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class SolveRunLog {

    private final Path runsDir;
    private final String modVersion;
    private final String mcVersion;

    public SolveRunLog(Path runsDir, String modVersion, String mcVersion) {
        this.runsDir = runsDir;
        this.modVersion = modVersion;
        this.mcVersion = mcVersion;
    }

    public Path getRunsDir() {
        return runsDir;
    }

    public synchronized void append(SolveRunRecord record) {
        record.modVersion = modVersion;
        record.mcVersion = mcVersion;
        record.finishedEpochMs = System.currentTimeMillis();
        String line = SolveRunRecord.toJsonLine(record) + "\n";
        Path file = runsDir.resolve(new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".jsonl");
        try {
            Files.createDirectories(runsDir);
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
