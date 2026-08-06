package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.LiveTrajectory;
import de.legoshi.parkourcalc.core.render.ConstraintBoxSource;
import de.legoshi.parkourcalc.core.render.ConstraintPlate;
import de.legoshi.parkourcalc.core.render.ConstraintShapes;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;

import java.util.Collections;
import java.util.List;

public final class LiveBestPathSource implements ConstraintBoxSource {

    private static final long ADOPT_INTERVAL_NANOS = 150_000_000L;
    private static final double MARKER_SIZE = 0.12;
    private static final int[] NO_CONSTRAINTS = new int[0];

    private final AngleSolverEngine engine;
    private final BoxController boxes;
    private final AngleSolverConstraintSource.ActiveGate active;

    private LiveTrajectory shown;
    private long lastAdoptNanos;

    public LiveBestPathSource(AngleSolverEngine engine, BoxController boxes,
                              AngleSolverConstraintSource.ActiveGate active) {
        this.engine = engine;
        this.boxes = boxes;
        this.active = active;
    }

    private LiveTrajectory current() {
        LiveTrajectory t = active.isActive() ? engine.liveTrajectory() : null;
        if (t == shown) return shown;
        long now = System.nanoTime();
        if (t == null || shown == null || now - lastAdoptNanos >= ADOPT_INTERVAL_NANOS) {
            shown = t;
            lastAdoptNanos = now;
        }
        return shown;
    }

    @Override
    public List<ConstraintPlate> platesAt(int tickIndex) {
        LiveTrajectory t = shown;
        if (t == null) return Collections.emptyList();
        int k = tickIndex - t.startTick;
        if (k < 0 || k >= t.pointCount()) return Collections.emptyList();
        Vec3dCore foot = boxes.getPosition(tickIndex);
        if (foot == null) return Collections.emptyList();
        AABB marker = AABB.ofCenteredXZ(new Vec3dCore(t.posX[k], foot.y, t.posZ[k]), MARKER_SIZE);
        return Collections.singletonList(new ConstraintPlate(ConstraintShapes.Sense.INCLUDE, t.feasible,
                Collections.singletonList(marker), Collections.<AABB>emptyList(), tickIndex, NO_CONSTRAINTS, false));
    }

    @Override
    public long revision() {
        LiveTrajectory t = current();
        return t == null ? 0L : t.seq;
    }
}
