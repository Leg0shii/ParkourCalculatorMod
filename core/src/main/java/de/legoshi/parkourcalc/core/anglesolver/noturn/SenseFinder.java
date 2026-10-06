package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SenseFinder {

    public enum Mode {
        HITS("Hits", "Most hittable mouse pixels on the first angles first"),
        FURTHEST("Furthest", "Largest landing offset first");

        public final String label;
        public final String hint;

        Mode(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    public interface Progress {
        void update(String stage, double fraction);
    }

    public static final class Config {
        public float minSens = 0.5f;
        public float maxSens = 1.0f;
        public int coarseSteps = 8000;
        public int refineSteps = 64;
        public int ascentSweeps = 2;
    }

    public static final class Turn {
        public final ExactJumpModel model;
        public final JumpPhysicsInputs scenario;
        public final JumpConstraintCompiler.Compiled compiled;
        public final Objective objective;
        public final JumpConstraint goalWall;
        public final double[] yaws;
        public final int[] turnTicks;
        public final double[] deltas;

        Turn(ExactJumpModel model, JumpPhysicsInputs scenario, JumpConstraintCompiler.Compiled compiled,
             Objective objective, JumpConstraint goalWall, double[] yaws, int[] turnTicks, double[] deltas) {
            this.model = model;
            this.scenario = scenario;
            this.compiled = compiled;
            this.objective = objective;
            this.goalWall = goalWall;
            this.yaws = yaws;
            this.turnTicks = turnTicks;
            this.deltas = deltas;
        }

        public int angles() {
            return turnTicks.length;
        }

        public double referenceOffset() {
            return offsetOf(this, yaws);
        }
    }

    public static final class Candidate {
        public final float sens;
        public final float sensLo;
        public final float sensHi;
        public final double pixelDeg;
        public final int[] pixels;
        public final int[] hits;
        public final double offset;
        public final double[] yaws;

        Candidate(float sens, float sensLo, float sensHi, double pixelDeg, int[] pixels, int[] hits, double offset,
                  double[] yaws) {
            this.sens = sens;
            this.sensLo = sensLo;
            this.sensHi = sensHi;
            this.pixelDeg = pixelDeg;
            this.pixels = pixels;
            this.hits = hits;
            this.offset = offset;
            this.yaws = yaws;
        }

        public double percent() {
            return SenseFinder.percent(sens);
        }
    }

    public static final double TURN_EPS_DEG = 1.0e-6;
    public static final int HITS_MAX = 50;
    private static final double OFFSET_TIE = 1.0e-6;
    private static final double MISS_PENALTY = 1.0e6;

    private SenseFinder() {
    }

    public static double percent(float sens) {
        return sens * 200.0;
    }

    public static float sensForPixel(double pixelDeg) {
        double f = StrictMath.cbrt(pixelDeg / 1.2);
        return (float) ((f - 0.2) / 0.6);
    }

    public static Turn turnOf(ExactJumpModel model, JumpSpec spec, double[] yawsAbs, double px, double pz) {
        JumpPhysicsInputs sc = Scoring.pinnedScenario(spec.asScenario(), px, pz);
        List<JumpConstraint> kept = new ArrayList<>();
        for (JumpConstraint c : spec.constraints) if (c.mode != JumpConstraint.Mode.F) kept.add(c);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(new JumpSpec(sc, kept, spec.objective));
        JumpConstraint wall = NoTurnRanking.goalWall(spec.constraints, spec.objective);
        double[] yaws = Angles.wrapAll(yawsAbs);
        List<Integer> ticks = new ArrayList<>();
        List<Double> deltas = new ArrayList<>();
        for (int t = 1; t < yaws.length; t++) {
            double d = Angles.wrapDelta(yaws[t] - yaws[t - 1]);
            if (Math.abs(d) <= TURN_EPS_DEG) continue;
            ticks.add(t);
            deltas.add(d);
        }
        int[] tt = new int[ticks.size()];
        double[] dd = new double[ticks.size()];
        for (int i = 0; i < tt.length; i++) {
            tt[i] = ticks.get(i);
            dd[i] = deltas.get(i);
        }
        return new Turn(model, sc, compiled, spec.objective, wall, yaws, tt, dd);
    }

    public static double[] quantizedYaws(Turn turn, int[] pixels, double pixelDeg) {
        int n = turn.yaws.length;
        double[] out = new double[n];
        double cur = turn.yaws[0];
        int k = 0;
        for (int t = 0; t < n; t++) {
            if (t > 0 && k < turn.turnTicks.length && turn.turnTicks[k] == t) {
                cur = Angles.wrap(cur + pixels[k] * pixelDeg);
                k++;
            }
            out[t] = cur;
        }
        return out;
    }

    public static double offsetOf(Turn turn, double[] yawsAbs) {
        double[] gf = turn.scenario.toGameFacings(Angles.wrapAll(yawsAbs));
        ForwardPath fp = turn.model.forward(turn.scenario, gf);
        double viol = turn.compiled.maxViolation(gf, fp);
        if (viol > 0.0) return Double.NaN;
        return NoTurnRanking.offset(turn.goalWall, turn.objective, fp.getPos(turn.objective.tick, turn.objective.axis));
    }

    private static double score(Turn turn, int[] pixels, double pixelDeg) {
        double[] gf = turn.scenario.toGameFacings(quantizedYaws(turn, pixels, pixelDeg));
        ForwardPath fp = turn.model.forward(turn.scenario, gf);
        double viol = turn.compiled.maxViolation(gf, fp);
        if (viol > 0.0) return -MISS_PENALTY - viol;
        return NoTurnRanking.offset(turn.goalWall, turn.objective, fp.getPos(turn.objective.tick, turn.objective.axis));
    }

    private static boolean lands(double score) {
        return score > -MISS_PENALTY;
    }

    private static int[] nearest(Turn turn, double pixelDeg) {
        int[] px = new int[turn.angles()];
        for (int k = 0; k < px.length; k++) px[k] = (int) Math.round(turn.deltas[k] / pixelDeg);
        return px;
    }

    private static double ascend(Turn turn, int[] pixels, double pixelDeg, double score, int sweeps) {
        for (int sweep = 0; sweep < sweeps; sweep++) {
            boolean moved = false;
            for (int k = 0; k < pixels.length; k++) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    pixels[k] += dir;
                    double s = score(turn, pixels, pixelDeg);
                    if (s > score) {
                        score = s;
                        moved = true;
                    } else {
                        pixels[k] -= dir;
                    }
                }
            }
            if (!moved) break;
        }
        return score;
    }

    private static final class Bracket {
        final int[] pixels;
        float lo;
        float hi;

        Bracket(int[] pixels, float s) {
            this.pixels = pixels;
            this.lo = s;
            this.hi = s;
        }
    }

    public static List<Candidate> run(Turn turn, Config cfg, AtomicBoolean cancel, Progress progress) {
        List<Candidate> out = new ArrayList<>();
        if (turn.angles() == 0) return out;
        int steps = Math.max(1, cfg.coarseSteps);
        float lo = Math.max(0f, Math.min(cfg.minSens, cfg.maxSens));
        float hi = Math.min(1f, Math.max(cfg.minSens, cfg.maxSens));
        double step = (hi - lo) / (double) steps;
        Map<String, Bracket> brackets = new LinkedHashMap<>();
        int[] warm = null;
        for (int i = 0; i <= steps; i++) {
            if (cancel != null && cancel.get()) return out;
            if (progress != null && (i & 255) == 0) {
                progress.update("sweeping sense " + String.format(java.util.Locale.ROOT, "%.2f%%", percent((float) (lo + i * step))),
                        0.8 * i / steps);
            }
            float s = (float) (lo + i * step);
            double p = TurnProfile.pixelDeg(s);
            int[] near = nearest(turn, p);
            double best = score(turn, near, p);
            int[] pixels = near;
            if (warm != null && !Arrays.equals(warm, near)) {
                double ws = score(turn, warm, p);
                if (ws > best) {
                    best = ws;
                    pixels = warm.clone();
                }
            }
            best = ascend(turn, pixels, p, best, cfg.ascentSweeps);
            warm = pixels;
            if (!lands(best)) continue;
            String key = Arrays.toString(pixels);
            Bracket b = brackets.get(key);
            if (b == null) brackets.put(key, new Bracket(pixels.clone(), s));
            else {
                b.lo = Math.min(b.lo, s);
                b.hi = Math.max(b.hi, s);
            }
        }
        int done = 0;
        int total = brackets.size();
        for (Bracket b : brackets.values()) {
            if (cancel != null && cancel.get()) break;
            if (progress != null) progress.update("refining " + (done + 1) + " of " + total, 0.8 + 0.2 * done / Math.max(1, total));
            Candidate c = refine(turn, b, (float) step, lo, hi, cfg);
            if (c != null) out.add(c);
            done++;
        }
        return out;
    }

    private static Candidate refine(Turn turn, Bracket b, float coarseStep, float lo, float hi, Config cfg) {
        float from = Math.max(lo, b.lo - coarseStep);
        float to = Math.min(hi, b.hi + coarseStep);
        int steps = Math.max(1, cfg.refineSteps);
        double fine = (to - from) / (double) steps;
        float landLo = Float.NaN;
        float landHi = Float.NaN;
        double bestOffset = Double.NaN;
        List<Float> plateau = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            float s = (float) (from + i * fine);
            double sc = score(turn, b.pixels, TurnProfile.pixelDeg(s));
            if (!lands(sc)) continue;
            if (Float.isNaN(landLo)) landLo = s;
            landHi = s;
            if (Double.isNaN(bestOffset) || sc > bestOffset + OFFSET_TIE) {
                bestOffset = sc;
                plateau.clear();
                plateau.add(s);
            } else if (sc >= bestOffset - OFFSET_TIE) {
                plateau.add(s);
            }
        }
        if (Double.isNaN(bestOffset)) return null;
        float center = (landLo + landHi) * 0.5f;
        float sens = plateau.get(0);
        for (float s : plateau) if (Math.abs(s - center) < Math.abs(sens - center)) sens = s;
        double p = TurnProfile.pixelDeg(sens);
        double offset = score(turn, b.pixels, p);
        if (!lands(offset)) return null;
        int[] hits = hits(turn, b.pixels, p);
        return new Candidate(sens, landLo, landHi, p, b.pixels.clone(), hits, offset, quantizedYaws(turn, b.pixels, p));
    }

    private static int[] hits(Turn turn, int[] pixels, double pixelDeg) {
        int[] hits = new int[pixels.length];
        int[] trial = pixels.clone();
        for (int k = 0; k < pixels.length; k++) {
            int count = 1;
            for (int dir = -1; dir <= 1 && count < HITS_MAX; dir += 2) {
                for (int d = 1; count < HITS_MAX; d++) {
                    trial[k] = pixels[k] + dir * d;
                    if (!lands(score(turn, trial, pixelDeg))) break;
                    count++;
                }
            }
            trial[k] = pixels[k];
            hits[k] = count;
        }
        return hits;
    }

    public static String hitsText(int hits) {
        return hits >= HITS_MAX ? HITS_MAX + "+" : Integer.toString(hits);
    }

    public static Comparator<Candidate> by(Mode mode) {
        return mode == Mode.FURTHEST ? furthest() : hits();
    }

    public static Comparator<Candidate> hits() {
        return (a, b) -> {
            int c = compareHits(a, b);
            if (c != 0) return c;
            c = compareOffset(a, b);
            if (c != 0) return c;
            return Float.compare(b.sens, a.sens);
        };
    }

    public static Comparator<Candidate> furthest() {
        return (a, b) -> {
            int c = compareOffset(a, b);
            if (c != 0) return c;
            c = compareHits(a, b);
            if (c != 0) return c;
            return Float.compare(b.sens, a.sens);
        };
    }

    private static int compareHits(Candidate a, Candidate b) {
        int n = Math.min(a.hits.length, b.hits.length);
        for (int k = 0; k < n; k++) {
            if (a.hits[k] != b.hits[k]) return Integer.compare(b.hits[k], a.hits[k]);
        }
        return Integer.compare(b.hits.length, a.hits.length);
    }

    private static int compareOffset(Candidate a, Candidate b) {
        if (Math.abs(a.offset - b.offset) <= OFFSET_TIE) return 0;
        return a.offset > b.offset ? -1 : 1;
    }
}
