package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class ColdCertifyProfileScreen {

    @Test
    public void profile() throws Exception {
        String path = System.getenv("PKC_COLD_PROF_FILE");
        String sig = System.getenv("PKC_COLD_PROF_SIG");
        Assume.assumeTrue("set PKC_COLD_PROF_FILE and PKC_COLD_PROF_SIG",
                path != null && !path.isEmpty() && sig != null && !sig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdSearch.Config cfg = new ColdSearch.Config();

        for (int rep = 0; rep < 3; rep++) {
            ColdSearch.profReset();
            long t0 = System.nanoTime();
            ColdResult r = ColdSearch.certifyLine(file, sig, cfg);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            System.out.printf(Locale.ROOT,
                    "rep=%d solved=%b viol=%.3e ms=%d entryEvals=%d probeSolves=%d feasEntries=%d "
                            + "buildSpecMs=%d probeSolveMs=%d closedFormMs=%d slpMs=%d%n",
                    rep, r != null && r.solved(), r == null ? Double.NaN : r.maxViolation, ms,
                    ColdSearch.profEntryEvals, ColdSearch.profProbeSolves, ColdSearch.profFeasEntries,
                    ColdSearch.profNsBuildSpec / 1_000_000L, ColdSearch.profNsProbeSolve / 1_000_000L,
                    ColdSearch.profNsClosedForm / 1_000_000L, ColdSearch.profNsSlp / 1_000_000L);
        }
    }
}
