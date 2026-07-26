package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public final class SolveRunLog {

    private final Path runsDir;
    private final String modVersion;
    private final String mcVersion;
    private final Set<String> dumpedProblems = new HashSet<String>();

    public SolveRunLog(Path runsDir, String modVersion, String mcVersion) {
        this.runsDir = runsDir;
        this.modVersion = modVersion;
        this.mcVersion = mcVersion;
    }

    public Path getRunsDir() {
        return runsDir;
    }

    public synchronized boolean needsProblem(String hash) {
        if (hash == null) return false;
        if (dumpedProblems.contains(hash)) return false;
        return !Files.isRegularFile(problemFile(hash));
    }

    public synchronized void writeProblem(String hash, String json) {
        if (hash == null || json == null) return;
        try {
            Path file = problemFile(hash);
            Files.createDirectories(file.getParent());
            if (!Files.isRegularFile(file)) {
                Files.write(file, json.getBytes(StandardCharsets.UTF_8));
            }
            dumpedProblems.add(hash);
        } catch (IOException ignored) {
        }
    }

    private Path problemFile(String hash) {
        return runsDir.resolve("problems").resolve(hash + ".json");
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
