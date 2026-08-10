package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the known human line for a capture through EVERY stage of the ColdCycleBeamScreen pipeline, to see
 * exactly where (if anywhere) a good solution falls out: survivor filter, coarse vs fine probe, the byte-exact
 * certify, and the candidate two-sided tail gate. PKC_COLD_DIAG_FILE + PKC_COLD_DIAG_SIG (the human sig).
 */
public class ColdHumanDiagScreen {

    @Test
    public void diag() throws Exception {
        String path = System.getenv("PKC_COLD_DIAG_FILE");
        String sig = System.getenv("PKC_COLD_DIAG_SIG");
        Assume.assumeTrue("set PKC_COLD_DIAG_FILE and PKC_COLD_DIAG_SIG",
                path != null && !path.isEmpty() && sig != null && !sig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdProblem p = ColdProblem.fromSave(file);
        ColdSearch.Config cfg = new ColdSearch.Config();
        AtomicBoolean cancel = new AtomicBoolean(false);

        int n = p.lastPressSeg + 1;
        int[] mk = new int[n];
        boolean[] hd = new boolean[n];
        int idx = 0;
        for (int k = 0; k < n; k++) {
            mk[k] = sig.charAt(idx) - '0';
            hd[k] = sig.charAt(idx + 1) == '+';
            idx += 2;
        }
        System.out.printf(Locale.ROOT, "%n===== HUMAN-LINE PIPELINE DIAG: %s =====%n", new File(path).getName());
        System.out.printf(Locale.ROOT, "sig=%s  last=%d numTicks=%d%n", sig, p.lastPressSeg, p.numTicks);

        // Scans
        ColdSearch.Sweep[] fine = scan(p, cfg, 0.5);
        ColdSearch.Sweep[] coarse = scan(p, cfg, 1.0);
        ColdSearch.Sweep[] fscan = scan(p, cfg, 1.0);

        // STAGE 1: survivor filter (momentum threads + crude tail margin) at some facing
        boolean surv = false;
        double bestW = Double.NEGATIVE_INFINITY;
        for (ColdSearch.Sweep s : fscan) {
            double[] tr = s.traceLineTo(mk, hd, p.lastPressSeg + 1);
            double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
            bestW = Math.max(bestW, w);
            if (w >= -cfg.rectSlack && s.lineTailReachable(mk, hd)) surv = true;
        }
        System.out.printf(Locale.ROOT, "STAGE1 survivor filter (momentum + lineTailReachable): %s  (bestMomentumWidth=%.4f)%n",
                surv ? "PASS (kept)" : "PRUNED (dropped -- UNSOUND!)", bestW);

        // STAGE 2: probe, fine (0.5deg) vs coarse (1deg)
        double pf = ColdSearch.probeViolOf(p, fine, sig, cfg, cancel);
        double pc = ColdSearch.probeViolOf(p, coarse, sig, cfg, cancel);
        System.out.printf(Locale.ROOT, "STAGE2 probe: fine(0.5deg)=%.4e  coarse(1deg)=%.4e  (gate was 0.01)%n", pf, pc);

        // STAGE 3: byte-exact certify via the search path (benchSig) and the pin path (certifyLine)
        long[] bench = ColdSearch.benchSig(p, fine, sig, cfg, true, false, cancel);
        System.out.printf(Locale.ROOT, "STAGE3 benchSig certify (search path): %s%n", bench[2] == 1 ? "SOLVED" : "MISS");
        ColdResult cl = ColdSearch.certifyLine(file, sig, cfg);
        System.out.printf(Locale.ROOT, "STAGE3 certifyLine (pin path): %s%s%n",
                cl != null && cl.solved() ? "SOLVED" : "MISS",
                cl != null && cl.solved() ? "  " + cl.summary() : "");

        // STAGE 4: two-sided tail gate on the human exit. Facing = the certify-found momentum facing
        // (yaws[0], held constant through the momentum phase), or the max-momentum-width facing if the
        // line did not certify. NOTE cl.facingDeg is the SEED facing, not the momentum facing.
        double theta = cl != null && cl.solved() ? cl.yaws[0] : bestMomentumTheta(p, mk, hd);
        twoSidedTailGate(p, mk, hd, theta);

        // STAGE 5: real certify cost, sweeping the capture's LAST-cycle glide length using the human sig's
        // own coast/glide/press combos for that cycle (capture-agnostic).
        int last = p.lastPressSeg;
        int[] presses = p.pressSegTicks;
        int a = presses.length >= 2 ? presses[presses.length - 2] + 1 : 0;
        int L = last - a + 1;
        int coast = mk[a];
        int glide = mk[Math.max(a, last - 1)];
        int press = mk[last];
        System.out.printf(Locale.ROOT,
                "STAGE5 certify-cost sample (last cycle segs [%d..%d], coast=%s glide=%s press=%s, glide sweep):%n",
                a, last, KeyLine.COMBO_LABEL[coast], KeyLine.COMBO_LABEL[glide], KeyLine.COMBO_LABEL[press]);
        long total = 0;
        int cnt = 0, expensive = 0, solved = 0;
        for (int j = 1; j <= L - 1; j++) {
            int[] mk2 = mk.clone();
            for (int i = 0; i < L; i++) {
                int seg = a + i;
                mk2[seg] = i < L - 1 - j ? coast : (i < L - 1 ? glide : press);
            }
            boolean[] hd2 = new boolean[last + 1];
            boolean on = false;
            for (int k = 0; k <= last; k++) {
                boolean canRun = KeyLine.canRun(mk2[k]);
                if (!canRun) on = false; else if (!on) on = true;
                hd2[k] = on && canRun;
            }
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k <= last; k++) sb.append(mk2[k]).append(hd2[k] ? '+' : '.');
            String s2 = sb.toString();
            long t0 = System.nanoTime();
            long[] r = ColdSearch.benchSig(p, fine, s2, cfg, true, false, cancel);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            total += ms; cnt++;
            if (ms > 60) expensive++;
            if (r[2] == 1) solved++;
            System.out.printf(Locale.ROOT, "  j=%2d certify=%4d ms %s%n", j, ms, r[2] == 1 ? "SOLVED" : "miss");
        }
        System.out.printf(Locale.ROOT, "STAGE5 avg=%.0f ms/certify  expensive(>60ms)=%d/%d  solved=%d%n",
                cnt == 0 ? 0.0 : (double) total / cnt, expensive, cnt, solved);
    }

