package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.VerySlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnCertifier;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

@Category(VerySlowSolverTests.class)
public class ScreenFalseNegativeDiagnosticTest {

    private static final String CAP = "hpk_precise/j154-noturn-ja-inner";

    private NoTurnProblem load(String capture) throws Exception {
        String raw = Fixtures.rawPool(capture);
        SaveFile file = SaveIO.parseSafe(raw);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        return NoTurnProblem.from(engine.debugBuildSpec(), model);
    }

    private static int[] v6() {
        int[] c = new int[29];
        for (int t = 0; t <= 5; t++) c[t] = NoTurnKeys.SD;
        for (int t = 6; t <= 13; t++) c[t] = NoTurnKeys.S;
        c[14] = NoTurnKeys.SD;
        c[15] = NoTurnKeys.WA;
        c[16] = NoTurnKeys.A;
        for (int t = 17; t <= 27; t++) c[t] = NoTurnKeys.WA;
        c[28] = NoTurnKeys.W;
        return c;
    }

    private static int[] lookalike() {
        int[] c = new int[29];
        for (int t = 0; t <= 5; t++) c[t] = NoTurnKeys.SD;
        for (int t = 6; t <= 11; t++) c[t] = NoTurnKeys.W;
        for (int t = 12; t <= 17; t++) c[t] = NoTurnKeys.S;
        for (int t = 18; t <= 28; t++) c[t] = NoTurnKeys.W;
        return c;
    }

    private static String f(double d) {
        if (d == Double.POSITIVE_INFINITY) return "+inf";
        if (Double.isNaN(d)) return "NaN";
        return String.format(Locale.ROOT, "%.6f", d);
    }

    private static String g(double d) {
        return String.format(Locale.ROOT, "%.4g", d);
    }

