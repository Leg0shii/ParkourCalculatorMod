package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
    private final Consumer<String> hudMessage;

    public ConstraintKeyController(MinecraftAccess mc, AngleSolverState state, SelectionManager selection,
                                   ConstraintSelection constraintSelection, Runnable onChanged,
                                   boolean modernCollision, IntSupplier rowCount, Settings settings,
                                   Consumer<String> hudMessage) {
        this.mc = mc;
        this.state = state;
        this.selection = selection;
        this.constraintSelection = constraintSelection;
        this.onChanged = onChanged;
        this.modernCollision = modernCollision;
        this.rowCount = rowCount;
        this.settings = settings;
        this.hudMessage = hudMessage;
    }

    public void onKey(boolean enter, boolean remove) {
        if (!mc.isReady()) return;
        BlockHit look = mc.getLookedAtHit();
        if (look == null || look.face == null) return;
        Face face = look.face;
        Vec3dCore hit = look.hitVec;
        int tick = selectedTick();
        if (tick < 0) return;
        int bx = look.x, by = look.y, bz = look.z;
        AABB cobweb = mc.getCobwebContactBox(bx, by, bz);
        if (cobweb != null && !ConstraintDeriver.isSide(face)) return;

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

        boolean honey = mc.isHoneyBlock(bx, by, bz);
        if (remove && mc.isClimbable(bx, by, bz)) {
            List<AABB> obstacles = obstacles(bx - 1, by, bz - 1, bx + 1, by + 1, bz + 1, by);
            double[] r = ConstraintDeriver.deriveCell(bx, bz, by, bx + 0.5, bz + 0.5, obstacles);
            state.setFootprint(tick, r[0], r[1], r[2], r[3]);
            onChanged.run();
            return;
        }
        if (remove && face == Face.POS_Y && (mc.isSlimeBlock(bx, by, bz) || mc.isIce(bx, by, bz))) {
            if (hit == null) return;
            List<AABB> obstacles = obstacles(bx - 1, by + 1, bz - 1, bx + 1, by + 2, bz + 1, by + 1.0);
            double[] r = ConstraintDeriver.deriveCell(bx, bz, by + 1.0, hit.x, hit.z, obstacles);
            state.setFootprint(tick, r[0], r[1], r[2], r[3]);
            onChanged.run();
            return;
        }

        if (face == Face.POS_Y) {
            if (remove) {
                state.clearFootprint(tick);
            } else {
                if (hit == null) return;
                AABB support = supportBox(bx, by, bz, hit);
                List<AABB> obstacles = obstacles(bx - 1, by, bz - 1, bx + 1, by + 2, bz + 1, support.max.y);
                double[] r = ConstraintDeriver.deriveFootprint(support, hit.x, hit.z, obstacles, modernCollision,
                        mc.getPlayerYaw());
                if (merge) {
                    double[] merged = mergedFootprint(tick, r, support.max.y, hit);
                    state.setFootprint(tick, merged[0], merged[1], merged[2], merged[3]);
                } else {
                    state.setFootprint(tick, r[0], r[1], r[2], r[3]);
                }
            }
        } else if (ConstraintDeriver.isSide(face)) {
            if (remove && !honey) {
                state.clearWall(tick, ConstraintDeriver.axisOf(face), ConstraintDeriver.wallIsLowerBound(face, enter));
            } else {
                if (hit == null) return;
                List<AABB> boxes;
                if (cobweb != null) {
                    boxes = Collections.singletonList(cobweb);
                } else if (honey && !remove) {
                    boxes = Collections.singletonList(fullCube(bx, by, bz));
                } else {
                    boxes = mc.getBlockCollisionBoxes(bx, by, bz);
                }
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

    public int countSelected() {
        return selectedConstraints().size();
    }

    public void removeSelected() {
        List<int[]> toDelete = selectedConstraints();
        if (toDelete.isEmpty()) return;
        toDelete.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(b[1], a[1]));
        for (int[] ti : toDelete) state.deleteConstraint(ti[0], ti[1]);
        constraintSelection.clear();
        onChanged.run();
    }

    private List<int[]> selectedConstraints() {
        List<int[]> selected = new ArrayList<>();
        for (int tick : state.populatedTicks()) {
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            List<Constraint> list = tc.getConstraints();
            for (int i = 0; i < list.size(); i++) {
                if (constraintSelection.highlights(tick, i, selection)) selected.add(new int[] {tick, i});
            }
        }
        return selected;
    }

    private double[] mergedFootprint(int tick, double[] fresh, double footY, Vec3dCore hit) {
        double[] existing = state.footprintOrNull(tick);
        if (existing == null) return fresh;
        double[] union = {
                Math.min(fresh[0], existing[0]), Math.max(fresh[1], existing[1]),
                Math.min(fresh[2], existing[2]), Math.max(fresh[3], existing[3])};
        double h = ConstraintDeriver.HALF;
        int by = (int) Math.floor(footY);
        List<AABB> obstacles = obstacles(
                (int) Math.floor(union[0] - h), by, (int) Math.floor(union[2] - h),
                (int) Math.ceil(union[1] + h), by + 2, (int) Math.ceil(union[3] + h), footY);
        double[] clipped = ConstraintDeriver.clipMergedFootprint(union, existing, footY, hit.x, hit.z, obstacles);
        if (clipped != null) return clipped;
        if (hudMessage != null) {
            hudMessage.accept("Merged footprint at T" + (tick + 1) + " spans a block; kept the plain union");
        }
        return union;
    }

    private List<AABB> obstacles(int x0, int y0, int z0, int x1, int y1, int z1, double footY) {
        List<AABB> out = new ArrayList<>(mc.getCollisionBoxes(x0, y0, z0, x1, y1, z1));
        double slideTop = footY + ConstraintDeriver.HONEY_HITBOX_INSET + 1.0e-7;
        for (int y = y0; y <= y1; y++) {
            if (y + 1.0 <= slideTop) continue;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (mc.isHoneyBlock(x, y, z)) out.add(fullCube(x, y, z));
                }
            }
        }
        return out;
    }

    private static AABB fullCube(int x, int y, int z) {
        return new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1.0, y + 1.0, z + 1.0));
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
