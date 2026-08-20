package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingLattice;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.PathTranslation;
import de.legoshi.parkourcalc.core.anglesolver.solver.SnapRepairPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CellSetDump {

    private static final String ILS_POINT = "tools/miqcp/rung5375-ils-point.json";
    private static final double[] DEFAULT_BASES = {0.0, 360.0, -360.0, 720.0, -720.0};

    private static double[] bases() {
        String v = System.getenv("PKC_CELLSET_BASES");
        if (v == null || v.isEmpty()) return DEFAULT_BASES;
        String[] parts = v.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i].trim());
        return out;
    }

    private final StringBuilder rep = new StringBuilder();

    @Test
    public void dump() throws Exception {
        Assume.assumeTrue("set PKC_CELLSET_DUMP=1 to run", "1".equals(System.getenv("PKC_CELLSET_DUMP")));
        String tag = System.getenv("PKC_CELLSET_TAG");
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_CELLSET_TAG required");
        int span = (int) envLong("PKC_CELLSET_SPAN", 64L);
        double[] baseSet = bases();
        StringBuilder bs = new StringBuilder();
        for (double b : baseSet) {
            if (bs.length() > 0) bs.append(',');
            bs.append((long) b);
        }

        emit("=== CellSetDump.dump (Lever 2 step 1: exact cell set, gate-flipping cells INCLUDED) tag=" + tag
                + " span=+-" + span + " bases=" + bs + " ===");

        Loaded l = loadCapture("razor-proof");
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
        JumpSpec spec = patch.spec;
        ExactJumpModel model = l.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (model.modern() || !model.perAxisInertia()) {
            throw new IllegalStateException("cellset dump supports the legacy 1.8.9 chain only");
        }
        double thr = model.inertiaThreshold();
        emit(String.format(Locale.ROOT, "spec: n=%d objTick=%d raised=%d thr=%.4f walls=%d",
                n, spec.objective.tick, patch.raised.size(), thr, spec.constraints.size()));

        JsonObject pt = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(ILS_POINT).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray gfj = pt.getAsJsonArray("gf");
        if (gfj.size() != n) throw new IllegalStateException("ils gf length " + gfj.size() + " != n " + n);
        double[] gf = new double[n];
        for (int i = 0; i < n; i++) gf[i] = gfj.get(i).getAsDouble();
        emit(String.format(Locale.ROOT, "base point: %s persisted exactViol=%.9e tx=%.9e tz=%.9e",
                ILS_POINT, pt.get("exactViol").getAsDouble(), pt.get("tx").getAsDouble(), pt.get("tz").getAsDouble()));

        TickConst[] tc = new TickConst[n];
        for (int t = 0; t < n; t++) tc[t] = tickConst(sc, t);
        StringBuilder bt = new StringBuilder();
        for (int t = 0; t < n; t++) if (tc[t].boost) bt.append(' ').append(t);
        emit("boost ticks:" + bt);

        ForwardPath path = model.forward(sc, gf);
        double[][] baseConsts = new double[n][];
        for (int t = 0; t < n; t++) baseConsts[t] = cellConsts(tc[t], (float) gf[t]);
        int reconMismatch = reconCheck(sc, tc, baseConsts, thr, path, n);
        emit("bit-exact reconstruction vs ExactJumpModel.forward: mismatches=" + reconMismatch
                + (reconMismatch == 0 ? " PASS" : " FAIL"));
        if (reconMismatch != 0) {
            writeReport("cellset-dump-" + tag);
            throw new AssertionError("cell-constant reconstruction not bit-exact: " + reconMismatch);
        }

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double baseViolAtAuthored = compiled.maxViolation(gf, path);
        emit(String.format(Locale.ROOT, "base exact at authored start: viol=%.9e objX=%.10f",
                baseViolAtAuthored, path.getPos(spec.objective.tick, spec.objective.axis)));

        JsonObject root = new JsonObject();
        root.addProperty("case", "rung5375");
        root.addProperty("tag", tag);
        root.addProperty("span", span);
        root.addProperty("bases", bs.toString());
        root.addProperty("modern", model.modern());
        root.addProperty("perAxisInertia", model.perAxisInertia());
        root.addProperty("inertiaThreshold", thr);
        root.addProperty("numTicks", n);
        root.addProperty("objTick", spec.objective.tick);
        root.addProperty("objAxis", spec.objective.axis.name());
        root.addProperty("objSense", spec.objective.sense.name());
        root.addProperty("raisedWalls", patch.raised.size());
        JsonArray sp = new JsonArray();
        sp.add(sc.startPos.x);
        sp.add(sc.startPos.y);
        sp.add(sc.startPos.z);
        root.add("startPos", sp);
        JsonArray iv = new JsonArray();
        iv.add(sc.initialVelocity.x);
        iv.add(sc.initialVelocity.y);
        iv.add(sc.initialVelocity.z);
        root.add("initialVelocity", iv);
        StartBox sb = sc.startBox;
        if (sb == null) throw new IllegalStateException("rung5375 lacks authored startBox");
        JsonObject sbj = new JsonObject();
        sbj.addProperty("pxLo", sb.pxLo);
        sbj.addProperty("pxHi", sb.pxHi);
        sbj.addProperty("pzLo", sb.pzLo);
        sbj.addProperty("pzHi", sb.pzHi);
        root.add("startBox", sbj);

        JsonArray cons = new JsonArray();
        for (JumpConstraint c : spec.constraints) {
            JsonObject cj = new JsonObject();
            cj.addProperty("name", c.name);
            cj.addProperty("mode", c.mode.name());
            cj.addProperty("t1", c.t1);
            if (c.t2 == null) cj.add("t2", null);
            else cj.addProperty("t2", c.t2);
            cj.addProperty("op", c.op.name());
            cj.addProperty("cmp", c.cmp.name());
            cj.addProperty("rhs", c.rhs);
            cons.add(cj);
        }
        root.add("constraints", cons);

        root.add("baseGf", arr(gf));
        root.add("basePosX", arr(path.posX));
        root.add("basePosZ", arr(path.posZ));
        root.add("baseVelX", arr(path.velX));
        root.add("baseVelZ", arr(path.velZ));
        root.addProperty("baseViolAtAuthoredStart", baseViolAtAuthored);
        root.addProperty("ilsTx", pt.get("tx").getAsDouble());
        root.addProperty("ilsTz", pt.get("tz").getAsDouble());
        root.addProperty("ilsExactViol", pt.get("exactViol").getAsDouble());

        JumpLinearModel lm = new JumpLinearModel(sc);
        JsonArray ticks = new JsonArray();
        long totalCells = 0;
        int minCells = Integer.MAX_VALUE;
        int maxCells = 0;
        long tStart = System.nanoTime();
        for (int t = 0; t < n; t++) {
            LinkedHashMap<Long, Float> map = new LinkedHashMap<Long, Float>();
            for (double b : baseSet) {
                float base = (float) (gf[t] + b);
                float[] reps = FacingLattice.cellRepresentatives(base, -span, span, false, tc[t].boost);
                for (float r : reps) {
                    Long id = Long.valueOf(FacingLattice.jointCellId(r, false, tc[t].boost));
                    if (!map.containsKey(id)) map.put(id, Float.valueOf(r));
                }
            }
            long baseId = FacingLattice.jointCellId((float) gf[t], false, tc[t].boost);
            int baseIdx = -1;
            JsonArray cells = new JsonArray();
            int idx = 0;
            for (Map.Entry<Long, Float> e : map.entrySet()) {
                float r = e.getValue().floatValue();
                double[] cc = cellConsts(tc[t], r);
                JsonObject co = new JsonObject();
                co.addProperty("gf", (double) r);
                co.addProperty("cx", cc[0]);
                co.addProperty("cz", cc[1]);
                if (tc[t].boost) {
                    co.addProperty("bx", cc[2]);
                    co.addProperty("bz", cc[3]);
                }
                co.addProperty("norm", normAt(r));
                long cid = e.getKey().longValue();
                double emin = r;
                double emax = r;
                for (int k = 1; k <= 8; k++) {
                    float up = (float) ((double) r + 360.0 * k);
                    if (Math.abs(up) > 2880.0F || FacingLattice.jointCellId(up, false, tc[t].boost) != cid) break;
                    emax = up;
                }
                for (int k = 1; k <= 8; k++) {
                    float dn = (float) ((double) r - 360.0 * k);
                    if (Math.abs(dn) > 2880.0F || FacingLattice.jointCellId(dn, false, tc[t].boost) != cid) break;
                    emin = dn;
                }
                co.addProperty("emin", emin);
                co.addProperty("emax", emax);
                cells.add(co);
                if (e.getKey().longValue() == baseId) baseIdx = idx;
                idx++;
            }
            if (baseIdx < 0) throw new IllegalStateException("base cell missing at t=" + t);
            double[] bc = cellConsts(tc[t], map.get(Long.valueOf(baseId)).floatValue());
            if (bc[0] != baseConsts[t][0] || bc[1] != baseConsts[t][1]
                    || bc[2] != baseConsts[t][2] || bc[3] != baseConsts[t][3]) {
                throw new IllegalStateException("base cell rep constants differ from base gf constants at t=" + t);
            }
            totalCells += cells.size();
            minCells = Math.min(minCells, cells.size());
            maxCells = Math.max(maxCells, cells.size());
            JsonObject tk = new JsonObject();
            tk.addProperty("t", t);
            tk.addProperty("f4", (double) tc[t].f4);
            tk.addProperty("mMag", lm.mMag(t));
            tk.addProperty("contact", tc[t].contact);
            if (tc[t].contact) tk.addProperty("slip", tc[t].slip);
            else tk.add("slip", null);
            tk.addProperty("jump", tc[t].jump);
            tk.addProperty("sprint", tc[t].sprint);
            tk.addProperty("boost", tc[t].boost);
            tk.addProperty("baseCellIdx", baseIdx);
            tk.add("cells", cells);
            ticks.add(tk);
        }
        long ms = (System.nanoTime() - tStart) / 1_000_000L;
        root.add("ticks", ticks);
        emit(String.format(Locale.ROOT, "cell enumeration: totalCells=%d perTick min=%d max=%d avg=%.1f wallMs=%d",
                totalCells, minCells, maxCells, totalCells / (double) n, ms));

        File dst = repoFile("tools/miqcp/rung5375-cellset-span" + span + "-" + tag + ".json");
        Files.write(dst.toPath(), new GsonBuilder().create().toJson(root).getBytes(StandardCharsets.UTF_8));
        emit("wrote " + dst.getPath());
        writeReport("cellset-dump-" + tag);
        System.out.println("applied: cellset-dump tag=" + tag + " span=" + span + " bases=" + bs
                + " ticks=" + n + " cellsTotal=" + totalCells + " raised=" + patch.raised.size()
                + " reconBitExact=true boostTicks=" + bt.toString().trim().replace(' ', ','));
    }

    @Test
    public void verify() throws Exception {
        Assume.assumeTrue("set PKC_CELLSET_VERIFY=1 to run", "1".equals(System.getenv("PKC_CELLSET_VERIFY")));
        String tag = System.getenv("PKC_CELLSET_TAG");
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_CELLSET_TAG required");
        String src = System.getenv("PKC_CELLSET_SRC");
        if (src == null || src.isEmpty()) throw new IllegalStateException("PKC_CELLSET_SRC required");

        emit("=== CellSetDump.verify (Lever 2 step 3: byte-exact arbiter) tag=" + tag + " src=" + src + " ===");

        Loaded l = loadCapture("razor-proof");
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
        JumpSpec spec = patch.spec;
        ExactJumpModel model = l.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        JumpConstraintCompiler.Compiled full = JumpConstraintCompiler.compile(spec);
        double[] dom = authoredDomain(sc);
        double[] domT = new double[]{dom[0], dom[1], dom[2] + 0.0625, dom[3]};
        emit(String.format(Locale.ROOT,
                "domains: authored tx[%.6f,%.6f] tz[%.6f,%.6f]; tightened tz-lo=%.6f",
                dom[0], dom[1], dom[2], dom[3], domT[2]));

        List<JumpConstraint> legalCons = new ArrayList<JumpConstraint>();
        double padRhs = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if ("X@49lo".equals(c.name)) padRhs = c.rhs;
            else legalCons.add(c);
        }
        if (Double.isNaN(padRhs) || legalCons.size() != spec.constraints.size() - 1) {
            throw new IllegalStateException("pad wall X@49lo not found exactly once");
        }
        JumpConstraintCompiler.Compiled legal =
                JumpConstraintCompiler.compile(new JumpSpec(sc, legalCons, spec.objective));
        List<JumpConstraint> walls = new ArrayList<JumpConstraint>();
        walls.addAll(full.ineq);
        walls.addAll(full.eq);

        JsonObject srcJson = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(src).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray sols = srcJson.getAsJsonArray("solutions");
        emit("solutions loaded: " + sols.size());

        double worstDelta = 0.0;
        double bestLegalShortfall = Double.POSITIVE_INFINITY;
        double bestViol = Double.POSITIVE_INFINITY;
        int haltCount = 0;
        for (int s = 0; s < sols.size(); s++) {
            JsonObject so = sols.get(s).getAsJsonObject();
            String mode = so.get("mode").getAsString();
            double obj = so.get("obj").getAsDouble();
            JsonArray ga = so.getAsJsonArray("gf");
            if (ga.size() != n) throw new IllegalStateException("sol[" + s + "] gf length " + ga.size());
            double[] gf = new double[n];
            for (int i = 0; i < n; i++) gf[i] = ga.get(i).getAsDouble();
            double pStartX = so.get("predStartX").getAsDouble();
            double pStartZ = so.get("predStartZ").getAsDouble();

            ForwardPath p = forwardAt(model, sc, gf, pStartX, pStartZ);
            double maxDelta = 0.0;
            String maxDeltaWall = "-";
            JsonObject pe = so.getAsJsonObject("predEvals");
            for (JumpConstraint c : walls) {
                if (pe == null || !pe.has(c.name)) continue;
                double pred = pe.get(c.name).getAsDouble();
                double meas = JumpConstraintCompiler.evaluate(c, gf, p);
                double d = Math.abs(meas - pred);
                if (d > maxDelta) {
                    maxDelta = d;
                    maxDeltaWall = c.name;
                }
            }
            worstDelta = Math.max(worstDelta, maxDelta);
            double violAtPred = full.maxViolation(gf, p);

            ForwardPath pB = model.forward(sc, gf);
            PathTranslation.Trans trFull = PathTranslation.bestTranslation(full, gf, pB,
                    domT[0], domT[1], domT[2], domT[3]);
            PathTranslation.Trans tf = PathTranslation.bestTranslation(legal, gf, pB,
                    domT[0], domT[1], domT[2], domT[3]);
            double legalShortfall = Double.NaN;
            double legalObjX = Double.NaN;
            if (tf.viol <= 0.0) {
                PathTranslation.Trans to = PathTranslation.bestTranslationObj(legal, gf, pB,
                        domT[0], domT[1], domT[2], domT[3], 0, true);
                if (to.viol <= 0.0) {
                    legalObjX = pB.getPos(objTick, spec.objective.axis) + to.tx;
                    legalShortfall = padRhs - legalObjX;
                }
            }
            emit(String.format(Locale.ROOT,
                    "sol[%02d] mode=%s milpObj=%+.9e exactViolAtPredStart=%.9e maxWallDelta=%.3e(%s)%s",
                    s, mode, obj, violAtPred, maxDelta, maxDeltaWall,
                    maxDelta > 1.0e-9 ? " HALT-FIDELITY" : ""));
            emit(String.format(Locale.ROOT,
                    "        exact bestTranslation(full,tightened): viol=%.9e tx=%.9e tz=%.9e feasible=%b",
                    trFull.viol, trFull.tx, trFull.tz, trFull.viol <= 0.0));
            if (!Double.isNaN(legalShortfall)) {
                emit(String.format(Locale.ROOT,
                        "        exact LEGAL: viol<=0, objX=%.13f shortfall=%.9e vs community 2.74e-4: %s",
                        legalObjX, legalShortfall,
                        legalShortfall < 2.74e-4 ? "BEATS (byte-exact; in-tool confirm pending)" : "does not beat"));
            } else {
                emit(String.format(Locale.ROOT,
                        "        exact LEGAL: infeasible, legalViol=%.9e (no legal translation in tightened domain)",
                        tf.viol));
            }
            if (maxDelta > 1.0e-9) haltCount++;
            if (!Double.isNaN(legalShortfall)) bestLegalShortfall = Math.min(bestLegalShortfall, legalShortfall);
            bestViol = Math.min(bestViol, trFull.viol);
        }

        emit(String.format(Locale.ROOT,
                "SUMMARY: sols=%d worstWallDelta=%.3e bestExactViol=%.9e bestLegalShortfall=%s haltCount=%d",
                sols.size(), worstDelta, bestViol,
                bestLegalShortfall == Double.POSITIVE_INFINITY ? "none-legal"
                        : String.format(Locale.ROOT, "%.9e", bestLegalShortfall), haltCount));
        writeReport("cellset-verify-" + tag);
        System.out.println(String.format(Locale.ROOT,
                "applied: cellset-verify tag=%s src=%s sols=%d worstDelta=%.3e bestViol=%.9e bestLegalShortfall=%s halts=%d",
                tag, src, sols.size(), worstDelta, bestViol,
                bestLegalShortfall == Double.POSITIVE_INFINITY ? "none"
                        : String.format(Locale.ROOT, "%.9e", bestLegalShortfall), haltCount));
        if (haltCount > 0) throw new AssertionError("double-recurrence fidelity above 1e-9 on " + haltCount + " solutions");
    }

    private static final String LEGAL_GAME_OUT =
            "C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/ATTEMPT_5.375bm_legal.json";
    private static final String LEGAL_REPO_OUT =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/core/src/test/resources/captures/razor-rung-legal-attempt.json";

    @Test
    public void deliverLegal() throws Exception {
        Assume.assumeTrue("set PKC_CELLSET_DELIVER=1 to run", "1".equals(System.getenv("PKC_CELLSET_DELIVER")));
        String tag = System.getenv("PKC_CELLSET_TAG");
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_CELLSET_TAG required");
        String pointPath = System.getenv("PKC_CELLSET_POINT");
        if (pointPath == null || pointPath.isEmpty()) throw new IllegalStateException("PKC_CELLSET_POINT required");
        String gameOut = env("PKC_CELLSET_OUT_GAME", LEGAL_GAME_OUT);
        String repoOut = env("PKC_CELLSET_OUT_REPO", LEGAL_REPO_OUT);
        double maxGfCap = Double.parseDouble(env("PKC_CELLSET_MAXGF_CAP", "0"));

        emit("=== CellSetDump.deliverLegal (legal attempt as locked-RAW-row TAS) tag=" + tag
                + " point=" + pointPath + " ===");
        emit("outputs: game=" + gameOut + " repo=" + repoOut
                + (maxGfCap > 0.0 ? String.format(Locale.ROOT, " maxGfCap=%.1f", maxGfCap) : ""));

        Loaded l = loadCapture("razor-proof");
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
        JumpSpec spec = patch.spec;
        ExactJumpModel model = l.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int objTick = spec.objective.tick;
        double[] dom = authoredDomain(sc);
        double[] domT = new double[]{dom[0], dom[1], dom[2] + 0.0625, dom[3]};

        List<JumpConstraint> legalCons = new ArrayList<JumpConstraint>();
        double padRhs = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if ("X@49lo".equals(c.name)) padRhs = c.rhs;
            else legalCons.add(c);
        }
        if (Double.isNaN(padRhs)) throw new IllegalStateException("pad wall X@49lo missing");
        JumpConstraintCompiler.Compiled legal =
                JumpConstraintCompiler.compile(new JumpSpec(sc, legalCons, spec.objective));

        JsonObject pj = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(pointPath).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray ga = pj.getAsJsonArray("gf");
        if (ga.size() != n) throw new IllegalStateException("point gf length " + ga.size() + " != n " + n);
        double[] gf = new double[n];
        for (int i = 0; i < n; i++) gf[i] = ga.get(i).getAsDouble();
        double expShortfall = pj.has("legalShortfall") ? pj.get("legalShortfall").getAsDouble() : Double.NaN;
        emit(String.format(Locale.ROOT, "point loaded: expected shortfall=%.9e", expShortfall));

        double ctx = 0.0;
        double ctz = 0.0;
        double legalViol = Double.POSITIVE_INFINITY;
        double shortfall = Double.NaN;
        double startX = sc.startPos.x;
        double startZ = sc.startPos.z;
        ForwardPath fp = model.forward(sc, gf);
        for (int it = 0; it < 4; it++) {
            PathTranslation.Trans to = PathTranslation.bestTranslationObj(legal, gf, fp,
                    domT[0] - ctx, domT[1] - ctx, domT[2] - ctz, domT[3] - ctz, 0, true);
            ctx += to.tx;
            ctz += to.tz;
            startX = sc.startPos.x + ctx;
            startZ = sc.startPos.z + ctz;
            fp = forwardAt(model, sc, gf, startX, startZ);
            legalViol = legal.maxViolation(gf, fp);
            shortfall = padRhs - fp.getPos(objTick, spec.objective.axis);
            emit(String.format(Locale.ROOT,
                    "translate iter=%d ctx=%.12e ctz=%.12e resim legalViol=%.9e shortfall=%.9e",
                    it, ctx, ctz, legalViol, shortfall));
            if (legalViol <= 0.0 && it > 0) break;
            if (legalViol <= 0.0 && to.tx == 0.0 && to.tz == 0.0) break;
        }
        if (legalViol > 0.0) {
            ctx -= 1.0e-11;
            startX = sc.startPos.x + ctx;
            fp = forwardAt(model, sc, gf, startX, startZ);
            legalViol = legal.maxViolation(gf, fp);
            shortfall = padRhs - fp.getPos(objTick, spec.objective.axis);
            emit(String.format(Locale.ROOT,
                    "backoff ctx-=1e-11: resim legalViol=%.9e shortfall=%.9e", legalViol, shortfall));
        }
        if (legalViol > 0.0) {
            writeReport("cellset-deliver-" + tag);
            throw new AssertionError("legal viol > 0 at resimmed delivery start: " + legalViol);
        }
        double maxGf = 0.0;
        for (double v : gf) maxGf = Math.max(maxGf, Math.abs(v));
        if (maxGfCap > 0.0 && maxGf > maxGfCap) {
            writeReport("cellset-deliver-" + tag);
            throw new AssertionError("point maxGameFacing " + maxGf + " exceeds cap " + maxGfCap);
        }
        double turnCapEnv = Double.parseDouble(env("PKC_CELLSET_TURNCAP", "0"));
        if (turnCapEnv > 0.0) {
            double maxTurn = 0.0;
            for (int t = 1; t < n; t++) maxTurn = Math.max(maxTurn, Math.abs(gf[t] - gf[t - 1]));
            emit(String.format(Locale.ROOT, "turn-chain check: maxDiff=%.3f cap=%.3f %s",
                    maxTurn, turnCapEnv, maxTurn <= turnCapEnv ? "PASS" : "FAIL"));
            if (maxTurn > turnCapEnv) {
                writeReport("cellset-deliver-" + tag);
                throw new AssertionError("turn diff " + maxTurn + " exceeds cap " + turnCapEnv);
            }
        }
        emit(String.format(Locale.ROOT,
                "DELIVERY POINT: start=(%.15f,%.15f) legalViol=%.9e objX=%.13f shortfall=%.9e maxGameFacing=%.3f",
                startX, startZ, legalViol, fp.getPos(objTick, spec.objective.axis), shortfall, maxGf));
        emit("encoding: locked RAW rows (entity=(float)abs, no wrap; SaveIO stores yaw raw; playback bit-identical).");
        emit("CAVEAT: file named ATTEMPT: legal-metric run, all hard walls held, only the pad X@49lo short by the"
                + " shortfall above (community best legal attempt 2.74e-4).");
        emit("CAVEAT: the file angleSolver block carries the PROOF constraints; the rung's three raised z-lo walls"
                + " are an IN-MEMORY patch (RazorFixtures.applyRung5375Patch); verify only against the patched rung.");

        String rawProof = Fixtures.rawPool("razor-proof");
        JsonObject root = new JsonParser().parse(rawProof).getAsJsonObject();
        JsonArray startPos = root.getAsJsonObject("start").getAsJsonArray("pos");
        startPos.set(0, new com.google.gson.JsonPrimitive(startX));
        startPos.set(2, new com.google.gson.JsonPrimitive(startZ));
        JsonObject solver = root.getAsJsonObject("angleSolver");
        JsonArray seedPos = solver.getAsJsonObject("seed").getAsJsonArray("pos");
        seedPos.set(0, new com.google.gson.JsonPrimitive(startX));
        seedPos.set(2, new com.google.gson.JsonPrimitive(startZ));
        JsonArray rows = root.getAsJsonArray("rows");
        for (int k = 0; k < n; k++) {
            JsonObject row = rows.get(k).getAsJsonObject();
            row.add("yaw", new com.google.gson.JsonPrimitive(gf[k]));
            row.add("yawLocked", new com.google.gson.JsonPrimitive(Boolean.TRUE));
        }
        String outJson = new GsonBuilder().setPrettyPrinting().create().toJson(root);
        emit("WRITE repo-copy: " + writeAddOnly(repoOut, outJson) + " -> " + repoOut);
        emit("WRITE game-file: " + writeAddOnly(gameOut, outJson) + " -> " + gameOut);

        String reJson = new String(Files.readAllBytes(new File(repoOut).toPath()), StandardCharsets.UTF_8);
        SaveFile file = SaveIO.parseSafe(reJson);
        if (file == null) throw new AssertionError("delivered file failed to parse");
        ExactJumpModel vModel = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, vModel);
        RazorFixtures.RungPatch vPatch = RazorFixtures.applyRung5375Patch(engine.debugBuildSpec());
        JumpPhysicsInputs vSc = vPatch.spec.asScenario();
        List<JumpConstraint> vLegalCons = new ArrayList<JumpConstraint>();
        double vPadRhs = Double.NaN;
        for (JumpConstraint c : vPatch.spec.constraints) {
            if ("X@49lo".equals(c.name)) vPadRhs = c.rhs;
            else vLegalCons.add(c);
        }
        JsonObject vRoot = new JsonParser().parse(reJson).getAsJsonObject();
        JsonArray vRows = vRoot.getAsJsonArray("rows");
        double[] rowGf = new double[n];
        boolean allLocked = true;
        for (int k = 0; k < n; k++) {
            JsonObject row = vRows.get(k).getAsJsonObject();
            rowGf[k] = row.get("yaw").getAsDouble();
            if (!row.has("yawLocked") || !row.get("yawLocked").getAsBoolean()) allLocked = false;
        }
        double[] vGf = vSc.toGameFacings(rowGf);
        boolean bitExact = true;
        for (int k = 0; k < n; k++) if (vGf[k] != gf[k]) bitExact = false;
        ForwardPath vp = vModel.forward(vSc, vGf);
        double vLegalViol = JumpConstraintCompiler.compile(
                new JumpSpec(vSc, vLegalCons, vPatch.spec.objective)).maxViolation(vGf, vp);
        double vObj = vp.getPos(vPatch.spec.objective.tick, vPatch.spec.objective.axis);
        double vShortfall = vPadRhs - vObj;
        double dSf = Math.abs(vShortfall - shortfall);
        emit(String.format(Locale.ROOT,
                "FRESH-REPARSE VERIFY (patched rung, locked rows scored RAW no-wrap): allLocked=%b gfBitExact=%b"
                        + " raised=%d start=(%.15f,%.15f) legalViol=%.9e X@49=%.13f shortfall=%.9e dShortfall=%.3e within1e-9=%b",
                allLocked, bitExact, vPatch.raised.size(), vSc.startPos.x, vSc.startPos.z,
                vLegalViol, vObj, vShortfall, dSf, dSf <= 1.0e-9));
        boolean pass = allLocked && bitExact && vLegalViol <= 0.0 && dSf <= 1.0e-9;
        emit("VERDICT deliverLegal: " + (pass ? "PASS byte-exact" : "FAIL"));
        writeReport("cellset-deliver-" + tag);
        System.out.println(String.format(Locale.ROOT,
                "applied: cellset-deliver tag=%s shortfall=%.9e legalViol=%.9e reparsePass=%b maxGf=%.3f",
                tag, vShortfall, vLegalViol, pass, maxGf));
        if (!pass) throw new AssertionError("deliverLegal fresh-reparse verify failed");
    }

    @Test
    public void chainExpress() throws Exception {
        Assume.assumeTrue("set PKC_CELLSET_CHAIN=1 to run", "1".equals(System.getenv("PKC_CELLSET_CHAIN")));
        String tag = System.getenv("PKC_CELLSET_TAG");
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_CELLSET_TAG required");
        String pointPath = System.getenv("PKC_CELLSET_POINT");
        if (pointPath == null || pointPath.isEmpty()) throw new IllegalStateException("PKC_CELLSET_POINT required");
        double turnCap = Double.parseDouble(env("PKC_CELLSET_TURNCAP", "359.9"));
        double center = Double.parseDouble(env("PKC_CELLSET_CENTER", "1200"));
        int windMax = (int) envLong("PKC_CELLSET_WINDMAX", 7L);
        double absCap = Double.parseDouble(env("PKC_CELLSET_ABSCAP", "2880"));

        emit("=== CellSetDump.chainExpress (re-express point windings; turn diff <= " + turnCap
                + ", center " + center + ", windings +-" + windMax + ", |gf| <= " + absCap + ") tag=" + tag
                + " point=" + pointPath + " ===");

        Loaded l = loadCapture("razor-proof");
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(l.spec);
        JumpSpec spec = patch.spec;
        ExactJumpModel model = l.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JsonObject pj = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(pointPath).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray ga = pj.getAsJsonArray("gf");
        if (ga.size() != n) throw new IllegalStateException("point gf length " + ga.size() + " != n " + n);
        double[] gf = new double[n];
        for (int i = 0; i < n; i++) gf[i] = ga.get(i).getAsDouble();

        boolean[] boost = new boolean[n];
        for (int t = 0; t < n; t++) {
            boolean grounded = !Double.isNaN(sc.slipAt(t));
            boost[t] = sc.jumpAt(t) && grounded && sc.sprintAt(t);
        }

        double[][] exprs = new double[n][];
        for (int t = 0; t < n; t++) {
            long id0 = FacingLattice.jointCellId((float) gf[t], false, boost[t]);
            java.util.ArrayList<Double> list = new java.util.ArrayList<Double>();
            for (int k = -windMax; k <= windMax; k++) {
                float e = (float) (gf[t] + 360.0 * k);
                if (Math.abs(e) > absCap) continue;
                if (FacingLattice.jointCellId(e, false, boost[t]) == id0) list.add((double) e);
            }
            if (list.isEmpty()) throw new IllegalStateException("no expression for tick " + t);
            double[] a = new double[list.size()];
            for (int i = 0; i < a.length; i++) a[i] = list.get(i).doubleValue();
            exprs[t] = a;
            emit(String.format(Locale.ROOT, "t%02d gf=%.4f expressions=%d [%.1f..%.1f]",
                    t, gf[t], a.length, a[0], a[a.length - 1]));
        }

        Integer[] startOrder = new Integer[exprs[0].length];
        for (int i = 0; i < startOrder.length; i++) startOrder[i] = i;
        java.util.Arrays.sort(startOrder, (x, y2) ->
                Double.compare(Math.abs(exprs[0][x] - center), Math.abs(exprs[0][y2] - center)));

        double[] chosen = null;
        for (Integer s0 : startOrder) {
            boolean[][] reach = new boolean[n][];
            int[][] pred = new int[n][];
            reach[0] = new boolean[exprs[0].length];
            reach[0][s0] = true;
            boolean dead = false;
            for (int t = 1; t < n; t++) {
                reach[t] = new boolean[exprs[t].length];
                pred[t] = new int[exprs[t].length];
                boolean any = false;
                for (int j = 0; j < exprs[t].length; j++) {
                    for (int i = 0; i < exprs[t - 1].length; i++) {
                        if (!reach[t - 1][i]) continue;
                        if (Math.abs(exprs[t][j] - exprs[t - 1][i]) <= turnCap) {
                            reach[t][j] = true;
                            pred[t][j] = i;
                            any = true;
                            break;
                        }
                    }
                }
                if (!any) {
                    emit(String.format(Locale.ROOT,
                            "start e0=%.1f: chain DEAD at tick %d (no expression within cap of any reachable)",
                            exprs[0][s0], t));
                    dead = true;
                    break;
                }
            }
            if (dead) continue;
            int cur = -1;
            for (int j = 0; j < exprs[n - 1].length; j++) {
                if (reach[n - 1][j]) {
                    cur = j;
                    break;
                }
            }
            chosen = new double[n];
            for (int t = n - 1; t >= 0; t--) {
                chosen[t] = exprs[t][cur];
                if (t > 0) cur = pred[t][cur];
            }
            break;
        }
        if (chosen == null) {
            emit("VERDICT chainExpress: NO turn-capped expression path exists for this point.");
            writeReport("cellset-chain-" + tag);
            throw new AssertionError("no chain path");
        }

        double maxDiff = 0.0;
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < n; t++) {
            lo = Math.min(lo, chosen[t]);
            hi = Math.max(hi, chosen[t]);
            if (t > 0) maxDiff = Math.max(maxDiff, Math.abs(chosen[t] - chosen[t - 1]));
        }
        ForwardPath p0 = model.forward(sc, gf);
        ForwardPath p1 = model.forward(sc, chosen);
        int mismatch = 0;
        for (int t = 0; t <= n; t++) {
            if (p0.posX[t] != p1.posX[t] || p0.posZ[t] != p1.posZ[t]
                    || p0.velX[t] != p1.velX[t] || p0.velZ[t] != p1.velZ[t]) mismatch++;
        }
        emit(String.format(Locale.ROOT,
                "CHAIN PATH: e0=%.3f range=[%.3f,%.3f] maxDiff=%.3f (cap %.1f) physicsBitExactVsOriginal=%s",
                chosen[0], lo, hi, maxDiff, turnCap, mismatch == 0 ? "PASS" : "FAIL(" + mismatch + ")"));
        if (mismatch != 0) {
            writeReport("cellset-chain-" + tag);
            throw new AssertionError("re-expressed physics differs: " + mismatch);
        }

        JsonObject out = new JsonObject();
        JsonArray oa = new JsonArray();
        for (double v : chosen) oa.add(v);
        out.add("gf", oa);
        for (String k : new String[]{"tx", "tz", "legalViol", "legalShortfall", "objX", "exactViol", "exactObjX"}) {
            if (pj.has(k)) out.add(k, pj.get(k));
        }
        out.addProperty("source", "chainExpress " + pointPath + " turnCap=" + turnCap + " tag=" + tag);
        File dst = repoFile("tools/miqcp/rung5375-chain-point-" + tag + ".json");
        Files.write(dst.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(out)
                .getBytes(StandardCharsets.UTF_8));
        emit("wrote " + dst.getPath());
        writeReport("cellset-chain-" + tag);
        System.out.println(String.format(Locale.ROOT,
                "applied: cellset-chain tag=%s point=%s e0=%.3f maxDiff=%.3f range=[%.3f,%.3f] bitExact=%b",
                tag, pointPath, chosen[0], maxDiff, lo, hi, mismatch == 0));
    }

    private static String writeAddOnly(String path, String content) throws Exception {
        File f = new File(path);
        if (f.exists()) {
            String cur = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return cur.equals(content) ? "EXISTS-IDENTICAL-SKIPPED" : "EXISTS-DIFFERENT-SKIPPED (add-only; not overwriting)";
        }
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return "CREATED";
    }

    private static final class TickConst {
        boolean contact;
        double slip;
        boolean jump;
        boolean sprint;
        boolean boost;
        float f4;
        float sF;
        float fF;
        boolean hasMove;
    }

    private static TickConst tickConst(JumpPhysicsInputs sc, int t) {
        TickConst tc = new TickConst();
        double slipOv = sc.slipAt(t);
        tc.contact = !Double.isNaN(slipOv);
        tc.slip = slipOv;
        float slipF = tc.contact ? (float) slipOv : Constants.SLIP_F;
        tc.jump = sc.jumpAt(t) && tc.contact;
        tc.sprint = sc.sprintAt(t);
        tc.boost = tc.jump && tc.sprint;
        float f4;
        float accelSpeed;
        if (tc.contact) {
            f4 = slipF * 0.91F;
            accelSpeed = Constants.attrValueF(sc.factorAmpAt(t), tc.sprint) * (0.16277136F / (f4 * f4 * f4));
        } else {
            f4 = 0.91F;
            accelSpeed = sc.factorSprintAt(t) ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
        }
        tc.f4 = f4;
        float forward = sc.forwardAt(t);
        float strafe;
        if (sc.strafeAt(t) && !tc.jump) {
            strafe = sc.strafeSign * 1.0F * 0.98F;
        } else {
            strafe = sc.strafeInputAt(t);
        }
        float fm = strafe * strafe + forward * forward;
        if (fm >= 1.0E-4F) {
            fm = (float) Math.sqrt((double) fm);
            if (fm < 1.0F) fm = 1.0F;
            fm = accelSpeed / fm;
            tc.sF = strafe * fm;
            tc.fF = forward * fm;
            tc.hasMove = true;
        }
        return tc;
    }

    private static double[] cellConsts(TickConst tc, float r) {
        double cx = 0.0;
        double cz = 0.0;
        double bx = 0.0;
        double bz = 0.0;
        if (tc.hasMove) {
            float rad = r * (float) Math.PI / 180.0F;
            float sinD = McSineTable.sinStep(rad);
            float cosD = McSineTable.cosStep(rad);
            cx = (double) (tc.sF * cosD - tc.fF * sinD);
            cz = (double) (tc.fF * cosD + tc.sF * sinD);
        }
        if (tc.boost) {
            float fj = r * (float) (Math.PI / 180.0);
            bx = -((double) (McSineTable.sinStep(fj) * 0.2F));
            bz = (double) (McSineTable.cosStep(fj) * 0.2F);
        }
        return new double[]{cx, cz, bx, bz};
    }

    private static int reconCheck(JumpPhysicsInputs sc, TickConst[] tc, double[][] cc, double thr,
                                  ForwardPath path, int n) {
        double px = sc.startPos.x;
        double pz = sc.startPos.z;
        double vx = sc.initialVelocity.x;
        double vz = sc.initialVelocity.z;
        int mismatch = 0;
        for (int t = 0; t < n; t++) {
            if (Math.abs(vx) < thr) vx = 0.0;
            if (Math.abs(vz) < thr) vz = 0.0;
            if (tc[t].boost) {
                vx += cc[t][2];
                vz += cc[t][3];
            }
            if (tc[t].hasMove) {
                vx += cc[t][0];
                vz += cc[t][1];
            }
            px = px + vx;
            pz = pz + vz;
            double nvx = vx * (double) tc[t].f4;
            double nvz = vz * (double) tc[t].f4;
            if (px != path.posX[t + 1] || pz != path.posZ[t + 1]
                    || nvx != path.velX[t + 1] || nvz != path.velZ[t + 1]) {
                mismatch++;
            }
            vx = nvx;
            vz = nvz;
        }
        return mismatch;
    }

    private static double[] authoredDomain(JumpPhysicsInputs sc) {
        StartBox sb = sc.startBox;
        if (sb == null || !sb.startFree()) throw new IllegalStateException("rung5375 lacks authored free startBox");
        double sx = sc.startPos.x;
        double sz = sc.startPos.z;
        return new double[]{sb.pxLo - sx, sb.pxHi - sx, sb.pzLo - sz, sb.pzHi - sz};
    }

    private static ForwardPath forwardAt(ExactJumpModel model, JumpPhysicsInputs sc, double[] gf,
                                         double startX, double startZ) {
        Vec3dCore saved = sc.startPos;
        if (startX == saved.x && startZ == saved.z) return model.forward(sc, gf);
        sc.startPos = new Vec3dCore(startX, saved.y, startZ);
        try {
            return model.forward(sc, gf);
        } finally {
            sc.startPos = saved;
        }
    }

    private static double normAt(double gfDeg) {
        float rad = (float) gfDeg * (float) Math.PI / 180.0F;
        double s = (double) McSineTable.sinStep(rad);
        double c = (double) McSineTable.cosStep(rad);
        return s * s + c * c - 1.0;
    }

    private static JsonArray arr(double[] a) {
        JsonArray j = new JsonArray();
        for (double v : a) j.add(v);
        return j;
    }

    private static final class Loaded {
        JumpSpec spec;
        ExactJumpModel model;
    }

    private static Loaded loadCapture(String capture) {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool(capture));
        if (file == null) throw new IllegalStateException(capture + ": failed to parse");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        Loaded l = new Loaded();
        l.spec = engine.debugBuildSpec();
        l.model = model;
        return l;
    }

    private void emit(String line) {
        System.out.println(line);
        rep.append(line).append('\n');
    }

    private void writeReport(String name) throws Exception {
        File dst = new File("build/reports/" + name + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static File repoFile(String rel) {
        File direct = new File(rel);
        if (direct.isAbsolute() || direct.exists()) return direct;
        File cwd = new File("").getAbsoluteFile();
        File root = cwd.getName().equals("core") ? cwd.getParentFile() : cwd;
        return new File(root, rel);
    }

    private static long envLong(String k, long def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : Long.parseLong(v.trim());
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : v;
    }
}
