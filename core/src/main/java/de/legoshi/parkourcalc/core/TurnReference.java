package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TurnReference {

    public static final int KEY_W = 1;
    public static final int KEY_A = 2;
    public static final int KEY_S = 4;
    public static final int KEY_D = 8;
    public static final int KEY_JUMP = 16;
    public static final int KEY_SNEAK = 32;
    public static final int KEY_SPRINT = 64;

    public static final class Landing {
        public final int tick;
        public final double xLo;
        public final double xHi;
        public final double zLo;
        public final double zHi;

        public Landing(int tick, double xLo, double xHi, double zLo, double zHi) {
            this.tick = tick;
            this.xLo = xLo;
            this.xHi = xHi;
            this.zLo = zLo;
            this.zHi = zHi;
        }

        public boolean hasX() {
            return !Double.isNaN(xLo) || !Double.isNaN(xHi);
        }

        public boolean hasZ() {
            return !Double.isNaN(zLo) || !Double.isNaN(zHi);
        }

        public boolean isEmpty() {
            return !hasX() && !hasZ();
        }

        public double marginX(double x) {
            return axisMargin(x, xLo, xHi);
        }

        public double marginZ(double z) {
            return axisMargin(z, zLo, zHi);
        }

        public double margin(double x, double z) {
            double mx = hasX() ? marginX(x) : Double.NEGATIVE_INFINITY;
            double mz = hasZ() ? marginZ(z) : Double.NEGATIVE_INFINITY;
            return Math.max(mx, mz);
        }

        public String worstAxis(double x, double z) {
            if (!hasX()) return "Z";
            if (!hasZ()) return "X";
            return marginX(x) >= marginZ(z) ? "X" : "Z";
        }

        private static double axisMargin(double pos, double lo, double hi) {
            double below = Double.isNaN(lo) ? Double.NEGATIVE_INFINITY : lo - pos;
            double above = Double.isNaN(hi) ? Double.NEGATIVE_INFINITY : pos - hi;
            return Math.max(below, above);
        }

        public String label(int base) {
            StringBuilder sb = new StringBuilder("tick ").append(base + tick + 1);
            if (hasX()) sb.append("  X ").append(bounds(xLo, xHi));
            if (hasZ()) sb.append("  Z ").append(bounds(zLo, zHi));
            return sb.toString();
        }

        private static String bounds(double lo, double hi) {
            if (Double.isNaN(hi)) return ">= " + ConstraintText.fixedStat(lo);
            if (Double.isNaN(lo)) return "<= " + ConstraintText.fixedStat(hi);
            return ConstraintText.fixedStat(lo) + " to " + ConstraintText.fixedStat(hi);
        }
    }

    private final InputData data = new InputData();
    private double[] facing = new double[0];
    private Landing landing;
    private int tasFirstTick = -1;

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.size() == 0;
    }

    public InputRow row(int i) {
        return data.get(i);
    }

    public double facing(int i) {
        return facing[i];
    }

    public double[] facings() {
        double[] out = new double[facing.length];
        double prev = 0.0;
        for (int i = 0; i < out.length; i++) {
            if (!Double.isNaN(facing[i])) prev = facing[i];
            out[i] = prev;
        }
        return out;
    }

    public boolean checkKeys(InputRow row) {
        return row.isOnejumpKeys();
    }

    public boolean checkYaw(InputRow row) {
        return row.getOnejumpFace() != InputRow.ONEJUMP_FACE_OFF;
    }

    public int optionalKeys(InputRow row) {
        return optionalMask(row);
    }

    public boolean still(InputRow row) {
        return row.getOnejumpFace() == InputRow.ONEJUMP_FACE_STILL;
    }

    public Landing landing() {
        return landing;
    }

    public void setLanding(Landing landing) {
        this.landing = landing;
    }

    public int tasFirstTick() {
        return tasFirstTick;
    }

    public void setTasFirstTick(int tick) {
        tasFirstTick = tick;
    }

    public void replace(List<InputRow> rows, double[] facings) {
        data.clear();
        facing = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            InputRow r = rows.get(i).copy();
            if (facings != null) {
                facing[i] = facings[i];
                r.setYaw(Double.isNaN(facings[i]) ? null : Float.valueOf((float) facings[i]));
            } else {
                Float yaw = r.getYaw();
                facing[i] = yaw == null ? Double.NaN : yaw;
            }
            data.insertRow(i, r);
        }
    }

    public void copyFrom(TurnReference other) {
        List<InputRow> rows = new ArrayList<InputRow>();
        for (int i = 0; i < other.size(); i++) rows.add(other.row(i));
        replace(rows, other.facing);
        landing = other.landing;
        tasFirstTick = other.tasFirstTick;
    }

    public TurnReference copy() {
        TurnReference c = new TurnReference();
        c.copyFrom(this);
        return c;
    }

    public boolean sameAs(TurnReference other) {
        if (other == null || other.size() != size() || other.tasFirstTick != tasFirstTick) return false;
        if (!sameLanding(landing, other.landing)) return false;
        for (int i = 0; i < size(); i++) {
            InputRow a = row(i);
            InputRow b = other.row(i);
            if (mask(a) != mask(b) || a.isOnejumpKeys() != b.isOnejumpKeys() || a.getOnejumpFace() != b.getOnejumpFace()
                    || optionalMask(a) != optionalMask(b) || !same(facing[i], other.facing[i])) return false;
        }
        return true;
    }

    private static boolean same(double a, double b) {
        return Double.isNaN(a) ? Double.isNaN(b) : a == b;
    }

    static final InputRow.Key[] KEYS = {InputRow.Key.W, InputRow.Key.A, InputRow.Key.S, InputRow.Key.D,
            InputRow.Key.JUMP, InputRow.Key.SNEAK, InputRow.Key.SPRINT};
    static final int[] BITS = {KEY_W, KEY_A, KEY_S, KEY_D, KEY_JUMP, KEY_SNEAK, KEY_SPRINT};
    public static final String[] LABELS = {"W", "A", "S", "D", "Spr", "Spc", "Snk"};
    public static final int[] LABEL_BITS = {KEY_W, KEY_A, KEY_S, KEY_D, KEY_SPRINT, KEY_JUMP, KEY_SNEAK};

    public static int mask(InputRow row) {
        int mask = 0;
        for (int i = 0; i < KEYS.length; i++) if (row.isKeyActive(KEYS[i])) mask |= BITS[i];
        return mask;
    }

    public static int optionalMask(InputRow row) {
        int mask = 0;
        for (int i = 0; i < KEYS.length; i++) if (row.isOnejumpOptional(KEYS[i])) mask |= BITS[i];
        return mask;
    }

    public static int mask(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean sneak, boolean sprint) {
        return (w ? KEY_W : 0) | (a ? KEY_A : 0) | (s ? KEY_S : 0) | (d ? KEY_D : 0) | (jump ? KEY_JUMP : 0)
                | (sneak ? KEY_SNEAK : 0) | (sprint ? KEY_SPRINT : 0);
    }

    public static void applyOptional(InputRow row, int mask) {
        for (int i = 0; i < KEYS.length; i++) row.setOnejumpOptional(KEYS[i], (mask & BITS[i]) != 0);
    }

    public static void applyKeys(InputRow row, int mask) {
        for (int i = 0; i < KEYS.length; i++) row.setKeyActive(KEYS[i], (mask & BITS[i]) != 0);
    }

    static boolean sameLanding(Landing a, Landing b) {
        if (a == null || b == null) return a == b;
        return a.tick == b.tick && same(a.xLo, b.xLo) && same(a.xHi, b.xHi) && same(a.zLo, b.zLo) && same(a.zHi, b.zHi);
    }

    public static String describe(int mask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LABEL_BITS.length; i++) {
            if ((mask & LABEL_BITS[i]) == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(LABELS[i]);
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    public static String keysText(int mask) {
        StringBuilder sb = new StringBuilder();
        if ((mask & KEY_W) != 0) sb.append('W');
        if ((mask & KEY_A) != 0) sb.append('A');
        if ((mask & KEY_S) != 0) sb.append('S');
        if ((mask & KEY_D) != 0) sb.append('D');
        if ((mask & KEY_JUMP) != 0) sb.append('J');
        if ((mask & KEY_SNEAK) != 0) sb.append('N');
        if ((mask & KEY_SPRINT) != 0) sb.append('R');
        return sb.length() == 0 ? "-" : sb.toString();
    }

    public static int parseKeys(String text) {
        int mask = 0;
        for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case 'W': mask |= KEY_W; break;
                case 'A': mask |= KEY_A; break;
                case 'S': mask |= KEY_S; break;
                case 'D': mask |= KEY_D; break;
                case 'J': mask |= KEY_JUMP; break;
                case 'N': mask |= KEY_SNEAK; break;
                case 'R': mask |= KEY_SPRINT; break;
                default: break;
            }
        }
        return mask;
    }
}
