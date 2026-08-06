package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MomentumAssembly {

    private static final double[] HOLDS = {45, 135, 225, 315};
    private static final double[] BOOST_AXES = {0, 90, 180, 270};
    private static final double[] BOOST_FAN = {0, 8, 16, 24, 4, 12, 20, 28, -8};
    private static final double[] MENU = {45, 0, 90, 135, 180, 225, 270, 315, 25, 65, 20, 70};
    public static final class Config {
        public int[] seamTrims = {2, 0, 4};
        public int templateTries = 6;
        public int frontierTries = 6;
        public int frontierCap = 400000;
        public double frontierSlack = 0.35;
        public double vxCap = 0.13;
        public double closerEps0 = 1.0e-3;
        public int perCandidateSec = 120;
        public HomotopyCloser.Config closer = new HomotopyCloser.Config();
    }

    public static final class Result {
        public final double[] yaws;
        public final double startX;
        public final double startZ;

        Result(double[] yaws, double startX, double startZ) {
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
        }
    }

    private MomentumAssembly() {
    }

    public static Result solve(ExactJumpModel model, JumpSpec spec, double feasTol, StartBox freeBox,
                               long deadlineNanos, AtomicBoolean cancel) {
        return solve(model, spec, feasTol, freeBox, deadlineNanos, cancel, new Config());
    }

    public static Result solve(ExactJumpModel model, JumpSpec spec, double feasTol, StartBox freeBox,
                               long deadlineNanos, AtomicBoolean cancel, Config cfg) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        int[] bounds = jumpBoundaries(sc);
        int jumps = bounds.length - 1;
        if (jumps < 3) return null;
        double[] dir = padDirection(spec, sc);

        int base = bounds[jumps - 2];
        int lo = bounds[jumps - 3] + 2;
        List<Integer> seams = new ArrayList<Integer>();
        for (int trim : cfg.seamTrims) {
            int s = base - trim;
            if (s >= lo && s >= 4 && s <= n - 8 && !seams.contains(s)) seams.add(s);
        }

        for (int seam : seams) {
            if (out(deadlineNanos, cancel)) return null;
            List<double[]> templates = templates(model, sc, seam, dir);
            int tried = 0;
            for (double[] setup : templates) {
                if (tried >= cfg.templateTries || out(deadlineNanos, cancel)) break;
                Result r = tryCandidate(model, spec, sc, setup, seam, n, feasTol, freeBox, deadlineNanos, cancel, cfg);
                if (r != null) return r;
                tried++;
            }
        }
        int seam = seams.isEmpty() ? base : seams.get(0);
        if (out(deadlineNanos, cancel)) return null;
        List<double[]> frontier = frontierCandidates(model, spec, sc, seam, dir, cfg);
        int tried = 0;
        for (double[] setup : frontier) {
            if (tried >= cfg.frontierTries || out(deadlineNanos, cancel)) break;
            Result r = tryCandidate(model, spec, sc, setup, seam, n, feasTol, freeBox, deadlineNanos, cancel, cfg);
            if (r != null) return r;
            tried++;
        }
        return null;
    }

    private static Result tryCandidate(ExactJumpModel model, JumpSpec spec, JumpPhysicsInputs sc,
                                       double[] setup, int seam, int n, double feasTol, StartBox freeBox,
                                       long deadlineNanos, AtomicBoolean cancel, Config cfg) {
        double[] gfAll = new double[n];
        System.arraycopy(setup, 0, gfAll, 0, seam);
        for (int t = seam; t < n; t++) gfAll[t] = setup[seam - 1];
        ForwardPath sp = model.forward(sc, gfAll);
        double dxLo = Double.NEGATIVE_INFINITY, dxHi = Double.POSITIVE_INFINITY;
        double dzLo = Double.NEGATIVE_INFINITY, dzHi = Double.POSITIVE_INFINITY;
        for (JumpConstraint c : spec.constraints) {
            if (c.t2 != null || c.mode == JumpConstraint.Mode.F || c.t1 >= seam) continue;
            boolean isX = c.mode == JumpConstraint.Mode.X;
            double pos = isX ? sp.posX[c.t1] : sp.posZ[c.t1];
            double b = c.rhs - pos;
            if (c.cmp == JumpConstraint.Cmp.GE) {
                if (isX) dxLo = Math.max(dxLo, b); else dzLo = Math.max(dzLo, b);
            } else if (c.cmp == JumpConstraint.Cmp.LE) {
                if (isX) dxHi = Math.min(dxHi, b); else dzHi = Math.min(dzHi, b);
            }
        }
        if (freeBox != null) {
            dxLo = Math.max(dxLo, freeBox.pxLo - sc.startPos.x);
            dxHi = Math.min(dxHi, freeBox.pxHi - sc.startPos.x);
            dzLo = Math.max(dzLo, freeBox.pzLo - sc.startPos.z);
            dzHi = Math.min(dzHi, freeBox.pzHi - sc.startPos.z);
        }
        if (dxLo > dxHi || dzLo > dzHi) return null;
        double d0x = Math.max(dxLo, Math.min(dxHi, 0.0));
        double d0z = Math.max(dzLo, Math.min(dzHi, 0.0));
        if (freeBox == null && (d0x != 0.0 || d0z != 0.0)) return null;

        JumpPhysicsInputs scAt = d0x == 0.0 && d0z == 0.0 ? sc
                : shiftedScenario(sc, sc.startPos.x + d0x, sc.startPos.z + d0z);
        ForwardPath spAt = scAt == sc ? sp : model.forward(scAt, gfAll);
        JumpSpec tail = tailSpec(spec, scAt, seam, n,
                new Vec3dCore(spAt.posX[seam], spAt.posY[seam], spAt.posZ[seam]),
                new Vec3dCore(spAt.velX[seam], 0.0, spAt.velZ[seam]),
                (float) setup[seam - 1]);
        long perCandidateNanos = cfg.perCandidateSec * 1_000_000_000L;
        long candDeadline = deadlineNanos > 0
                ? Math.min(deadlineNanos, System.nanoTime() + perCandidateNanos)
                : System.nanoTime() + perCandidateNanos;
        double[] y = HomotopyCloser.close(model, tail, null, cfg.closerEps0, candDeadline, cancel, cfg.closer);
        if (y == null) return null;

        double[] yTail = Angles.wrapAll(y);
        double[] yaws = new double[n];
        System.arraycopy(setup, 0, yaws, 0, seam);
        for (int t = seam; t < n; t++) yaws[t] = yTail[t - seam];
        yaws = Angles.wrapAll(yaws);
        double[] replay = scAt.toGameFacings(yaws);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double viol = compiled.maxViolation(replay, model.forward(scAt, replay));
        if (viol > feasTol) {
            JumpSpec specAt = new JumpSpec(scAt, spec.constraints, spec.objective);
            double[] rep = HomotopyCloser.descend(model, specAt, yaws, candDeadline, cancel, cfg.closer);
            double[] rr = scAt.toGameFacings(Angles.wrapAll(rep));
            if (compiled.maxViolation(rr, model.forward(scAt, rr)) > feasTol) return null;
            yaws = Angles.wrapAll(rep);
        }
        return new Result(yaws, scAt.startPos.x, scAt.startPos.z);
    }

    private static JumpPhysicsInputs shiftedScenario(JumpPhysicsInputs sc, double px, double pz) {
        JumpPhysicsInputs p = new JumpPhysicsInputs(sc.numTicks);
        p.startPos = new Vec3dCore(px, sc.startPos.y, pz);
        p.initialVelocity = sc.initialVelocity;
        p.startYaw = sc.startYaw;
        p.incomingSprint = sc.incomingSprint;
        p.incomingAmp = sc.incomingAmp;
        p.liveAirSprintFactor = sc.liveAirSprintFactor;
        p.strafeSign = sc.strafeSign;
        p.jumpPerTick = sc.jumpPerTick;
        p.strafePerTick = sc.strafePerTick;
        p.yawLockedPerTick = sc.yawLockedPerTick;
        p.speedAmplifier = sc.speedAmplifier;
        p.slipPerTick = sc.slipPerTick;
        p.surfacePerTick = sc.surfacePerTick;
        p.soulsandCellsPerTick = sc.soulsandCellsPerTick;
        p.sneakPerTick = sc.sneakPerTick;
        p.sprintPerTick = sc.sprintPerTick;
        p.forwardInputPerTick = sc.forwardInputPerTick;
        p.strafeInputPerTick = sc.strafeInputPerTick;
        p.startBox = StartBox.pinned(px, pz, sc.initialVelocity.x, sc.initialVelocity.z);
        return p;
    }

    private static final class Group {
        final double score;
        final List<double[]> setups;

        Group(double score, List<double[]> setups) {
            this.score = score;
            this.setups = setups;
        }
    }

    private static List<double[]> templates(ExactJumpModel model, JumpPhysicsInputs sc, int seam, double[] dir) {
        int n = sc.numTicks;
        List<Integer> boosts = new ArrayList<Integer>();
        for (int j = 1; j < seam; j++) {
            if (sc.jumpAt(j) && sc.sprintAt(j)) boosts.add(j);
        }
        List<Group> groups = new ArrayList<Group>();
        for (double hold : HOLDS) {
            if (boosts.isEmpty()) {
                double[] tpl = filled(seam, hold);
                List<double[]> one = new ArrayList<double[]>();
                one.add(tpl);
                groups.add(new Group(score(model, sc, seam, n, tpl, dir), one));
                continue;
            }
            for (int j : boosts) {
                double bestAxis = BOOST_AXES[0];
                double bestScore = Double.NEGATIVE_INFINITY;
                for (double axis : BOOST_AXES) {
                    double[] tpl = filled(seam, hold);
                    tpl[j] = axis;
                    double s = score(model, sc, seam, n, tpl, dir);
                    if (s > bestScore) {
                        bestScore = s;
                        bestAxis = axis;
                    }
                }
                List<double[]> fan = new ArrayList<double[]>();
                for (double delta : BOOST_FAN) {
                    double[] tpl = filled(seam, hold);
                    tpl[j] = bestAxis + delta;
                    fan.add(tpl);
                }
                groups.add(new Group(bestScore, fan));
            }
        }
        groups.sort(new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                return Double.compare(b.score, a.score);
            }
        });
        List<double[]> out = new ArrayList<double[]>();
        for (Group g : groups) out.addAll(g.setups);
        return out;
    }

    private static double score(ExactJumpModel model, JumpPhysicsInputs sc, int seam, int n,
                                double[] tpl, double[] dir) {
        double[] gf = new double[n];
        System.arraycopy(tpl, 0, gf, 0, seam);
        for (int t = seam; t < n; t++) gf[t] = tpl[seam - 1];
        ForwardPath p = model.forward(sc, gf);
        return p.velX[seam] * dir[0] + p.velZ[seam] * dir[1];
    }

    private static double[] filled(int len, double v) {
        double[] a = new double[len];
        java.util.Arrays.fill(a, v);
        return a;
    }

    private static double[] padDirection(JumpSpec spec, JumpPhysicsInputs sc) {
        int objTick = spec.objective.tick;
        double xLo = Double.NaN, xHi = Double.NaN, zLo = Double.NaN, zHi = Double.NaN;
        for (JumpConstraint c : spec.constraints) {
            if (c.t1 != objTick || c.t2 != null || c.mode == JumpConstraint.Mode.F) continue;
            if (c.mode == JumpConstraint.Mode.X) {
                if (c.cmp == JumpConstraint.Cmp.GE) xLo = c.rhs;
                if (c.cmp == JumpConstraint.Cmp.LE) xHi = c.rhs;
            } else {
                if (c.cmp == JumpConstraint.Cmp.GE) zLo = c.rhs;
                if (c.cmp == JumpConstraint.Cmp.LE) zHi = c.rhs;
            }
        }
        double cx = !Double.isNaN(xLo) && !Double.isNaN(xHi) ? 0.5 * (xLo + xHi) : sc.startPos.x;
        double cz = !Double.isNaN(zLo) && !Double.isNaN(zHi) ? 0.5 * (zLo + zHi) : sc.startPos.z;
        double dx = cx - sc.startPos.x;
        double dz = cz - sc.startPos.z;
        double norm = Math.sqrt(dx * dx + dz * dz);
        if (norm < 1.0e-9) {
            boolean x = spec.objective.axis == JumpPhysicsInputs.Axis.X;
            double s = spec.objective.sense == Objective.Sense.MAX ? 1.0 : -1.0;
            return x ? new double[]{s, 0.0} : new double[]{0.0, s};
        }
        return new double[]{dx / norm, dz / norm};
    }

    private static final class Node {
        final long a0, a1;
        final double vx, vz, px, pz, dip;

        Node(long a0, long a1, double vx, double vz, double px, double pz, double dip) {
            this.a0 = a0;
            this.a1 = a1;
            this.vx = vx;
            this.vz = vz;
            this.px = px;
            this.pz = pz;
            this.dip = dip;
        }
    }

    private static List<double[]> frontierCandidates(ExactJumpModel model, JumpSpec spec,
                                                     JumpPhysicsInputs sc, int seam, double[] dir, Config cfg) {
        List<Node> frontier = new ArrayList<Node>();
        frontier.add(new Node(0L, 0L, sc.initialVelocity.x, sc.initialVelocity.z, sc.startPos.x, sc.startPos.z, sc.startPos.z));
        for (int t = 0; t < seam && t < 32; t++) {
            frontier.sort(new Comparator<Node>() {
                @Override
                public int compare(Node a, Node b) {
                    return Double.compare(b.vz * dir[1] + b.vx * dir[0], a.vz * dir[1] + a.vx * dir[0]);
                }
            });
            HashMap<Long, Node> grid = new HashMap<Long, Node>();
            for (Node nd : frontier) {
                for (int ai = 0; ai < MENU.length; ai++) {
                    Node c = step(model, sc, nd, ai, t);
                    if (Math.abs(c.vx * dir[1] - c.vz * dir[0]) > cfg.vxCap) continue;
                    if (violatesSetup(spec, t + 1, seam, c.px, c.pz, cfg.frontierSlack)) continue;
                    long key = ((Math.round(c.vz / 2e-4) * 270001L + Math.round((c.pz - c.dip) / 2e-2)) * 270001L
                            + Math.round(c.vx / 1e-3)) * 161L + Math.round(c.px / 5e-2);
                    Node prev = grid.get(key);
                    if (prev == null) {
                        if (grid.size() < cfg.frontierCap) grid.put(key, c);
                    } else if (c.vz * dir[1] + c.vx * dir[0] > prev.vz * dir[1] + prev.vx * dir[0]) {
                        grid.put(key, c);
                    }
                }
            }
            frontier = new ArrayList<Node>(grid.values());
            if (frontier.isEmpty()) return new ArrayList<double[]>();
        }
        HashMap<Long, Node> exact = new HashMap<Long, Node>();
        for (Node nd : frontier) {
            long k = (Math.round(nd.vz / 1e-7) * 1000003L + Math.round(nd.pz / 1e-6)) * 1000003L
                    + Math.round(nd.vx / 1e-7) * 31L + Math.round(nd.px / 1e-6);
            if (!exact.containsKey(k)) exact.put(k, nd);
        }
        HashMap<Integer, List<Node>> bands = new HashMap<Integer, List<Node>>();
        for (Node nd : exact.values()) {
            int band = (int) Math.floor((nd.pz - nd.dip) / 0.5);
            List<Node> in = bands.get(band);
            if (in == null) {
                in = new ArrayList<Node>();
                bands.put(band, in);
            }
            in.add(nd);
        }
        Comparator<Node> byScore = new Comparator<Node>() {
            @Override
            public int compare(Node a, Node b) {
                return Double.compare(b.vz * dir[1] + b.vx * dir[0], a.vz * dir[1] + a.vx * dir[0]);
            }
        };
        List<List<Node>> picks = new ArrayList<List<Node>>();
        for (List<Node> in : bands.values()) in.sort(byScore);
        List<List<Node>> ordered = new ArrayList<List<Node>>(bands.values());
        ordered.sort(new Comparator<List<Node>>() {
            @Override
            public int compare(List<Node> a, List<Node> b) {
                return byScoreTop(a, b);
            }

            private int byScoreTop(List<Node> a, List<Node> b) {
                Node na = a.get(0), nb = b.get(0);
                return Double.compare(nb.vz * dir[1] + nb.vx * dir[0], na.vz * dir[1] + na.vx * dir[0]);
            }
        });
        for (List<Node> in : ordered) {
            List<Node> sel = new ArrayList<Node>();
            for (Node nd : in) {
                if (sel.size() >= 2) break;
                sel.add(nd);
            }
            picks.add(sel);
        }
        List<double[]> out = new ArrayList<double[]>();
        for (int round = 0; out.size() < 24; round++) {
            boolean any = false;
            for (List<Node> sel : picks) {
                if (round < sel.size()) {
                    Node nd = sel.get(round);
                    double[] s = new double[seam];
                    for (int t = 0; t < seam; t++) {
                        int ai = (int) ((t < 16 ? nd.a0 >>> (4 * t) : nd.a1 >>> (4 * (t - 16))) & 0xF);
                        s[t] = MENU[ai];
                    }
                    out.add(s);
                    any = true;
                    if (out.size() >= 24) break;
                }
            }
            if (!any) break;
        }
        return out;
    }

    private static Node step(ExactJumpModel model, JumpPhysicsInputs full, Node parent, int ai, int t) {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(1);
        sc.startPos = new Vec3dCore(parent.px, full.startPos.y, parent.pz);
        sc.initialVelocity = new Vec3dCore(parent.vx, 0.0, parent.vz);
        sc.startYaw = (float) MENU[ai];
        sc.incomingSprint = t == 0 ? full.incomingSprint : full.sprintAt(t - 1);
        sc.incomingAmp = t == 0 ? full.incomingAmp : full.speedAmplifierAt(t - 1);
        sc.liveAirSprintFactor = full.liveAirSprintFactor;
        sc.strafeSign = full.strafeSign;
        sc.jumpPerTick = new boolean[]{full.jumpAt(t)};
        sc.slipPerTick = new double[]{full.slipAt(t)};
        sc.surfacePerTick = new SurfaceKind[]{full.surfaceAt(t)};
        sc.soulsandCellsPerTick = new int[]{full.soulsandCellsAt(t)};
        sc.sneakPerTick = new boolean[]{full.sneakAt(t)};
        sc.strafePerTick = new boolean[]{full.strafeAt(t)};
        sc.sprintPerTick = new boolean[]{full.sprintAt(t)};
        sc.forwardInputPerTick = new float[]{full.forwardAt(t)};
        sc.strafeInputPerTick = new float[]{full.strafeInputAt(t)};
        sc.speedAmplifier = new int[]{full.speedAmplifierAt(t)};
        sc.yawLockedPerTick = new boolean[]{false};
        ForwardPath p = model.forward(sc, new double[]{MENU[ai]});
        long na0 = parent.a0, na1 = parent.a1;
        if (t < 16) na0 |= (long) ai << (4 * t);
        else na1 |= (long) ai << (4 * (t - 16));
        return new Node(na0, na1, p.velX[1], p.velZ[1], p.posX[1], p.posZ[1], Math.min(parent.dip, p.posZ[1]));
    }

    private static boolean violatesSetup(JumpSpec spec, int tick, int seam, double px, double pz,
                                         double frontierSlack) {
        if (tick >= seam) return false;
        for (JumpConstraint c : spec.constraints) {
            if (c.t1 != tick || c.t2 != null) continue;
            double val = c.mode == JumpConstraint.Mode.X ? px : (c.mode == JumpConstraint.Mode.Z ? pz : Double.NaN);
            if (Double.isNaN(val)) continue;
            if (c.cmp == JumpConstraint.Cmp.GE && val < c.rhs - frontierSlack) return true;
            if (c.cmp == JumpConstraint.Cmp.LE && val > c.rhs + frontierSlack) return true;
        }
        return false;
    }

    private static JumpSpec tailSpec(JumpSpec spec, JumpPhysicsInputs sc, int a, int c,
                                     Vec3dCore pos, Vec3dCore vel, float yaw) {
        int len = c - a;
        JumpPhysicsInputs p = new JumpPhysicsInputs(len);
        p.startPos = pos;
        p.initialVelocity = vel;
        p.startYaw = yaw;
        p.strafeSign = sc.strafeSign;
        p.incomingSprint = a == 0 ? sc.incomingSprint : sc.sprintAt(a - 1);
        p.incomingAmp = a == 0 ? sc.incomingAmp : sc.speedAmplifierAt(a - 1);
        p.liveAirSprintFactor = sc.liveAirSprintFactor;
        p.jumpPerTick = sliceBool(sc.jumpPerTick, a, len);
        p.strafePerTick = sliceBool(sc.strafePerTick, a, len);
        p.yawLockedPerTick = sliceBool(sc.yawLockedPerTick, a, len);
        p.speedAmplifier = sliceInt(sc.speedAmplifier, a, len);
        p.slipPerTick = sliceDouble(sc.slipPerTick, a, len);
        p.surfacePerTick = sliceKind(sc.surfacePerTick, a, len);
        p.soulsandCellsPerTick = sliceInt(sc.soulsandCellsPerTick, a, len);
        p.sneakPerTick = sliceBool(sc.sneakPerTick, a, len);
        p.sprintPerTick = sliceBool(sc.sprintPerTick, a, len);
        p.forwardInputPerTick = sliceFloat(sc.forwardInputPerTick, a, len, 0.98F);
        p.strafeInputPerTick = sliceFloat(sc.strafeInputPerTick, a, len, 0.0F);
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        for (JumpConstraint jc : spec.constraints) {
            boolean in1 = jc.t1 >= a && jc.t1 <= c;
            boolean in2 = jc.t2 == null || (jc.t2 >= a && jc.t2 <= c);
            if (in1 && in2) {
                Integer t2 = jc.t2 == null ? null : (jc.t2 - a);
                cons.add(new JumpConstraint(jc.mode, jc.t1 - a, t2, jc.op, jc.cmp, jc.rhs, jc.name));
            }
        }
        return new JumpSpec(p, cons, new Objective(spec.objective.axis, spec.objective.sense, len));
    }

    private static int[] jumpBoundaries(JumpPhysicsInputs sc) {
        List<Integer> b = new ArrayList<Integer>();
        b.add(0);
        for (int t = 1; t < sc.numTicks; t++) {
            if (sc.jumpAt(t) && !Double.isNaN(sc.slipAt(t))) b.add(t);
        }
        b.add(sc.numTicks);
        int[] out = new int[b.size()];
        for (int i = 0; i < out.length; i++) out[i] = b.get(i);
        return out;
    }

    private static boolean out(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) return true;
        return deadlineNanos > 0 && System.nanoTime() >= deadlineNanos;
    }

    private static boolean[] sliceBool(boolean[] x, int f, int len) {
        if (x == null) return null;
        boolean[] o = new boolean[len];
        for (int i = 0; i < len; i++) o[i] = f + i < x.length && x[f + i];
        return o;
    }

    private static int[] sliceInt(int[] x, int f, int len) {
        if (x == null) return null;
        int[] o = new int[len];
        for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : 0;
        return o;
    }

    private static double[] sliceDouble(double[] x, int f, int len) {
        if (x == null) return null;
        double[] o = new double[len];
        for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : Double.NaN;
        return o;
    }

    private static SurfaceKind[] sliceKind(SurfaceKind[] x, int f, int len) {
        if (x == null) return null;
        SurfaceKind[] o = new SurfaceKind[len];
        for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : SurfaceKind.NORMAL;
        return o;
    }

    private static float[] sliceFloat(float[] x, int f, int len, float d) {
        if (x == null) return null;
        float[] o = new float[len];
        for (int i = 0; i < len; i++) o[i] = f + i < x.length ? x[f + i] : d;
        return o;
    }
}
