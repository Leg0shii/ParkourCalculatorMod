package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdProblem;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.LineSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PressWindows {

    private PressWindows() {
    }

    public static double[] startRegion(ProblemCompiler.Compiled spec, KeyLine line, double facingDeg) {
        ColdProblem p = ColdProblem.fromSave(spec.save);
        ExactJumpModel model = ExactJumpModel.forMcVersion(spec.save.mcVersion);
        int n = p.landingTick;
        double[] yaws = new double[n];
        Arrays.fill(yaws, facingDeg);
        double cx = 0.5 * (p.rectXLo + p.rectXHi);
        double cz = 0.5 * (p.rectZLo + p.rectZHi);
        JumpPhysicsInputs sc = LineSpec.build(line, facingDeg, cx, cz).asScenario();
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = model.forward(sc, gf);
        double bx = path.posX[0];
        double bz = path.posZ[0];
        double xLo = p.rectXLo;
        double xHi = p.rectXHi;
        double zLo = p.rectZLo;
        double zHi = p.rectZHi;
        List<ColdProblem.Wall> walls = new ArrayList<ColdProblem.Wall>(p.momentumWalls);
        walls.addAll(p.tailWalls);
        for (ColdProblem.Wall w : walls) {
            int t = w.segTick;
            if (t < 0 || t >= path.posX.length) {
                continue;
            }
            if (w.axisX) {
                double d = path.posX[t] - bx;
                xLo = Math.max(xLo, w.lo - d);
                xHi = Math.min(xHi, w.hi - d);
            } else {
                double d = path.posZ[t] - bz;
                zLo = Math.max(zLo, w.lo - d);
                zHi = Math.min(zHi, w.hi - d);
            }
        }
        if (xLo > xHi || zLo > zHi) {
            return null;
        }
        return new double[]{xLo, xHi, zLo, zHi};
    }

    public static double[] intersect(double[] a, double[] b) {
        if (a == null || b == null) {
            return null;
        }
        double xLo = Math.max(a[0], b[0]);
        double xHi = Math.min(a[1], b[1]);
        double zLo = Math.max(a[2], b[2]);
        double zHi = Math.min(a[3], b[3]);
        if (xLo > xHi || zLo > zHi) {
            return null;
        }
        return new double[]{xLo, xHi, zLo, zHi};
    }

    public static double area(double[] r) {
        return r == null ? -1.0 : (r[1] - r[0]) * (r[3] - r[2]);
    }
}
