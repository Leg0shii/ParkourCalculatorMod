package de.legoshi.parkourcalc.anglesolver.harness;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.forge.core.replay.FixedMovementCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class CompileStandaloneIpoptPhysics {
    public static void main(String[] args) throws Exception {
        Path template = Paths.get(args[0]).toAbsolutePath(), input = Paths.get(args[1]).toAbsolutePath(), output = Paths.get(args[2]).toAbsolutePath();
        if (output.normalize().equals(template.normalize()) || output.normalize().equals(input.normalize())
                || Files.exists(output) && (Files.isSameFile(output, template) || Files.isSameFile(output, input)))
            throw new IllegalArgumentException("Write corrected physics to a separate output file");
        JsonObject compiled = FixedMovementCompiler.compile(SaveIO.parseSafe(ReplayStandaloneIpopt.read(template)), ReplayStandaloneIpopt.object(input));
        ReplayStandaloneIpopt.write(output, new GsonBuilder().setPrettyPrinting().create().toJson(compiled));
        System.out.println("Compiled mod input/sprint movement for " + compiled.get("ticks") + " inputs into " + output);
    }
}
