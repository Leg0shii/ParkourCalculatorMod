package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProblemCompiler {

    public static final int MAX_SPECS = 128;

    private ProblemCompiler() {
    }

    public static final class Compiled {
        public final SaveFile save;
        public final int[] fireTicks;
        public final int[] landTicks;
        public final int[] groundCounts;
        public final double[] yPerTick;
        public final StratProblem.Area[] landings;
        public final StratProblem problem;
        public final String label;

        Compiled(SaveFile save, int[] fireTicks, int[] landTicks, int[] groundCounts, double[] yPerTick,
                 StratProblem.Area[] landings, StratProblem problem, String label) {
            this.save = save;
            this.fireTicks = fireTicks;
            this.landTicks = landTicks;
            this.groundCounts = groundCounts;
            this.yPerTick = yPerTick;
            this.landings = landings;
            this.problem = problem;
            this.label = label;
        }
    }

    public static final class Compilation {
        public final List<Compiled> specs;
        public final boolean truncated;
        public final List<String> notes;

        Compilation(List<Compiled> specs, boolean truncated, List<String> notes) {
            this.specs = specs;
            this.truncated = truncated;
            this.notes = notes;
        }
    }

    public static Compilation compile(StratProblem p) {
        List<Compiled> specs = new ArrayList<Compiled>();
        List<String> notes = new ArrayList<String>();
        if (p == null || p.start == null || p.segments == null || p.segments.isEmpty()) {
            notes.add("problem needs a start area and at least one segment");
            return new Compilation(specs, false, notes);
        }
        for (int i = 0; i < p.segments.size(); i++) {
            StratProblem.Segment seg = p.segments.get(i);
            if (seg == null || seg.landings == null || seg.landings.isEmpty()) {
                notes.add("segment " + (i + 1) + " has no landing area");
                return new Compilation(specs, false, notes);
            }
            if (seg.groundLo < 1 || seg.groundHi < seg.groundLo) {
                notes.add("segment " + (i + 1) + " has an invalid ground tick range");
                return new Compilation(specs, false, notes);
            }
            if (seg.ja && i != p.segments.size() - 1) {
                notes.add("segment " + (i + 1) + " requests ja; only the last segment can free its fire facing");
            }
        }
        boolean truncated = enumerate(p, 0, new int[p.segments.size()],
                new StratProblem.Area[p.segments.size()], specs, notes);
        return new Compilation(specs, truncated, notes);
    }

    private static boolean enumerate(StratProblem p, int idx, int[] grounds, StratProblem.Area[] chosen,
                                     List<Compiled> specs, List<String> notes) {
        if (idx == p.segments.size()) {
            if (specs.size() >= MAX_SPECS) {
                return true;
            }
            Compiled c = build(p, grounds, chosen, notes);
            if (c != null) {
                specs.add(c);
            }
            return false;
        }
        StratProblem.Segment seg = p.segments.get(idx);
        for (int g = seg.groundLo; g <= seg.groundHi; g++) {
            for (StratProblem.Area land : seg.landings) {
                grounds[idx] = g;
                chosen[idx] = land;
                if (enumerate(p, idx + 1, grounds, chosen, specs, notes)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Compiled build(StratProblem p, int[] grounds, StratProblem.Area[] chosen, List<String> notes) {
        boolean legacy = JumpArcs.legacyThreshold(p.mcVersion);
        int n = p.segments.size();
        int[] fires = new int[n];
        int[] lands = new int[n];
        int[] groundCounts = grounds.clone();
        StratProblem.Area ground = p.start;
        int t = 0;
        List<double[]> arcs = new ArrayList<double[]>();
        for (int i = 0; i < n; i++) {
            int press = t + grounds[i] - 1;
            double dy = chosen[i].top() - ground.top();
            StratProblem.Segment sg = p.segments.get(i);
            double ceiling = sg.ceilingY;
            double headroom = Double.isNaN(ceiling) ? Double.NaN : ceiling - ground.top();
            int d;
            double[] arc;
            if (sg.airTicks > 0) {
                d = sg.airTicks;
                if (sg.arcRel != null && sg.arcRel.length == d - 1) {
                    arc = new double[d + 1];
                    arc[d] = dy;
                    for (int a = 1; a < d; a++) {
                        arc[a] = sg.arcRel[a - 1];
                    }
                } else if (JumpArcs.duration(dy, headroom, legacy) == d) {
                    arc = JumpArcs.heights(d, dy, headroom, legacy);
                } else {
                    arc = new double[d + 1];
                    arc[d] = dy;
                    for (int a = 1; a < d; a++) {
                        arc[a] = dy * a / d;
                    }
                }
            } else {
                d = JumpArcs.duration(dy, headroom, legacy);
                if (d < 1) {
                    notes.add(String.format(Locale.ROOT, "segment %d: rise %.2f is not jumpable%s, branch skipped",
                            i + 1, dy, Double.isNaN(headroom) ? "" : " under that ceiling"));
                    return null;
                }
                arc = JumpArcs.heights(d, dy, headroom, legacy);
            }
            fires[i] = press;
            lands[i] = press + d;
            arcs.add(arc);
            t = lands[i];
            ground = chosen[i];
        }
        int landingTick = lands[n - 1];
        int lastPress = fires[n - 1];
        boolean ja = p.segments.get(n - 1).ja;

        boolean[] grounded = new boolean[landingTick + 1];
        boolean[] isLanding = new boolean[landingTick + 1];
        for (int i = 0; i < n; i++) {
            isLanding[lands[i]] = true;
        }
        StratProblem.Area[] surface = new StratProblem.Area[landingTick + 1];
        double[] y = new double[landingTick + 1];
        StratProblem.Area cur = p.start;
        int tick = 0;
        for (int i = 0; i < n; i++) {
            for (int g = 0; g < grounds[i]; g++) {
                grounded[tick] = true;
                surface[tick] = cur;
                y[tick] = cur.top();
                tick++;
            }
            double[] arc = arcs.get(i);
            for (int a = 1; a < arc.length - 1; a++) {
                grounded[tick] = false;
                y[tick] = cur.top() + arc[a];
                tick++;
            }
            cur = chosen[i];
            grounded[tick] = true;
            surface[tick] = cur;
            y[tick] = cur.top();
            if (i == n - 1) {
                tick++;
            }
        }

        SaveFile save = new SaveFile();
        save.mcVersion = p.mcVersion;
        save.angleSolver = new SaveFile.AngleSolver();
        SaveFile.AngleSolver as = save.angleSolver;
        as.startTick = 0;
        as.landingTick = landingTick;
        as.defaultSlipperiness = "AIR";
        as.defaultInputs = "KEEP";
        as.defaultSprint = "DERIVE";
        as.ticks = new ArrayList<SaveFile.Tick>();
        for (int k = 0; k <= landingTick; k++) {
            SaveFile.Tick tk = new SaveFile.Tick();
            tk.tick = k;
            boolean any = false;
            if (grounded[k]) {
                tk.override = new SaveFile.Override();
                tk.override.slipperiness = surface[k].slipperiness != null
                        ? surface[k].slipperiness : "DEFAULT";
                any = true;
                SaveFile.Constraint fx = rangeConstraint("X",
                        surface[k].xLo - StratProblem.HALF_WIDTH, surface[k].xHi + StratProblem.HALF_WIDTH);
                SaveFile.Constraint fz = rangeConstraint("Z",
                        surface[k].zLo - StratProblem.HALF_WIDTH, surface[k].zHi + StratProblem.HALF_WIDTH);
                fx.derived = !isLanding[k];
                fz.derived = !isLanding[k];
                tk.constraints.add(fx);
                tk.constraints.add(fz);
            }
            int dfEnd = ja ? lastPress - 1 : landingTick - 1;
            if (k >= 1 && k <= dfEnd) {
                SaveFile.Constraint df = dfZero();
                df.derived = true;
                tk.constraints.add(df);
                any = true;
            }
            for (StratProblem.Wall w : p.userWalls) {
                int nt = translateTick(w.tick, p.segments, fires, lands);
                if (nt == k) {
                    SaveFile.Constraint c = new SaveFile.Constraint();
                    c.range = false;
                    c.field = w.field;
                    c.op = w.op;
                    c.value = w.value;
                    tk.constraints.add(c);
                    any = true;
                }
            }
            if (any) {
                as.ticks.add(tk);
            }
        }

        SaveFile.Start start = new SaveFile.Start();
        start.pos = new double[]{p.start.centerX(), p.start.top(), p.start.centerZ()};
        start.vel = new double[]{0.0, 0.0, 0.0};
        start.yaw = 0f;
        start.pitch = 0f;
        save.start = start;
        SaveFile.Start seed = new SaveFile.Start();
        seed.pos = start.pos.clone();
        seed.vel = start.vel.clone();
        seed.yaw = 0f;
        as.seed = seed;

        save.rows = new ArrayList<SaveFile.Row>();
        for (int k = 0; k <= landingTick; k++) {
            SaveFile.Row row = new SaveFile.Row();
            row.keys = new ArrayList<String>();
            save.rows.add(row);
        }

        StringBuilder label = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                label.append(' ');
            }
            label.append('g').append(grounds[i]).append('>').append(chosen[i].label != null
                    ? chosen[i].label : "land" + (i + 1));
        }
        return new Compiled(save, fires, lands, groundCounts, y, chosen.clone(), p, label.toString());
    }

    static int translateTick(int t, List<StratProblem.Segment> segments, int[] fires, int[] lands) {
        int n = segments.size();
        for (int i = 0; i < n; i++) {
            if (segments.get(i).refFire < 0 || segments.get(i).refLand < 0) {
                return -1;
            }
        }
        if (t <= segments.get(0).refFire) {
            int nt = t + (fires[0] - segments.get(0).refFire);
            return nt >= 1 ? nt : -1;
        }
        for (int i = 0; i < n; i++) {
            StratProblem.Segment sg = segments.get(i);
            if (t <= sg.refLand) {
                return t + (fires[i] - sg.refFire);
            }
            if (i + 1 < n && t <= segments.get(i + 1).refFire) {
                int nt = t + (lands[i] - sg.refLand);
                return nt <= fires[i + 1] ? nt : -1;
            }
        }
        return t + (lands[n - 1] - segments.get(n - 1).refLand);
    }

    private static SaveFile.Constraint rangeConstraint(String field, double lo, double hi) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = true;
        c.field = field;
        c.lo = lo;
        c.hi = hi;
        return c;
    }

    private static SaveFile.Constraint dfZero() {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = "DF";
        c.op = "EQ";
        c.value = 0.0;
        return c;
    }
}
