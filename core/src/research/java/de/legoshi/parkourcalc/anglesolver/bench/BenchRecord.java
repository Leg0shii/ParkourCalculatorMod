package de.legoshi.parkourcalc.anglesolver.bench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BenchRecord {

    public static final int SCHEMA = 1;

    public static final class Jump {
        public String suite;
        public String key;
        public String id;
        public Integer warp;
        public String name;
        public String difficulty;
        public String mcVersion;
    }

    public static final class Strength {
        public String tier;
        public int budgetMs;
    }

    public static final class Problem {
        public String hash;
        public int startTick;
        public int landingTick;
        public int numTicks;
        public int jumps;
        public int constraints;
        public String axis;
        public String sense;
        public boolean freeStart;
    }

    public static final class Result {
        public boolean lands;
        public String verdict;
        public Double objective;
        public Double offset;
        public String yawHash;
        public String solver;
        public Map<String, String> details = new LinkedHashMap<String, String>();
    }

    public static final class Time {
        public long wallMs;
        public double workMs;
        public boolean overran;
        public Map<String, Long> work = new LinkedHashMap<String, Long>();
    }

    public static final class Step {
        public String id;
        public String taken;
        public double workMs;
        public Double objective;
        public boolean feasible;
    }

    public static final class Turn {
        public double[] start;
        public Float startYaw;
        public double[] yaws;
    }

    public static final class Smoothness {
        public Double travelDeg;
        public Integer reversals;
        public Double maxStepDeg;
        public Double jerkDeg;
    }

    public static final class Stratfinder {
        public Integer stratsFound;
        public Integer hpkStratsMatched;
        public Integer hpkStratsTotal;
        public Double solveSeconds;
    }

    public int schema = SCHEMA;
    public String kind = "solve";
    public String build;
    public String variant;
    public String graphHash;
    public String graphEdits;
    public Jump jump;
    public String category;
    public String mode;
    public Strength strength;
    public long seed;
    public boolean repeatable;
    public Problem problem;
    public Result result;
    public Time time;
    public List<Step> steps = new ArrayList<Step>();
    public Turn turn;
    public Smoothness smoothness;
    public Stratfinder stratfinder;
    public String machine;
    public String error;

    public String problemKey() {
        return jump.suite + "|" + jump.key + "|" + category + "|" + mode;
    }

    public String configKey() {
        return variant + "|" + strength.tier + "|" + strength.budgetMs;
    }

    public String runKey() {
        return problemKey() + "|" + configKey() + "|" + seed;
    }

    public String identity() {
        return result == null ? "none" : result.yawHash + "|" + result.objective + "|" + (time == null ? "" : time.work.toString());
    }
}
