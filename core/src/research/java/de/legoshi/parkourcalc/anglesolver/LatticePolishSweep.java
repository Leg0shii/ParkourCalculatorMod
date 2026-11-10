package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LatticePolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class LatticePolishSweep {

    @Test
    public void sweep() throws IOException {
        String rootName = System.getProperty("pkc.polish.sweep");
        Assume.assumeTrue("set -Dpkc.polish.sweep=<folder with captures>", rootName != null && !rootName.isEmpty());
        String only = System.getProperty("pkc.polish.only", "");
        Path root = Paths.get(rootName);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".json")).forEach(files::add);
        }
        Collections.sort(files);
        int specs = 0;
        int declined = 0;
        int seeded = 0;
        int improved = 0;
        int bad = 0;
        for (Path p : files) {
            String name = root.relativize(p).toString().replace(".json", "");
            if (!only.isEmpty() && !name.contains(only)) continue;
            SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                    || file.rows == null || file.rows.isEmpty()) {
                continue;
            }
            InputData inputs = new InputData();
            SaveIO.applyRowsTo(file, inputs);
            AngleSolverState state = new AngleSolverState();
            SaveIO.applyAngleSolverTo(file, state);
            state.clearResult();
            ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
            AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            JumpSpec spec = engine.debugBuildSpec();
            if (spec == null) continue;
            specs++;
            JumpPhysicsInputs sc = spec.asScenario();
            String why = LatticePolish.unsupported(spec);
            if (why != null) {
                declined++;
                print("%-62s declined: %s", name, why);
                continue;
            }
            double[] seed = ClosedFormSolve.optimize(model, spec, 0.0, new AtomicBoolean(false));
            if (seed == null) {
                print("%-62s no seed", name);
                continue;
            }
            seed = Angles.wrapAll(seed);
            double[] seedFacings = sc.toGameFacings(seed);
            ForwardPath seedPath = model.forward(sc, seedFacings);
            if (JumpConstraintCompiler.compile(spec).maxViolation(seedFacings, seedPath) > 0.0) {
                print("%-62s seed misses a constraint", name);
                continue;
            }
            seeded++;
            double before = spec.objective.evaluate(seedPath);
            long t0 = System.nanoTime();
            LatticePolish.Result r = LatticePolish.polish(model, spec, seed, sc.startPos.x, sc.startPos.z,
                    new LatticePolish.Config(), 0L, null);
            double ms = (System.nanoTime() - t0) / 1.0e6;
            if (r == null) {
                print("%-62s seed=%.9f kept %.1f ms", name, before, ms);
                continue;
            }
            StartBox box = sc.startBox;
            boolean free = box != null && box.startFree();
            boolean inBox = free
                    ? r.startX >= box.pxLo && r.startX <= box.pxHi && r.startZ >= box.pzLo && r.startZ <= box.pzHi
                    : r.startX == sc.startPos.x && r.startZ == sc.startPos.z;
            JumpPhysicsInputs at = sc.pinnedAt(r.startX, r.startZ);
            double[] facings = at.toGameFacings(r.yaws);
            ForwardPath path = model.forward(at, facings);
            double violation = JumpConstraintCompiler.compile(spec).maxViolation(facings, path);
            double after = spec.objective.evaluate(path);
            double gain = spec.objective.sense == Objective.Sense.MAX ? after - before : before - after;
            boolean ok = violation <= 0.0 && inBox && gain > 0.0 && after == r.objective;
            if (ok) improved++;
            else bad++;
            print("%-62s seed=%.9f -> %.9f gain=%.3e smooth=%.9f %.1f ms%s", name, before, after, gain,
                    r.continuous, ms, ok ? "" : " BAD violation=" + violation + " inBox=" + inBox);
        }
        print("specs=%d declined=%d seeded=%d improved=%d bad=%d", specs, declined, seeded, improved, bad);
        assertEquals("results that fail the byte-exact replay", 0, bad);
    }

    private static void print(String format, Object... args) {
        System.out.println(String.format(Locale.ROOT, format, args));
    }
}
