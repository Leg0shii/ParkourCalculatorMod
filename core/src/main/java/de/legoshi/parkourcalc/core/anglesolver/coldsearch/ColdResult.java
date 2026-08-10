package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;

import java.util.Locale;

public final class ColdResult {

    public final KeyLine line;
    public final double facingDeg;
    public final double[] yaws;
    public final double startX;
    public final double startZ;
    public final double maxViolation;
    public final ForwardPath path;
    public final long elapsedMs;
    public final int changesLevel;
    public final long nodesVisited;
    public final int candidatesGenerated;
    public final int candidatesProbed;
    public final int candidatesCertified;
    public final boolean truncated;

    ColdResult(KeyLine line, double facingDeg, double[] yaws, double startX, double startZ,
               double maxViolation, ForwardPath path, long elapsedMs, int changesLevel,
               long nodesVisited, int candidatesGenerated, int candidatesProbed, int candidatesCertified,
               boolean truncated) {
        this.line = line;
        this.facingDeg = facingDeg;
        this.yaws = yaws;
        this.startX = startX;
        this.startZ = startZ;
        this.maxViolation = maxViolation;
        this.path = path;
        this.elapsedMs = elapsedMs;
        this.changesLevel = changesLevel;
        this.nodesVisited = nodesVisited;
        this.candidatesGenerated = candidatesGenerated;
        this.candidatesProbed = candidatesProbed;
        this.candidatesCertified = candidatesCertified;
        this.truncated = truncated;
    }

    public boolean solved() {
        return line != null && maxViolation <= 0.0;
    }

    public String summary() {
        if (!solved()) {
            return String.format(Locale.ROOT,
                    "no line found: level<=%d nodes=%d cands=%d probed=%d certified=%d truncated=%b elapsedMs=%d",
                    changesLevel, nodesVisited, candidatesGenerated, candidatesProbed, candidatesCertified,
                    truncated, elapsedMs);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "solved in %d ms (level=%d nodes=%d cands=%d probed=%d certified=%d)%n",
                elapsedMs, changesLevel, nodesVisited, candidatesGenerated, candidatesProbed, candidatesCertified));
        sb.append(String.format(Locale.ROOT, "sig=%s tail=%s seedFacing=%.4f%n",
                line.signature(), KeyLine.COMBO_LABEL[line.tailCombo], facingDeg));
        sb.append(String.format(Locale.ROOT, "start=(%.17g, %.17g) viol=%.3e%n",
                startX, startZ, maxViolation));
        sb.append("line: ").append(line.describe()).append(String.format(Locale.ROOT, "%n"));
        sb.append("yaws:");
        for (int i = 0; i < yaws.length; i++) {
            sb.append(String.format(Locale.ROOT, " %d:%.17g", line.problem.startTick + i + 1, yaws[i]));
        }
        return sb.toString();
    }
}
