package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class ColdSearchRegressionTest {

    private static SaveFile load(String stem) {
        return new Gson().fromJson(Fixtures.rawPool(stem), SaveFile.class);
    }

    private static void assertLineCertifies(String stem, String sig) {
        SaveFile file = load(stem);
        ColdResult r = ColdSearch.certifyLine(file, sig, new ColdSearch.Config());
        assertNotNull(stem + ": no result", r);
        assertTrue(stem + ": line did not certify: " + r.summary(), r.solved());
        assertEquals(stem + ": violation not byte-exact zero", 0.0, r.maxViolation, 0.0);
    }

    @Test
    public void j925LineCertifiesByteExact() {
        assertLineCertifies("hpk_human/d11/j925-Sidewalled_Single_Piston_Butterfly_Neo_1bl",
                "0.0.2.3+3+3+3+3+3+3+3+3+3+1+");
    }

    @Test
    public void j1150LineCertifiesByteExact() {
        assertLineCertifies("hpk_human/d11/j1150-2x2bm_Nix_Neo",
                "0.0.7.7.7.7.7.7.7.7.7.7.7.7.7.7.3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+3+1+");
    }

    @Test
    public void j012HeldLineCertifiesByteExact() {
        assertLineCertifies("hpk_human/d2/j012_1bm_4.25b",
                "1.1+1+1+1+1+1+1+1+1+1+1+1+");
    }

    @Test
    public void j264HeldLineCertifiesByteExact() {
        assertLineCertifies("hpk_human/d1/j264_1bm_Cross_Neo", "1+1+");
    }

    @Test
    public void j014HeldLineCertifiesByteExact() {
        assertLineCertifies("hpk_human/d1/j014_1bm_Double_Neo", "3+1+1+1+1+1+");
    }

    @Test
    public void j276HeldLineCertifiesByteExact() {
        assertLineCertifies("hpk_human/d2/j276_1bm_Cross_Neup", "1+1+1+1+1+1+");
    }

    @Test
    public void j154KnifeEdgeLineCertifiesByteExact() {
        assertLineCertifies("hpk_human/d12/j154_1bm_Head_Butterfly_Neo",
                "4.8.8.8.8.8.8.8.8.8.8.8.8.8.6.2+4.2+2+2+2+2+2+2+2+2+2+2+1+");
    }
}
