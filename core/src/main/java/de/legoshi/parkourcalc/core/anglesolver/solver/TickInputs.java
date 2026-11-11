package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class TickInputs {

    public static final float SCALE = 0.98F;
    private static final float TOL = 1.0e-6F;

    public static final TickInputs NONE = new TickInputs(false, false, false, false, false);

    public final boolean w;
    public final boolean a;
    public final boolean s;
    public final boolean d;
    public final boolean sprint;

    public TickInputs(boolean w, boolean a, boolean s, boolean d, boolean sprint) {
        this.w = w;
        this.a = a;
        this.s = s;
        this.d = d;
        this.sprint = sprint;
    }

    public static TickInputs of(JumpPhysicsInputs sc, int t) {
        boolean sprint = sc.sprintAt(t);
        if (sc.strafeAt(t)) {
            int side = sc.strafeSignAt(t);
            return new TickInputs(true, side > 0, false, side < 0, sprint);
        }
        int fs = sign(sc.forwardAt(t));
        int ss = sign(sc.strafeInputAt(t));
        if (fs == 2 || ss == 2) return null;
        return new TickInputs(fs > 0, ss > 0, fs < 0, ss < 0, sprint);
    }

    private static int sign(float v) {
        if (Math.abs(v) <= TOL) return 0;
        if (Math.abs(v - SCALE) <= TOL) return 1;
        if (Math.abs(v + SCALE) <= TOL) return -1;
        return 2;
    }

    public boolean forward() {
        return w && !s;
    }

    public boolean strafes() {
        return a != d;
    }

    public boolean diagonal() {
        return forward() && strafes();
    }

    public int strafeSign() {
        return a == d ? 0 : a ? 1 : -1;
    }

    public TickInputs withSprint(boolean on) {
        return on == sprint ? this : new TickInputs(w, a, s, d, on);
    }

    public void applyTo(JumpPhysicsInputs sc, int t) {
        if (diagonal()) {
            sc.strafePerTick[t] = true;
            sc.strafeSignPerTick[t] = strafeSign();
            sc.forwardInputPerTick[t] = SCALE;
            sc.strafeInputPerTick[t] = 0.0F;
        } else {
            sc.strafePerTick[t] = false;
            sc.forwardInputPerTick[t] = SCALE * ((w ? 1 : 0) - (s ? 1 : 0));
            sc.strafeInputPerTick[t] = SCALE * ((a ? 1 : 0) - (d ? 1 : 0));
        }
        sc.sprintPerTick[t] = sprint;
        sc.sprintKeyPerTick[t] = sprint;
    }

    public static JumpPhysicsInputs editable(JumpPhysicsInputs base) {
        JumpPhysicsInputs sc = base.copy();
        int n = sc.numTicks;
        float[] fwd = new float[n];
        float[] str = new float[n];
        boolean[] sprint = new boolean[n];
        boolean[] key = new boolean[n];
        boolean[] strafe = new boolean[n];
        int[] sides = new int[n];
        for (int t = 0; t < n; t++) {
            fwd[t] = base.forwardAt(t);
            str[t] = base.strafeInputAt(t);
            sprint[t] = base.sprintAt(t);
            key[t] = base.sprintKeyAt(t);
            strafe[t] = base.strafeAt(t);
            sides[t] = base.strafeSignAt(t);
        }
        sc.forwardInputPerTick = fwd;
        sc.strafeInputPerTick = str;
        sc.sprintPerTick = sprint;
        sc.sprintKeyPerTick = key;
        sc.strafePerTick = strafe;
        sc.strafeSignPerTick = sides;
        return sc;
    }

    public static JumpPhysicsInputs cloneInputs(JumpPhysicsInputs editable) {
        JumpPhysicsInputs sc = editable.copy();
        sc.forwardInputPerTick = editable.forwardInputPerTick.clone();
        sc.strafeInputPerTick = editable.strafeInputPerTick.clone();
        sc.sprintPerTick = editable.sprintPerTick.clone();
        sc.sprintKeyPerTick = editable.sprintKeyPerTick.clone();
        sc.strafePerTick = editable.strafePerTick.clone();
        sc.strafeSignPerTick = editable.strafeSignPerTick.clone();
        return sc;
    }

    public String keys() {
        StringBuilder sb = new StringBuilder();
        if (w) sb.append("W ");
        if (a) sb.append("A ");
        if (s) sb.append("S ");
        if (d) sb.append("D ");
        if (sb.length() == 0) return "none";
        return sb.toString().trim();
    }

    public String label() {
        String k = keys();
        if (forward() && !sprint) return k + " (no sprint)";
        return k;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TickInputs)) return false;
        TickInputs x = (TickInputs) o;
        return w == x.w && a == x.a && s == x.s && d == x.d && sprint == x.sprint;
    }

    @Override
    public int hashCode() {
        return (w ? 1 : 0) | (a ? 2 : 0) | (s ? 4 : 0) | (d ? 8 : 0) | (sprint ? 16 : 0);
    }

    @Override
    public String toString() {
        return label();
    }
}
