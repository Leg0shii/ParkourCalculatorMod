package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;
import java.util.Locale;

public final class RelaxationExport {

    private RelaxationExport() {
    }

    public static String json(ExactJumpModel exact, JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        SineTableGeometry geom = new SineTableGeometry(exact, sc);
        JumpLinearModel lin = new JumpLinearModel(sc, null, null, true);
        boolean[] triv = {false};
        List<JumpLinearModel.Wall> walls = lin.compileAxisWalls(spec.constraints, 0.0, triv);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"n\":").append(n).append(",\"trivial\":").append(triv[0]);
        sb.append(",\"thr\":").append(exact.inertiaThreshold()).append(",\"perAxis\":").append(exact.perAxisInertia());
        double[] rlo = new double[n];
        double[] rhi = new double[n];
        double[] u = new double[2];
        for (int t = 0; t < n; t++) {
            if (!geom.hasInput(t)) continue;
            rlo[t] = Double.POSITIVE_INFINITY;
            for (int k = 0; k < 7200; k++) {
                geom.exactU(t, (float) (k * 0.05 - 180.0), u);
                double r = StrictMath.hypot(u[0], u[1]);
                rlo[t] = Math.min(rlo[t], r);
                rhi[t] = Math.max(rhi[t], r);
            }
        }
        sb.append(",\"rlo\":").append(arr(rlo)).append(",\"rhi\":").append(arr(rhi));
        double[] fr = new double[n];
        for (int t = 0; t < n; t++) fr[t] = lin.friction(t);
        sb.append(",\"friction\":").append(arr(fr));
        StartBox box = sc.startBox;
        if (box != null && box.startFree()) {
            sb.append(String.format(Locale.ROOT, ",\"box\":[%.17g,%.17g,%.17g,%.17g]", box.pxLo - box.px,
                    box.pxHi - box.px, box.pzLo - box.pz, box.pzHi - box.pz));
        }
        double[] z0 = new double[n];
        double[] z90 = new double[n];
        double[] ax = new double[n];
        double[] az = new double[n];
        double[] ph = new double[n];
        double[] sg = new double[n];
        for (int t = 0; t < n; t++) z90[t] = 90.0;
        lin.inputVectors(z0, ax, az);
        for (int t = 0; t < n; t++) ph[t] = Math.toDegrees(Math.atan2(az[t], ax[t]));
        lin.inputVectors(z90, ax, az);
        for (int t = 0; t < n; t++) {
            double d = Math.toDegrees(Math.atan2(az[t], ax[t])) - ph[t];
            d = d - 360.0 * Math.round(d / 360.0);
            sg[t] = d > 0 ? 1.0 : -1.0;
        }
        sb.append(",\"phase\":").append(arr(ph)).append(",\"sign\":").append(arr(sg));
        if (JumpLinearModel.hasFacingWall(spec.constraints)) {
            YawTies ties = YawTies.of(spec.constraints, n);
            if (ties != null) {
                double[] var = new double[n];
                double[] off = new double[n];
                double[] pin = new double[n];
                for (int t = 0; t < n; t++) {
                    var[t] = ties.varOf(t);
                    off[t] = ties.offsetOf(t);
                    pin[t] = var[t] < 0 ? ties.pinOf(t) : 0.0;
                }
                sb.append(",\"tieVar\":").append(arr(var)).append(",\"tieOff\":").append(arr(off))
                        .append(",\"tiePin\":").append(arr(pin));
            }
        }
        sb.append(",\"vel\":[");
        boolean first = true;
        for (int axis = 0; axis < 2; axis++) {
            for (int t = 1; t < n; t++) {
                double[] vc = new double[n];
                for (int q = 0; q < t; q++) vc[q] = lin.velocityCoef(axis, q, t);
                JumpLinearModel.Wall kw = lin.keepAliveWall(axis, t, 0.0, true);
                double c0 = kw == null ? 0.0 : kw.bPrime;
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"axis\":").append(axis).append(",\"t\":").append(t).append(",\"c\":").append(arr(vc))
                        .append(String.format(Locale.ROOT, ",\"c0\":%.17g}", c0));
            }
        }
        sb.append("]");
        sb.append(",\"walls\":[");
        for (int i = 0; i < walls.size(); i++) {
            JumpLinearModel.Wall w = walls.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(w.name).append("\",\"eq\":").append(w.eq);
            sb.append(",\"cx\":").append(arr(w.coefX != null ? w.coefX : new double[n]));
            sb.append(",\"cz\":").append(arr(w.coefZ != null ? w.coefZ : new double[n]));
            sb.append(String.format(Locale.ROOT, ",\"b\":%.17g,\"p0x\":%.17g,\"p0z\":%.17g}", w.bPrime, w.p0coefX, w.p0coefZ));
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String replay(ExactJumpModel exact, JumpSpec spec, double[] yaws, double dpx, double dpz) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpLinearModel lin0 = new JumpLinearModel(sc, null, null, true);
        double[] th = yaws;
        yaws = new double[th.length];
        for (int t = 0; t < th.length; t++) {
            double a = Math.toRadians(th[t]);
            yaws[t] = lin0.recoverYawDeg(t, Math.cos(a), Math.sin(a));
        }
        double[] lux = new double[th.length];
        double[] luz = new double[th.length];
        lin0.inputVectors(yaws, lux, luz);
        boolean[] tr = {false};
        double linViol = Double.NEGATIVE_INFINITY;
        StringBuilder angs = new StringBuilder();
        for (int t = 0; t < th.length; t++) angs.append(String.format(Locale.ROOT, " %.2f", Math.toDegrees(Math.atan2(luz[t], lux[t]))));
        for (JumpLinearModel.Wall w : lin0.compileAxisWalls(spec.constraints, 0.0, tr)) {
            double v = -w.bPrime - w.p0coefX * dpx - w.p0coefZ * dpz;
            for (int t = 0; t < th.length; t++) {
                if (w.coefX != null) v += w.coefX[t] * lux[t];
                if (w.coefZ != null) v += w.coefZ[t] * luz[t];
            }
            linViol = Math.max(linViol, v);
        }
        double px = sc.startBox != null ? sc.startBox.px + dpx : sc.startPos.x;
        double pz = sc.startBox != null ? sc.startBox.pz + dpz : sc.startPos.z;
        JumpPhysicsInputs rep = sc.pinnedAt(px, pz);
        double[] gf = rep.toGameFacings(yaws);
        ForwardPath path = exact.forward(rep, gf);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double viol = compiled.maxViolation(gf, path);
        FoldReplayDriver.Params p = new FoldReplayDriver.Params();
        p.deadlineNanos = System.nanoTime() + 5_000_000_000L;
        FoldReplayDriver.Result pr = FoldReplayDriver.polishFromAnchor(exact, spec, yaws, px, pz, p);
        String pol = pr.best == null ? "none" : (pr.best.feasible() ? "FEASIBLE obj=" + pr.best.objective : "viol=" + pr.best.maxViolation);
        StringBuilder vel = new StringBuilder();
        for (int k = 0; k <= sc.numTicks; k++) {
            double lx = lin0.constPos(k, 0) + dpx;
            double lz = lin0.constPos(k, 1) + dpz;
            for (int t = 0; t < k; t++) {
                lx += lin0.coefAxis(0, t, k) * lux[t];
                lz += lin0.coefAxis(1, t, k) * luz[t];
            }
            vel.append(String.format(Locale.ROOT, " %d:lin(%.4f,%.4f)ex(%.4f,%.4f)", k, lx, lz, path.posX[k], path.posZ[k]));
        }
        for (int t = 0; t <= sc.numTicks && t < path.velX.length; t++) {
            vel.append(String.format(Locale.ROOT, " %d:%.4f,%.4f", t, path.velX[t], path.velZ[t]));
        }
        return "linViol=" + linViol + " angs" + angs + "\n replay viol=" + viol + " polish " + pol + "\n vel" + vel;
    }

    private static String arr(double[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.17g", a[i]));
        }
        return sb.append(']').toString();
    }
}
