package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.List;

public final class StartDragGate {

    public interface SimProbe {
        void simulateAt(Vec3dCore start);

        Vec3dCore tickPosition(int index);
    }

    private static final int CLAMP_ITERATIONS = 24;

    private final SimProbe probe;
    private final double tolerance;

    private boolean active;
    private Vec3dCore baselineStart;
    private Vec3dCore lastValidStart;
    private List<Integer> indices;
    private List<Vec3dCore> basePositions;

    public StartDragGate(SimProbe probe, double tolerance) {
        this.probe = probe;
        this.tolerance = tolerance;
    }

    public boolean isActive() {
        return active;
    }

    public void begin(Vec3dCore baselineStart, List<Integer> selectedIndices, List<Vec3dCore> baselinePositions) {
        this.baselineStart = baselineStart;
        this.lastValidStart = baselineStart;
        this.indices = selectedIndices;
        this.basePositions = baselinePositions;
        this.active = true;
    }

    public void move(Vec3dCore candidate) {
        boolean valid = simAndCheck(candidate);
        lastValidStart = valid ? candidate : clampToValid(baselineStart, candidate);
    }

    public boolean end() {
        boolean moved = lastValidStart != null && !lastValidStart.equals(baselineStart);
        reset();
        return moved;
    }

    private void reset() {
        active = false;
        baselineStart = null;
        lastValidStart = null;
        indices = null;
        basePositions = null;
    }

    private boolean simAndCheck(Vec3dCore start) {
        probe.simulateAt(start);
        return isRigid(start);
    }

    private boolean isRigid(Vec3dCore candidate) {
        if (indices == null || indices.isEmpty()) return true;
        double dx = candidate.x - baselineStart.x;
        double dy = candidate.y - baselineStart.y;
        double dz = candidate.z - baselineStart.z;
        for (int k = 0; k < indices.size(); k++) {
            Vec3dCore actual = probe.tickPosition(indices.get(k));
            if (actual == null) return false;
            Vec3dCore base = basePositions.get(k);
            if (Math.abs(actual.x - (base.x + dx)) > tolerance) return false;
            if (Math.abs(actual.y - (base.y + dy)) > tolerance) return false;
            if (Math.abs(actual.z - (base.z + dz)) > tolerance) return false;
        }
        return true;
    }

    private Vec3dCore clampToValid(Vec3dCore from, Vec3dCore to) {
        Vec3dCore afterX = clampAxis(from, new Vec3dCore(to.x, from.y, from.z));
        Vec3dCore clamped = clampAxis(afterX, new Vec3dCore(afterX.x, afterX.y, to.z));
        simAndCheck(clamped);
        return clamped;
    }

    private Vec3dCore clampAxis(Vec3dCore from, Vec3dCore to) {
        if (simAndCheck(to)) {
            return to;
        }
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < CLAMP_ITERATIONS; i++) {
            double mid = (lo + hi) * 0.5;
            if (simAndCheck(lerp(from, to, mid))) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lerp(from, to, lo);
    }

    private static Vec3dCore lerp(Vec3dCore a, Vec3dCore b, double t) {
        return new Vec3dCore(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
