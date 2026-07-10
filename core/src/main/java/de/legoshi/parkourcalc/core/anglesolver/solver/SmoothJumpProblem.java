package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SmoothJumpProblem {

    private static final double DEG_PER_RAD = 180.0 / Math.PI;
    private static final float RAD_PER_DEG_F = (float) (Math.PI / 180.0);
    private static final float PI_F = (float) Math.PI;

    public static final class Term {
        public final double constant;
        public final double[] sinC;
        public final double[] cosC;
        public final double[] boostSinC;
        public final double[] boostCosC;
        public final double[] thetaC;
        public final String name;
        public final boolean isEq;
        public final int axis;
        public final double transCoef;
        public final JumpConstraint source;

        Term(double constant, double[] sinC, double[] cosC, double[] boostSinC, double[] boostCosC,
             double[] thetaC, String name, boolean isEq, int axis, double transCoef, JumpConstraint source) {
            this.constant = constant;
            this.sinC = sinC;
            this.cosC = cosC;
            this.boostSinC = boostSinC;
            this.boostCosC = boostCosC;
            this.thetaC = thetaC;
            this.name = name;
            this.isEq = isEq;
            this.axis = axis;
            this.transCoef = transCoef;
            this.source = source;
        }
    }

    private final int n;
    private final boolean modern;
    private final double objectiveSign;
    private final Term objective;
    private final List<Term> ineq;
    private final List<Term> eq;

    private SmoothJumpProblem(int n, boolean modern, double objectiveSign, Term objective,
                              List<Term> ineq, List<Term> eq) {
        this.n = n;
        this.modern = modern;
        this.objectiveSign = objectiveSign;
        this.objective = objective;
        this.ineq = Collections.unmodifiableList(ineq);
        this.eq = Collections.unmodifiableList(eq);
    }

    public static SmoothJumpProblem compile(JumpSpec spec, boolean[] zeroX, boolean[] zeroZ, boolean modern) {
        return compile(spec, zeroX, zeroZ, modern, 0.0);
    }

    public static SmoothJumpProblem compile(JumpSpec spec, boolean[] zeroX, boolean[] zeroZ,
                                            boolean modern, double velBound) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        JumpLinearModel jlm = new JumpLinearModel(sc, zeroX, zeroZ);

        Objective obj = spec.objective;
        int objAxis = obj.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        double[] objCoef = new double[n];
        for (int s = 0; s < n; s++) objCoef[s] = jlm.coefAxis(objAxis, s, obj.tick);
        double objConst = jlm.constPos(obj.tick, objAxis);
        Term objective = mapAxisTerm(objAxis, objCoef, objConst, jlm, n, "objective", false, 1.0, null);
        double objSign = obj.sense == Objective.Sense.MAX ? -1.0 : 1.0;

        List<Term> ineq = new ArrayList<>();
        List<Term> eq = new ArrayList<>();
        boolean[] trivialInfeasible = new boolean[1];
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.F) {
                Term t = facingTerm(c, n);
                if (c.cmp == JumpConstraint.Cmp.EQ) eq.add(t);
                else ineq.add(t);
                continue;
            }
            JumpLinearModel.Wall w = jlm.compileWall(c, 0.0, trivialInfeasible);
            if (w == null) continue;
            Term t = wallTerm(w, jlm, n, c);
            if (w.eq) eq.add(t);
            else ineq.add(t);
        }
        if (velBound > 0.0) {
            for (JumpLinearModel.Wall w : jlm.velocityWalls(velBound)) {
                ineq.add(wallTerm(w, jlm, n, null));
            }
        }
        return new SmoothJumpProblem(n, modern, objSign, objective, ineq, eq);
    }

    private static Term wallTerm(JumpLinearModel.Wall w, JumpLinearModel jlm, int n, JumpConstraint source) {
        double transCoef = w.p0coef == 0.0 ? 0.0 : -w.p0coef;
        return mapAxisTerm(w.axis, w.coef, -w.bPrime, jlm, n, w.name, w.eq, transCoef, source);
    }

    public SmoothJumpProblem withTranslationBox(double loX, double hiX, double loZ, double hiZ) {
        List<Term> ext = new ArrayList<>(ineq);
        ext.add(boxTerm(n, 0, 1.0, -hiX, "transbox:x+"));
        ext.add(boxTerm(n, 0, -1.0, loX, "transbox:x-"));
        ext.add(boxTerm(n, 1, 1.0, -hiZ, "transbox:z+"));
        ext.add(boxTerm(n, 1, -1.0, loZ, "transbox:z-"));
        return new SmoothJumpProblem(n, modern, objectiveSign, objective, ext, new ArrayList<>(eq));
    }

    private static Term boxTerm(int n, int axis, double transCoef, double constant, String name) {
        return new Term(constant, new double[n], new double[n], new double[n], new double[n], new double[n],
                name, false, axis, transCoef, null);
    }

    private static Term mapAxisTerm(int axis, double[] coef, double constant, JumpLinearModel jlm, int n,
                                    String name, boolean isEq, double transCoef, JumpConstraint source) {
        double[] sinC = new double[n];
        double[] cosC = new double[n];
        double[] boostSinC = new double[n];
        double[] boostCosC = new double[n];
        double[] thetaC = new double[n];
        for (int s = 0; s < n; s++) {
            double c = coef[s];
            if (c == 0.0) continue;
            if (axis == 0) {
                cosC[s] += c * jlm.strafeMag(s);
                sinC[s] -= c * jlm.forwardMag(s);
                boostSinC[s] -= c * jlm.boostAt(s);
            } else {
                cosC[s] += c * jlm.forwardMag(s);
                sinC[s] += c * jlm.strafeMag(s);
                boostCosC[s] += c * jlm.boostAt(s);
            }
        }
        return new Term(constant, sinC, cosC, boostSinC, boostCosC, thetaC, name, isEq, axis, transCoef, source);
    }

    private static Term facingTerm(JumpConstraint c, int n) {
        double[] thetaC = new double[n];
        double opSign = c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
        double constant;
        boolean isEq = c.cmp == JumpConstraint.Cmp.EQ;
        double sign = c.cmp == JumpConstraint.Cmp.GE ? -1.0 : 1.0;
        if (c.t1 >= 0 && c.t1 < n) thetaC[c.t1] += sign;
        if (c.t2 != null && c.t2 >= 0 && c.t2 < n) thetaC[c.t2] += sign * opSign;
        constant = sign * (-c.rhs);
        return new Term(constant, new double[n], new double[n], new double[n], new double[n], thetaC,
                c.name, isEq, -1, 0.0, c);
    }

    public int n() {
        return n;
    }

    public Term objective() {
        return objective;
    }

    public double objectiveSign() {
        return objectiveSign;
    }

    public List<Term> ineq() {
        return ineq;
    }

    public List<Term> eq() {
        return eq;
    }

    public double smoothValue(Term t, double[] thetaRad) {
        double v = t.constant;
        for (int k = 0; k < n; k++) {
            double th = thetaRad[k];
            double s = Math.sin(th);
            double c = Math.cos(th);
            v += (t.sinC[k] + t.boostSinC[k]) * s + (t.cosC[k] + t.boostCosC[k]) * c;
            if (t.thetaC[k] != 0.0) v += t.thetaC[k] * th * DEG_PER_RAD;
        }
        return v;
    }

    public double shiftContribution(Term t, double tx, double tz) {
        if (t.transCoef == 0.0) return 0.0;
        if (t.axis == 0) return t.transCoef * tx;
        if (t.axis == 1) return t.transCoef * tz;
        return 0.0;
    }

    public double smoothValue(Term t, double[] thetaRad, double tx, double tz) {
        return smoothValue(t, thetaRad) + shiftContribution(t, tx, tz);
    }

    public double augLagrangianT(double[] thetaRad, double tx, double tz, double[] lambda, double[] nu,
                                 double pen, double[] gOut) {
        double[] sinA = new double[n];
        double[] cosA = new double[n];
        for (int k = 0; k < n; k++) {
            sinA[k] = Math.sin(thetaRad[k]);
            cosA[k] = Math.cos(thetaRad[k]);
        }
        Arrays.fill(gOut, 0.0);
        double objVal = termValue(objective, thetaRad, sinA, cosA) + shiftContribution(objective, tx, tz);
        double value = objectiveSign * objVal;
        accumGrad(objective, thetaRad, sinA, cosA, objectiveSign, gOut);
        addShiftGrad(objective, objectiveSign, gOut);
        for (int i = 0; i < ineq.size(); i++) {
            Term term = ineq.get(i);
            double gi = termValue(term, thetaRad, sinA, cosA) + shiftContribution(term, tx, tz);
            double t = Math.max(0.0, lambda[i] + pen * gi);
            value += 0.5 / pen * (t * t - lambda[i] * lambda[i]);
            if (t != 0.0) {
                accumGrad(term, thetaRad, sinA, cosA, t, gOut);
                addShiftGrad(term, t, gOut);
            }
        }
        for (int j = 0; j < eq.size(); j++) {
            Term term = eq.get(j);
            double hj = termValue(term, thetaRad, sinA, cosA) + shiftContribution(term, tx, tz);
            value += nu[j] * hj + 0.5 * pen * hj * hj;
            double scale = nu[j] + pen * hj;
            accumGrad(term, thetaRad, sinA, cosA, scale, gOut);
            addShiftGrad(term, scale, gOut);
        }
        return value;
    }

    public void smoothGradientT(double[] thetaRad, double tx, double tz, double[] lambda, double[] nu,
                                double pen, double[] gOut) {
        augLagrangianT(thetaRad, tx, tz, lambda, nu, pen, gOut);
    }

    private void addShiftGrad(Term t, double scale, double[] gOut) {
        if (t.transCoef == 0.0) return;
        if (t.axis == 0) gOut[n] += scale * t.transCoef;
        else if (t.axis == 1) gOut[n + 1] += scale * t.transCoef;
    }

    public void smoothGradient(double[] thetaRad, double[] lambda, double[] nu, double pen, double[] gOut) {
        augEval(thetaRad, lambda, nu, pen, gOut);
    }

    public double augLagrangian(double[] thetaRad, double[] lambda, double[] nu, double pen, double[] gOut) {
        return augEval(thetaRad, lambda, nu, pen, gOut);
    }

    private double augEval(double[] thetaRad, double[] lambda, double[] nu, double pen, double[] gOut) {
        double[] sinA = new double[n];
        double[] cosA = new double[n];
        for (int k = 0; k < n; k++) {
            sinA[k] = Math.sin(thetaRad[k]);
            cosA[k] = Math.cos(thetaRad[k]);
        }
        Arrays.fill(gOut, 0.0);
        double value = objectiveSign * termValue(objective, thetaRad, sinA, cosA);
        accumGrad(objective, thetaRad, sinA, cosA, objectiveSign, gOut);
        for (int i = 0; i < ineq.size(); i++) {
            Term term = ineq.get(i);
            double gi = termValue(term, thetaRad, sinA, cosA);
            double t = Math.max(0.0, lambda[i] + pen * gi);
            value += 0.5 / pen * (t * t - lambda[i] * lambda[i]);
            if (t != 0.0) accumGrad(term, thetaRad, sinA, cosA, t, gOut);
        }
        for (int j = 0; j < eq.size(); j++) {
            Term term = eq.get(j);
            double hj = termValue(term, thetaRad, sinA, cosA);
            value += nu[j] * hj + 0.5 * pen * hj * hj;
            accumGrad(term, thetaRad, sinA, cosA, nu[j] + pen * hj, gOut);
        }
        return value;
    }

    private double termValue(Term t, double[] thetaRad, double[] sinA, double[] cosA) {
        double v = t.constant;
        for (int k = 0; k < n; k++) {
            v += (t.sinC[k] + t.boostSinC[k]) * sinA[k] + (t.cosC[k] + t.boostCosC[k]) * cosA[k];
            if (t.thetaC[k] != 0.0) v += t.thetaC[k] * thetaRad[k] * DEG_PER_RAD;
        }
        return v;
    }

    private void accumGrad(Term t, double[] thetaRad, double[] sinA, double[] cosA, double scale, double[] gOut) {
        for (int k = 0; k < n; k++) {
            double d = (t.sinC[k] + t.boostSinC[k]) * cosA[k] - (t.cosC[k] + t.boostCosC[k]) * sinA[k];
            if (t.thetaC[k] != 0.0) d += t.thetaC[k] * DEG_PER_RAD;
            if (d != 0.0) gOut[k] += scale * d;
        }
    }

    public double fastValue(Term t, float[] gameFacingDeg) {
        double v = t.constant;
        for (int k = 0; k < n; k++) {
            float g = gameFacingDeg[k];
            float moveRad = modern ? g * RAD_PER_DEG_F : g * PI_F / 180.0F;
            float sinM = McSineTable.sinStep(moveRad);
            float cosM = McSineTable.cosStep(moveRad);
            v += t.sinC[k] * (double) sinM + t.cosC[k] * (double) cosM;
            if (t.boostSinC[k] != 0.0 || t.boostCosC[k] != 0.0) {
                float boostRad = g * RAD_PER_DEG_F;
                float sinB = McSineTable.sinStep(boostRad);
                float cosB = McSineTable.cosStep(boostRad);
                v += t.boostSinC[k] * (double) sinB + t.boostCosC[k] * (double) cosB;
            }
            if (t.thetaC[k] != 0.0) v += t.thetaC[k] * (double) g;
        }
        return v;
    }

    public double fastValue(Term t, float[] gameFacingDeg, double tx, double tz) {
        return fastValue(t, gameFacingDeg) + shiftContribution(t, tx, tz);
    }
}
