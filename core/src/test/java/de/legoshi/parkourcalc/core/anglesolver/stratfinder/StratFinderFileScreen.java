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

        StratFinder finder = new StratFinder(witness, model, store, solveMs, Double.NaN, true);
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
            String margin = Double.isNaN(item.margin)
                    ? "-" : String.format(Locale.ROOT, "%+.4f", item.margin);
            System.out.printf(Locale.ROOT, "  %-24s edits=%d %-6s margin=%s %5dms snapshot=%s%n",
                    item.label, item.edits, item.feasible ? "feas" : "INFEAS", margin,
                    item.elapsedMs, item.appliedSnapshotJson != null ? "yes" : "no");
        }
    }
}
