package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Stage P5 probe: dF (facing) constraints threaded through the P3/P5 {@link DiskSocpKernel}. A dF=0 chain
 *  ("do not change facing") pins each tick's phase to the previous, collapsing a whole chain to ONE shared
 *  direction DOF: exactly the fold {@link FacingPrefold} already performs, whose {@link FacingPrefold.Reduced}
 *  output (n, cx, cz, mMag, walls, each wall's {@code p0coef} preserved) IS the {@link DiskSocpKernel} input
 *  shape. So dF needs NO kernel change: fold with FacingPrefold, run the disk kernel on the reduced model, and
 *  it composes with the P5 free-start term for free (df-chain-free-start carries both). A chain the merge
 *  cannot take (spanning jump ticks with different base phases) becomes an open group scanned over its anchor
 *  yaw ({@link FacingPrefold#scannable}); each pinned anchor reduces and solves the same way.
 *
 *  <p>No COPT oracle exists for dF (COPT drops facing walls). The reference is the shipped convex mechanism:
 *  the folded model is an ordinary constant-modulus problem the shipped {@link CostateDualSolver} solves, so
 *  the disk kernel must be AT LEAST as tight as it (the IPM converges where the shipped dual stalls), and the
 *  recovered solution is certified through {@link ExactJumpModel}. */
@Category(SlowSolverTests.class)
public class DfChainKernelProbe {

    private static final double SMOOTH = 5.0e-4;
    private static final double TIGHT_TOL = 1.0e-5;
    private static final double POSITION_ONLY_FREE_BOUND = -3.870467453;

    @Test
    public void dfChainFreeStartFoldsAndComposesWithFreeStart() {
        Case c = fold("df-chain-free-start", SMOOTH);
        report(c);
        assertTrue("dF walls must be present and foldable (analyze != null, not identity)", c.foldable);
        assertTrue("fold must reduce the tick count (n=" + c.n + " -> vars=" + c.vars + ")", c.vars < c.n);
        assertTrue("the free-start box must be derived (dF + free-start compose)", c.startFree);
        assertNotNull("disk kernel on the folded free-start model returned null", c.disk);
        assertTrue("folded free-start kernel must converge, gap=" + c.gap, c.converged);
        assertTrue("folded free-start disk primal must be feasible, max|u|-m=" + c.diskViol, c.diskViol <= 1.0e-9);
        assertTrue("disk kernel must be at least as tight as the shipped dual (diskValue=" + c.diskValue
                + " dualValue=" + c.dualValue + ", stalled=" + c.dualStalled + ")",
                c.diskValue <= c.dualValue + TIGHT_TOL);
        assertTrue("dF-live bound must not exceed the position-only relaxation " + POSITION_ONLY_FREE_BOUND
                + " (MAX-X; dropping dF only relaxes), was " + c.bound, c.bound <= POSITION_ONLY_FREE_BOUND + 1.0e-4);
        assertTrue("recovered free start must be inside the box (dv " + c.dvx + "," + c.dvz + ")",
                inBox(c));
        assertTrue("byte-exact recovered solution must be near feasible (viol=" + c.byteViol + ")",
                c.byteViol < 1.0e-3);
    }

    @Test
    public void f2fDfChainIsAScanCaseThreadedPerAnchorThroughTheKernel() {
        ScanCase c = scan("f2f-dfchain-multijump", 1.0);
        System.out.printf("DF-SCAN %-20s n=%d convergedAnchors=%d bestObj=%.9f bestViol=%.3e "
                + "(coupled multi-jump: disk relaxation is loose, byte-exact closing is the P1 residual's job)%n",
                c.name, c.n, c.tried, c.bestObj, c.bestViol);
        assertNull("f2f is a scan case: the pure fold must decline (analyze == null)", c.foldResult);
        assertNotNull("f2f must be a scannable open chain", c.scan);
        assertTrue("the disk kernel must thread the pinned-chain reduced model per scan anchor, localizing a "
                + "feasible-relaxation anchor arc (converged anchors=" + c.tried + ")", c.tried >= 20);
        assertTrue("the scan must recover a candidate at the best anchor", c.bestFound);
    }

    @Test
    public void determinismHolds() {
        Case a = fold("df-chain-free-start", SMOOTH);
        Case b = fold("df-chain-free-start", SMOOTH);
        assertTrue("folded bound must be deterministic", a.bound == b.bound && a.diskValue == b.diskValue);
    }

    private static final class Ctx {
        SaveFile file;
        ExactJumpModel model;
        JumpSpec spec;
        JumpPhysicsInputs sc;
        JumpLinearModel lin;
        double[] cx;
        double[] cz;
        List<JumpLinearModel.Wall> walls;
        StartBox box;
        int axis;
        boolean max;
        double constPos;
        CostateDualSolver.FreeP0 freeP0;
    }

    private Ctx load(String cap, double smooth) {
        String raw = Fixtures.rawPool(cap);
        SaveFile file = SaveIO.parseSafe(raw);
        assertNotNull(cap + ": parse", file);
        Ctx x = new Ctx();
        x.file = file;
        x.model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, x.model);
        x.spec = engine.debugBuildSpec();
        assertNotNull(cap + ": spec", x.spec);
        x.sc = x.spec.asScenario();
        x.box = x.sc.startBox;
        x.lin = new JumpLinearModel(x.sc);
        x.cx = new double[x.lin.n];
        x.cz = new double[x.lin.n];
        x.lin.objectiveVectors(x.spec.objective, x.cx, x.cz);
        boolean[] trivial = {false};
        x.walls = x.lin.compileWalls(x.spec.constraints, 0.0, trivial);
        x.axis = x.spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        x.max = x.spec.objective.sense == Objective.Sense.MAX;
        x.constPos = x.lin.constPos(x.spec.objective.tick, x.axis);
        boolean free = x.box != null && x.box.startFree();
        x.freeP0 = free ? buildFreeP0(x.box, x.spec.objective, smooth) : null;
        return x;
    }

    private static final class Case {
        String name;
        int n;
        int vars;
        boolean foldable;
        boolean startFree;
        DiskSocpKernel.Result disk;
        boolean converged;
        double bound;
        double diskValue;
        double gap;
        double diskViol;
        double dvx;
        double dvz;
        double dualValue;
        boolean dualStalled;
        double byteObj;
        double byteViol;
        StartBox box;
    }

    private Case fold(String cap, double smooth) {
        Ctx x = load(cap, smooth);
        Case c = new Case();
        c.name = cap;
        c.n = x.lin.n;
        c.box = x.box;
        c.startFree = x.box != null && x.box.startFree();
        FacingPrefold pre = FacingPrefold.analyze(x.spec.constraints, x.lin);
        c.foldable = pre != null && !pre.isIdentity();
        if (pre == null) return c;

        FacingPrefold.Reduced red = pre.reduce(x.cx, x.cz, x.lin.mMagAll(), x.walls);
        c.vars = red.n;
        DiskSocpKernel.Result disk = DiskSocpKernel.solve(red.n, red.cx, red.cz, red.mMag, red.walls, x.freeP0);
        c.disk = disk;
        if (disk != null) {
            c.converged = disk.converged;
            c.diskValue = disk.value;
            c.bound = x.max ? x.constPos + disk.value : x.constPos - disk.value;
            c.gap = disk.gap;
            c.dvx = disk.dvx;
            c.dvz = disk.dvz;
            double mv = 0.0;
            for (int t = 0; t < red.n; t++) mv = Math.max(mv, Math.hypot(disk.ux[t], disk.uz[t]) - red.mMag[t]);
            c.diskViol = mv;
        }
        CostateDualSolver dual = new CostateDualSolver(red.n, red.cx, red.cz, red.mMag, red.walls, x.freeP0);
        CostateDualSolver.Result dr = dual.solve(0.0, null);
        c.dualValue = dr == null ? Double.POSITIVE_INFINITY : dr.value;
        c.dualStalled = dual.lastStalled;
        if (disk != null) {
            double[] yaws = pre.expand(x.lin, x.spec.objective, disk.gx, disk.gz);
            double p0x = x.box == null ? x.sc.startPos.x : clamp(x.box.px + c.dvx, x.box.pxLo, x.box.pxHi);
            double p0z = x.box == null ? x.sc.startPos.z : clamp(x.box.pz + c.dvz, x.box.pzLo, x.box.pzHi);
            double[] viol = {Double.NaN};
            c.byteObj = byteObjective(x, yaws, p0x, p0z, viol);
            c.byteViol = viol[0];
        }
        return c;
    }

    private static final class ScanCase {
        String name;
        int n;
        FacingPrefold foldResult;
        FacingPrefold.ChainScan scan;
        int tried;
        boolean bestFound;
        double bestObj;
        double bestViol = Double.POSITIVE_INFINITY;
    }

    private ScanCase scan(String cap, double stepDeg) {
        Ctx x = load(cap, SMOOTH);
        ScanCase c = new ScanCase();
        c.name = cap;
        c.n = x.lin.n;
        c.foldResult = FacingPrefold.analyze(x.spec.constraints, x.lin);
        c.scan = FacingPrefold.scannable(x.spec.constraints, x.lin);
        if (c.scan == null) return c;
        double bestObj = x.max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (double th = -180.0; th < 180.0; th += stepDeg) {
            FacingPrefold pinned = c.scan.at(th);
            FacingPrefold.Reduced red = pinned.reduce(x.cx, x.cz, x.lin.mMagAll(), x.walls);
            DiskSocpKernel.Result disk = DiskSocpKernel.solve(red.n, red.cx, red.cz, red.mMag, red.walls, x.freeP0);
            if (disk == null || !disk.converged) continue;
            c.tried++;
            double[] yaws = pinned.expand(x.lin, x.spec.objective, disk.gx, disk.gz);
            double[] viol = {Double.NaN};
            double obj = byteObjective(x, yaws, x.sc.startPos.x, x.sc.startPos.z, viol);
            if (viol[0] < c.bestViol - 1.0e-9
                    || (Math.abs(viol[0] - c.bestViol) <= 1.0e-9 && (x.max ? obj > bestObj : obj < bestObj))) {
                c.bestViol = viol[0];
                c.bestObj = obj;
                bestObj = obj;
                c.bestFound = true;
            }
        }
        return c;
    }

    private static double byteObjective(Ctx x, double[] yaws, double p0x, double p0z, double[] violOut) {
        JumpPhysicsInputs at = x.sc.copy();
        at.startPos = new Vec3dCore(p0x, x.sc.startPos.y, p0z);
        at.startBox = StartBox.pinned(p0x, p0z, x.sc.initialVelocity.x, x.sc.initialVelocity.z);
        double[] gf = at.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = x.model.forward(at, gf);
        List<JumpConstraint> pos = new ArrayList<>();
        for (JumpConstraint jc : x.spec.constraints) {
            if (jc.mode == JumpConstraint.Mode.X || jc.mode == JumpConstraint.Mode.Z) pos.add(jc);
        }
        JumpSpec posSpec = new JumpSpec(at, pos, x.spec.objective);
        violOut[0] = JumpConstraintCompiler.compile(posSpec).maxViolation(gf, path);
        return path.getPos(x.spec.objective.tick, x.spec.objective.axis);
    }

    private static boolean inBox(Case c) {
        if (c.box == null) return true;
        double p0x = c.box.px + c.dvx;
        double p0z = c.box.pz + c.dvz;
        return p0x >= c.box.pxLo - 1.0e-9 && p0x <= c.box.pxHi + 1.0e-9
                && p0z >= c.box.pzLo - 1.0e-9 && p0z <= c.box.pzHi + 1.0e-9;
    }

    private static CostateDualSolver.FreeP0 buildFreeP0(StartBox box, Objective obj, double smooth) {
        boolean max = obj.sense == Objective.Sense.MAX;
        double objDevX = obj.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
        double objDevZ = obj.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
        return new CostateDualSolver.FreeP0(box.pxLo - box.px, box.pxHi - box.px,
                box.pzLo - box.pz, box.pzHi - box.pz, objDevX, objDevZ, smooth);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static void report(Case c) {
        System.out.printf(
                "DF   %-22s n=%d vars=%d foldable=%b startFree=%b conv=%b%n"
                + "    bound=%.9f diskValue=%.9f dualValue=%.9f dualStalled=%b diskViol=%.3e gap=%.3e%n"
                + "    dv=(%.6f,%.6f) byteObj=%.9f byteViol=%.3e%n",
                c.name, c.n, c.vars, c.foldable, c.startFree, c.converged,
                c.bound, c.diskValue, c.dualValue, c.dualStalled, c.diskViol, c.gap,
                c.dvx, c.dvz, c.byteObj, c.byteViol);
    }
}
