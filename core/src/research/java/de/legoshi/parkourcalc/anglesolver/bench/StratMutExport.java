package de.legoshi.parkourcalc.anglesolver.bench;

import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.RelaxationExport;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

public final class StratMutExport {

    private StratMutExport() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args[0]);
        File out = new File(args[1]);
        long ms = Long.parseLong(args.length > 2 ? args[2] : "2000");
        int perJump = Integer.parseInt(args.length > 3 ? args[3] : "4");
        File[] dirs = root.listFiles();
        Arrays.sort(dirs);
        for (File dir : dirs) {
            File[] files = dir.isDirectory() ? dir.listFiles() : new File[]{dir};
            Arrays.sort(files);
            for (File f : files) {
                if (!f.getName().endsWith(".json")) continue;
                String stem = f.getName().substring(0, Math.min(5, f.getName().length())).replace('_', '-');
                try {
                    NoTurnCapture cap = NoTurnCapture.load(f.getPath());
                    JumpSpec spec = cap.buildSpec();
                    if (spec == null) continue;
                    NoTurnProblem p = NoTurnProblem.from(spec, cap.model, false);
                    if (p.issue != null) continue;
                    JumpPhysicsInputs sc = spec.asScenario();
                    int[] combos = NoTurnProblem.combosOf(sc, sc.numTicks - 1);
                    boolean[] sprint = NoTurnProblem.sprintOf(sc, sc.numTicks - 1);
                    int turn = new StructurePoolDriver.Config().turnCombo;
                    emit(cap, p.buildSpec(combos, sprint, turn, false), out, stem + "-h", NoTurnKeys.describe(combos), ms);
                    int made = 0;
                    for (int i = 1; i < combos.length && made < perJump; i++) {
                        if (combos[i] == combos[i - 1]) continue;
                        for (int dir2 = 0; dir2 < 2 && made < perJump; dir2++) {
                            int[] mc = combos.clone();
                            if (dir2 == 0) mc[i] = combos[i - 1];
                            else mc[i - 1] = combos[i];
                            made++;
                            emit(cap, p.buildSpec(mc, sprint, turn, false), out, stem + "-m" + i + (dir2 == 0 ? "a" : "b"),
                                    NoTurnKeys.describe(mc), ms);
                        }
                    }
                } catch (Exception e) {
                    System.out.println(f.getName() + "\tERROR " + e);
                }
            }
        }
    }

    private static void emit(NoTurnCapture cap, JumpSpec spec, File out, String name, String keys, long ms) throws Exception {
        if (spec == null) return;
        if (!Boolean.getBoolean("pkc.cl.noexport")) {
            Files.write(new File(out, name + ".json").toPath(), RelaxationExport.json(cap.model, spec).getBytes(StandardCharsets.UTF_8));
        }
        CertifiedBnb.Config cfg = new CertifiedBnb.Config();
        cfg.nodeCap = 1000000;
        cfg.foldTies = Boolean.getBoolean("pkc.cl.fold");
        cfg.skipSeeds = Boolean.getBoolean("pkc.cl.skipseeds");
        cfg.excludeClamps = Boolean.getBoolean("pkc.cl.exclude");
        long t0 = System.nanoTime();
        cfg.deadlineNanos = t0 + ms * 1_000_000L;
        CertifiedBnb.Result r = CertifiedBnb.solve(cap.model, spec, cfg);
        System.out.println(String.format(Locale.ROOT, "%s\t%s\tdeclined=%s\tfeasible=%s\tcertInf=%s\tnodes=%d\tms=%d\tviol=%.3e",
                name, keys, r.declined, r.feasible, r.certifiedInfeasible, r.nodes, (System.nanoTime() - t0) / 1_000_000L, r.bestInfeasViol));
    }
}
