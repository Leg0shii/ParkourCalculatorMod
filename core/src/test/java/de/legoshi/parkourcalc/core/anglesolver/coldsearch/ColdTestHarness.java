package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.save.SaveFile;

final class ColdTestHarness {

    private ColdTestHarness() {
    }

    static SaveFile loadSave(String stem) {
        return new Gson().fromJson(Fixtures.rawPool(stem), SaveFile.class);
    }

    static ColdProblem loadProblem(String stem) {
        return ColdProblem.fromSave(loadSave(stem));
    }

    static ColdSearch.Sweep[] buildScan(ColdProblem p, ColdSearch.Config cfg, double stepDeg) {
        int steps = (int) Math.round(360.0 / stepDeg);
        ColdSearch.Sweep[] scan = new ColdSearch.Sweep[steps];
        for (int i = 0; i < steps; i++) {
            scan[i] = new ColdSearch.Sweep(p, cfg, -180.0 + i * stepDeg, 0, null);
        }
        return scan;
    }
}
