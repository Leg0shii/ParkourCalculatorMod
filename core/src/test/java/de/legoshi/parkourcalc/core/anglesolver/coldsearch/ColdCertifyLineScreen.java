package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ColdCertifyLineScreen {

    @Test
    public void certifyLine() throws Exception {
        String path = System.getenv("PKC_COLD_CERTIFY_FILE");
        String sig = System.getenv("PKC_COLD_CERTIFY_SIG");
        Assume.assumeTrue("set PKC_COLD_CERTIFY_FILE and PKC_COLD_CERTIFY_SIG",
                path != null && !path.isEmpty() && sig != null && !sig.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ColdResult r = ColdSearch.certifyLine(file, sig, new ColdSearch.Config());
        System.out.println("COLD certifyLine " + new File(path).getName());
        System.out.println(r == null ? "null result" : r.summary());
        if (r == null || !r.solved()) {
            System.out.println("lastDirect=[" + ColdSearch.lastDirectDebug + "]");
        }
    }
}
