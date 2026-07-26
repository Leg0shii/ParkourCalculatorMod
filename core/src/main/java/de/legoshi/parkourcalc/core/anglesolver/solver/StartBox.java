package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class StartBox {

    public final double px;
    public final double pz;
    public final double vx;
    public final double vz;

    public final double pxLo;
    public final double pxHi;
    public final double pzLo;
    public final double pzHi;

    public final double vxLo;
    public final double vxHi;
    public final double vzLo;
    public final double vzHi;

    public StartBox(double px, double pz, double vx, double vz,
                    double pxLo, double pxHi, double pzLo, double pzHi,
                    double vxLo, double vxHi, double vzLo, double vzHi) {
        this.px = px;
        this.pz = pz;
        this.vx = vx;
        this.vz = vz;
        this.pxLo = pxLo;
        this.pxHi = pxHi;
        this.pzLo = pzLo;
        this.pzHi = pzHi;
        this.vxLo = vxLo;
        this.vxHi = vxHi;
        this.vzLo = vzLo;
        this.vzHi = vzHi;
    }

    public static StartBox pinned(double px, double pz, double vx, double vz) {
        return new StartBox(px, pz, vx, vz, px, px, pz, pz, vx, vx, vz, vz);
    }

    public boolean startFree() {
        return pxHi > pxLo || pzHi > pzLo;
    }

    public boolean velocityFree() {
        return vxHi > vxLo || vzHi > vzLo;
    }

    public boolean isPinned() {
        return !startFree() && !velocityFree();
    }

    public String label() {
        if (isPinned()) return "pinned";
        StringBuilder sb = new StringBuilder();
        if (startFree()) {
            sb.append(String.format(java.util.Locale.ROOT, "startX[%.4f,%.4f] startZ[%.4f,%.4f]", pxLo, pxHi, pzLo, pzHi));
        }
        if (velocityFree()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format(java.util.Locale.ROOT, "velX[%.4f,%.4f] velZ[%.4f,%.4f]", vxLo, vxHi, vzLo, vzHi));
        }
        return sb.toString();
    }
}