    /** Forward interval reachability over the tail with constraint intersection. Sound (over-approximates the
     *  reachable set, treating X/Z accel independently), so a feasible line CANNOT be pruned. Reports whether the
     *  human exit passes and which constraint (if any) it fails. */
    private void twoSidedTailGate(ColdProblem p, int[] mk, boolean[] hd, double theta) {
        int last = p.lastPressSeg;
        ColdSearch.Sweep sw = new ColdSearch.Sweep(p, new ColdSearch.Config(), theta, 0, null);
        double[] tr = sw.traceLine(mk, hd);  // txLo,txHi,tzLo,tzHi, vx,vz, dx,dz  at tick=last
        double velX = tr[4], velZ = tr[5];
        // exit POSITION is a RANGE over the feasible start rect, not a point
        double xlo = tr[0] + tr[6], xhi = tr[1] + tr[6];
        double zlo = tr[2] + tr[7], zhi = tr[3] + tr[7];
        double vxlo = velX, vxhi = velX, vzlo = velZ, vzhi = velZ;
        System.out.printf(Locale.ROOT,
                "STAGE4 two-sided tail gate: exit@%d posX=[%.5f,%.5f] posZ=[%.5f,%.5f] vel=(%.5f,%.5f) theta=%.4f%n",
                last, xlo, xhi, zlo, zhi, velX, velZ, theta);
        double slack = cfg().rectSlack + 2.0e-3;
        boolean pruned = false;
        String failAt = "-";
        for (int k = last; k < p.numTicks; k++) {
            double a = 0.98 * (p.slip[k] < 1.0 ? accelSprint(p, k)
                    : de.legoshi.parkourcalc.core.anglesolver.solver.Constants.AIR_SPEED_F);
            if (isPress(p, k) && p.slip[k] < 1.0) a += 0.2;
            vxlo -= a; vxhi += a; vzlo -= a; vzhi += a;
            xlo += vxlo; xhi += vxhi; zlo += vzlo; zhi += vzhi;
            double f = p.slip[k] < 1.0 ? ((float) p.slip[k]) * 0.91F : 0.91;
            vxlo *= f; vxhi *= f; vzlo *= f; vzhi *= f;
            int at = k + 1;
            for (ColdProblem.Wall w : p.tailWalls) {
                if (w.segTick != at) continue;
                if (w.axisX) {
                    xlo = Math.max(xlo, w.lo - slack);
                    xhi = Math.min(xhi, w.hi + slack);
                    if (xlo > xhi) { pruned = true; failAt = "X@" + at + " reach=[" + fmt(xlo) + "," + fmt(xhi) + "] box=[" + fmt(w.lo) + "," + fmt(w.hi) + "]"; }
                } else {
                    zlo = Math.max(zlo, w.lo - slack);
                    zhi = Math.min(zhi, w.hi + slack);
                    if (zlo > zhi) { pruned = true; failAt = "Z@" + at + " reach=[" + fmt(zlo) + "," + fmt(zhi) + "] box=[" + fmt(w.lo) + "," + fmt(w.hi) + "]"; }
                }
            }
            if (pruned) break;
        }
        System.out.printf(Locale.ROOT, "STAGE4 result: %s%s%n",
                pruned ? "PRUNES the human line (UNSOUND!)" : "PASSES the human line (safe to use)",
                pruned ? "  failAt " + failAt : "");
        System.out.printf(Locale.ROOT, "  tail constraints: %s%n", tailWallStr(p));
    }

