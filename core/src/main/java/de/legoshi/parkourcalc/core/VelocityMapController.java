package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.velocity.LandingPad;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.SimulationRunner;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxController;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.anglesolver.VelocityMapWidget;

import java.util.List;
import java.util.function.IntConsumer;

public final class VelocityMapController {

    private final AngleSolverState angleSolverState;
    private final BoxController boxController;
    private final SimulationRunner runner;
    private final SaveController saveController;
    private final InputData inputData;
    private final ExactJumpModel forwardModel;
    private final IntConsumer onUserChange;
    private final VelocityMapWidget widget;
    private String velocitySnapshotJson;

    public VelocityMapController(AngleSolverState angleSolverState, BoxController boxController,
                                 SimulationRunner runner, SaveController saveController, InputData inputData,
                                 ExactJumpModel forwardModel, IntConsumer onUserChange, int threads) {
        this.angleSolverState = angleSolverState;
        this.boxController = boxController;
        this.runner = runner;
        this.saveController = saveController;
        this.inputData = inputData;
        this.forwardModel = forwardModel;
        this.onUserChange = onUserChange;
        this.widget = new VelocityMapWidget(
                this::buildVelocityFinder, this::velocityGrid,
                this::applyVelocityCandidate, this::currentEntryVelocity,
                saveController::isTempActive, saveController::restoreInitialTrajectory,
                saveController::clearTempTrajectory, this::saveVelocityCopyAs,
                threads);
    }

    public VelocityMapWidget widget() {
        return widget;
    }

    private VelocityFinder buildVelocityFinder() {
        int st = angleSolverState.getStartTick();
        int lt = angleSolverState.getLandingTick();
        if (forwardModel == null || st < 0 || st >= boxController.size() || lt <= st) return null;
        TickState seed = boxController.getState(st);
        FileSystemSaveStore store = saveController.getSaveStore();
        if (seed == null || store == null) return null;

        final String json = SaveIO.snapshotJson(store, inputData, runner.getStartPosition(),
                runner.getStartVelocity(), runner.getStartYaw(), runner.getStartPitch(), angleSolverState, boxController.getStates());
        this.velocitySnapshotJson = json;
        VelocityFinder.ProblemFactory factory = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveFile f = SaveIO.parseSafe(json);
                if (f != null) SaveIO.applyAngleSolverTo(f, s);
                s.setEffort(angleSolverState.getEffort());
                return s;
            }
            public InputData newInputs() {
                InputData in = new InputData();
                SaveFile f = SaveIO.parseSafe(json);
                if (f != null) SaveIO.applyRowsTo(f, in);
                return in;
            }
        };
        VelocityFinder.Anchor anchor =
                new VelocityFinder.Anchor(st, seed.position, seed.yaw, seed.velocity.y, inputData.size());
        double[] pb = landPadBounds();
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        VelocityFinder vf = new VelocityFinder(factory, forwardModel, anchor, lt, pad, boxController.getStates(), 20_000L);
        vf.setObjectiveConstraint(objectiveLandingConstraint());
        return vf;
    }

    private double objectiveLandingConstraint() {
        TickConstraints tc = angleSolverState.tickConstraintsOrNull(angleSolverState.getLandingTick());
        if (tc == null) return Double.NaN;
        boolean axisX = angleSolverState.getAxis() == AngleSolverState.Axis.X;
        boolean max = angleSolverState.getGoal() == AngleSolverState.Goal.MAX;
        Constraint.Field field = axisX ? Constraint.Field.X : Constraint.Field.Z;
        double v = Double.NaN;
        for (Constraint c : tc.getConstraints()) {
            if (!c.isEnabled() || c.getField() != field) continue;
            v = c.isRange() ? (max ? c.getLo() : c.getHi()) : c.getValue();
        }
        return v;
    }

    private double[] landPadBounds() {
        TickConstraints tc = angleSolverState.tickConstraintsOrNull(angleSolverState.getLandingTick());
        return LandingPad.derive(tc == null ? null : tc.getConstraints(), baseLandBox());
    }

    private double[] baseLandBox() {
        BlockSelection land = angleSolverState.getLandBlock();
        if (land != null) {
            return new double[]{ land.box.min.x, land.box.max.x, land.box.min.z, land.box.max.z };
        }
        int st = angleSolverState.getStartTick();
        int lt = angleSolverState.getLandingTick();
        TickState landState = lt >= 0 && lt < boxController.size() ? boxController.getState(lt) : null;
        TickState seedState = st >= 0 && st < boxController.size() ? boxController.getState(st) : null;
        Vec3dCore lp = landState != null ? landState.position
                : (seedState != null ? seedState.position : Vec3dCore.ZERO);
        return new double[]{ lp.x - 0.5, lp.x + 0.5, lp.z - 0.5, lp.z + 0.5 };
    }

    private VelocityFinder.Grid velocityGrid() {
        double[] v = currentEntryVelocity();
        double cx = v != null ? v[0] : 0.0;
        double cz = v != null ? v[1] : 0.0;
        double radius = 0.25, step = 0.02;
        return new VelocityFinder.Grid(cx - radius, cx + radius, step, cz - radius, cz + radius, step);
    }

    private double[] currentEntryVelocity() {
        int st = angleSolverState.getStartTick();
        if (st < 0 || st >= boxController.size()) return null;
        TickState s = boxController.getState(st);
        return s == null ? null : new double[]{s.velocity.x, s.velocity.z};
    }

    private void applyVelocityCandidate(VelocityFinder.Candidate c) {
        if (c == null || c.yawsGameFacing == null || velocitySnapshotJson == null) return;
        SaveFile snap = SaveIO.parseSafe(velocitySnapshotJson);
        if (snap == null || snap.angleSolver == null || snap.angleSolver.seed == null) return;
        int st = snap.angleSolver.startTick;
        int lt = snap.angleSolver.landingTick;
        if (st < 0 || lt <= st) return;
        SaveFile.Start seed = snap.angleSolver.seed;
        if (seed.pos == null || seed.pos.length < 3 || seed.vel == null || seed.vel.length < 3) return;

        InputData orig = new InputData();
        SaveIO.applyRowsTo(snap, orig);
        List<InputRow> oRows = orig.getRows();
        if (st >= oRows.size()) return;

        saveController.beginTempTrajectory();
        runner.setStartPosition(new Vec3dCore(seed.pos[0], seed.pos[1], seed.pos[2]));
        runner.setStartYaw(seed.yaw);
        runner.setStartVelocity(new Vec3dCore(c.vx, seed.vel[1], c.vz));

        int yn = c.yawsGameFacing.length;
        inputData.clear();
        for (int k = st; k < oRows.size(); k++) {
            InputRow row = oRows.get(k);
            int idx = k - st;
            if (idx < yn) {
                row.setYawLocked(true);
                row.setYaw((float) c.yawsGameFacing[idx]);
                realizeForce45(row, c.force45Mask, c.strafeMask, c.strafeSign, idx);
            }
            inputData.getRows().add(row);
        }
        onUserChange.accept(-1);
    }

    private String saveVelocityCopyAs(String name) {
        Result<String> r = saveController.saveCopyAs(name);
        return r.ok ? "Saved copy: " + r.value : r.error;
    }

    private static void realizeForce45(InputRow row, boolean[] force45Mask, boolean[] strafeMask,
                                       int strafeSign, int idx) {
        if (force45Mask == null || idx >= force45Mask.length || !force45Mask[idx]) return;
        boolean strafeThis = strafeMask != null && idx < strafeMask.length && strafeMask[idx];
        row.applyForce45(strafeThis, strafeSign);
    }
}
