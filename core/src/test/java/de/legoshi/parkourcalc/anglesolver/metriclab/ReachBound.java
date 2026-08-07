package de.legoshi.parkourcalc.anglesolver.metriclab;

import de.legoshi.parkourcalc.core.anglesolver.solver.Constants;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.List;

public final class ReachBound {

    private static final double PAD_PER_TICK = 1.0e-9;

    private ReachBound() {
    }

    static final class Gate {
        int index;
        double loX = Double.NEGATIVE_INFINITY;
        double hiX = Double.POSITIVE_INFINITY;
        double loZ = Double.NEGATIVE_INFINITY;
        double hiZ = Double.POSITIVE_INFINITY;
    }

    public static boolean possiblyFeasible(SaveFile save, ExactJumpModel model) {
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(save, model);
        } catch (RuntimeException ex) {
            return true;
        }
        if (spec == null) {
            return true;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        double[] w = stepBounds(sc, model);
        List<Gate> gates = gates(save, sc, w.length);
        for (int i = 0; i < gates.size(); i++) {
            for (int j = i + 1; j < gates.size(); j++) {
                Gate a = gates.get(i);
                Gate b = gates.get(j);
                double reach = PAD_PER_TICK;
                for (int k = a.index; k < b.index; k++) {
                    reach += w[k];
                }
                double dx = gap(a.loX, a.hiX, b.loX, b.hiX);
                double dz = gap(a.loZ, a.hiZ, b.loZ, b.hiZ);
                if (Math.sqrt(dx * dx + dz * dz) > reach) {
                    return false;
                }
            }
        }
        return true;
    }

    static double[] stepBounds(JumpPhysicsInputs sc, ExactJumpModel model) {
        int n = sc.numTicks;
        double[] w = new double[n];
        double vx = sc.initialVelocity.x;
        double vz = sc.initialVelocity.z;
        double u = Math.sqrt(vx * vx + vz * vz);
        for (int t = 0; t < n; t++) {
            double slipOv = sc.slipAt(t);
            boolean contact = !Double.isNaN(slipOv);
            float slipF = contact ? (float) slipOv : Constants.SLIP_F;
            boolean sprint = sc.sprintAt(t);
            float f4;
            float accelSpeed;
            if (contact) {
                f4 = slipF * 0.91F;
                float ground = model.modern()
                        ? 0.21600002F / (slipF * slipF * slipF)
                        : 0.16277136F / (f4 * f4 * f4);
                accelSpeed = Constants.attrValueF(sc.factorAmpAt(t), sprint) * ground;
            } else {
                f4 = 0.91F;
                accelSpeed = sc.factorSprintAt(t) ? Constants.AIR_SPEED_F : Constants.AIR_SPEED_NO_SPRINT_F;
            }
            if (sc.jumpAt(t) && contact && sprint) {
                u += 0.2;
            }
            u += accelSpeed;
            w[t] = u;
            u *= f4;
        }
        return w;
    }

    private static List<Gate> gates(SaveFile save, JumpPhysicsInputs sc, int n) {
        List<Gate> out = new ArrayList<Gate>();
        int startTick = save.angleSolver.startTick;
        boolean hasStartGate = false;
        if (save.angleSolver.ticks != null) {
            for (SaveFile.Tick t : save.angleSolver.ticks) {
                if (t == null || t.constraints == null) {
                    continue;
                }
                int index = t.tick - startTick;
                if (index < 0 || index > n) {
                    continue;
                }
                Gate g = new Gate();
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
                            g.loX = Math.max(g.loX, lo);
                            g.hiX = Math.min(g.hiX, hi);
                        } else {
                            g.loZ = Math.max(g.loZ, lo);
                            g.hiZ = Math.min(g.hiZ, hi);
                        }
                    } else if ("LE".equals(c.op)) {
                        if (x) {
                            g.hiX = Math.min(g.hiX, c.value);
                        } else {
                            g.hiZ = Math.min(g.hiZ, c.value);
                        }
                    } else if ("GE".equals(c.op)) {
                        if (x) {
                            g.loX = Math.max(g.loX, c.value);
                        } else {
                            g.loZ = Math.max(g.loZ, c.value);
                        }
                    } else if ("EQ".equals(c.op)) {
                        if (x) {
                            g.loX = Math.max(g.loX, c.value);
                            g.hiX = Math.min(g.hiX, c.value);
                        } else {
                            g.loZ = Math.max(g.loZ, c.value);
                            g.hiZ = Math.min(g.hiZ, c.value);
                        }
                    }
                }
                if (any) {
                    if (index == 0) {
                        hasStartGate = true;
                    }
                    out.add(g);
                }
            }
        }
        if (!hasStartGate) {
            Gate g = new Gate();
            g.index = 0;
            g.loX = sc.startPos.x;
            g.hiX = sc.startPos.x;
            g.loZ = sc.startPos.z;
            g.hiZ = sc.startPos.z;
            out.add(g);
        }
        out.sort((a, b) -> Integer.compare(a.index, b.index));
        return out;
    }

    private static double gap(double lo1, double hi1, double lo2, double hi2) {
        return Math.max(0.0, Math.max(lo2 - hi1, lo1 - hi2));
    }
}
