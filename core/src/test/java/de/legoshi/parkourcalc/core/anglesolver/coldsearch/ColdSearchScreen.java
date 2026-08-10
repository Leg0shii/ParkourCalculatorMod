package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ColdSearchScreen {

    @Test
    public void solveCold() throws Exception {
        String path = System.getenv("PKC_COLD_FILE");
        Assume.assumeTrue("set PKC_COLD_FILE=<capture.json> to run", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));

        ColdSearch.Config cfg = new ColdSearch.Config();
        String budget = System.getenv("PKC_COLD_BUDGET_MS");
        if (budget != null && !budget.isEmpty()) cfg.timeBudgetMs = Long.parseLong(budget);
        String step = System.getenv("PKC_COLD_FACING_STEP");
        if (step != null && !step.isEmpty()) cfg.facingStepDeg = Double.parseDouble(step);
        String maxChanges = System.getenv("PKC_COLD_MAX_CHANGES");
        if (maxChanges != null && !maxChanges.isEmpty()) cfg.maxChanges = Integer.parseInt(maxChanges);
        String certifyCap = System.getenv("PKC_COLD_CERTIFY_CAP");
        if (certifyCap != null && !certifyCap.isEmpty()) cfg.certifyCap = Integer.parseInt(certifyCap);
        String seeded = System.getenv("PKC_COLD_SEEDED");
        if (seeded != null && !seeded.isEmpty()) cfg.seededPass = Boolean.parseBoolean(seeded);
        String nodeCap = System.getenv("PKC_COLD_ARC_NODE_CAP");
        if (nodeCap != null && !nodeCap.isEmpty()) cfg.arcNodeCap = Long.parseLong(nodeCap);
        String exhLevel = System.getenv("PKC_COLD_EXH_LEVEL");
        if (exhLevel != null && !exhLevel.isEmpty()) cfg.arcExhaustiveMaxLevel = Integer.parseInt(exhLevel);

        String logPath = System.getenv("PKC_COLD_LOG");
        java.io.PrintStream log = System.out;
        java.io.PrintStream fileLog = null;
        if (logPath != null && !logPath.isEmpty()) {
            fileLog = new java.io.PrintStream(new java.io.FileOutputStream(logPath, true), true, "UTF-8");
            log = fileLog;
        }
        try {
            ColdResult result = ColdSearch.solve(file, cfg, log);
            log.println("COLD " + new File(path).getName());
            log.println(result.summary());
            System.out.println("COLD " + new File(path).getName());
            System.out.println(result.summary());
        } finally {
            if (fileLog != null) fileLog.close();
        }
    }
}
