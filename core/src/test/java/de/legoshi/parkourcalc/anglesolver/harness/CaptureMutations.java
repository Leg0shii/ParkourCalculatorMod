package de.legoshi.parkourcalc.anglesolver.harness;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;

import java.util.Locale;

public final class CaptureMutations {

    public static final double SIXTEENTH = 1.0 / 16.0;

    public static final class Mutation {
        public final int goalShiftSixteenths;
        public final int tightenSixteenths;
        public final double momentumScale;

        public Mutation(int goalShiftSixteenths, int tightenSixteenths, double momentumScale) {
            this.goalShiftSixteenths = goalShiftSixteenths;
            this.tightenSixteenths = tightenSixteenths;
            this.momentumScale = momentumScale;
        }

        public String label() {
            StringBuilder b = new StringBuilder();
            if (goalShiftSixteenths != 0) {
                b.append("~g").append(goalShiftSixteenths > 0 ? "+" : "").append(goalShiftSixteenths);
            }
            if (tightenSixteenths != 0) {
                b.append("~t").append(tightenSixteenths > 0 ? "+" : "").append(tightenSixteenths);
            }
            if (momentumScale != 1.0) {
                b.append("~m").append(String.format(Locale.ROOT, "%.2g", momentumScale));
            }
            return b.toString();
        }
    }

    private CaptureMutations() {
    }

    public static SaveFile copy(SaveFile file) {
        return SaveIO.parseSafe(new Gson().toJson(file));
    }

    public static boolean apply(SaveFile file, Mutation m) {
        boolean changed = false;
        if (m.goalShiftSixteenths != 0) {
            if (!shiftGoal(file, m.goalShiftSixteenths)) return false;
            changed = true;
        }
        if (m.tightenSixteenths != 0) {
            if (!tighten(file, m.tightenSixteenths)) return false;
            changed = true;
        }
        if (m.momentumScale != 1.0) {
            if (!scaleMomentum(file, m.momentumScale)) return false;
            changed = true;
        }
        if (changed && file.angleSolver != null) file.angleSolver.result = null;
        return changed;
    }

    private static boolean shiftGoal(SaveFile file, int sixteenths) {
        SaveFile.AngleSolver a = file.angleSolver;
        if (a == null || a.ticks == null) return false;
        String axisField = "Z".equalsIgnoreCase(a.axis) ? "Z" : "X";
        boolean max = a.goal == null || "MAX".equalsIgnoreCase(a.goal);
        double delta = sixteenths * SIXTEENTH * (max ? 1.0 : -1.0);
        boolean moved = false;
        for (SaveFile.Tick t : a.ticks) {
            if (t == null || t.tick != a.landingTick || t.constraints == null) continue;
            for (SaveFile.Constraint c : t.constraints) {
                if (c == null || c.disabled || !axisField.equalsIgnoreCase(c.field)) continue;
                if (c.range) {
                    c.lo += delta;
                    c.hi += delta;
                } else {
                    c.value += delta;
                }
                moved = true;
            }
        }
        return moved;
    }

    private static boolean tighten(SaveFile file, int sixteenths) {
        SaveFile.AngleSolver a = file.angleSolver;
        if (a == null || a.ticks == null) return false;
        double d = sixteenths * SIXTEENTH;
        boolean moved = false;
        for (SaveFile.Tick t : a.ticks) {
            if (t == null || t.constraints == null) continue;
            for (SaveFile.Constraint c : t.constraints) {
                if (c == null || c.disabled || !isSpatial(c.field)) continue;
                if (c.range) {
                    if (c.hi - c.lo <= 2.0 * d) return false;
                    c.lo += d;
                    c.hi -= d;
                    moved = true;
                } else if ("LE".equalsIgnoreCase(c.op) || "LT".equalsIgnoreCase(c.op)) {
                    c.value -= d;
                    moved = true;
                } else if ("GE".equalsIgnoreCase(c.op) || "GT".equalsIgnoreCase(c.op)) {
                    c.value += d;
                    moved = true;
                }
            }
        }
        return moved;
    }

    private static boolean isSpatial(String field) {
        return "X".equalsIgnoreCase(field) || "Z".equalsIgnoreCase(field);
    }

    private static boolean scaleMomentum(SaveFile file, double scale) {
        SaveFile.AngleSolver a = file.angleSolver;
        if (a == null || a.seed == null || a.seed.vel == null || a.seed.vel.length < 3) return false;
        double vx = a.seed.vel[0];
        double vz = a.seed.vel[2];
        if (vx == 0.0 && vz == 0.0) return false;
        a.seed.vel[0] = vx * scale;
        a.seed.vel[2] = vz * scale;
        return true;
    }
}
