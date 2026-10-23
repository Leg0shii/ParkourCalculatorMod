package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
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
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class StructureVariantDump {

    private static final double BASE_WARM_VIOL = 0.06248650866300087;
    private static final int BASE_N = 49;
    private static final int[] BASE_GROUNDED = {0, 12, 13, 25, 37, 38};
    private static final int[] BASE_JUMPS = {0, 13, 25, 38};

    private static final int[] DIAGONAL_TICKS = {14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
    private static final int[] NO_REFACE = {};

    private static final class Variant {
        final String name;
        final int p;
        final int srcTick;
        final int refTick;
        final int[] reface;

        Variant(String name, int p, int srcTick, int refTick, int[] reface) {
            this.name = name;
            this.p = p;
            this.srcTick = srcTick;
            this.refTick = refTick;
            this.reface = reface;
        }
    }

    private static Variant variantOf(String name) {
        if ("none".equals(name)) return new Variant("none", -1, -1, -1, NO_REFACE);
        if ("pre".equals(name)) return new Variant("pre", 0, 12, 0, NO_REFACE);
        if ("t12".equals(name)) return new Variant("t12", 13, 12, 12, NO_REFACE);
        if ("land25".equals(name)) return new Variant("land25", 25, 12, 25, NO_REFACE);
        if ("pre38".equals(name)) return new Variant("pre38", 38, 37, 37, NO_REFACE);
        if ("ref".equals(name)) return new Variant("ref", -1, -1, -1, DIAGONAL_TICKS);
        if ("preref".equals(name)) return new Variant("preref", 0, 12, 0, DIAGONAL_TICKS);
        throw new IllegalArgumentException("unknown variant: " + name);
    }

    private static int[] wallStates(Variant v, int k) {
        if (v.p < 0 || k == 0) return new int[0];
        int from = v.p == 0 ? 1 : v.p;
        int[] out = new int[k];
        for (int i = 0; i < k; i++) out[i] = from + i;
        return out;
    }

    @Test
    public void dump() throws Exception {
        Assume.assumeTrue("set PKC_SVAR=1 to run", "1".equals(System.getenv("PKC_SVAR")));
        String specEnv = System.getenv("PKC_SVAR_SPEC");
        String tag = System.getenv("PKC_SVAR_TAG");
        if (specEnv == null || specEnv.isEmpty()) {
            specEnv = "none:0,pre:1,pre:2,t12:1,t12:2,land25:1,land25:2,pre38:1,pre38:2";
        }
        if (tag == null || tag.isEmpty()) throw new IllegalStateException("PKC_SVAR_TAG required");

        double[] warmBase = loadWarm();
        Base base = buildBase(warmBase);
        for (String item : specEnv.split(",")) {
            String[] parts = item.trim().split(":");
            run(variantOf(parts[0]), Integer.parseInt(parts[1]), tag, warmBase, base);
        }
    }

    private static final class Base {
        JumpSpec spec;
        JumpPhysicsInputs sc;
        JumpLinearModel lm;
        ExactJumpModel model;
        ForwardPath warmPath;
        double warmViol;
        double platXLo;
        double platXHi;
        double platZLo;
        double platZLoRaised;
        double platZHi;
    }

    private static Base buildBase(double[] warmBase) {
        SaveFile file = parseProof();
        double[] plat = platformBounds(file);
        Built b = build(file);
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(b.spec);
        Base out = new Base();
        out.spec = patch.spec;
        out.sc = patch.spec.asScenario();
        out.lm = new JumpLinearModel(out.sc);
        out.model = b.model;
        double[] warm = warmBase.clone();
        double[] gf = out.sc.toGameFacings(Angles.wrapAll(warm));
        out.warmPath = out.model.forward(out.sc, gf);
        out.warmViol = JumpConstraintCompiler.compile(patch.spec).maxViolation(gf, out.warmPath);
        require(Math.abs(out.warmViol - BASE_WARM_VIOL) < 1e-9,
                "base warm viol drifted: got " + out.warmViol + " expected " + BASE_WARM_VIOL);
        out.platXLo = plat[0];
        out.platXHi = plat[1];
        out.platZLo = plat[2];
        out.platZLoRaised = plat[2] + RazorFixtures.RUNG_RAISE;
        out.platZHi = plat[3];
        return out;
    }

    private static void run(Variant v, int k, String tag, double[] warmBase, Base base) throws Exception {
        String cse = "svar-" + v.name + "-k" + k + "-" + tag;
        SaveFile file = parseProof();
        int[] walls = wallStates(v, k);
        mutate(file, v, k, base, walls);
        Built b = build(file);
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(b.spec);
        JumpSpec spec = patch.spec;
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        require(n == BASE_N + k, cse + ": n=" + n + " expected " + (BASE_N + k));
        require(spec.objective.tick == n, cse + ": objTick=" + spec.objective.tick + " expected " + n);

        JumpLinearModel lm = new JumpLinearModel(sc);
        TreeSet<Integer> grounded = new TreeSet<Integer>();
        TreeSet<Integer> jumps = new TreeSet<Integer>();
        for (int t = 0; t < n; t++) {
            if (!Double.isNaN(sc.slipAt(t))) grounded.add(t);
            if (sc.jumpAt(t) && !Double.isNaN(sc.slipAt(t))) jumps.add(t);
        }
        require(grounded.equals(expectedTicks(BASE_GROUNDED, v, k, true)),
                cse + ": grounded census " + grounded + " expected " + expectedTicks(BASE_GROUNDED, v, k, true));
        require(jumps.equals(expectedTicks(BASE_JUMPS, v, k, false)),
                cse + ": jump census " + jumps + " expected " + expectedTicks(BASE_JUMPS, v, k, false));
        for (int i = 0; i < k; i++) {
            int t = v.p + i;
            require(sc.slipAt(t) == 0.6, cse + ": inserted t" + t + " slip=" + sc.slipAt(t));
            require(!sc.jumpAt(t), cse + ": inserted t" + t + " has jump");
            require(lm.mMag(t) == base.lm.mMag(v.srcTick),
                    cse + ": inserted t" + t + " mMag=" + lm.mMag(t) + " expected " + base.lm.mMag(v.srcTick));
            require(lm.baseArg(t) == base.lm.baseArg(v.srcTick),
                    cse + ": inserted t" + t + " baseArg=" + lm.baseArg(t) + " expected " + base.lm.baseArg(v.srcTick));
        }
        for (int r : v.reface) {
            int idx = v.p >= 0 && r >= v.p ? r + k : r;
            require(Math.abs(lm.baseArg(idx) - (base.lm.baseArg(r) + Math.PI / 2.0)) < 1e-12,
                    cse + ": refaced t" + idx + " baseArg=" + lm.baseArg(idx)
                            + " expected " + (base.lm.baseArg(r) + Math.PI / 2.0));
            require(lm.mMag(idx) == base.lm.mMag(r), cse + ": refaced t" + idx + " mMag changed");
            require(lm.friction(idx) == base.lm.friction(r), cse + ": refaced t" + idx + " friction changed");
            require(Double.isNaN(sc.slipAt(idx)), cse + ": refaced t" + idx + " not airborne");
        }
        constraintCensus(cse, v, k, spec, base, walls);

        double[] warm = variantWarm(v, k, warmBase, lm);
        double[] gf = sc.toGameFacings(Angles.wrapAll(warm));
        ForwardPath path = b.model.forward(sc, gf);
        int prefixEnd = v.p < 0 ? BASE_N : v.p;
        if (v.reface.length > 0) prefixEnd = Math.min(prefixEnd, v.reface[0]);
        for (int t = 0; t <= prefixEnd; t++) {
            require(path.posX[t] == base.warmPath.posX[t] && path.posZ[t] == base.warmPath.posZ[t],
                    cse + ": warm prefix diverged at state " + t);
        }
        double warmViol = JumpConstraintCompiler.compile(spec).maxViolation(gf, path);
        double warmObjX = path.getPos(spec.objective.tick, spec.objective.axis);
        if (v.p < 0 && v.reface.length == 0) {
            require(warmViol == base.warmViol, cse + ": control viol " + warmViol + " != base " + base.warmViol);
        }
        if (v.p < 0 && v.reface.length > 0) {
            require(Math.abs(warmViol - base.warmViol) < 1e-2,
                    cse + ": reface warm viol " + warmViol + " too far from base " + base.warmViol);
        }

        writeDump(cse, tag, v, k, walls, spec, sc, lm, b.model, warm, gf, path, warmObjX, warmViol, base);
        System.out.println("applied: case=" + cse + " tag=" + tag + " n=" + n + " objTick=" + spec.objective.tick
                + " grounded=" + grounded + " jumps=" + jumps + " walls=" + intsToString(walls)
                + " warmObjX=" + String.format("%.10f", warmObjX)
                + " warmViol=" + String.format("%.6e", warmViol)
                + " prefixOkThrough=" + prefixEnd);
    }

    private static TreeSet<Integer> expectedTicks(int[] baseTicks, Variant v, int k, boolean addInserted) {
        TreeSet<Integer> out = new TreeSet<Integer>();
        for (int t : baseTicks) out.add(v.p >= 0 && t >= v.p ? t + k : t);
        if (addInserted && v.p >= 0) {
            for (int i = 0; i < k; i++) out.add(v.p + i);
        }
        return out;
    }

    private static void constraintCensus(String cse, Variant v, int k, JumpSpec spec, Base base, int[] walls) {
        List<String> expected = new ArrayList<String>();
        for (JumpConstraint c : base.spec.constraints) {
            require(c.t2 == null, "base constraint with t2: " + c.name);
            int t1 = v.p >= 0 && c.t1 > v.p ? c.t1 + k : c.t1;
            expected.add(consKey(c.mode.name(), t1, c.cmp.name(), c.rhs));
        }
        for (int w : walls) {
            expected.add(consKey("X", w, "GE", base.platXLo));
            expected.add(consKey("X", w, "LE", base.platXHi));
            expected.add(consKey("Z", w, "GE", base.platZLoRaised));
            expected.add(consKey("Z", w, "LE", base.platZHi));
        }
        List<String> actual = new ArrayList<String>();
        for (JumpConstraint c : spec.constraints) {
            require(c.t2 == null, cse + ": variant constraint with t2: " + c.name);
            actual.add(consKey(c.mode.name(), c.t1, c.cmp.name(), c.rhs));
        }
        Collections.sort(expected);
        Collections.sort(actual);
        require(expected.equals(actual), cse + ": constraint census mismatch\nexpected=" + expected + "\nactual=" + actual);
        StartBox sb = spec.asScenario().startBox;
        require(sb != null && sb.startFree(), cse + ": start box not free");
        require(sb.pzLo == base.platZLoRaised, cse + ": startBox pzLo=" + sb.pzLo + " expected " + base.platZLoRaised);
    }

    private static String consKey(String mode, int t1, String cmp, double rhs) {
        return mode + "|" + t1 + "|" + cmp + "|" + Double.doubleToLongBits(rhs);
    }

    private static double[] variantWarm(Variant v, int k, double[] warmBase, JumpLinearModel lmVar) {
        double[] adj = warmBase.clone();
        for (int r : v.reface) adj[r] = adj[r] - 90.0;
        if (v.p < 0 || k == 0) return adj;
        int nb = adj.length;
        double[] out = new double[nb + k];
        for (int t = 0; t < v.p; t++) out[t] = adj[t];
        int refVar = v.refTick >= v.p ? v.refTick + k : v.refTick;
        double phiRef = lmVar.baseArg(refVar) + Math.toRadians(adj[v.refTick]);
        double yawIns = Math.toDegrees(phiRef - lmVar.baseArg(v.p));
        for (int i = 0; i < k; i++) out[v.p + i] = yawIns;
        for (int t = v.p; t < nb; t++) out[t + k] = adj[t];
        return out;
    }

    private static void mutate(SaveFile file, Variant v, int k, Base base, int[] walls) {
        raiseStartBoxZLo(file, base);
        for (int r : v.reface) {
            SaveFile.Row row = file.rows.get(r);
            int ai = row.keys.indexOf("A");
            require(ai >= 0, v.name + ": reface base t" + r + " has no A key: " + row.keys);
            require(!row.keys.contains("D"), v.name + ": reface base t" + r + " already has D");
            row.keys.set(ai, "D");
            SaveFile.DebugTick d = file.debug.get(r + 1);
            require(d.moveStrafe != null && d.moveStrafe > 0,
                    v.name + ": reface base t" + r + " moveStrafe=" + d.moveStrafe);
            d.moveStrafe = -d.moveStrafe;
        }
        if (v.p < 0 || k == 0) return;
        SaveFile.Row srcRow = file.rows.get(v.srcTick);
        for (int i = 0; i < k; i++) file.rows.add(v.p, copyRow(srcRow));
        SaveFile.DebugTick srcDebug = file.debug.get(v.srcTick + 1);
        for (int i = 0; i < k; i++) file.debug.add(v.p + 1, copyDebug(srcDebug));
        SaveFile.AngleSolver a = file.angleSolver;
        a.landingTick += k;
        List<SaveFile.Tick> rebuilt = new ArrayList<SaveFile.Tick>();
        for (SaveFile.Tick e : a.ticks) {
            int consTick = e.tick > v.p ? e.tick + k : e.tick;
            int ovrTick = e.tick >= v.p ? e.tick + k : e.tick;
            if (e.constraints != null && !e.constraints.isEmpty()) {
                entry(rebuilt, consTick).constraints.addAll(e.constraints);
            }
            if (e.override != null) {
                entry(rebuilt, ovrTick).override = e.override;
            }
        }
        for (int i = 0; i < k; i++) {
            SaveFile.Tick t = entry(rebuilt, v.p + i);
            require(t.override == null, v.name + ": override collision at tick " + (v.p + i));
            t.override = groundOverride();
        }
        for (int w : walls) {
            SaveFile.Tick t = entry(rebuilt, w);
            t.constraints.add(rangeCons("X", base.platXLo, base.platXHi));
            t.constraints.add(rangeCons("Z", base.platZLoRaised, base.platZHi));
        }
        a.ticks = rebuilt;
    }

    private static void raiseStartBoxZLo(SaveFile file, Base base) {
        int edits = 0;
        for (SaveFile.Tick e : file.angleSolver.ticks) {
            if (e.tick != 0 || e.constraints == null) continue;
            for (SaveFile.Constraint c : e.constraints) {
                if (c.range && "Z".equals(c.field) && Math.abs(c.lo - RazorFixtures.RUNG_RHS) < 1e-9) {
                    c.lo = c.lo + RazorFixtures.RUNG_RAISE;
                    edits++;
                }
            }
        }
        require(edits == 1, "startBox z-lo raise edited " + edits + " constraints, expected 1");
    }

    private static SaveFile.Tick entry(List<SaveFile.Tick> list, int tick) {
        for (SaveFile.Tick t : list) {
            if (t.tick == tick) return t;
        }
        SaveFile.Tick t = new SaveFile.Tick();
        t.tick = tick;
        list.add(t);
        return t;
    }

    private static SaveFile.Override groundOverride() {
        SaveFile.Override o = new SaveFile.Override();
        o.slipperiness = "DEFAULT";
        return o;
    }

    private static SaveFile.Constraint rangeCons(String field, double lo, double hi) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = true;
        c.field = field;
        c.op = "IN";
        c.lo = lo;
        c.hi = hi;
        c.loInclusive = true;
        c.hiInclusive = true;
        c.disabled = false;
        return c;
    }

    private static SaveFile.Row copyRow(SaveFile.Row src) {
        SaveFile.Row r = new SaveFile.Row();
        r.keys = new ArrayList<String>(src.keys);
        r.yaw = src.yaw;
        r.yawLocked = src.yawLocked;
        r.pitch = src.pitch;
        r.pitchLocked = src.pitchLocked;
        r.speedAmplifier = src.speedAmplifier;
        r.jumpBoostAmplifier = src.jumpBoostAmplifier;
        return r;
    }

    private static SaveFile.DebugTick copyDebug(SaveFile.DebugTick src) {
        SaveFile.DebugTick d = new SaveFile.DebugTick();
        d.pos = src.pos == null ? null : src.pos.clone();
        d.vel = src.vel == null ? null : src.vel.clone();
        d.yaw = src.yaw;
        d.onGround = src.onGround;
        d.sneaking = src.sneaking;
        d.sprinting = src.sprinting;
        d.wallCollision = src.wallCollision;
        d.softCollision = src.softCollision;
        d.collisionAngle = src.collisionAngle;
        d.moveForward = src.moveForward;
        d.moveStrafe = src.moveStrafe;
        return d;
    }

    private static final class Built {
        JumpSpec spec;
        ExactJumpModel model;
    }

    private static Built build(SaveFile file) {
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        if (spec == null) throw new IllegalStateException("spec build failed");
        Built b = new Built();
        b.spec = spec;
        b.model = model;
        return b;
    }

    private static SaveFile parseProof() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("razor-proof"));
        if (file == null) throw new IllegalStateException("razor-proof: failed to parse");
        if (file.debug == null || file.debug.isEmpty()) throw new IllegalStateException("razor-proof: no debug ticks");
        return file;
    }

    private static double[] platformBounds(SaveFile file) {
        Double xLo = null;
        Double xHi = null;
        Double zLo = null;
        Double zHi = null;
        for (SaveFile.Tick e : file.angleSolver.ticks) {
            if (e.tick != 0 || e.constraints == null) continue;
            for (SaveFile.Constraint c : e.constraints) {
                if (!c.range) continue;
                if ("X".equals(c.field)) {
                    xLo = c.lo;
                    xHi = c.hi;
                } else if ("Z".equals(c.field)) {
                    zLo = c.lo;
                    zHi = c.hi;
                }
            }
        }
        require(xLo != null && zLo != null, "tick-0 platform constraints missing");
        require(Math.abs(zLo - RazorFixtures.RUNG_RHS) < 1e-9, "tick-0 z-lo " + zLo + " != rung rhs");
        return new double[]{xLo, xHi, zLo, zHi};
    }

    private static double[] loadWarm() {
        SaveFile file = SaveIO.parseSafe(Fixtures.rawPool("razor-proof-improved"));
        if (file == null) throw new IllegalStateException("razor-proof-improved: failed to parse");
        int startTick = file.angleSolver.startTick;
        int n = file.angleSolver.landingTick - startTick;
        require(n == BASE_N, "proof-improved n=" + n);
        double[] warm = new double[n];
        for (int j = 0; j < n; j++) warm[j] = file.debug.get(startTick + j + 1).yaw;
        return warm;
    }

    private static void writeDump(String cse, String tag, Variant v, int k, int[] walls, JumpSpec spec,
                                  JumpPhysicsInputs sc, JumpLinearModel lm, ExactJumpModel model,
                                  double[] warm, double[] gf, ForwardPath path,
                                  double warmObjX, double warmViol, Base base) throws Exception {
        int n = sc.numTicks;
        JsonObject root = new JsonObject();
        root.addProperty("case", cse);
        root.addProperty("warmSource", "razor-proof-improved yaws, variant-shifted, inserted ticks phi-matched to ref t" + v.refTick);
        root.addProperty("modern", model.modern());
        root.addProperty("perAxisInertia", model.perAxisInertia());
        root.addProperty("inertiaThreshold", model.inertiaThreshold());
        root.addProperty("numTicks", n);
        root.addProperty("objTick", spec.objective.tick);
        root.addProperty("objAxis", spec.objective.axis.name());
        root.addProperty("objSense", spec.objective.sense.name());
        root.addProperty("startTick", 0);

        JsonObject variant = new JsonObject();
        variant.addProperty("name", v.name);
        variant.addProperty("k", k);
        variant.addProperty("insertPos", v.p);
        variant.addProperty("srcTick", v.srcTick);
        variant.addProperty("refTick", v.refTick);
        variant.addProperty("tag", tag);
        variant.addProperty("baseCase", "rung5375");
        variant.addProperty("startBoxZLoRaised", base.platZLoRaised);
        JsonArray wallArr = new JsonArray();
        for (int w : walls) wallArr.add(w);
        variant.add("wallStates", wallArr);
        JsonArray refArr = new JsonArray();
        for (int r : v.reface) refArr.add(v.p >= 0 && r >= v.p ? r + k : r);
        variant.add("refaceTicks", refArr);
        root.add("variant", variant);

        JsonArray startPos = new JsonArray();
        startPos.add(sc.startPos.x);
        startPos.add(sc.startPos.y);
        startPos.add(sc.startPos.z);
        root.add("startPos", startPos);

        JsonArray initVel = new JsonArray();
        initVel.add(sc.initialVelocity.x);
        initVel.add(sc.initialVelocity.y);
        initVel.add(sc.initialVelocity.z);
        root.add("initialVelocity", initVel);

        StartBox sb = sc.startBox;
        JsonObject sbj = new JsonObject();
        sbj.addProperty("startFree", sb.startFree());
        sbj.addProperty("pinned", sb.isPinned());
        sbj.addProperty("px", sb.px);
        sbj.addProperty("pz", sb.pz);
        sbj.addProperty("vx", sb.vx);
        sbj.addProperty("vz", sb.vz);
        sbj.addProperty("pxLo", sb.pxLo);
        sbj.addProperty("pxHi", sb.pxHi);
        sbj.addProperty("pzLo", sb.pzLo);
        sbj.addProperty("pzHi", sb.pzHi);
        sbj.addProperty("vxLo", sb.vxLo);
        sbj.addProperty("vxHi", sb.vxHi);
        sbj.addProperty("vzLo", sb.vzLo);
        sbj.addProperty("vzHi", sb.vzHi);
        root.add("startBox", sbj);

        JsonArray ticks = new JsonArray();
        for (int t = 0; t < n; t++) {
            JsonObject tk = new JsonObject();
            tk.addProperty("t", t);
            tk.addProperty("f4", lm.friction(t));
            tk.addProperty("mMag", lm.mMag(t));
            tk.addProperty("baseArg", lm.baseArg(t));
            tk.addProperty("forwardMag", lm.forwardMag(t));
            tk.addProperty("strafeMag", lm.strafeMag(t));
            tk.addProperty("boost", lm.boostAt(t));
            double slip = sc.slipAt(t);
            boolean contact = !Double.isNaN(slip);
            tk.addProperty("contact", contact);
            if (contact) tk.addProperty("slip", slip);
            else tk.add("slip", null);
            tk.addProperty("jump", sc.jumpAt(t) && contact);
            tk.addProperty("sprint", sc.sprintAt(t));
            tk.addProperty("factorSprint", sc.factorSprintAt(t));
            tk.addProperty("amp", sc.factorAmpAt(t));
            ticks.add(tk);
        }
        root.add("ticks", ticks);

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

        JsonArray warmYaws = new JsonArray();
        for (double w : warm) warmYaws.add(w);
        root.add("warmYawsDeg", warmYaws);

        JsonArray warmGf = new JsonArray();
        for (double g : gf) warmGf.add(g);
        root.add("warmGameFacings", warmGf);

        JsonArray phiWarm = new JsonArray();
        for (int t = 0; t < n; t++) phiWarm.add(lm.baseArg(t) + Math.toRadians(warm[t]));
        root.add("phiWarmRad", phiWarm);

        root.add("warmPosX", arr(path.posX));
        root.add("warmPosZ", arr(path.posZ));
        root.add("warmVelX", arr(path.velX));
        root.add("warmVelZ", arr(path.velZ));
        root.addProperty("warmObjX", warmObjX);
        root.addProperty("warmViol", warmViol);

        String json = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
        File dst = new File("build/reports/miqcp-dump-" + cse + ".json");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), json.getBytes(StandardCharsets.UTF_8));
        System.out.println("SVAR DUMP wrote " + dst.getAbsolutePath()
                + " n=" + n + " objTick=" + spec.objective.tick + " warmObjX=" + warmObjX + " warmViol=" + warmViol
                + " constraints=" + spec.constraints.size());
    }

    private static JsonArray arr(double[] a) {
        JsonArray j = new JsonArray();
        for (double v : a) j.add(v);
        return j;
    }

    private static String intsToString(int[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(a[i]);
        }
        return sb.append("]").toString();
    }

    private static void require(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
