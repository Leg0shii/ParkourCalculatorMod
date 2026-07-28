package de.legoshi.parkourcalc.anglesolver.metriclab;

public interface ScoringMetric {

    String name();

    double score(JumpMeasurements m);
}
