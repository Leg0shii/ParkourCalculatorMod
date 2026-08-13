package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

public final class StratProblem {

    public static final double HALF_WIDTH = 0.6f / 2f;

    private static final Gson COPY_GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    public StratProblem copy() {
        return COPY_GSON.fromJson(COPY_GSON.toJson(this), StratProblem.class);
    }

    public static final class Area {
        public double xLo;
        public double xHi;
        public double yLo;
        public double yHi;
        public double zLo;
        public double zHi;
        public String label;
        public String slipperiness = "DEFAULT";

        public Area() {
        }

        public Area(double xLo, double xHi, double yLo, double yHi, double zLo, double zHi, String label) {
            this.xLo = xLo;
            this.xHi = xHi;
            this.yLo = yLo;
            this.yHi = yHi;
            this.zLo = zLo;
            this.zHi = zHi;
            this.label = label;
        }

        public static Area block(int x, int y, int z) {
            return new Area(x, x + 1, y, y + 1, z, z + 1, x + "," + y + "," + z);
        }

        public double top() {
            return yHi;
        }

        public double centerX() {
            return 0.5 * (xLo + xHi);
        }

        public double centerZ() {
            return 0.5 * (zLo + zHi);
        }
    }

    public static final class Segment {
        public int groundLo = 1;
        public int groundHi = 5;
        public List<Area> landings = new ArrayList<Area>();
        public boolean ja;
        public int[] alphabet;
        public int maxChanges = 2;
        public double ceilingY = Double.NaN;
        public int airTicks;
        public double[] arcRel;
        public int refFire = -1;
        public int refLand = -1;
    }

    public static final class Wall {
        public int tick;
        public String field;
        public String op;
        public double value;

        public Wall() {
        }

        public Wall(int tick, String field, String op, double value) {
            this.tick = tick;
            this.field = field;
            this.op = op;
            this.value = value;
        }
    }

    public Area start;
    public List<Segment> segments = new ArrayList<Segment>();
    public List<Area> collisions = new ArrayList<Area>();
    public List<Wall> userWalls = new ArrayList<Wall>();
    public String mcVersion;
}
