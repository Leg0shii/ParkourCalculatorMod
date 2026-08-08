package de.legoshi.parkourcalc.anglesolver.metriclab;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratPlans;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class StratTemplates {

    public static final int ROW_PAD = 4;

    private static final Gson GSON = new Gson();

    private StratTemplates() {
    }

    public static final class Instance {
        public final String label;
        public final SaveFile save;
        public final boolean noTurn;

        Instance(String label, SaveFile save, boolean noTurn) {
            this.label = label;
            this.save = save;
            this.noTurn = noTurn;
        }
    }

    static final class Analysis {
        int fireH;
        int landingH;
        double[] runway;
        String groundSlip;
        JumpPhysicsInputs sc;
        double preLoX = Double.NEGATIVE_INFINITY;
        double preHiX = Double.POSITIVE_INFINITY;
        double preLoZ = Double.NEGATIVE_INFINITY;
        double preHiZ = Double.POSITIVE_INFINITY;
        double boxLoX = Double.NaN;
        double boxHiX = Double.NaN;
        double boxLoZ = Double.NaN;
        double boxHiZ = Double.NaN;
    }

    private static TreeSet<String> keys(String... names) {
        TreeSet<String> set = new TreeSet<String>();
        Collections.addAll(set, names);
        return set;
    }

    public static List<StratPlans.Plan> plans() {
        return StratPlans.plans("1".equals(System.getenv("PKC_TEMPLATE_WIDE")));
    }

    public static List<Instance> instancesFor(SaveFile human, ExactJumpModel model) {
        Analysis a = analyze(human, model);
        if (a == null) {
            return Collections.emptyList();
        }
        List<Instance> out = new ArrayList<Instance>();
        for (StratPlans.Plan p : plans()) {
            SaveFile s = realize(human, a, p);
            if (s == null) {
                continue;
            }
            SaveFile chained = copy(s);
            int landingNew = chained.angleSolver.landingTick;
            for (int t = 1; t <= landingNew; t++) {
                addDfZero(chained, t);
            }
            out.add(new Instance(p.label + "/nt", chained, true));
            int lastFire = lastJumpRow(s, landingNew);
            if (lastFire >= 1) {
                SaveFile ja = copy(s);
                for (int t = 1; t <= landingNew; t++) {
                    if (t != lastFire) {
                        addDfZero(ja, t);
                    }
                }
                out.add(new Instance(p.label + "/ja", ja, true));
            }
            out.add(new Instance(p.label, s, false));
        }
        return out;
    }

    private static int lastJumpRow(SaveFile s, int landingNew) {
        int last = -1;
        for (int t = 0; t <= landingNew && t < s.rows.size(); t++) {
            SaveFile.Row r = s.rows.get(t);
            if (r != null && r.keys != null && r.keys.contains("JUMP")) {
                last = t;
            }
        }
        return last;
    }

    static Analysis analyze(SaveFile human, ExactJumpModel model) {
        if (human.angleSolver.startTick != 0 || human.debug == null || human.debug.isEmpty()) {
            return null;
        }
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(human, model);
        } catch (RuntimeException ex) {
            return null;
        }
        if (spec == null) {
            return null;
        }
        Analysis a = new Analysis();
        a.sc = spec.asScenario();
        a.landingH = human.angleSolver.landingTick;
        a.fireH = -1;
        for (int k = 0; k < a.sc.numTicks; k++) {
            if (a.sc.jumpAt(k) && MeasurementEngine.groundedAt(a.sc, k)) {
                a.fireH = k;
                break;
            }
        }
        if (a.fireH < 0) {
            return null;
        }
        a.runway = startRect(human);
        if (a.runway == null) {
            return null;
        }
        a.groundSlip = slipNameAt(human, a.fireH);
        collectPreFire(human, a);
        return a;
    }

    private static double[] startRect(SaveFile human) {
        if (human.angleSolver.ticks == null) {
            return null;
        }
        for (SaveFile.Tick t : human.angleSolver.ticks) {
            if (t == null || t.tick > 1 || t.constraints == null) {
                continue;
            }
            Double loX = null;
            Double hiX = null;
            Double loZ = null;
            Double hiZ = null;
            for (SaveFile.Constraint c : t.constraints) {
                if (c == null || c.disabled || !c.range || c.vsDz || c.refTick != null) {
                    continue;
                }
                double lo = Math.min(c.lo, c.hi);
                double hi = Math.max(c.lo, c.hi);
                if ("X".equals(c.field)) {
                    loX = loX == null ? lo : Math.max(loX, lo);
                    hiX = hiX == null ? hi : Math.min(hiX, hi);
                } else if ("Z".equals(c.field)) {
                    loZ = loZ == null ? lo : Math.max(loZ, lo);
                    hiZ = hiZ == null ? hi : Math.min(hiZ, hi);
                }
            }
            if (loX != null && hiX != null && loZ != null && hiZ != null) {
                return new double[]{loX, hiX, loZ, hiZ};
            }
        }
        return null;
    }

    private static void collectPreFire(SaveFile human, Analysis a) {
        if (human.angleSolver.ticks == null) {
            return;
        }
        for (SaveFile.Tick ht : human.angleSolver.ticks) {
            if (ht == null || ht.tick < 0 || ht.tick > a.fireH || ht.constraints == null) {
                continue;
            }
            for (SaveFile.Constraint c : ht.constraints) {
                if (c == null || c.disabled || c.vsDz || c.refTick != null) {
                    continue;
                }
                boolean x = "X".equals(c.field);
                boolean z = "Z".equals(c.field);
                if (!x && !z) {
                    continue;
                }
                if (c.range) {
                    double lo = Math.min(c.lo, c.hi);
                    double hi = Math.max(c.lo, c.hi);
                    if (x) {
                        a.boxLoX = Double.isNaN(a.boxLoX) ? lo : Math.min(a.boxLoX, lo);
                        a.boxHiX = Double.isNaN(a.boxHiX) ? hi : Math.max(a.boxHiX, hi);
                    } else {
                        a.boxLoZ = Double.isNaN(a.boxLoZ) ? lo : Math.min(a.boxLoZ, lo);
                        a.boxHiZ = Double.isNaN(a.boxHiZ) ? hi : Math.max(a.boxHiZ, hi);
                    }
                } else if ("LE".equals(c.op)) {
                    if (x) {
                        a.preHiX = Math.min(a.preHiX, c.value);
                    } else {
                        a.preHiZ = Math.min(a.preHiZ, c.value);
                    }
                } else if ("GE".equals(c.op)) {
                    if (x) {
                        a.preLoX = Math.max(a.preLoX, c.value);
                    } else {
                        a.preLoZ = Math.max(a.preLoZ, c.value);
                    }
                }
            }
        }
    }

    static SaveFile realize(SaveFile human, Analysis a, StratPlans.Plan p) {
        int delta = p.fire - a.fireH;
        int landingNew = a.landingH + delta;
        if (landingNew < 2) {
            return null;
        }
        SaveFile s = copy(human);
        s.angleSolver.result = null;
        s.angleSolver.defaultInputs = "KEEP";
        s.angleSolver.defaultSprint = "DERIVE";
        s.angleSolver.effort = "FAST";
        s.angleSolver.stopOnFeasible = Boolean.TRUE;
        s.angleSolver.landingTick = landingNew;
        if (s.angleSolver.seed.vel == null || s.angleSolver.seed.vel.length < 3) {
            s.angleSolver.seed.vel = new double[]{0.0, -0.0784000015258789, 0.0};
        }

        int rowCount = landingNew + 1 + ROW_PAD;
        List<SaveFile.Row> rowsNew = new ArrayList<SaveFile.Row>(rowCount);
        for (int t = 0; t < rowCount; t++) {
            TreeSet<String> ks;
            if (t < p.fire) {
                ks = p.preRows.containsKey(t) ? new TreeSet<String>(p.preRows.get(t)) : keys();
            } else if (t == p.fire) {
                ks = new TreeSet<String>(p.fireKeys);
                int ht = t - delta;
                if (ht >= 0 && ht < human.rows.size() && human.rows.get(ht).keys != null) {
                    for (String k : human.rows.get(ht).keys) {
                        if ("A".equals(k) || "D".equals(k)) {
                            ks.add(k);
                        }
                    }
                }
            } else {
                int ht = t - delta;
                if (ht >= 0 && ht < human.rows.size() && human.rows.get(ht).keys != null) {
                    ks = new TreeSet<String>(human.rows.get(ht).keys);
                } else {
                    ks = keys("W");
                }
                int rel = t - p.fire;
                if (p.post.containsKey(rel)) {
                    String[][] patch = p.post.get(rel);
                    Collections.addAll(ks, patch[0]);
                    for (String off : patch[1]) {
                        ks.remove(off);
                    }
                }
            }
            SaveFile.Row row = new SaveFile.Row();
            row.keys = new ArrayList<String>(ks);
            rowsNew.add(row);
        }
        s.rows = rowsNew;

        List<SaveFile.Tick> ticksNew = new ArrayList<SaveFile.Tick>();
        Map<Integer, SaveFile.Tick> byTick = new LinkedHashMap<Integer, SaveFile.Tick>();
        double[] rw = effectiveRunway(a);
        for (int gt : p.groundTicks) {
            SaveFile.Tick tick = tickFor(byTick, ticksNew, gt);
            tick.override = new SaveFile.Override();
            tick.override.slipperiness = a.groundSlip;
            if (gt == p.fire && p.fire > 0) {
                continue;
            }
            addRange(tick, "X", rw[0], rw[1]);
            addRange(tick, "Z", rw[2], rw[3]);
        }
        for (int t = 0; t <= p.fire; t++) {
            addPlanes(byTick, ticksNew, t, a);
        }
        if (human.angleSolver.ticks != null) {
            for (SaveFile.Tick ht : human.angleSolver.ticks) {
                if (ht == null || ht.tick <= a.fireH || ht.tick > a.landingH) {
                    continue;
                }
                SaveFile.Tick tick = tickFor(byTick, ticksNew, ht.tick + delta);
                if (ht.override != null) {
                    tick.override = GSON.fromJson(GSON.toJson(ht.override), SaveFile.Override.class);
                    if (tick.override != null) {
                        tick.override.inputs = null;
                    }
                }
                if (ht.constraints != null) {
                    for (SaveFile.Constraint c : ht.constraints) {
                        if (c == null || "F".equals(c.field) || "DF".equals(c.field)) {
                            continue;
                        }
                        SaveFile.Constraint cc = GSON.fromJson(GSON.toJson(c), SaveFile.Constraint.class);
                        if (cc.refTick != null) {
                            cc.refTick = cc.refTick + delta;
                        }
                        tick.constraints.add(cc);
                    }
                }
            }
        }
        s.angleSolver.ticks = ticksNew;

        s.debug = synthesizeDebug(s, p, landingNew);
        return s;
    }

    private static SaveFile.Tick tickFor(Map<Integer, SaveFile.Tick> byTick, List<SaveFile.Tick> ticks, int t) {
        SaveFile.Tick tick = byTick.get(t);
        if (tick == null) {
            tick = new SaveFile.Tick();
            tick.tick = t;
            byTick.put(t, tick);
            ticks.add(tick);
        }
        return tick;
    }

    private static void addRange(SaveFile.Tick tick, String field, double lo, double hi) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = true;
        c.field = field;
        c.lo = lo;
        c.hi = hi;
        tick.constraints.add(c);
    }

    private static double[] effectiveRunway(Analysis a) {
        double[] rw = a.runway.clone();
        double loX = Math.max(Double.isNaN(a.boxLoX) ? Double.NEGATIVE_INFINITY : a.boxLoX - 0.3, a.preLoX);
        double hiX = Math.min(Double.isNaN(a.boxHiX) ? Double.POSITIVE_INFINITY : a.boxHiX + 0.3, a.preHiX);
        double loZ = Math.max(Double.isNaN(a.boxLoZ) ? Double.NEGATIVE_INFINITY : a.boxLoZ - 0.3, a.preLoZ);
        double hiZ = Math.min(Double.isNaN(a.boxHiZ) ? Double.POSITIVE_INFINITY : a.boxHiZ + 0.3, a.preHiZ);
        if (Math.max(rw[0], loX) <= Math.min(rw[1], hiX)) {
            rw[0] = Math.max(rw[0], loX);
            rw[1] = Math.min(rw[1], hiX);
        }
        if (Math.max(rw[2], loZ) <= Math.min(rw[3], hiZ)) {
            rw[2] = Math.max(rw[2], loZ);
            rw[3] = Math.min(rw[3], hiZ);
        }
        return rw;
    }

    private static void addPlanes(Map<Integer, SaveFile.Tick> byTick, List<SaveFile.Tick> ticks, int t, Analysis a) {
        if (a.preLoX == Double.NEGATIVE_INFINITY && a.preHiX == Double.POSITIVE_INFINITY
                && a.preLoZ == Double.NEGATIVE_INFINITY && a.preHiZ == Double.POSITIVE_INFINITY) {
            return;
        }
        SaveFile.Tick tick = tickFor(byTick, ticks, t);
        if (a.preLoX > Double.NEGATIVE_INFINITY) {
            addPlane(tick, "X", "GE", a.preLoX);
        }
        if (a.preHiX < Double.POSITIVE_INFINITY) {
            addPlane(tick, "X", "LE", a.preHiX);
        }
        if (a.preLoZ > Double.NEGATIVE_INFINITY) {
            addPlane(tick, "Z", "GE", a.preLoZ);
        }
        if (a.preHiZ < Double.POSITIVE_INFINITY) {
            addPlane(tick, "Z", "LE", a.preHiZ);
        }
    }

    private static void addPlane(SaveFile.Tick tick, String field, String op, double value) {
        for (SaveFile.Constraint c : tick.constraints) {
            if (c != null && !c.range && field.equals(c.field) && op.equals(c.op) && c.value == value) {
                return;
            }
        }
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = field;
        c.op = op;
        c.value = value;
        tick.constraints.add(c);
    }

    private static List<SaveFile.DebugTick> synthesizeDebug(SaveFile s, StratPlans.Plan p, int landingNew) {
        TreeSet<Integer> grounds = new TreeSet<Integer>();
        grounds.addAll(p.groundTicks);
        String def = s.angleSolver.defaultSlipperiness != null ? s.angleSolver.defaultSlipperiness : "AIR";
        for (int t = p.fire + 1; t <= landingNew; t++) {
            String slip = def;
            for (SaveFile.Tick tick : s.angleSolver.ticks) {
                if (tick != null && tick.tick == t && tick.override != null && tick.override.slipperiness != null) {
                    slip = tick.override.slipperiness;
                }
            }
            if (!"AIR".equals(slip)) {
                grounds.add(t);
            }
        }
        int n = s.rows.size() + 1;
        List<SaveFile.DebugTick> out = new ArrayList<SaveFile.DebugTick>(n);
        double[] pos = s.angleSolver.seed.pos;
        boolean sprint = false;
        for (int i = 0; i < n; i++) {
            SaveFile.DebugTick d = new SaveFile.DebugTick();
            d.pos = new double[]{pos[0], pos[1], pos[2]};
            d.vel = new double[]{0.0, 0.0, 0.0};
            d.yaw = s.angleSolver.seed.yaw;
            d.onGround = i == 0 || grounds.contains(i) || i > landingNew;
            if (i >= 1) {
                TreeSet<String> ks = new TreeSet<String>();
                if (s.rows.get(i - 1).keys != null) {
                    ks.addAll(s.rows.get(i - 1).keys);
                }
                boolean canRun = ks.contains("W") && !ks.contains("S") && !ks.contains("SNEAK");
                if (!canRun) {
                    sprint = false;
                } else if (!sprint && ks.contains("SPRINT")) {
                    sprint = true;
                }
                float scale = MeasurementEngine.KEY_INPUT_SCALE
                        * (ks.contains("SNEAK") ? MeasurementEngine.SNEAK_INPUT_SCALE : 1.0F);
                d.moveForward = scale * ((ks.contains("W") ? 1 : 0) - (ks.contains("S") ? 1 : 0));
                d.moveStrafe = scale * ((ks.contains("A") ? 1 : 0) - (ks.contains("D") ? 1 : 0));
                d.sprinting = sprint;
            } else {
                d.moveForward = 0.0F;
                d.moveStrafe = 0.0F;
                d.sprinting = false;
            }
            out.add(d);
        }
        return out;
    }

    static void addDfZero(SaveFile save, int absTick) {
        if (save.angleSolver.ticks == null) {
            save.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        }
        for (SaveFile.Tick tick : save.angleSolver.ticks) {
            if (tick != null && tick.tick == absTick) {
                for (SaveFile.Constraint c : tick.constraints) {
                    if (c != null && !c.disabled && "DF".equals(c.field)) {
                        return;
                    }
                }
                SaveFile.Constraint c = dfZero();
                tick.constraints.add(c);
                return;
            }
        }
        SaveFile.Tick tick = new SaveFile.Tick();
        tick.tick = absTick;
        tick.constraints.add(dfZero());
        save.angleSolver.ticks.add(tick);
    }

    private static SaveFile.Constraint dfZero() {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = "DF";
        c.op = "EQ";
        c.value = 0.0;
        return c;
    }

    public static void applyMovedStart(SaveFile save, double x, double z) {
        if (save.angleSolver.seed != null && save.angleSolver.seed.pos != null
                && save.angleSolver.seed.pos.length >= 3) {
            save.angleSolver.seed.pos[0] = x;
            save.angleSolver.seed.pos[2] = z;
        }
        if (save.start != null && save.start.pos != null && save.start.pos.length >= 3) {
            save.start.pos[0] = x;
            save.start.pos[2] = z;
        }
    }

    private static String slipNameAt(SaveFile file, int absTick) {
        if (file.angleSolver.ticks != null) {
            for (SaveFile.Tick tk : file.angleSolver.ticks) {
                if (tk != null && tk.tick == absTick && tk.override != null && tk.override.slipperiness != null) {
                    return tk.override.slipperiness;
                }
            }
        }
        return file.angleSolver.defaultSlipperiness != null ? file.angleSolver.defaultSlipperiness : "DEFAULT";
    }

    private static SaveFile copy(SaveFile file) {
        return GSON.fromJson(GSON.toJson(file), SaveFile.class);
    }
}
