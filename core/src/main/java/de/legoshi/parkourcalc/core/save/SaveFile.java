package de.legoshi.parkourcalc.core.save;

import java.util.ArrayList;
import java.util.List;

public final class SaveFile {

    public static final int FORMAT_VERSION = 1;

    public int version;
    public String createdAt;
    public String modVersion;
    public String mcVersion;
    public World world;
    public Start start;
    public List<Row> rows = new ArrayList<Row>();
    public AngleSolver angleSolver;
    public List<DebugTick> debug;                    // null unless "save debug values" was on at save time

    public static final class World {
        public String dimension;
        public String worldName;
        public String serverAddress;
    }

    public static final class Start {
        public double[] pos;
        public double[] vel;
        public float yaw;
    }

    public static final class Row {
        public List<String> keys = new ArrayList<String>();
        public Float yaw;
        public boolean yawLocked;
        public int speedAmplifier;
        public int jumpBoostAmplifier;
    }

    /** Angle Solver problem: defaults, per-tick constraints/overrides (0-based ticks), and last solve result. */
    public static final class AngleSolver {
        public int startTick;
        public int landingTick;
        public String axis;                              // Axis enum name
        public String goal;                              // Goal enum name
        public String effort;                            // Effort enum name (absent in old files -> BALANCED)
        public String defaultInputs;                     // InputMode enum name
        public String defaultSlipperiness;               // Slipperiness enum name
        public List<Dose> defaultPotions = new ArrayList<Dose>();
        public List<Tick> ticks = new ArrayList<Tick>();
        public List<BlockSel> selectedBlocks = new ArrayList<BlockSel>(); // absent in old files -> empty
        public Start seed;                               // launch state (pos/vel/yaw) at startTick; what a solve begins from
        public Result result;                            // null = no solve yet
    }

    /** A picked start / collision / land block: its role, integer coords, and captured world-space hitbox. */
    public static final class BlockSel {
        public String kind;                              // BlockSelection.Kind enum name
        public int x;
        public int y;
        public int z;
        public double[] box;                             // [minX,minY,minZ,maxX,maxY,maxZ]
    }

    public static final class Tick {
        public int tick;                                 // 0-based index into the route
        public List<Constraint> constraints = new ArrayList<Constraint>();
        public Override override;                        // null = no state override
    }

    public static final class Constraint {
        public boolean range;                            // true = range (IN), false = scalar
        public String field;                             // Field enum name
        public String op;                                // Op enum name (scalar only)
        public double value;                             // scalar bound
        public double lo;
        public double hi;
        public boolean loInclusive;
        public boolean hiInclusive;
        public boolean disabled;                         // gh-118; absent in old saves = enabled
    }

    public static final class Override {
        public String inputs;                            // InputMode enum name, null = inherit
        public String slipperiness;                      // Slipperiness enum name, null = inherit
        public List<Dose> added = new ArrayList<Dose>();
        public List<String> removed = new ArrayList<String>(); // Potion enum names
    }

    public static final class Dose {
        public String potion;                            // Potion enum name
        public int level;
    }

    public static final class Result {
        public boolean success;
        public int met;
        public int total;
        public int startTick;                            // 1-based for display
        public int landingTick;                          // 1-based for display
        public long durationMs;
        public long durationNanos;                       // precise solve compute time; 0 = legacy save
        public String finishedAt;                        // formatted clock time, null if unset
        public double objectiveValue;
        public boolean hasObjective;
        public List<Outcome> outcomes = new ArrayList<Outcome>();
        public List<Yaw> yaws = new ArrayList<Yaw>();
    }

    public static final class Outcome {
        public String field;
        public String tick;
        public String relation;
        public String found;
        public String margin;
    }

    public static final class Yaw {
        public int tick;                                 // 1-based for display
        public double yaw;
    }

    /** Optional full per-tick dump, written ONLY when "save debug values" is on (absent otherwise). Purely for
     *  inspection; the solver does not use it (it reads {@code rows[].onGround} and {@code angleSolver.seed}). */
    public static final class DebugTick {
        public double[] pos;                             // [x,y,z]
        public double[] vel;                             // post-tick motion (per-axis collision-clamped)
        public float yaw;
        public boolean onGround;
        public boolean sneaking;
        public boolean sprinting;
        public boolean wallCollision;
        public boolean softCollision;
        public Double collisionAngle;                    // degrees; null = not modelled / NaN
        public Float moveForward;                        // moveFlying inputs the tick ran with; null = unsampled
        public Float moveStrafe;
    }
}
