package de.legoshi.parkourcalc.anglesolver.bench;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CannotLandProbe {

    private CannotLandProbe() {
    }

    public static void main(String[] args) {
        String sets = System.getProperty("pkc.cl.sets", "hpk");
        String only = System.getProperty("pkc.cl.jumps", "");
        String cats = System.getProperty("pkc.cl.categories", "human,theoretical");
        long budgetMs = Long.getLong("pkc.cl.ms", 5000L);
        int nodeCap = Integer.getInteger("pkc.cl.nodes", 1000000);
        Set<String> ids = new HashSet<String>();
        for (String s : only.split(",")) if (!s.trim().isEmpty()) ids.add(s.trim());
        List<BenchJumps.Entry> entries = BenchJumps.load(sets);
        System.out.println("jump\tcategory\tticks\tdeclined\tfeasible\tcertInfeasible\tunresolved\tnodes\tkernel\tms\tbestViol");
        for (BenchJumps.Entry e : entries) {
            if (!ids.isEmpty() && !ids.contains(e.jump.id) && !ids.contains(e.jump.key)) continue;
            for (String cat : cats.split(",")) {
                SaveFile file = e.file;
                InputData inputs = new InputData();
                SaveIO.applyRowsTo(file, inputs);
                AngleSolverState state = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, state);
                SolverBench.applyCategory(state, cat);
                state.setEffort(AngleSolverState.Effort.FAST);
                state.setBudgetMs(0);
                ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
                AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, x -> { }, model);
                JumpSpec spec = engine.debugBuildSpec();
                if (spec == null) {
                    System.out.println(e.jump.key + "\t" + cat + "\tNO_SPEC");
                    continue;
                }
                if (Boolean.getBoolean("pkc.cl.dump")) {
                    for (de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint c : spec.constraints) {
                        System.out.println("  " + c.mode + " t1=" + c.t1 + " t2=" + c.t2 + " " + c.op + " " + c.cmp + " " + c.rhs + " " + c.name);
                    }
                    System.out.println("  objective " + spec.objective.type + " " + spec.objective.sense + " " + spec.objective.axis + " t=" + spec.objective.tick);
                    System.out.println("  v0=" + spec.asScenario().initialVelocity.x + "," + spec.asScenario().initialVelocity.z + " box=" + spec.asScenario().startBox);
                }
                String replay = System.getProperty("pkc.cl.replay");
                if (replay != null) {
                    String[] parts = replay.trim().split("\\s+");
                    double[] yaws = new double[parts.length - 2];
                    for (int i = 0; i < yaws.length; i++) yaws[i] = Double.parseDouble(parts[i]);
                    System.out.println(e.jump.key + " " + cat + " " + de.legoshi.parkourcalc.core.anglesolver.solver.RelaxationExport
                            .replay(model, spec, yaws, Double.parseDouble(parts[parts.length - 2]), Double.parseDouble(parts[parts.length - 1])));
                    continue;
                }
                String exportDir = System.getProperty("pkc.cl.export");
                if (exportDir != null) {
                    try {
                        java.nio.file.Files.write(java.nio.file.Paths.get(exportDir, e.jump.id + "-" + cat + ".json"),
                                de.legoshi.parkourcalc.core.anglesolver.solver.RelaxationExport.json(model, spec)
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (java.io.IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    continue;
                }
                CertifiedBnb.Config cfg = new CertifiedBnb.Config();
                cfg.mode = CertifiedBnb.Mode.FIRST_FEASIBLE;
                cfg.nodeCap = nodeCap;
                cfg.resolveProbes = Boolean.getBoolean("pkc.cl.resolve");
                cfg.foldTies = Boolean.getBoolean("pkc.cl.fold");
                cfg.skipSeeds = Boolean.getBoolean("pkc.cl.skipseeds");
                cfg.excludeClamps = Boolean.getBoolean("pkc.cl.exclude");
                long t0 = System.nanoTime();
                cfg.deadlineNanos = t0 + budgetMs * 1_000_000L;
                CertifiedBnb.Result r = CertifiedBnb.solve(model, spec, cfg);
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                System.out.println(String.format(Locale.ROOT, "%s\t%s\t%d\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%.3e",
                        e.jump.key, cat, spec.asScenario().numTicks, r.declined, r.feasible, r.certifiedInfeasible,
                        r.unresolved, r.nodes, r.kernelSolves, ms, r.bestInfeasViol));
            }
        }
    }
}
