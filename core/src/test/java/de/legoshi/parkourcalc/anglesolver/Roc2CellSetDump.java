package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
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
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
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

public class Roc2CellSetDump {

    private static final double[] DEFAULT_BASES = {0.0, 360.0, -360.0, 720.0, -720.0};

    private static double[] bases() {
        String v = System.getenv("PKC_CELLSET_BASES");
        if (v == null || v.isEmpty()) return DEFAULT_BASES;
        String[] parts = v.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i].trim());
        return out;
    }

    @Test
    public void dump() throws Exception {
        Assume.assumeTrue("set PKC_ROC2_CELLSET=1 to run", "1".equals(System.getenv("PKC_ROC2_CELLSET")));
        String tag = System.getenv("PKC_CELLSET_TAG");
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_CELLSET_TAG required");
        String pointPath = System.getenv("PKC_CELLSET_POINT");
        if (pointPath == null || pointPath.isEmpty()) throw new IllegalStateException("PKC_CELLSET_POINT required");
        int span = envInt("PKC_CELLSET_SPAN", 64);
        double[] baseSet = bases();
        StringBuilder bs = new StringBuilder();
        for (double b : baseSet) {
            if (bs.length() > 0) bs.append(',');
            bs.append((long) b);
        }

        Loaded l = load();
        JumpSpec spec = padAugmented(l.spec);
        ExactJumpModel model = l.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        if (model.modern() || !model.perAxisInertia()) {
            throw new IllegalStateException("cellset dump supports the legacy 1.8.9 chain only");
        }
        double thr = model.inertiaThreshold();
        System.out.printf(Locale.ROOT, "ROC2CELLSET spec: n=%d objTick=%d thr=%.4f walls=%d tag=%s span=%d bases=%s%n",
                n, spec.objective.tick, thr, spec.constraints.size(), tag, span, bs);

        String[] pointPaths = pointPath.split(";");
        List<double[]> points = new ArrayList<double[]>();
        for (String pp : pointPaths) {
            if (pp.trim().isEmpty()) continue;
            JsonObject pt = new JsonParser().parse(new String(
                    Files.readAllBytes(repoFile(pp.trim()).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray gfj = pt.getAsJsonArray("gf");
            if (gfj.size() != n) throw new IllegalStateException(pp + ": point gf length " + gfj.size() + " != n " + n);
            double[] p = new double[n];
            for (int i = 0; i < n; i++) p[i] = gfj.get(i).getAsDouble();
            points.add(p);
        }
        double[] gf = points.get(0);
        System.out.println("ROC2CELLSET points=" + points.size());

        TickConst[] tc = new TickConst[n];
        for (int t = 0; t < n; t++) tc[t] = tickConst(sc, t);
        StringBuilder bt = new StringBuilder();
        for (int t = 0; t < n; t++) if (tc[t].boost) bt.append(' ').append(t);
        System.out.println("ROC2CELLSET boost ticks:" + bt);

        ForwardPath path = model.forward(sc, gf);
        double[][] baseConsts = new double[n][];
        for (int t = 0; t < n; t++) baseConsts[t] = cellConsts(tc[t], (float) gf[t]);
        int reconMismatch = reconCheck(sc, tc, baseConsts, thr, path, n);
        System.out.println("ROC2CELLSET recon mismatches=" + reconMismatch + (reconMismatch == 0 ? " PASS" : " FAIL"));
        if (reconMismatch != 0) throw new AssertionError("cell-constant reconstruction not bit-exact: " + reconMismatch);

        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double baseViol = compiled.maxViolation(gf, path);
        double maxV = 0.0;
        for (int t = 0; t <= n; t++) {
            maxV = Math.max(maxV, Math.abs(path.velX[t]));
            maxV = Math.max(maxV, Math.abs(path.velZ[t]));
        }
        System.out.printf(Locale.ROOT, "ROC2CELLSET base: viol=%.9e objX=%.10f maxAbsVel=%.4f%n",
                baseViol, path.getPos(spec.objective.tick, spec.objective.axis), maxV);

        JsonObject root = new JsonObject();
        root.addProperty("case", "roc2");
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
        JsonObject sbj = new JsonObject();
        if (sb != null && sb.startFree()) {
            sbj.addProperty("pxLo", sb.pxLo);
            sbj.addProperty("pxHi", sb.pxHi);
            sbj.addProperty("pzLo", sb.pzLo);
            sbj.addProperty("pzHi", sb.pzHi);
        } else {
            sbj.addProperty("pxLo", sc.startPos.x);
            sbj.addProperty("pxHi", sc.startPos.x);
            sbj.addProperty("pzLo", sc.startPos.z);
            sbj.addProperty("pzHi", sc.startPos.z);
        }
        root.add("startBox", sbj);

        JsonArray cons = new JsonArray();
        for (JumpConstraint c : spec.constraints) {
            JsonObject cj = new JsonObject();
            cj.addProperty("name", c.name);
            cj.addProperty("mode", c.mode.name());
            cj.addProperty("t1", c.t1);
            if (c.t2 == null) cj.add("t2", null);
            else cj.addProperty("t2", c.t2);
            cj.addProperty("op", c.op == null ? "PLUS" : c.op.name());
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
        root.addProperty("baseViolAtAuthoredStart", baseViol);

        JumpLinearModel lm = new JumpLinearModel(sc);
        JsonArray ticks = new JsonArray();
        long totalCells = 0;
        int minCells = Integer.MAX_VALUE;
        int maxCells = 0;
        long tStart = System.nanoTime();
        int wideTicks = envInt("PKC_CELLSET_WIDE_TICKS", 0);
        int wideSpan = envInt("PKC_CELLSET_WIDE_SPAN", span);
        boolean adSub = "1".equals(System.getenv("PKC_CELLSET_ADSUB"));
        TickConst[] tcFlip = new TickConst[n];
        int flipTicks = 0;
        for (int t = 0; t < n; t++) {
            if (adSub && tc[t].hasMove && tc[t].sF != 0.0F) {
                tcFlip[t] = tickConstFlipped(sc, t);
                flipTicks++;
            }
        }
        if (adSub) System.out.println("ROC2CELLSET adSub ticks=" + flipTicks);
        int coarseTicks = envInt("PKC_CELLSET_COARSE_TICKS", 0);
        double coarseStep = System.getenv("PKC_CELLSET_COARSE_STEP") != null
                ? Double.parseDouble(System.getenv("PKC_CELLSET_COARSE_STEP")) : 1.0;
        for (int t = 0; t < n; t++) {
            int spanT = t < wideTicks ? wideSpan : span;
            LinkedHashMap<Long, Float> map = new LinkedHashMap<Long, Float>();
            LinkedHashMap<Long, Float> flipMap = new LinkedHashMap<Long, Float>();
            for (double[] p : points) {
                for (double b : baseSet) {
                    float base = (float) (p[t] + b);
                    float[] reps = FacingLattice.cellRepresentatives(base, -spanT, spanT, false, tc[t].boost);
                    for (float r : reps) {
                        Long id = Long.valueOf(FacingLattice.jointCellId(r, false, tc[t].boost));
                        if (!map.containsKey(id)) map.put(id, Float.valueOf(r));
                    }
                    if (t < coarseTicks) {
                        for (double d = -180.0; d < 180.0; d += coarseStep) {
                            float[] one = FacingLattice.cellRepresentatives((float) (p[t] + b + d), 0, 0, false, tc[t].boost);
                            for (float r : one) {
                                Long id = Long.valueOf(FacingLattice.jointCellId(r, false, tc[t].boost));
                                if (!map.containsKey(id)) map.put(id, Float.valueOf(r));
                            }
                        }
                    }
                    if (tcFlip[t] != null) {
                        double delta = Math.toDegrees(2.0 * Math.atan2(tc[t].sF, tc[t].fF));
                        for (int sgn = -1; sgn <= 1; sgn += 2) {
                            float fbase = (float) (p[t] + b + sgn * delta);
                            float[] freps = FacingLattice.cellRepresentatives(fbase, -spanT, spanT, false, tcFlip[t].boost);
                            for (float r : freps) {
                                Long id = Long.valueOf(FacingLattice.jointCellId(r, false, tcFlip[t].boost));
                                if (!flipMap.containsKey(id)) flipMap.put(id, Float.valueOf(r));
                            }
                        }
                    }
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
                for (int k = 1; k <= 12; k++) {
                    float up = (float) ((double) r + 360.0 * k);
                    if (Math.abs(up) > 12000.0F || FacingLattice.jointCellId(up, false, tc[t].boost) != cid) break;
                    emax = up;
                }
                for (int k = 1; k <= 12; k++) {
                    float dn = (float) ((double) r - 360.0 * k);
                    if (Math.abs(dn) > 12000.0F || FacingLattice.jointCellId(dn, false, tc[t].boost) != cid) break;
                    emin = dn;
                }
                co.addProperty("emin", emin);
                co.addProperty("emax", emax);
                cells.add(co);
                if (e.getKey().longValue() == baseId) baseIdx = idx;
                idx++;
            }
            for (Map.Entry<Long, Float> e : flipMap.entrySet()) {
                float r = e.getValue().floatValue();
                double[] cc = cellConsts(tcFlip[t], r);
                JsonObject co = new JsonObject();
                co.addProperty("gf", (double) r);
                co.addProperty("cx", cc[0]);
                co.addProperty("cz", cc[1]);
                if (tcFlip[t].boost) {
                    co.addProperty("bx", cc[2]);
                    co.addProperty("bz", cc[3]);
                }
                co.addProperty("norm", normAt(r));
                co.addProperty("adFlip", true);
                cells.add(co);
                idx++;
            }
            if (baseIdx < 0) throw new IllegalStateException("base cell missing at t=" + t);
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
        System.out.printf(Locale.ROOT, "ROC2CELLSET cells: total=%d perTick min=%d max=%d avg=%.1f wallMs=%d%n",
                totalCells, minCells, maxCells, totalCells / (double) n, ms);

        File dst = repoFile("tools/miqcp/roc2-cellset-span" + span + "-" + tag + ".json");
        Files.write(dst.toPath(), new GsonBuilder().create().toJson(root).getBytes(StandardCharsets.UTF_8));
        System.out.println("ROC2CELLSET wrote " + dst.getPath());
    }

    @Test
    public void verify() throws Exception {
        Assume.assumeTrue("set PKC_ROC2_VERIFY=1 to run", "1".equals(System.getenv("PKC_ROC2_VERIFY")));
        String src = System.getenv("PKC_CELLSET_SRC");
        if (src == null || src.isEmpty()) throw new IllegalStateException("PKC_CELLSET_SRC required");

        Loaded l = load();
        JumpSpec spec = padAugmented(l.spec);
        ExactJumpModel model = l.model;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpConstraintCompiler.Compiled full = JumpConstraintCompiler.compile(spec);

        JsonObject srcJson = new JsonParser().parse(new String(
                Files.readAllBytes(repoFile(src).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray sols = srcJson.getAsJsonArray("solutions");
        System.out.println("ROC2VERIFY solutions=" + sols.size() + " src=" + src);

        double bestViol = Double.POSITIVE_INFINITY;
        double bestObj = Double.NEGATIVE_INFINITY;
        int bestIdx = -1;
        for (int s = 0; s < sols.size(); s++) {
            JsonObject so = sols.get(s).getAsJsonObject();
            JsonArray ga = so.getAsJsonArray("gf");
            if (ga.size() != n) throw new IllegalStateException("sol[" + s + "] gf length " + ga.size());
            double[] gf = new double[n];
            for (int i = 0; i < n; i++) gf[i] = ga.get(i).getAsDouble();
            ForwardPath p = model.forward(sc, gf);
            double viol = full.maxViolation(gf, p);
            double obj = p.getPos(spec.objective.tick, spec.objective.axis);
            System.out.printf(Locale.ROOT, "ROC2VERIFY sol[%02d] milpObj=%+.9e exactViol=%.9e exactObjX=%.10f%s%n",
                    s, so.get("obj").getAsDouble(), viol, obj, viol <= 0.0 ? "  <-FEASIBLE" : "");
            for (JumpConstraint c : spec.constraints) {
                if (c.t2 != null) continue;
                double got = c.mode == JumpConstraint.Mode.X ? p.posX[c.t1] : p.posZ[c.t1];
                double cv;
                if (c.cmp == JumpConstraint.Cmp.GE) cv = c.rhs - got;
                else if (c.cmp == JumpConstraint.Cmp.LE) cv = got - c.rhs;
                else cv = Math.abs(got - c.rhs);
                if (cv > 1.0e-9) {
                    System.out.printf(Locale.ROOT, "ROC2VERIFY   sol[%02d] VIOL %s t=%d %s rhs=%.6f got=%.9f by=%.3e%n",
                            s, c.mode, c.t1, c.cmp, c.rhs, got, cv);
                }
            }
            if (viol < bestViol || (viol <= 0.0 && obj > bestObj)) {
                bestViol = Math.min(bestViol, viol);
                if (viol <= 0.0) bestObj = Math.max(bestObj, obj);
                bestIdx = s;
            }
        }
        System.out.printf(Locale.ROOT, "ROC2VERIFY SUMMARY bestViol=%.9e bestFeasObjX=%s bestIdx=%d%n",
                bestViol, bestObj == Double.NEGATIVE_INFINITY ? "none" : String.format(Locale.ROOT, "%.10f", bestObj),
                bestIdx);
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

    private static TickConst tickConstFlipped(JumpPhysicsInputs sc, int t) {
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
            strafe = -sc.strafeSign * 1.0F * 0.98F;
        } else {
            strafe = -sc.strafeInputAt(t);
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

    private static Loaded load() throws Exception {
        String path = System.getenv("PKC_SOLVE_FILE");
        if (path == null || path.isEmpty()) throw new IllegalStateException("PKC_SOLVE_FILE required");
        SaveFile file = SaveIO.parseSafe(new String(
                Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        if (file == null) throw new IllegalStateException(path + ": failed to parse");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        String startOv = System.getenv("PKC_START_TICK");
        if (startOv != null && !startOv.isEmpty()) state.setStartTick(Integer.parseInt(startOv));
        String landOv = System.getenv("PKC_LANDING_TICK");
        if (landOv != null && !landOv.isEmpty()) state.setLandingTick(Integer.parseInt(landOv));
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        Loaded l = new Loaded();
        l.spec = engine.debugBuildSpec();
        l.model = model;
        return l;
    }

    private static JumpSpec padAugmented(JumpSpec spec) {
        String rhsEnv = System.getenv("PKC_GOAL_RHS");
        if (rhsEnv == null || rhsEnv.isEmpty()) throw new IllegalStateException("PKC_GOAL_RHS required");
        double rhs = Double.parseDouble(rhsEnv);
        List<JumpConstraint> aug = new ArrayList<JumpConstraint>(spec.constraints);
        aug.add(new JumpConstraint(JumpConstraint.Mode.X, spec.objective.tick, null, null,
                JumpConstraint.Cmp.GE, rhs, "padGE"));
        return new JumpSpec(spec.asScenario(), aug, spec.objective);
    }

    private static File repoFile(String rel) {
        File direct = new File(rel);
        if (direct.isAbsolute() || direct.exists()) return direct;
        File cwd = new File("").getAbsoluteFile();
        File root = cwd.getName().equals("core") ? cwd.getParentFile() : cwd;
        return new File(root, rel);
    }

    private static int envInt(String name, int dflt) {
        String v = System.getenv(name);
        return v != null && !v.isEmpty() ? Integer.parseInt(v) : dflt;
    }
}
