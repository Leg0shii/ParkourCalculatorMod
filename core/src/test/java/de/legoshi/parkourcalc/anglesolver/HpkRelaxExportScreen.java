package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.metriclab.HpkHumanSet;
import de.legoshi.parkourcalc.anglesolver.metriclab.RelaxExport;
import de.legoshi.parkourcalc.anglesolver.metriclab.StratTemplates;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HpkRelaxExportScreen {

    @Test
    public void export() throws Exception {
        Assume.assumeTrue("set PKC_RELAX_EXPORT=1 to run", "1".equals(System.getenv("PKC_RELAX_EXPORT")));
        String only = System.getenv("PKC_RELAX_ONLY");
        String dFilter = System.getenv("PKC_RELAX_D");
        File dir = new File("build/hpk-metric/relax");
        dir.mkdirs();
        int count = 0;
        for (HpkHumanSet.Sample s : HpkHumanSet.loadAll()) {
            if (only != null && !only.isEmpty() && !s.name.contains(only)) {
                continue;
            }
            if (dFilter != null && !dFilter.isEmpty() && !dLevelIn(s.dLevel, dFilter)) {
                continue;
            }
            ExactJumpModel model = ExactJumpModel.forMcVersion(s.save.mcVersion);
            List<StratTemplates.Instance> instances = StratTemplates.instancesFor(s.save, model);
            File out = new File(dir, s.name + ".jsonl");
            PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8.name());
            try {
                for (StratTemplates.Instance inst : instances) {
                    String json = RelaxExport.export(s.name, inst.label, inst.save, model);
                    if (json != null) {
                        pw.println(json);
                        count++;
                    }
                }
            } finally {
                pw.close();
            }
        }
        System.out.println("exported " + count + " instances to " + dir.getAbsolutePath());
    }

    private static boolean dLevelIn(int d, String filter) {
        for (String part : filter.split(",")) {
            if (!part.trim().isEmpty() && Integer.parseInt(part.trim()) == d) {
                return true;
            }
        }
        return false;
    }
}
