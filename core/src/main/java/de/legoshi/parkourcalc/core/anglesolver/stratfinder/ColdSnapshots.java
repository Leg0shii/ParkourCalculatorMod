package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdResult;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.LineSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

public final class ColdSnapshots {

    private static final Gson GSON = new Gson();
    private static final float KEY_SCALE = StratVariants.KEY_INPUT_SCALE;

    private ColdSnapshots() {
    }

    public static SaveFile build(ProblemCompiler.Compiled spec, ColdResult r) {
        if (r == null || !r.solved()) {
            return null;
        }
        KeyLine line = r.line;
        int landingTick = spec.save.angleSolver.landingTick;
        int numTicks = landingTick;
        double[] gf = LineSpec.build(line, r.yaws[0], r.startX, r.startZ)
                .asScenario().toGameFacings(r.yaws);
        boolean[] sprint = line.sprintStates();

        SaveFile out = GSON.fromJson(GSON.toJson(spec.save), SaveFile.class);
        out.start = new SaveFile.Start();
        out.start.pos = new double[]{r.startX, spec.yPerTick[0], r.startZ};
        out.start.vel = new double[]{0.0, 0.0, 0.0};
        out.start.yaw = (float) gf[0];
        out.start.pitch = 0f;
        if (out.angleSolver.seed != null) {
            out.angleSolver.seed.pos = out.start.pos.clone();
            out.angleSolver.seed.vel = new double[]{0.0, 0.0, 0.0};
            out.angleSolver.seed.yaw = (float) gf[0];
        }
        out.angleSolver.defaultInputs = "KEEP";
        out.angleSolver.defaultSprint = "DERIVE";

        List<InputRow> src = line.toRows();
        out.rows = new ArrayList<SaveFile.Row>();
        for (int t = 0; t <= landingTick; t++) {
            SaveFile.Row row = new SaveFile.Row();
            row.keys = new ArrayList<String>();
            if (t < src.size()) {
                InputRow s = src.get(t);
                for (InputRow.Key k : InputRow.Key.values()) {
                    if (s.isKeyActive(k)) {
                        row.keys.add(k.name());
                    }
                }
            }
            if (t < numTicks) {
                row.yaw = (float) gf[t];
                row.yawLocked = true;
            }
            out.rows.add(row);
        }

        SaveFile.Result res = new SaveFile.Result();
        res.success = true;
        res.met = 0;
        res.total = 0;
        res.yaws = new ArrayList<SaveFile.Yaw>();
        for (int k = 0; k < r.yaws.length; k++) {
            SaveFile.Yaw y = new SaveFile.Yaw();
            y.tick = k + 1;
            y.yaw = r.yaws[k];
            res.yaws.add(y);
        }
        out.angleSolver.result = res;

        boolean[] grounded = new boolean[landingTick + 1];
        for (int i = 0; i < spec.fireTicks.length; i++) {
            int from = i == 0 ? 0 : spec.landTicks[i - 1];
            for (int t = from; t <= spec.fireTicks[i]; t++) {
                grounded[t] = true;
            }
            grounded[spec.landTicks[i]] = true;
        }

        out.debug = new ArrayList<SaveFile.DebugTick>();
        for (int t = 0; t <= landingTick; t++) {
            SaveFile.DebugTick d = new SaveFile.DebugTick();
            double x = t == 0 ? r.startX : r.path.posX[t - 1];
            double z = t == 0 ? r.startZ : r.path.posZ[t - 1];
            d.pos = new double[]{x, spec.yPerTick[t], z};
            d.vel = new double[]{0.0, 0.0, 0.0};
            d.yaw = (float) gf[Math.min(Math.max(t - 1, 0), numTicks - 1)];
            d.onGround = grounded[t];
            out.debug.add(d);
        }
        for (int k = 0; k < numTicks; k++) {
            SaveFile.DebugTick d = out.debug.get(k + 1);
            int combo = line.comboAt(k);
            d.moveForward = KEY_SCALE * KeyLine.FORWARD_SIGN[combo];
            d.moveStrafe = KEY_SCALE * KeyLine.STRAFE_SIGN[combo];
            d.sprinting = sprint[k];
        }

        out.angleSolver.effort = "FAST";
        out.angleSolver.stopOnFeasible = Boolean.TRUE;
        return out;
    }
}
