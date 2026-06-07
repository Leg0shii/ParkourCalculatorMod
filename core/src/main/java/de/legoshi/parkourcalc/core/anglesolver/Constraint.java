package de.legoshi.parkourcalc.core.anglesolver;

/**
 * One per-tick constraint. X/Z/F are scalar fields; dX/dZ are range-only. Changing the
 * field across that boundary converts the constraint between scalar and range form.
 */
public final class Constraint {

    public enum Field {
        X("X"), Z("Z"), F("F"), DX("dX"), DZ("dZ");

        public final String label;

        Field(String label) {
            this.label = label;
        }

        public boolean isRangeOnly() {
            return this == DX || this == DZ;
        }
    }

    public enum Op {
        GT(">"), LT("<"), GE(">="), LE("<="), EQ("="), IN("∈");

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

    public Field getField() { return field; }
    public Op getOp() { return op; }
    public double getValue() { return value; }
    public double getLo() { return lo; }
    public double getHi() { return hi; }
    public boolean isLoInclusive() { return loInclusive; }
    public boolean isHiInclusive() { return hiInclusive; }
    public boolean isRange() { return op == Op.IN; }

    public void setValue(double value) { this.value = value; }
    public void setLo(double lo) { this.lo = lo; }
    public void setHi(double hi) { this.hi = hi; }
    public void setOp(Op op) { this.op = op; }

    public void setInclusive(boolean lo, boolean hi) {
        this.loInclusive = lo;
        this.hiInclusive = hi;
    }

    public void setField(Field next) {
        boolean wasRange = isRange();
        this.field = next;
        if (next.isRangeOnly() && !wasRange) {
            op = Op.IN;
            lo = value;
            hi = value;
            loInclusive = false;
            hiInclusive = false;
        } else if (!next.isRangeOnly() && wasRange) {
            op = Op.GT;
            value = lo;
        }
    }

    public Constraint copy() {
        Constraint c = new Constraint(field, op, value);
        c.lo = lo;
        c.hi = hi;
        c.loInclusive = loInclusive;
        c.hiInclusive = hiInclusive;
        return c;
    }
}
