package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        public String label() {
            StringBuilder sb = new StringBuilder("tick ").append(tick + 1);
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
    private final Map<Integer, boolean[]> flags = new HashMap<Integer, boolean[]>();
    private Landing landing;
    private int tasFirstTick = -1;

    public InputData data() {
        return data;
    }

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.size() == 0;
    }

    public InputRow row(int i) {
        return data.get(i);
    }

    public int keys(int i) {
        return mask(data.get(i));
    }

    public double[] facings() {
        double[] out = new double[data.size()];
        double prev = 0.0;
        for (int i = 0; i < out.length; i++) {
            Float yaw = data.get(i).getYaw();
            if (yaw != null) prev = yaw;
            out[i] = prev;
        }
        return out;
    }

    public boolean checkKeys(InputRow row) {
        boolean[] f = flags.get(row.getId());
        return f == null || f[0];
    }

    public boolean checkYaw(InputRow row) {
        boolean[] f = flags.get(row.getId());
        return f == null || f[1];
    }

    public void setCheckKeys(InputRow row, boolean value) {
        flagsOf(row)[0] = value;
    }

    public void setCheckYaw(InputRow row, boolean value) {
        flagsOf(row)[1] = value;
    }

    private boolean[] flagsOf(InputRow row) {
        boolean[] f = flags.get(row.getId());
        if (f == null) {
            f = new boolean[] {true, true};
            flags.put(row.getId(), f);
        }
        return f;
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

    public void replace(List<InputRow> rows, boolean[] checkKeys, boolean[] checkYaw) {
        data.clear();
        flags.clear();
        for (int i = 0; i < rows.size(); i++) {
            InputRow r = rows.get(i).copy();
            data.insertRow(i, r);
            boolean[] f = flagsOf(r);
            f[0] = checkKeys == null || checkKeys[i];
            f[1] = checkYaw == null || checkYaw[i];
        }
    }

    public void copyFrom(TurnReference other) {
        List<InputRow> rows = new ArrayList<InputRow>();
        boolean[] ck = new boolean[other.size()];
        boolean[] cy = new boolean[other.size()];
        for (int i = 0; i < other.size(); i++) {
            InputRow r = other.row(i);
            rows.add(r);
            ck[i] = other.checkKeys(r);
            cy[i] = other.checkYaw(r);
        }
        replace(rows, ck, cy);
        landing = other.landing;
        tasFirstTick = other.tasFirstTick;
    }

    public TurnReference copy() {
        TurnReference c = new TurnReference();
        c.copyFrom(this);
        return c;
    }

    public void clear() {
        data.clear();
        flags.clear();
        landing = null;
        tasFirstTick = -1;
    }

    public static int mask(InputRow row) {
        return mask(row.isKeyActive(InputRow.Key.W), row.isKeyActive(InputRow.Key.A), row.isKeyActive(InputRow.Key.S),
                row.isKeyActive(InputRow.Key.D), row.isKeyActive(InputRow.Key.JUMP), row.isKeyActive(InputRow.Key.SNEAK),
                row.isKeyActive(InputRow.Key.SPRINT));
    }

    public static int mask(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean sneak, boolean sprint) {
        return (w ? KEY_W : 0) | (a ? KEY_A : 0) | (s ? KEY_S : 0) | (d ? KEY_D : 0) | (jump ? KEY_JUMP : 0)
                | (sneak ? KEY_SNEAK : 0) | (sprint ? KEY_SPRINT : 0);
    }

    public static void applyKeys(InputRow row, int mask) {
        row.setKeyActive(InputRow.Key.W, (mask & KEY_W) != 0);
        row.setKeyActive(InputRow.Key.A, (mask & KEY_A) != 0);
        row.setKeyActive(InputRow.Key.S, (mask & KEY_S) != 0);
        row.setKeyActive(InputRow.Key.D, (mask & KEY_D) != 0);
        row.setKeyActive(InputRow.Key.JUMP, (mask & KEY_JUMP) != 0);
        row.setKeyActive(InputRow.Key.SNEAK, (mask & KEY_SNEAK) != 0);
        row.setKeyActive(InputRow.Key.SPRINT, (mask & KEY_SPRINT) != 0);
    }

    public static String describe(int mask) {
        StringBuilder sb = new StringBuilder();
        String[] labels = {"W", "A", "S", "D", "Spr", "Spc", "Snk"};
        int[] bits = {KEY_W, KEY_A, KEY_S, KEY_D, KEY_SPRINT, KEY_JUMP, KEY_SNEAK};
        for (int i = 0; i < bits.length; i++) {
            if ((mask & bits[i]) == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(labels[i]);
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