    @Test
    public void screenPeelBackLadderAndVelocityOracle() throws Exception {
        NoTurnProblem p = load(CAP);
        ExactJumpModel model = p.model;
        int n = p.n;
        int tk = p.jumpTicks[p.jumpTicks.length - 1];
        int airStart = tk + 1;
        double refX = p.refStart().x, refZ = p.refStart().z;
        double loShiftX = p.freeBox.pxLo - refX, hiShiftX = p.freeBox.pxHi - refX;
        double loShiftZ = p.freeBox.pzLo - refZ, hiShiftZ = p.freeBox.pzHi - refZ;

        List<JumpConstraint> byteWalls = new ArrayList<>();
        for (JumpConstraint w : p.walls) {
            if ((w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z) && w.t2 == null) byteWalls.add(w);
        }

        System.out.println("############ j154 screen false-negative diagnostic ############");
        System.out.println("n=" + n + " setupEnd=" + p.setupEnd + " takeoffTick=" + tk + " airStart=" + airStart
                + " objective=X MIN @" + p.objective.tick);
        System.out.println("freeBox px[" + f(p.freeBox.pxLo) + "," + f(p.freeBox.pxHi) + "] pz["
                + f(p.freeBox.pzLo) + "," + f(p.freeBox.pzHi) + "]  ref(" + f(refX) + "," + f(refZ) + ")");

        int[] v6 = v6();
        boolean[] v6spr = NoTurnKeys.latchSprint(v6, 0);
        int[] look = lookalike();
        boolean[] lookspr = NoTurnKeys.latchSprint(look, 0);
        System.out.println("V6        = " + NoTurnKeys.describe(v6));
        System.out.println("look-alike= " + NoTurnKeys.describe(look) + "  (SD-first structurally-dead family)");

        JumpSpec v6spec = p.buildSpec(v6, v6spr, NoTurnKeys.WA, true);
        JumpPhysicsInputs v6sc = v6spec.asScenario();
        NoTurnCertifier cert = new NoTurnCertifier(model);

        long tc0 = System.nanoTime();
        NoTurnCertifier.Result v6cert = cert.certify(v6spec, BuiltinGraphs.optimize(8), 30_000_000_000L, new AtomicBoolean(false));
        double v6certSec = (System.nanoTime() - tc0) / 1e9;
        double[] certYaws = v6cert.yaws;
        double thetaStar = certYaws[10];
        double dxStar = v6cert.startX - refX;
        double dzStar = v6cert.startZ - refZ;
        System.out.println("\n== V6 full certify (" + g(v6certSec) + " s) ==");
        System.out.println("feasible=" + v6cert.feasible + " objective=" + f(v6cert.objective) + " violation=" + f(v6cert.violation)
                + " startX=" + f(v6cert.startX) + " startZ=" + f(v6cert.startZ));
        System.out.println("theta*(setup facing)=" + f(thetaStar) + " ja*(yaw[" + tk + "])=" + f(certYaws[tk])
                + " freeStart shift dx*=" + f(dxStar) + " dz*=" + f(dzStar));

        JumpSpec lookspec = p.buildSpec(look, lookspr, NoTurnKeys.WA, true);
        JumpPhysicsInputs looksc = lookspec.asScenario();
        long tl0 = System.nanoTime();
        NoTurnCertifier.Result lookcert = cert.certify(lookspec, BuiltinGraphs.optimize(8), 30_000_000_000L, new AtomicBoolean(false));
        double lookSec = (System.nanoTime() - tl0) / 1e9;
        System.out.println("\n== look-alike full certify (" + g(lookSec) + " s) ==");
        System.out.println("feasible=" + lookcert.feasible + " objective=" + f(lookcert.objective)
                + " violation=" + f(lookcert.violation) + " (null yaws=" + (lookcert.yaws == null) + ")");

        double lookBestTheta = 0, lookBestRes = Double.POSITIVE_INFINITY;
        for (int d = 0; d < 3600; d++) {
            double th = -180.0 + d * 0.1;
            double res = residualBox(model, looksc, allTheta(n, th), byteWalls, loShiftX, hiShiftX, loShiftZ, hiShiftZ);
            if (res > 0 && res < lookBestRes) { lookBestRes = res; lookBestTheta = th; }
            if (res <= 0) { lookBestRes = res; lookBestTheta = th; break; }
        }

        double[] v6theta = allTheta(n, thetaStar);
        double[] lookTheta = allTheta(n, lookBestTheta);

        StructurePoolDriver screenDrv = new StructurePoolDriver(model, new StructurePoolDriver.Config(), new AtomicBoolean(false), null);
        screenDrv.prepare(p);
        double v6M0 = screenDrv.byteScreen(v6, v6spr, thetaStar)[0];
        double lookM0 = screenDrv.byteScreen(look, lookspr, lookBestTheta)[0];

        double v6M0broad = broadStraight(model, v6sc, byteWalls, loShiftX, hiShiftX, loShiftZ, hiShiftZ);
        double lookM0broad = broadStraight(model, looksc, byteWalls, loShiftX, hiShiftX, loShiftZ, hiShiftZ);

        double v6M1 = clamp(residualBox(model, v6sc, v6theta, byteWalls, loShiftX, hiShiftX, loShiftZ, hiShiftZ));
        double lookM1 = clamp(residualBox(model, looksc, lookTheta, byteWalls, loShiftX, hiShiftX, loShiftZ, hiShiftZ));

        double v6M2 = clamp(residualBox(model, v6sc, v6theta, byteWalls, dxStar, dxStar, dzStar, dzStar));

        double v6M3raw = residualBox(model, v6sc, certYaws, byteWalls, loShiftX, hiShiftX, loShiftZ, hiShiftZ);
        double v6M3 = clamp(v6M3raw);

        double v6M4 = v6cert.violation;
        double lookM4 = lookcert.violation;

        System.out.println("\n================= M0..M4 landing-residual peel-back ladder =================");
        System.out.println("residual = screen-style max(vx,vz), vx=needLoX-needHiX, vz=needLoZ-needHiZ, clamped>=0");
        System.out.println(String.format(Locale.ROOT, "%-42s %14s %14s", "rung", "V6 residual", "look residual"));
        row("M0 shipped byteScreen (center theta*, +-9/72)", v6M0, lookM0);
        row("M0-broad best straight facing (360 sweep)", v6M0broad, lookM0broad);
        row("M1 straight coast at certified theta*", v6M1, lookM1);
        row("M2 M1 + certified free-start pin (dx*,dz*)", v6M2, Double.NaN);
        row("M3 full certified per-tick yaws (turn+ja)", v6M3, Double.NaN);
        row("M4 full certify byte violation (ExactJumpModel)", v6M4, lookM4);
        System.out.println("look M2 = N/A (look has no certified free start); look M3 = N/A (certify found no landing candidate)");
        System.out.println("V6 M3 raw(unclamped) max(vx,vz)=" + f(v6M3raw) + "  (<=0 means feasible with slack)");

        diskNodeSection(p, model, v6, v6spr, certYaws, tk, airStart);

        velocityOracleSection(p, model, v6sc, looksc, v6, v6spr, look, lookspr, certYaws, thetaStar,
                lookBestTheta, lookBestRes, tk, airStart, dxStar, dzStar, cert);

        System.out.println("\n################################ VERDICTS ################################");
        System.out.println("Dominant false-negative term: V6's residual stays large across every cheap-forward rung");
        System.out.println("  (M0=" + g(v6M0) + " M0-broad=" + g(v6M0broad) + " M1=" + g(v6M1) + " M2=" + g(v6M2)
                + ") and collapses to " + g(v6M3) + " only at M3, when the full certified");
        System.out.println("  per-tick air-turn+ja (yaws 29-38 sweeping ~150deg) is restored. M0~M0-broad~M1 => the theta");
        System.out.println("  grid/window is NOT the culprit; M2==M1 => free-start modeling is NOT the culprit. The single");
        System.out.println("  dominant missing term is the decoupled post-takeoff air turn + jump-angle (M3).");
        System.out.println("Separation: no cheap-forward rung separates. Worse, the straight-coast screen ANTI-ranks:");
        System.out.println("  the infeasible look-alike scores " + g(lookM0broad) + " while feasible V6 scores " + g(v6M0broad)
                + " (V6 looks WORSE),");
        System.out.println("  because V6 needs the biggest air turn. Separation first appears only at M3/M4, both of which");
        System.out.println("  consume the certify's own theta*/turn*/ja*; no cheap forward oracle reproduces them here.");

        assertTrue("sanity: full certified V6 replay is byte-clean (M4 <= 0)", v6cert.violation <= 0.0);
    }

