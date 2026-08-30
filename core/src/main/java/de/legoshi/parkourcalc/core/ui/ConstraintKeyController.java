package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

public final class ConstraintKeyController {

    private final MinecraftAccess mc;
    private final AngleSolverState state;
    private final SelectionManager selection;
    private final ConstraintSelection constraintSelection;
    private final Runnable onChanged;
    private final boolean modernCollision;
    private final IntSupplier rowCount;
    private final Settings settings;

    public ConstraintKeyController(MinecraftAccess mc, AngleSolverState state, SelectionManager selection,
                                   ConstraintSelection constraintSelection, Runnable onChanged,
                                   boolean modernCollision, IntSupplier rowCount, Settings settings) {
        this.mc = mc;
        this.state = state;
        this.selection = selection;
        this.constraintSelection = constraintSelection;
        this.onChanged = onChanged;
        this.modernCollision = modernCollision;
        this.rowCount = rowCount;
        this.settings = settings;
    }

    public void onKey(boolean enter, boolean remove) {
        if (!mc.isReady()) return;
        Face face = mc.getLookedAtFace();
        if (face == null) return;
        int[] block = mc.getLookedAtBlock();
        if (block == null || block.length < 3) return;
        int tick = selectedTick();
        if (tick < 0) return;
        int bx = block[0], by = block[1], bz = block[2];

        boolean merge = mc.isAltDown();
        if (merge) remove = false;

        if (remove) {
            double[] plate = mc.getPressurePlateFootprint(bx, by, bz);
            if (plate != null) {
                double[] r = pressurePlateFootprint(bx, bz, plate);
                state.setFootprint(tick, r[0], r[1], r[2], r[3]);
                onChanged.run();
                return;
            }
        }

        if (remove && mc.isClimbable(bx, by, bz)) {
            List<AABB> obstacles = mc.getCollisionBoxes(bx - 1, by, bz - 1, bx + 1, by + 1, bz + 1);
            double[] r = ConstraintDeriver.deriveCell(bx, bz, by, bx + 0.5, bz + 0.5, obstacles);
            state.setFootprint(tick, r[0], r[1], r[2], r[3]);
            onChanged.run();
            return;
        }
        if (remove && face == Face.POS_Y && (mc.isSlimeBlock(bx, by, bz) || mc.isIce(bx, by, bz))) {
            Vec3dCore hit = mc.getLookedAtHitVec();
            if (hit == null) return;
            List<AABB> obstacles = mc.getCollisionBoxes(bx - 1, by + 1, bz - 1, bx + 1, by + 2, bz + 1);
            double[] r = ConstraintDeriver.deriveCell(bx, bz, by + 1.0, hit.x, hit.z, obstacles);
            state.setFootprint(tick, r[0], r[1], r[2], r[3]);
            onChanged.run();
            return;
        }

        if (face == Face.POS_Y) {
            if (remove) {
                state.clearFootprint(tick);
            } else {
                Vec3dCore hit = mc.getLookedAtHitVec();
                if (hit == null) return;
                AABB support = supportBox(bx, by, bz, hit);
                List<AABB> obstacles = mc.getCollisionBoxes(bx - 1, by, bz - 1, bx + 1, by + 2, bz + 1);
                double[] r = ConstraintDeriver.deriveFootprint(support, hit.x, hit.z, obstacles, modernCollision,
                        mc.getPlayerYaw());
                if (merge) {
                    state.mergeFootprint(tick, r[0], r[1], r[2], r[3]);
                } else {
                    state.setFootprint(tick, r[0], r[1], r[2], r[3]);
                }
            }
        } else if (ConstraintDeriver.isSide(face)) {
            if (remove) {
                state.clearWall(tick, ConstraintDeriver.axisOf(face), ConstraintDeriver.wallIsLowerBound(face, enter));
            } else {
                Vec3dCore hit = mc.getLookedAtHitVec();
                if (hit == null) return;
                List<AABB> boxes = mc.getBlockCollisionBoxes(bx, by, bz);
                Constraint wall = ConstraintDeriver.deriveWall(face, boxes, hit, enter);
                if (merge) {
                    state.mergeWall(tick, wall);
                } else {
                    state.putScalarReplacingDirection(tick, wall);
                }
            }
        } else {
            return;
        }
        onChanged.run();
    }

    public void removeSelected() {
        List<int[]> toDelete = new ArrayList<>();
        for (int tick : state.populatedTicks()) {
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            List<Constraint> list = tc.getConstraints();
            for (int i = 0; i < list.size(); i++) {
                if (constraintSelection.highlights(tick, i, selection)) toDelete.add(new int[] {tick, i});
            }
        }
        if (toDelete.isEmpty()) return;
        toDelete.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(b[1], a[1]));
        for (int[] ti : toDelete) state.deleteConstraint(ti[0], ti[1]);
        constraintSelection.clear();
        onChanged.run();
    }

    private AABB supportBox(int bx, int by, int bz, Vec3dCore hit) {
        List<AABB> boxes = mc.getBlockCollisionBoxes(bx, by, bz);
        AABB best = null;
        double bestGap = Double.POSITIVE_INFINITY;
        for (AABB b : boxes) {
            double gap = Math.abs(b.max.y - hit.y);
            if (gap < bestGap) {
                bestGap = gap;
                best = b;
            }
        }
        if (best != null) return ConstraintDeriver.mergeCoplanarSupport(best, boxes);
        return new AABB(new Vec3dCore(bx, by, bz), new Vec3dCore(bx + 1.0, by + 1.0, bz + 1.0));
    }

    private double[] pressurePlateFootprint(int bx, int bz, double[] interaction) {
        double h = ConstraintDeriver.HALF;
        if (settings != null && settings.pressurePlateFullBlock) {
            return new double[] {bx - h, bx + 1.0 + h, bz - h, bz + 1.0 + h};
        }
        return new double[] {interaction[0] - h, interaction[1] + h, interaction[2] - h, interaction[3] + h};
    }

    private int selectedTick() {
        Set<Integer> rows = selection.getSelectedRows();
        int tick = rows.isEmpty() ? state.getLandingTick() : rows.iterator().next();
        return Math.min(tick, rowCount.getAsInt() - 1);
    }
}
