package de.legoshi.parkourcalc.anglesolver.metriclab;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.List;

public final class RelaxExport {

    private static final Gson GSON = new Gson();

    public static final class TickData {
        public double acc;
        public double f4;
        public boolean jumpBoost;
    }

    public static final class GateData {
        public int index;
        public Double loX;
        public Double hiX;
        public Double loZ;
        public Double hiZ;
    }

    public static final class InstanceData {
        public String capture;
        public String label;
        public int n;
        public double velX;
        public double velZ;
        public List<TickData> ticks = new ArrayList<TickData>();
        public List<GateData> gates = new ArrayList<GateData>();
    }

    private RelaxExport() {
    }

    public static String export(String capture, String label, SaveFile save, ExactJumpModel model) {
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(save, model);
        } catch (RuntimeException ex) {
            return null;
        }
        if (spec == null) {
            return null;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        InstanceData d = new InstanceData();
        d.capture = capture;
        d.label = label;
        d.n = sc.numTicks;
        d.velX = sc.initialVelocity.x;
        d.velZ = sc.initialVelocity.z;
        for (int t = 0; t < sc.numTicks; t++) {
            double slipOv = sc.slipAt(t);
            boolean contact = !Double.isNaN(slipOv);
            float slipF = contact ? (float) slipOv : Constants.SLIP_F;
            boolean sprint = sc.sprintAt(t);
            TickData td = new TickData();
            if (contact) {
                td.f4 = slipF * 0.91F;
                float ground = model.modern()
                        ? 0.21600002F / (slipF * slipF * slipF)
                        : 0.16277136F / ((slipF * 0.91F) * (slipF * 0.91F) * (slipF * 0.91F));
                td.acc = Constants.attrValueF(sc.factorAmpAt(t), sprint) * ground;
            } else {
                td.f4 = 0.91F;
                td.acc = sc.factorSprintAt(t) ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }
            td.jumpBoost = sc.jumpAt(t) && contact && sprint;
            d.ticks.add(td);
        }
        int startTick = save.angleSolver.startTick;
        if (save.angleSolver.ticks != null) {
            for (SaveFile.Tick t : save.angleSolver.ticks) {
                if (t == null || t.constraints == null) {
                    continue;
                }
                int index = t.tick - startTick;
                if (index < 0 || index > sc.numTicks) {
                    continue;
                }
                GateData g = new GateData();
                g.index = index;
                boolean any = false;
                for (SaveFile.Constraint c : t.constraints) {
                    if (c == null || c.disabled || c.vsDz || c.refTick != null) {
                        continue;
                    }
                    boolean x = "X".equals(c.field);
                    boolean z = "Z".equals(c.field);
                    if (!x && !z) {
                        continue;
                    }
                    any = true;
                    if (c.range) {
                        double lo = Math.min(c.lo, c.hi);
                        double hi = Math.max(c.lo, c.hi);
                        if (x) {
                            g.loX = g.loX == null ? lo : Math.max(g.loX, lo);
                            g.hiX = g.hiX == null ? hi : Math.min(g.hiX, hi);
                        } else {
                            g.loZ = g.loZ == null ? lo : Math.max(g.loZ, lo);
                            g.hiZ = g.hiZ == null ? hi : Math.min(g.hiZ, hi);
                        }
                    } else if ("LE".equals(c.op)) {
                        if (x) {
                            g.hiX = g.hiX == null ? c.value : Math.min(g.hiX, c.value);
                        } else {
                            g.hiZ = g.hiZ == null ? c.value : Math.min(g.hiZ, c.value);
                        }
                    } else if ("GE".equals(c.op)) {
                        if (x) {
                            g.loX = g.loX == null ? c.value : Math.max(g.loX, c.value);
                        } else {
                            g.loZ = g.loZ == null ? c.value : Math.max(g.loZ, c.value);
                        }
                    } else if ("EQ".equals(c.op)) {
                        if (x) {
                            g.loX = g.loX == null ? c.value : Math.max(g.loX, c.value);
                            g.hiX = g.hiX == null ? c.value : Math.min(g.hiX, c.value);
                        } else {
                            g.loZ = g.loZ == null ? c.value : Math.max(g.loZ, c.value);
                            g.hiZ = g.hiZ == null ? c.value : Math.min(g.hiZ, c.value);
                        }
                    }
                }
                if (any) {
                    d.gates.add(g);
                }
            }
        }
        return GSON.toJson(d);
    }
}