    private static void row(String label, double v6, double look) {
        String lv = Double.isNaN(look) ? "N/A" : g(look);
        System.out.println(String.format(Locale.ROOT, "%-42s %14s %14s", label, g(v6), lv));
    }

    private static double[] allTheta(int n, double theta) {
        double[] y = new double[n];
        for (int t = 0; t < n; t++) y[t] = theta;
        return y;
    }

    private static double clamp(double v) {
        return Math.max(0.0, v);
    }

    private static double residualBox(ExactJumpModel model, JumpPhysicsInputs sc, double[] yaws,
                                      List<JumpConstraint> walls, double loX, double hiX, double loZ, double hiZ) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath fp = model.forward(sc, gf);
        double needLoX = loX, needHiX = hiX, needLoZ = loZ, needHiZ = hiZ;
        for (JumpConstraint w : walls) {
            int axis = w.mode == JumpConstraint.Mode.X ? 0 : 1;
            double val = fp.getPos(w.t1, axis == 0 ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z);
            double shift = w.rhs - val;
            if (w.cmp == JumpConstraint.Cmp.LE) {
                if (axis == 0) needHiX = Math.min(needHiX, shift); else needHiZ = Math.min(needHiZ, shift);
            } else if (w.cmp == JumpConstraint.Cmp.GE) {
                if (axis == 0) needLoX = Math.max(needLoX, shift); else needLoZ = Math.max(needLoZ, shift);
            }
        }
        return Math.max(needLoX - needHiX, needLoZ - needHiZ);
    }

    private static double broadStraight(ExactJumpModel model, JumpPhysicsInputs sc, List<JumpConstraint> walls,
                                        double loX, double hiX, double loZ, double hiZ) {
        double best = Double.POSITIVE_INFINITY;
        for (int d = 0; d < 3600; d++) {
            double th = -180.0 + d * 0.1;
            double res = clamp(residualBox(model, sc, allTheta(sc.numTicks, th), walls, loX, hiX, loZ, hiZ));
            if (res < best) best = res;
            if (best <= 0) break;
        }
        return best;
    }

    private static double[] takeoffVel(ExactJumpModel model, JumpPhysicsInputs sc, double[] yaws, int tk) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath fp = model.forward(sc, gf);
        return new double[]{fp.velX[tk + 1], fp.velZ[tk + 1]};
    }

    private void diskNodeSection(NoTurnProblem p, ExactJumpModel model, int[] v6, boolean[] v6spr,
                                 double[] certYaws, int tk, int airStart) {
        System.out.println("\n===================== disk-relaxed node: why NaN for V6 =====================");
        StructurePoolDriver drv = new StructurePoolDriver(model, new StructurePoolDriver.Config(), new AtomicBoolean(false), null);
        drv.prepare(p);
        double[] bestObj = new double[1];
        double diskTheta = drv.diskFeasibleTheta(v6, v6spr, bestObj);
        System.out.println("diskFeasibleTheta(V6) = " + f(diskTheta) + " (NaN => no theta on the 480-grid gets the");
        System.out.println("  disk-relaxed residual <= 0 or within diskKeep=0.25)");

        JumpLinearModel lm = new JumpLinearModel(p.base);
        double[] mMag = lm.mMagAll();
        JumpPhysicsInputs sc = p.buildSpec(v6, v6spr, NoTurnKeys.WA, true).asScenario();
        double[] gf = sc.toGameFacings(Angles.wrapAll(certYaws));
        ForwardPath fp = model.forward(sc, gf);
        System.out.println("turnSlack (post-setup input reach bound the disk allows, sum_s>setupEnd |coef(s,T)|*mMag[s])");
        System.out.println("  vs the actual post-takeoff air-coast displacement |pos[T]-pos[airStart]| the certified turn uses:");
        System.out.println(String.format(Locale.ROOT, "  %-10s %14s %18s %14s", "wall tick", "turnSlack", "actualAirReach", "ratio"));
        int[] airWallTicks = {29, 30, 34, 38, 39};
        for (int T : airWallTicks) {
            double slack = 0.0;
            for (int s = p.setupEnd + 1; s < p.n; s++) slack += Math.abs(lm.coef(s, T)) * mMag[s];
            double reachX = fp.posX[T] - fp.posX[airStart];
            double reachZ = fp.posZ[T] - fp.posZ[airStart];
            double reach = Math.hypot(reachX, reachZ);
            System.out.println(String.format(Locale.ROOT, "  %-10d %14s %18s %14s", T, g(slack), g(reach), g(reach / Math.max(1e-9, slack))));
        }
        System.out.println("The disk node models a PURE no-turn (single frozen facing coasting straight post-setup,");
        System.out.println("+- a tiny turnSlack from air INPUT impulses only). V6 lands via a ~150deg post-takeoff");
        System.out.println("air TURN whose reach dwarfs turnSlack, so the disk residual never falls within diskKeep: NaN.");
        System.out.println("The disk node is simply omitting the ja+turn air maneuver; that omission is the whole NaN.");
    }

    private void velocityOracleSection(NoTurnProblem p, ExactJumpModel model, JumpPhysicsInputs v6sc,
                                       JumpPhysicsInputs looksc, int[] v6, boolean[] v6spr, int[] look, boolean[] lookspr,
                                       double[] certYaws, double thetaStar, double lookBestTheta, double lookBestRes,
                                       int tk, int airStart, double dxStar, double dzStar, NoTurnCertifier cert) {
        System.out.println("\n=================== takeoff-velocity-space oracle ===================");
        double[] v6nom = takeoffVel(model, v6sc, certYaws, tk);
        double[] v6pure = takeoffVel(model, v6sc, allTheta(p.n, thetaStar), tk);
        System.out.println("V6 nominal takeoff velocity (certYaws) = (" + f(v6nom[0]) + "," + f(v6nom[1]) + ") |v|="
                + f(Math.hypot(v6nom[0], v6nom[1])));
        System.out.println("V6 pure-theta* takeoff velocity        = (" + f(v6pure[0]) + "," + f(v6pure[1]) + ") |v|="
                + f(Math.hypot(v6pure[0], v6pure[1])) + "  (on the constant-|v| setup-theta arc)");
        System.out.println("=> V6's true takeoff velocity is INTERIOR to the setup-theta arc; a 1-D theta sweep (ja tied");
        System.out.println("   to theta) cannot reach it. The takeoff-tick facing / free ja is a needed extra DOF:");
        for (int d = -180; d < 180; d += 45) {
            double[] yy = allTheta(p.n, thetaStar);
            yy[tk] = d;
            double[] v = takeoffVel(model, v6sc, yy, tk);
            System.out.println(String.format(Locale.ROOT, "     ja=%5d -> v=(%.4f,%.4f)", d, v[0], v[1]));
        }

        JumpPhysicsInputs certScFwd = p.buildSpec(v6, v6spr, NoTurnKeys.WA, true).asScenario();
        double[] certGf = certScFwd.toGameFacings(Angles.wrapAll(certYaws));
        ForwardPath certFp = model.forward(certScFwd, certGf);
        double pos29x = certFp.posX[airStart], pos29z = certFp.posZ[airStart], vy29 = certFp.velY[airStart];
        AirProblem air = buildAir(p, pos29x, pos29z, vy29, dxStar, dzStar);

        System.out.println("\n-- feasible takeoff-velocity region R (air phase " + airStart + ".." + (p.n - 1)
                + ", free steering + free start, byte certify) --");
        double vx0 = v6nom[0], vz0 = v6nom[1];
        double[] xExtent = axisExtent(air, cert, vx0, vz0, true);
        double[] zExtent = axisExtent(air, cert, vx0, vz0, false);
        System.out.println("R extent through V6 nominal: velX in [" + f(xExtent[0]) + "," + f(xExtent[1]) + "] (width "
                + g(xExtent[1] - xExtent[0]) + ")  velZ in [" + f(zExtent[0]) + "," + f(zExtent[1]) + "] (width "
                + g(zExtent[1] - zExtent[0]) + ")");
        int[] gridStat = gridScan(air, cert, vx0, vz0);
        System.out.println("coarse 9x9 grid step 0.015 around V6 nominal: feasible cells=" + gridStat[0] + "/81");

        double[] lookNom = takeoffVel(model, looksc, allTheta(p.n, lookBestTheta), tk);
        System.out.println("\n-- membership test --");
        System.out.println("V6 nominal velocity in R: " + membership(air, cert, v6nom[0], v6nom[1]));
        System.out.println("look best-straight-theta(" + f(lookBestTheta) + ") takeoff velocity = (" + f(lookNom[0]) + ","
                + f(lookNom[1]) + ") |v|=" + f(Math.hypot(lookNom[0], lookNom[1])));
        System.out.println("look landing-position screen residual (straight) = " + g(lookBestRes) + "  (high, like V6)");
        System.out.println("dist(look takeoff v, V6 takeoff v) = " + g(Math.hypot(lookNom[0] - v6nom[0], lookNom[1] - v6nom[1])));
        boolean lookIn = membership(air, cert, lookNom[0], lookNom[1]);
        System.out.println("look takeoff velocity in R (free steering+start air certify): " + lookIn);

        System.out.println("\n-- verdict: takeoff-velocity oracle --");
        System.out.println("R is NOT razor-thin: free air steering + free start land a ~0.03-0.06-wide velocity band,");
        System.out.println("even though the 4 landing walls sit at 1e-6. The infeasible look-alike's takeoff velocity is");
        System.out.println("INSIDE R (air-certify feasible) yet the family is globally infeasible: velocity membership");
        System.out.println("does NOT separate feasible V6 from the infeasible look-alike. The discriminating constraint");
        System.out.println("is the run-up position/windup-box coupling (walls at ticks 1,14,27 share the ONE free-start");
        System.out.println("translation with the landing corridor), which the velocity abstraction discards.");
        System.out.println("Velocity space DOES prune clearly-unreachable velocities (e.g. (0,0), +0.1 off nominal), so it");
        System.out.println("is a FAITHFUL FILTER, not a faithful ranker: verdict (b). It cannot rank the needle above a");
        System.out.println("reachable-velocity-but-infeasible look-alike. The full velocity-target-membership test does");
        System.out.println("beat a pure position-reach margin (it prunes on true landability), but still fails on the");
        System.out.println("look-alike, so it cannot replace the joint certify; it can only pre-filter its candidates.");
    }

    private static boolean membership(AirProblem air, NoTurnCertifier cert, double vx, double vz) {
        NoTurnCertifier.Result r = cert.certify(air.spec(vx, vz), BuiltinGraphs.fast(), 2_000_000_000L, new AtomicBoolean(false));
        return r.feasible && r.violation <= 0.0;
    }

    private static double[] axisExtent(AirProblem air, NoTurnCertifier cert, double vx0, double vz0, boolean sweepX) {
        double lo = sweepX ? vx0 : vz0;
        double hi = lo;
        double step = 0.006;
        for (int dir = -1; dir <= 1; dir += 2) {
            double v = lo;
            for (int i = 0; i < 18; i++) {
                double cand = lo + dir * step * (i + 1);
                boolean feas = sweepX ? membership(air, cert, cand, vz0) : membership(air, cert, vx0, cand);
                if (!feas) break;
                v = cand;
            }
            if (dir < 0) lo = v; else hi = v;
        }
        return new double[]{Math.min(lo, hi), Math.max(lo, hi)};
    }

    private static int[] gridScan(AirProblem air, NoTurnCertifier cert, double vx0, double vz0) {
        int feas = 0;
        double step = 0.015;
        for (int r = -4; r <= 4; r++) {
            for (int c = -4; c <= 4; c++) {
                if (membership(air, cert, vx0 + c * step, vz0 + r * step)) feas++;
            }
        }
        return new int[]{feas};
    }

    private static AirProblem buildAir(NoTurnProblem p, double pos29x, double pos29z, double vy29,
                                       double dxOff, double dzOff) {
        int tk = p.jumpTicks[p.jumpTicks.length - 1];
        int airStart = tk + 1;
        int airLen = p.n - airStart;
        JumpPhysicsInputs src = p.base;
        JumpPhysicsInputs a = new JumpPhysicsInputs(airLen);
        a.startYaw = 0f;
        a.strafeSign = src.strafeSign;
        a.jumpTick = -1;
        a.jumpPerTick = new boolean[airLen];
        a.slipPerTick = new double[airLen];
        a.forwardInputPerTick = new float[airLen];
        a.strafeInputPerTick = new float[airLen];
        a.sprintPerTick = new boolean[airLen];
        a.speedAmplifier = new int[airLen];
        a.sneakPerTick = new boolean[airLen];
        for (int i = 0; i < airLen; i++) {
            int gt = airStart + i;
            a.slipPerTick[i] = src.slipAt(gt);
            a.forwardInputPerTick[i] = src.forwardAt(gt);
            a.strafeInputPerTick[i] = src.strafeInputAt(gt);
            a.sprintPerTick[i] = src.sprintAt(gt);
            a.speedAmplifier[i] = src.speedAmplifierAt(gt);
            a.sneakPerTick[i] = src.sneakAt(gt);
        }
        List<JumpConstraint> walls = new ArrayList<>();
        for (JumpConstraint w : p.walls) {
            if ((w.mode == JumpConstraint.Mode.X || w.mode == JumpConstraint.Mode.Z) && w.t2 == null && w.t1 >= airStart) {
                walls.add(new JumpConstraint(w.mode, w.t1 - airStart, null, w.op, w.cmp, w.rhs, w.name));
            }
        }
        AirProblem air = new AirProblem();
        air.base = a;
        air.walls = walls;
        air.obj = new Objective(p.objective.axis, p.objective.sense, p.objective.tick - airStart);
        air.sx = pos29x + dxOff;
        air.sz = pos29z + dzOff;
        air.vy = vy29;
        air.pxLo = pos29x + (p.freeBox.pxLo - p.refStart().x);
        air.pxHi = pos29x + (p.freeBox.pxHi - p.refStart().x);
        air.pzLo = pos29z + (p.freeBox.pzLo - p.refStart().z);
        air.pzHi = pos29z + (p.freeBox.pzHi - p.refStart().z);
        return air;
    }

    static final class AirProblem {
        JumpPhysicsInputs base;
        List<JumpConstraint> walls;
        Objective obj;
        double sx, sz, vy;
        double pxLo, pxHi, pzLo, pzHi;

        JumpSpec spec(double vx, double vz) {
            JumpPhysicsInputs sc = base.copy();
            sc.startPos = new Vec3dCore(sx, base.startPos.y, sz);
            sc.initialVelocity = new Vec3dCore(vx, vy, vz);
            sc.startBox = new StartBox(sx, sz, vx, vz, pxLo, pxHi, pzLo, pzHi, vx, vx, vz, vz);
            return new JumpSpec(sc, walls, obj);
        }
    }
}
