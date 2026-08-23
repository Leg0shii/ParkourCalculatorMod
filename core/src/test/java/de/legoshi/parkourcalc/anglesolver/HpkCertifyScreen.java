package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * gh-418: does the closed form certify across the hpk corpus, and does the recovery repair change
 * that? Set PKC_SCREENS=1; -Dpkc.cf.repair=0 turns the repair off for an A/B.
 */
public class HpkCertifyScreen {

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        List<File> files = new ArrayList<File>();
        collect(resolve("/captures/hpk"), files);
        java.util.Collections.sort(files);

        Method build = HpkDualRecoveryScreen.class.getDeclaredMethod("buildSpec", SaveFile.class,
                ExactJumpModel.class);
        build.setAccessible(true);

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        boolean repair = !"0".equals(System.getProperty("pkc.cf.repair", "1"));
        out.printf("HPK repair=%s captures=%d%n", repair, files.size());
        int certAsc = 0;
        int certRobust = 0;
        int usable = 0;
        long t0 = System.nanoTime();
        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            try {
                SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()),
                        StandardCharsets.UTF_8));
                if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                        || file.rows == null || file.rows.isEmpty()) {
                    out.printf("%-56s skip%n", stem);
                    continue;
                }
                ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
                JumpSpec spec = (JumpSpec) build.invoke(null, file, exact);
                if (spec == null) {
                    out.printf("%-56s skip (null spec)%n", stem);
                    continue;
                }
                usable++;
                AtomicBoolean cancel = new AtomicBoolean(false);
                long a0 = System.nanoTime();
                double[] asc = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
                long aMs = (System.nanoTime() - a0) / 1000000L;
                ClosedFormSolve.Result rob = ClosedFormSolve.optimizeRobustGraded(exact, spec, 0.0, cancel);
                if (asc != null) certAsc++;
                if (rob != null && rob.feasible) certRobust++;
                out.printf("%-56s n=%-4d asc=%-5s robust=%-5s robustViol=%11.4e ms=%d%n",
                        stem, spec.asScenario().numTicks, asc != null,
                        rob != null && rob.feasible,
                        rob == null ? Double.NaN : rob.violation, aMs);
            } catch (Throwable t) {
                out.printf("%-56s EXC %s%n", stem, t);
            }
        }
        out.printf("HPK TOTAL usable=%d ascendingCertified=%d robustCertified=%d totalMs=%d%n",
                usable, certAsc, certRobust, (System.nanoTime() - t0) / 1000000L);
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/hpk-certify-" + (repair ? "on" : "off") + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir == null ? null : dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, out);
            else if (k.getName().endsWith(".json")) out.add(k);
        }
    }

    private static File resolve(String res) throws Exception {
        java.net.URL u = HpkCertifyScreen.class.getResource(res);
        return u == null ? null : new File(u.toURI());
    }
}
