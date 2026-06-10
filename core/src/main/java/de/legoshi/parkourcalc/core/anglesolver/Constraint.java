package de.legoshi.parkourcalc.core.anglesolver;

/**
 * One per-tick constraint. Every field (X/Z/F/dX/dZ) accepts either a scalar comparison or a
 * range; the op carries the form (IN = range). Changing the op across that boundary converts
 * the values: entering a range seeds [value, value], leaving one keeps the lower bound.
 */
public final class Constraint {

    public enum Field {
        X("X"),
        Z("Z"),
        F("F"),
        DX("dX"),
        DZ("dZ");

        public final String label;

        Field(String label) {
            this.label = label;
        }
    }

    public enum Op {
        GT(">"),
        LT("<"),
        GE(">="),
        LE("<="),
        EQ("="),
        IN("∈");

        public final String glyph;

        Op(String glyph) {
            this.glyph = glyph;
        }
    }

    private Field field;
    private Op op;
    private double value;
    private double lo;
    private double hi;
    private boolean loInclusive;
    private boolean hiInclusive;
    /** A disabled constraint keeps its definition but is invisible to the solver (gh-118). */
    private boolean enabled = true;

    private Constraint(Field field, Op op, double value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }

    public static Constraint scalar(Field field, Op op, double value) {
        return new Constraint(field, op, value);
    }

    public static Constraint range(Field field, double lo, double hi, boolean loInclusive, boolean hiInclusive) {
        Constraint c = new Constraint(field, Op.IN, 0);
        c.lo = lo;
        c.hi = hi;
        c.loInclusive = loInclusive;
        c.hiInclusive = hiInclusive;
        return c;
    }

    public Field getField() {
        return field;
    }

    public void setField(Field next) {
        this.field = next;
    }

    public Op getOp() {
        return op;
    }

    public void setOp(Op op) {
        if (op == Op.IN && this.op != Op.IN) {
            lo = value;
            hi = value;
            loInclusive = true;
            hiInclusive = true;
        } else if (op != Op.IN && this.op == Op.IN) {
            value = lo;
        }
        this.op = op;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getLo() {
        return lo;
    }

    public void setLo(double lo) {
        this.lo = lo;
    }

    public double getHi() {
        return hi;
    }

    public void setHi(double hi) {
        this.hi = hi;
    }

    public boolean isLoInclusive() {
        return loInclusive;
    }

    public boolean isHiInclusive() {
        return hiInclusive;
    }

    public void setInclusive(boolean lo, boolean hi) {
        this.loInclusive = lo;
        this.hiInclusive = hi;
    }

    public boolean isRange() {
        return op == Op.IN;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Constraint copy() {
        Constraint c = new Constraint(field, op, value);
        c.lo = lo;
        c.hi = hi;
        c.loInclusive = loInclusive;
        c.hiInclusive = hiInclusive;
        c.enabled = enabled;
        return c;
    }
}
