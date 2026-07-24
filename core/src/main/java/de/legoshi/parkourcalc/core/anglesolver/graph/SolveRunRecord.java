package de.legoshi.parkourcalc.core.anglesolver.graph;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SolveRunRecord {

    public static final String STATUS_SOLVED = "SOLVED";
    public static final String STATUS_STOPPED_BEST = "STOPPED_BEST";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_FAILED = "FAILED";

    public static final class Metric {
        public String type;
        public double feasTol;
        public String sense;
        public double smoothLambda;
    }

    public static final class Param {
        public String key;
        public Double num;
        public Boolean flag;
        public String str;
    }

    public static final class NodeConfig {
        public String id;
        public String type;
        public List<Param> params = new ArrayList<Param>();
    }

    public static final class Config {
        public String preset;
        public String graphHash;
        public String effort;
        public Metric metric;
        public List<NodeConfig> nodes = new ArrayList<NodeConfig>();
    }

    public static final class Problem {
        public String name;
        public String hash;
        public int numTicks;
        public int jumps;
        public int constraints;
        public String axis;
        public String sense;
        public int objectiveTick;
        public boolean freeStart;
    }

    public static final class Outcome {
        public String status;
        public long wallNanos;
        public Double objective;
        public Double violation;
        public boolean feasible;
        public String chain;
        public Double yawTravelDeg;
        public Integer yawDirChanges;
        public Double yawMaxStepDeg;
        public Double yawJerkDeg;
    }

    public static final class Sample {
        public long elapsedNanos;
        public double obj;
        public double viol;
        public boolean feasible;
        public String stage;
        public String node;
    }

    public static final class NodeRun {
        public String id;
        public String label;
        public int visits;
        public long elapsedNanos;
        public String taken;
        public long evals;
    }

    public static final class Race {
        public boolean spawned;
        public long spawnElapsedNanos;
        public String winner;
        public String exploreChain;
        public String exploreGraphHash;
        public List<NodeRun> exploreNodes = new ArrayList<NodeRun>();
    }

    public static final class Counters {
        public long cmaesEvals;
        public long smoothingEvals;
    }

    public Config config;
    public Problem problem;
    public Outcome outcome;
    public List<Sample> trajectory = new ArrayList<Sample>();
    public List<NodeRun> nodes = new ArrayList<NodeRun>();
    public Race race;
    public Counters counters;
    public String modVersion;
    public String mcVersion;
    public String model;
    public long finishedEpochMs;

    public static Config configOf(SolverGraph graph, String presetName, String effort, double feasTol, Objective objective) {
        Config c = new Config();
        c.preset = presetName != null ? presetName : graph.name;
        c.graphHash = graphHash(graph);
        c.effort = effort;
        c.metric = new Metric();
        c.metric.type = "hierarchical";
        c.metric.feasTol = feasTol;
        c.metric.sense = objective.sense.name();
        c.metric.smoothLambda = objective.smoothLambda;
        for (GraphNode n : graph.nodes) {
            NodeConfig nc = new NodeConfig();
            nc.id = n.id;
            nc.type = n.type.id;
            for (ParamSpec spec : n.type.params) {
                nc.params.add(paramOf(n, spec));
            }
            c.nodes.add(nc);
        }
        return c;
    }

    private static Param paramOf(GraphNode n, ParamSpec spec) {
        Param p = new Param();
        p.key = spec.key;
        switch (spec.kind) {
            case INT:
                p.num = (double) n.params.getInt(spec.key);
                break;
            case DOUBLE:
                p.num = n.params.getDouble(spec.key);
                break;
            case BOOL:
                p.flag = n.params.getBool(spec.key);
                break;
            case ENUM:
            case STRING:
                p.str = n.params.getString(spec.key);
                break;
        }
        return p;
    }

    public static Problem problemOf(JumpSpec spec, int jumps) {
        JumpPhysicsInputs sc = spec.asScenario();
        Problem p = new Problem();
        p.hash = problemHash(spec);
        p.numTicks = sc.numTicks;
        p.jumps = jumps;
        p.constraints = spec.constraints.size();
        p.axis = spec.objective.axis.name();
        p.sense = spec.objective.sense.name();
        p.objectiveTick = spec.objective.tick;
        p.freeStart = sc.startBox != null && sc.startBox.startFree();
        return p;
    }

    public static void smoothnessOf(Outcome out, double[] yaws) {
        if (out == null || yaws == null || yaws.length < 2) return;
        double travel = 0.0;
        double maxStep = 0.0;
        for (int t = 1; t < yaws.length; t++) {
            double d = yaws[t] - yaws[t - 1];
            d -= 360.0 * Math.round(d / 360.0);
            double a = Math.abs(d);
            travel += a;
            if (a > maxStep) maxStep = a;
        }
        out.yawTravelDeg = travel;
        out.yawDirChanges = Angles.reversals(yaws, Angles.REVERSAL_FLOOR_DEG);
        out.yawMaxStepDeg = maxStep;
        out.yawJerkDeg = Angles.wiggleDeg(yaws);
    }

    public static List<Sample> samplesOf(List<SolveProgress.Sample> samples) {
        List<Sample> out = new ArrayList<Sample>(samples.size());
        for (SolveProgress.Sample s : samples) {
            Sample r = new Sample();
            r.elapsedNanos = s.elapsedNanos;
            r.obj = s.objective;
            r.viol = s.violation;
            r.feasible = s.feasible;
            r.stage = s.stage;
            r.node = s.node;
            out.add(r);
        }
        return out;
    }

    public static List<NodeRun> nodeRunsOf(List<NodeStatus> statuses) {
        List<NodeRun> out = new ArrayList<NodeRun>(statuses.size());
        for (NodeStatus s : statuses) {
            NodeRun r = new NodeRun();
            r.id = s.nodeId;
            r.label = s.label;
            r.visits = s.visits;
            r.elapsedNanos = s.elapsedNanos;
            r.taken = s.taken != null ? s.taken.name() : null;
            r.evals = s.evals;
            out.add(r);
        }
        return out;
    }

    public static String graphHash(SolverGraph graph) {
        StringBuilder sb = new StringBuilder();
        List<GraphNode> nodes = new ArrayList<GraphNode>(graph.nodes);
        nodes.sort((a, b) -> a.id.compareTo(b.id));
        for (GraphNode n : nodes) {
            sb.append("n:").append(n.id).append(':').append(n.type.id);
            for (ParamSpec spec : n.type.params) {
                sb.append(';').append(spec.key).append('=');
                switch (spec.kind) {
                    case INT:
                        sb.append(n.params.getInt(spec.key));
                        break;
                    case DOUBLE:
                        sb.append(Double.doubleToLongBits(n.params.getDouble(spec.key)));
                        break;
                    case BOOL:
                        sb.append(n.params.getBool(spec.key));
                        break;
                    case ENUM:
                    case STRING:
                        sb.append(n.params.getString(spec.key));
                        break;
                }
            }
            sb.append('\n');
        }
        List<String> edgeLines = new ArrayList<String>(graph.edges.size());
        for (GraphEdge e : graph.edges) {
            edgeLines.add("e:" + e.fromNode + ":" + e.branch.name() + ":" + e.toNode);
        }
        Collections.sort(edgeLines);
        for (String line : edgeLines) {
            sb.append(line).append('\n');
        }
        return sha256Hex16(sb.toString());
    }

    public static String problemHash(JumpSpec spec) {
        JumpPhysicsInputs sc = spec.asScenario();
        StringBuilder sb = new StringBuilder();
        sb.append("obj:").append(spec.objective.axis.name()).append(':')
                .append(spec.objective.sense.name()).append(':').append(spec.objective.tick).append('\n');
        sb.append("n:").append(sc.numTicks).append('\n');
        sb.append("start:").append(bits(sc.startPos.x)).append(',').append(bits(sc.startPos.z))
                .append(',').append(Float.floatToIntBits(sc.startYaw))
                .append(',').append(bits(sc.initialVelocity.x)).append(',').append(bits(sc.initialVelocity.z)).append('\n');
        StartBox box = sc.startBox;
        if (box != null) {
            sb.append("box:").append(bits(box.pxLo)).append(',').append(bits(box.pxHi))
                    .append(',').append(bits(box.pzLo)).append(',').append(bits(box.pzHi))
                    .append(',').append(bits(box.vxLo)).append(',').append(bits(box.vxHi))
                    .append(',').append(bits(box.vzLo)).append(',').append(bits(box.vzHi)).append('\n');
        }
        sb.append("jumpTick:").append(sc.jumpTick).append('\n');
        sb.append("incoming:").append(sc.incomingSprint).append(',').append(sc.incomingAmp).append('\n');
        for (int t = 0; t < sc.numTicks; t++) {
            sb.append("t").append(t).append(':')
                    .append(sc.jumpAt(t) ? 1 : 0)
                    .append(sc.strafeAt(t) ? 1 : 0)
                    .append(sc.sprintAt(t) ? 1 : 0)
                    .append(sc.yawLockedPerTick != null && t < sc.yawLockedPerTick.length && sc.yawLockedPerTick[t] ? 1 : 0)
                    .append(',').append(sc.speedAmplifierAt(t))
                    .append(',').append(bits(sc.slipAt(t)))
                    .append(',').append(Float.floatToIntBits(sc.forwardAt(t)))
                    .append(',').append(Float.floatToIntBits(sc.strafeInputAt(t)))
                    .append('\n');
        }
        List<String> cons = new ArrayList<String>(spec.constraints.size());
        for (JumpConstraint c : spec.constraints) {
            cons.add("c:" + c.mode.name() + ":" + c.t1 + ":" + c.t2 + ":" + c.op.name() + ":"
                    + c.cmp.name() + ":" + bits(c.rhs));
        }
        Collections.sort(cons);
        for (String line : cons) {
            sb.append(line).append('\n');
        }
        return sha256Hex16(sb.toString());
    }

    private static long bits(double v) {
        return Double.doubleToLongBits(v);
    }

    private static String sha256Hex16(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "sha256-unavailable";
        }
    }

    public static String toJsonLine(SolveRunRecord record) {
        return new Gson().toJson(record);
    }

    public static SolveRunRecord parse(String line) {
        try {
            return new Gson().fromJson(line, SolveRunRecord.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
