package de.legoshi.parkourcalc.anglesolver.metriclab;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.Iterator;

public final class Variant45 {

    private static final Gson GSON = new Gson();

    private Variant45() {
    }

    public static SaveFile build(SaveFile human) {
        SaveFile copy = GSON.fromJson(GSON.toJson(human), SaveFile.class);
        copy.angleSolver.defaultInputs = "FORCE_45";
        copy.angleSolver.result = null;
        if (copy.angleSolver.ticks != null) {
            for (SaveFile.Tick tick : copy.angleSolver.ticks) {
                if (tick == null) {
                    continue;
                }
                if (tick.constraints != null) {
                    Iterator<SaveFile.Constraint> it = tick.constraints.iterator();
                    while (it.hasNext()) {
                        SaveFile.Constraint c = it.next();
                        if (c == null || "F".equals(c.field) || "DF".equals(c.field)) {
                            it.remove();
                        }
                    }
                }
                if (tick.override != null) {
                    tick.override.inputs = null;
                }
            }
        }
        return copy;
    }

    public static void attachResult(SaveFile file, SolveResult result) {
        SaveFile.Result out = new SaveFile.Result();
        out.success = result.isSuccess();
        out.met = result.getMet();
        out.total = result.getTotal();
        out.startTick = file.angleSolver.startTick + 1;
        out.landingTick = file.angleSolver.landingTick + 1;
        out.objectiveValue = result.hasObjective() ? result.getObjectiveValue() : 0.0;
        out.hasObjective = result.hasObjective();
        out.yaws = new ArrayList<SaveFile.Yaw>();
        for (SolveResult.YawEntry y : result.getYaws()) {
            SaveFile.Yaw sy = new SaveFile.Yaw();
            sy.tick = y.tick;
            sy.yaw = y.yaw;
            out.yaws.add(sy);
        }
        file.angleSolver.result = out;
    }
}
