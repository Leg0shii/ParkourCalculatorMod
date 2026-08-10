package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Test;

import java.util.Locale;

public class ColdSlpDecisiveScreen {

    private static final String[][] PINS = {
            {"hpk_human/d11/j925-Sidewalled_Single_Piston_Butterfly_Neo_1bl", "0.0.2.3+3+3+3+3+3+3+3+3+3+1+"},
            {"hpk_human/d11/j1150-2x2bm_Nix_Neo",
                    "0.0.7.7.7.7.7.7.7.7.7.7.7.7.7.7.3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+1+"},
            {"hpk_human/d2/j012_1bm_4.25b", "1.1+1+1+1+1+1+1+1+1+1+1+1+"},
            {"hpk_human/d1/j264_1bm_Cross_Neo", "1+1+"},
            {"hpk_human/d1/j014_1bm_Double_Neo", "3+1+1+1+1+1+"},
            {"hpk_human/d2/j276_1bm_Cross_Neup", "1+1+1+1+1+1+"},
            {"hpk_human/d12/j154_1bm_Head_Butterfly_Neo",
                    "4.8.8.8.8.8.8.8.8.8.8.8.8.8.6.2+4.2+2+2+2+2+2+2+2+2+2+2+1+"},
    };

    @Test
    public void slpDecisive() {
        double globalMax = 0.0;
        int globalNull = 0;
        int globalDescent = 0;
        double globalDescentMiss = 0.0;
        for (String[] pin : PINS) {
            SaveFile file = new Gson().fromJson(Fixtures.rawPool(pin[0]), SaveFile.class);
            ColdSearch.profReset();
            ColdResult r = ColdSearch.certifyLine(file, pin[1], new ColdSearch.Config());
            globalMax = Math.max(globalMax, ColdSearch.profSlpDecisiveMaxViol);
            globalNull += ColdSearch.profSlpDecisiveNull;
            globalDescent += ColdSearch.profDescentDecisive;
            globalDescentMiss = Math.max(globalDescentMiss, ColdSearch.profDescentDecisiveMaxMiss);
            System.out.printf(Locale.ROOT,
                    "%-58s solved=%b probeSolves=%d slpDecisive=%d(null=%d,maxViol=%.3e) "
                            + "descentDecisive=%d(maxMiss=%.3e)%n",
                    pin[0].substring(pin[0].lastIndexOf('/') + 1), r != null && r.solved(),
                    ColdSearch.profProbeSolves, ColdSearch.profSlpDecisiveCount, ColdSearch.profSlpDecisiveNull,
                    ColdSearch.profSlpDecisiveMaxViol, ColdSearch.profDescentDecisive,
                    ColdSearch.profDescentDecisiveMaxMiss);
        }
        System.out.printf(Locale.ROOT,
                "GLOBAL slpDecisiveMaxViol=%.3e slpDecisiveNull=%d descentDecisive=%d descentMaxMiss=%.3e%n",
                globalMax, globalNull, globalDescent, globalDescentMiss);
    }
}
