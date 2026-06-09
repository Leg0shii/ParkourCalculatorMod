package de.legoshi.parkourcalc.anglesolver.harness;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Per-problem expectations from an optional {@code <name>.expect.json} sidecar. Any null field falls back to
 *  a default (the folder name decides the check kind; other defaults come from the capture's own recorded
 *  result), so a problem with no sidecar still runs. */
public final class Expect {

    public String check;           // "solve" | "closedform" | "blocks" | "keepoutSide"; default: the folder name
    public Boolean shouldSolve;    // solve: default the capture's angleSolver.result.success
    public String effort;          // "FAST" | "BALANCED" | "THOROUGH"; default FAST
    public Long maxSolveMs;        // solve: wall-clock budget; null = no timing assertion
    public Integer minMet;         // solve: require >= this many constraints met; null = require full success
    public Boolean allDirections;  // solve: every axis x goal must solve (not just the saved direction)

    public Double refObjective;    // closedform: recorded in-game objective the solve must not regress past
    public Double maxObjectiveGap; // closedform: max objective shortfall vs refObjective (default 1e-2)
    public Double maxMicros;       // closedform: max us per single solve (default 2000)

    private static final Gson GSON = new Gson();

    public static Expect load(File sidecar) {
        if (sidecar == null || !sidecar.isFile()) return new Expect();
        try {
            String json = new String(Files.readAllBytes(sidecar.toPath()), StandardCharsets.UTF_8);
            Expect e = GSON.fromJson(json, Expect.class);
            return e != null ? e : new Expect();
        } catch (Exception ex) {
            throw new RuntimeException("bad sidecar: " + sidecar, ex);
        }
    }

    public String check(String defaultCheck) {
        return check != null ? check : defaultCheck;
    }

    public boolean shouldSolve(SaveFile file) {
        if (shouldSolve != null) return shouldSolve;
        return file.angleSolver != null && file.angleSolver.result != null && file.angleSolver.result.success;
    }

    public boolean allDirections() {
        return allDirections != null && allDirections;
    }

    public AngleSolverState.Effort effort() {
        return effort == null ? AngleSolverState.Effort.FAST : AngleSolverState.Effort.valueOf(effort);
    }

    public double maxObjectiveGap() {
        return maxObjectiveGap != null ? maxObjectiveGap : 1.0e-2;
    }

    public double maxMicros() {
        return maxMicros != null ? maxMicros : 2000.0;
    }
}
