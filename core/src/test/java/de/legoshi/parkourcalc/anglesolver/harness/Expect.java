package de.legoshi.parkourcalc.anglesolver.harness;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Per-problem expectations from an optional {@code <name>.expect.json} sidecar. Any null field falls back to
 *  a default (the folder name decides the check kind; other defaults come from the capture's own recorded
 *  result), so a problem with no sidecar still runs. The field list is the sidecar schema; which check reads
 *  which field is documented in {@code resources/problems/README.md}. */
public final class Expect {

    public String check;
    public String capture;
    public Boolean shouldSolve;
    public String effort;
    public Integer optimizeSeconds;
    public Long maxSolveMs;
    public Long timeoutMs;
    public Integer minMet;
    public Boolean allDirections;
    public String noticeLabel;
    public List<String> noticeContains;
    public String verdict;
    public Double maxRecomputeDivergence;
    public Double reproducibleWithin;
    public Boolean boxSeeds;

    public Double refObjective;
    public Double maxObjectiveGap;
    public Double maxMicros;
    public Integer bnbSeconds;

    public Integer numTicks;
    public Boolean debugPositions;
    public Boolean feasible;
    public Double refViolation;
    public Double violationTol;
    public Double objectiveTol;
    public Boolean legal;
    public String goalWall;
    public Double goalWallRhs;
    public Double refShortfall;
    public Double shortfallTol;
    public Integer bindingTick;
    public List<Integer> feasibleTicks;

    public Integer minCompared;
    public Integer minJumps;
    public Double groundSlip;

    public Boolean savedLegalMode;
    public Integer starveSeedMs;
    public Double minShortfall;
    public Double maxShortfall;
    public Boolean deterministic;

    public String mode;
    public Integer minIncumbents;
    public String seedsTaken;

    public Double displaceSeedX;
    public Double startXLo;
    public Double startXHi;
    public Double startZLo;
    public Double startZHi;
    public Boolean pinnedAfterAdoption;

    public String axis;
    public String goal;
    public Double minGain;
    public Boolean seedMayMiss;
    public Double cap;

    public Boolean resmooth;

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

    public boolean flag(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    public AngleSolverState.Effort effort() {
        return effort == null ? AngleSolverState.Effort.FAST : AngleSolverState.Effort.valueOf(effort);
    }

    public AngleSolverState.Axis axis() {
        return axis == null ? null : AngleSolverState.Axis.valueOf(axis);
    }

    public AngleSolverState.Goal goal() {
        return goal == null ? null : AngleSolverState.Goal.valueOf(goal);
    }

    public double maxObjectiveGap() {
        return maxObjectiveGap != null ? maxObjectiveGap : 1.0e-2;
    }

    public double maxMicros() {
        return maxMicros != null ? maxMicros : 2000.0;
    }

    public long timeoutMs(long fallback) {
        return timeoutMs != null ? timeoutMs : fallback;
    }

    public double or(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    public int or(Integer value, int fallback) {
        return value != null ? value : fallback;
    }
}
