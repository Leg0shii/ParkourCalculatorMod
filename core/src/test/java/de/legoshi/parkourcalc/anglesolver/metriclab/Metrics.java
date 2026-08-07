package de.legoshi.parkourcalc.anglesolver.metriclab;

import java.util.ArrayList;
import java.util.List;

public final class Metrics {

    public static final double SIG_ANGLE_DEG = 360.0 / 65536.0;

    private Metrics() {
    }

    public static List<ScoringMetric> all() {
        List<ScoringMetric> out = new ArrayList<ScoringMetric>();
        out.add(toleranceOnly());
        out.add(countsOnly());
        out.add(tolPlusCounts());
        out.add(tolMomEdges());
        out.add(tightTicks());
        out.add(combinedV1());
        out.add(combinedV2());
        out.add(combinedV3());
        out.add(combinedV4());
        return out;
    }

    public static ScoringMetric toleranceOnly() {
        return new ScoringMetric() {
            public String name() {
                return "toleranceOnly";
            }

            public double score(JumpMeasurements m) {
                return toleranceCore(m);
            }
        };
    }

    public static ScoringMetric countsOnly() {
        return new ScoringMetric() {
            public String name() {
                return "countsOnly";
            }

            public double score(JumpMeasurements m) {
                return m.inputEdgesTotal() + 2.0 * m.turnTicksTotal();
            }
        };
    }

    public static ScoringMetric tolPlusCounts() {
        return new ScoringMetric() {
            public String name() {
                return "tolPlusCounts";
            }

            public double score(JumpMeasurements m) {
                return toleranceCore(m) + 0.3 * m.inputEdgesTotal() + 0.06 * m.turnTicksTotal()
                        + (m.jumpAngle ? 1.5 : 0.0);
            }
        };
    }

    public static ScoringMetric tolMomEdges() {
        return new ScoringMetric() {
            public String name() {
                return "tolMomEdges";
            }

            public double score(JumpMeasurements m) {
                return toleranceCore(m) + 0.3 * m.inputEdgesMomentum + 0.06 * m.turnTicksJump
                        + (m.jumpAngle ? 1.5 : 0.0);
            }
        };
    }

    public static ScoringMetric tightTicks() {
        return new ScoringMetric() {
            public String name() {
                return "tightTicks";
            }

            public double score(JumpMeasurements m) {
                int tight = 0;
                for (int k = 0; k < m.windowLo.length; k++) {
                    if (m.windowLo[k] + m.windowHi[k] < 0.25) {
                        tight++;
                    }
                }
                return toleranceCore(m) + 0.05 * tight
                        + 0.03 * (m.inputEdgesTotal() + m.turnTicksTotal())
                        + (m.jumpAngle ? 1.0 : 0.0);
            }
        };
    }

    public static ScoringMetric combinedV1() {
        return new ScoringMetric() {
            public String name() {
                return "combinedV1";
            }

            public double score(JumpMeasurements m) {
                return toleranceCore(m) + 0.3 * m.inputEdgesMomentum + 0.06 * m.turnTicksJump
                        + (m.jumpAngle ? 1.5 : 0.0)
                        + (m.minMargin < 5.0e-5 ? 1.5 : 0.0);
            }
        };
    }

    public static ScoringMetric combinedV2() {
        return new ScoringMetric() {
            public String name() {
                return "combinedV2";
            }

            public double score(JumpMeasurements m) {
                return toleranceCore(m)
                        + 0.5 * momentumShiftDemandSum(m)
                        + 0.5 * shiftDemand(m.shiftGeoMomentumTicks)
                        + 0.06 * m.turnTicksJump
                        + (m.jumpAngle ? 1.5 : 0.0)
                        + (m.minMargin < 5.0e-5 ? 1.5 : 0.0);
            }
        };
    }

    public static ScoringMetric combinedV3() {
        return new ScoringMetric() {
            public String name() {
                return "combinedV3";
            }

            public double score(JumpMeasurements m) {
                return toleranceCore(m)
                        + 0.8 * effShiftDemandSum(m)
                        + 0.06 * m.turnTicksJump
                        + (m.jumpAngle ? 1.5 : 0.0)
                        + (m.minMargin < 5.0e-5 ? 1.5 : 0.0);
            }
        };
    }

    public static ScoringMetric combinedV4() {
        return new ScoringMetric() {
            public String name() {
                return "combinedV4";
            }

            public double score(JumpMeasurements m) {
                return toleranceCore(m)
                        + 0.8 * effShiftDemandSum(m)
                        + 0.06 * m.turnTicksJump
                        + (m.jumpAngle ? 1.5 : 0.0)
                        + (m.minMargin < 5.0e-5 ? 1.5 : 0.0)
                        + 0.15 * Math.log1p(m.smoothJerkDeg);
            }
        };
    }

    private static double effShiftDemandSum(JumpMeasurements m) {
        if (m.shiftEdgeRow == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftLoFree[i] || m.shiftHiFree[i]) {
                continue;
            }
            sum += shiftDemand(m.shiftLo[i] + m.shiftHi[i]);
        }
        return sum;
    }

    private static double momentumShiftDemandSum(JumpMeasurements m) {
        if (m.shiftEdgeRow == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftEdgeMomentum[i]) {
                sum += shiftDemand(m.shiftLo[i] + m.shiftHi[i]);
            }
        }
        return sum;
    }

    private static double shiftDemand(double widthTicks) {
        if (Double.isNaN(widthTicks)) {
            return 0.0;
        }
        double full = 2.0 * MeasurementEngine.SHIFT_CAP_TICKS + 1.0;
        return Math.log10(full / (1.0 + widthTicks));
    }

    private static double toleranceCore(JumpMeasurements m) {
        double jitter = Math.max(m.jitterDeg, SIG_ANGLE_DEG);
        double geo = Math.max(m.winGeoDeg, SIG_ANGLE_DEG);
        return -Math.log10(jitter) - 0.5 * Math.log10(geo);
    }
}
