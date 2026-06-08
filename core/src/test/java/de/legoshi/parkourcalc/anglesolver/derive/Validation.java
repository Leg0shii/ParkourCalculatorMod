package de.legoshi.parkourcalc.anglesolver.derive;

import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;

/** Ground-truth verdict for a candidate solution: swept-clean against the collision blocks AND landed
 *  in the pad footprint. Produced only by {@link DeriveOracle}; a derive cannot fabricate it. */
public final class Validation {

    public final boolean valid;     // clean && landed
    public final boolean clean;     // no swept collision over any move
    public final boolean landed;    // final position inside the land footprint
    public final boolean cancelled; // solve returned null (cancelled / no result)

    public final double[] yaws;     // absolute wrapped facings the solve found (null if cancelled)
    public final ForwardPath path;  // forwarded path (null if cancelled)

    public final int hitK;          // first colliding move index, or -1
    public final char hitAxis;      // 'X' / 'Y' / 'Z', or 0
    public final int hitObs;        // colliding obstacle index, or -1

    public final double landX, landZ;

    public Validation(boolean valid, boolean clean, boolean landed, double[] yaws, ForwardPath path,
                      int hitK, char hitAxis, int hitObs, double landX, double landZ) {
        this.valid = valid;
        this.clean = clean;
        this.landed = landed;
        this.cancelled = false;
        this.yaws = yaws;
        this.path = path;
        this.hitK = hitK;
        this.hitAxis = hitAxis;
        this.hitObs = hitObs;
        this.landX = landX;
        this.landZ = landZ;
    }

    private Validation() {
        this.valid = false;
        this.clean = false;
        this.landed = false;
        this.cancelled = true;
        this.yaws = null;
        this.path = null;
        this.hitK = -1;
        this.hitAxis = 0;
        this.hitObs = -1;
        this.landX = Double.NaN;
        this.landZ = Double.NaN;
    }

    public static Validation cancelled() {
        return new Validation();
    }

    public String describe() {
        if (cancelled) return "CANCELLED/no-result";
        String hit = clean ? "clean" : ("HIT " + hitAxis + " @move" + hitK + " obs" + hitObs);
        return (valid ? "VALID" : "FAIL") + " [" + hit + ", "
                + (landed ? "landed" : "MISSED") + " land=(" + fmt(landX) + "," + fmt(landZ) + ")]";
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }
}
