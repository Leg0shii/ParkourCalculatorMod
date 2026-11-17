package de.legoshi.parkourcalc.anglesolver.harness;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.replay.IpoptReplay;
import de.legoshi.parkourcalc.core.replay.StaticScene;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.forge.core.replay.LocalReplayOracle;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

public final class ReplayStandaloneIpopt {
    public static void main(String[] args) throws Exception {
        Path template = Paths.get(args[0]), input = Paths.get(args[1]), result = Paths.get(args[2]);
        Path output = Paths.get(args[3]).toAbsolutePath(), report = Paths.get(args[4]).toAbsolutePath();
        for (Path source : new Path[]{template, input, result})
            if (same(source, output) || same(source, report)) throw new IllegalArgumentException("Output/report must not overwrite an input");
        if (same(output, report)) throw new IllegalArgumentException("Output and report must be distinct");
        if (!args[5].isEmpty() && (same(Paths.get(args[5]), output) || same(Paths.get(args[5]), report)))
            throw new IllegalArgumentException("Output/report must not overwrite the scene");
        JsonObject summary = new JsonObject();
        try {
            JsonObject problem = object(input), solved = object(result);
            JsonObject candidate = solved.has("candidate") ? solved.getAsJsonObject("candidate") : solved;
            StaticScene scene = args[5].isEmpty() ? null : new StaticScene(object(Paths.get(args[5])));
            LocalReplayOracle oracle = new LocalReplayOracle(problem, scene);
            IpoptReplay replay = new IpoptReplay(SaveIO.parseSafe(read(template)), problem, candidate, Double.parseDouble(args[6]), Double.parseDouble(args[7]));
            while (!replay.isDone()) replay.advance(oracle);
            summary = replay.report();
            if (replay.validatedJson() != null) write(output, replay.validatedJson());
            summary.addProperty("outputWritten", replay.validatedJson() != null);
        } catch (RuntimeException error) {
            summary.addProperty("status", "error");
            summary.addProperty("reason", error.toString());
            summary.addProperty("outputWritten", false);
        }
        summary.addProperty("output", output.toString());
        write(report, new GsonBuilder().setPrettyPrinting().create().toJson(summary));
        System.out.println(summary);
    }

    private static boolean same(Path a, Path b) throws Exception {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()) || Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
    }

    static JsonObject object(Path path) throws Exception { return new JsonParser().parse(read(path)).getAsJsonObject(); }

    static String read(Path path) throws Exception {
        try (InputStream in = path.toString().endsWith(".gz") ? new GZIPInputStream(Files.newInputStream(path)) : Files.newInputStream(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void write(Path path, String json) throws Exception {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".ipopt-replay-", ".tmp");
        try {
            Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