    private static String tailWallStr(ColdProblem p) {
        StringBuilder sb = new StringBuilder();
        for (ColdProblem.Wall w : p.tailWalls) {
            sb.append(w.axisX ? "X@" : "Z@").append(w.segTick)
                    .append("[").append(fmt(w.lo)).append(",").append(fmt(w.hi)).append("] ");
        }
        return sb.toString();
    }

    private static double bestMomentumTheta(ColdProblem p, int[] mk, boolean[] hd) {
        double best = 0.0;
        double bestW = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 720; i++) {
            double th = -180.0 + i * 0.5;
            ColdSearch.Sweep s = new ColdSearch.Sweep(p, new ColdSearch.Config(), th, 0, null);
            double[] tr = s.traceLine(mk, hd);
            double w = Math.min(tr[1] - tr[0], tr[3] - tr[2]);
            if (w > bestW) {
                bestW = w;
                best = th;
            }
        }
        return best;
    }

    private static double accelSprint(ColdProblem p, int k) {
        float slipF = (float) p.slip[k];
        float fr = slipF * 0.91F;
        float ground = 0.16277136F / (fr * fr * fr);
        return de.legoshi.parkourcalc.core.anglesolver.solver.Constants.attrValueF(0, true) * ground;
    }

    private static boolean isPress(ColdProblem p, int k) {
        for (int s : p.pressSegTicks) if (s == k) return true;
        return false;
    }

    private ColdSearch.Config cfg() {
        return new ColdSearch.Config();
    }

    private static ColdSearch.Sweep[] scan(ColdProblem p, ColdSearch.Config cfg, double step) {
        int steps = (int) Math.round(360.0 / step);
        ColdSearch.Sweep[] s = new ColdSearch.Sweep[steps];
        for (int i = 0; i < steps; i++) s[i] = new ColdSearch.Sweep(p, cfg, -180.0 + i * step, 0, null);
        return s;
    }

    private static String fmt(double v) {
        if (v == Double.NEGATIVE_INFINITY) return "-inf";
        if (v == Double.POSITIVE_INFINITY) return "+inf";
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
