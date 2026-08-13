package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

public class StratFinderFileScreen {

    @Test
    public void screen() throws Exception {
        String path = System.getenv("PKC_STRATFIND_FILE");
        Assume.assumeTrue("set PKC_STRATFIND_FILE=<save path> to run", path != null && !path.isEmpty());
        long solveMs = System.getenv("PKC_STRATFIND_MS") != null
                ? Long.parseLong(System.getenv("PKC_STRATFIND_MS")) : 2000L;

        String raw = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        SaveFile witness = new Gson().fromJson(raw, SaveFile.class);
        ExactJumpModel model = ExactJumpModel.forMcVersion(witness.mcVersion);
        FileSystemSaveStore store = new FileSystemSaveStore(
                Files.createTempDirectory("pkc-stratfind"), "screen", witness.mcVersion, null);

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        StratFinder finder = new StratFinder(witness, model, store, solveMs, Double.NaN, true,
                new StratVariants.Filter(null, StratVariants.Filter.Shape.ANY, true), threads);
        finder.start();
        long deadline = System.currentTimeMillis() + 60_000L + solveMs * 600L;
        while (finder.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        if (finder.isRunning()) {
            finder.cancel();
        }

        System.out.printf(Locale.ROOT, "%s: %d variants, canaryFailed=%s%n",
                Paths.get(path).getFileName(), finder.total(), finder.canaryFailed());
        for (StratFinder.Item item : finder.ranked()) {
            if (!item.feasible) {
                continue;
            }
            String diff = Double.isNaN(item.difficulty)
                    ? "-" : String.format(Locale.ROOT, "%.2f", item.difficulty);
            String slack = "-";
            StratMeasurements m = item.measurements;
            if (m != null && m.shiftEdgeRow != null) {
                int worst = Integer.MAX_VALUE;
                boolean any = false;
                for (int i = 0; i < m.shiftEdgeRow.length; i++) {
                    if (m.shiftLoFree[i] || m.shiftHiFree[i]) {
                        continue;
                    }
                    any = true;
                    worst = Math.min(worst, m.shiftLo[i] + m.shiftHi[i]);
                }
                slack = any ? worst + "t" : "free";
            }
            System.out.printf(Locale.ROOT, "  %-28s -> %-28s diff=%-6s slack=%-5s corpus=%-4d %5dms%n",
                    item.label, StratLabels.display(item.label), diff, slack,
                    item.corpusEntries, item.elapsedMs);
        }
        int infeas = 0;
        for (StratFinder.Item item : finder.ranked()) {
            if (!item.feasible) {
                infeas++;
            }
        }
        System.out.printf(Locale.ROOT, "  (%d infeasible not listed)%n", infeas);
    }
}
