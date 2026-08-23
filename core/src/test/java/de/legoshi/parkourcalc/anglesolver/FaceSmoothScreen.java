package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FaceSmoothScreen {

    private static final double FLOOR = 0.01;

    @Test
    public void screen() throws Exception {
        Assume.assumeTrue("set PKC_SCREENS=1 to run", System.getenv("PKC_SCREENS") != null);
        List<File> files = new ArrayList<File>();
        collect(resolve("/captures/hpk"), files);
        Collections.sort(files);

        Method build = HpkDualRecoveryScreen.class.getDeclaredMethod("buildSpec", SaveFile.class, ExactJumpModel.class);
        build.setAccessible(true);

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf("%-52s %3s %-14s %-14s %-14s%n", "capture", "n", "ladderRev", "faceRev", "slpRev");

        int both = 0, faceBetter = 0, faceWorse = 0, faceSame = 0;
        int ladderSumBoth = 0, faceSumBoth = 0;
        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            try {
                SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                        || file.rows == null || file.rows.isEmpty()) continue;
                ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
                JumpSpec spec = (JumpSpec) build.invoke(null, file, exact);
                if (spec == null) continue;
                JumpPhysicsInputs sc = spec.asScenario();
                double anchor = sc.startYaw;
                AtomicBoolean cancel = new AtomicBoolean(false);

                double[] lad = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
                double[] slp = SlpSolve.optimize(exact, spec, 0.0, cancel);
                double[] face = ClosedFormSolve.recoverFace(exact, spec, 0.0, cancel,
                        lad != null ? lad : (slp != null ? slp : null), 0L, null);

                Integer lRev = revOf(exact, sc, spec, anchor, lad);
                Integer fRev = revOf(exact, sc, spec, anchor, face);
                Integer sRev = revOf(exact, sc, spec, anchor, slp);

                if (lRev != null && fRev != null) {
                    both++;
                    ladderSumBoth += lRev;
                    faceSumBoth += fRev;
                    if (fRev < lRev) faceBetter++;
                    else if (fRev > lRev) faceWorse++;
                    else faceSame++;
                }
                String flag = "";
                if (lRev != null && fRev != null) flag = fRev < lRev ? "  face smoother" : fRev > lRev ? "  face rougher" : "";
                out.printf("%-52s %3d %-14s %-14s %-14s%s%n", stem, sc.numTicks,
                        str(lRev), str(fRev), str(sRev), flag);
            } catch (Throwable t) {
                out.printf("%-52s EXC %s%n", stem, t);
            }
        }
        out.printf("%nBoth-certify captures=%d  faceSmoother=%d faceRougher=%d same=%d%n",
                both, faceBetter, faceWorse, faceSame);
        out.printf("Sum reversals over both-certify: ladder=%d face=%d%n", ladderSumBoth, faceSumBoth);
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/face-smooth.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));
    }

    private static Integer revOf(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double anchor, double[] yaws) {
        if (yaws == null) return null;
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        JumpConstraintCompiler.Compiled c = JumpConstraintCompiler.compile(spec);
        if (c.maxViolation(gf, exact.forward(sc, gf)) > 0.0) return null;
        return reversals(anchor, Angles.wrapAll(yaws));
    }

    private static int reversals(double anchor, double[] y) {
        int c = 0, last = 0;
        double prev = anchor;
        for (double v : y) {
            double d = Angles.wrapDelta(v - prev);
            prev = v;
            if (Math.abs(d) <= FLOOR) continue;
            int s = d > 0 ? 1 : -1;
            if (last != 0 && s != last) c++;
            last = s;
        }
        return c;
    }

    private static String str(Integer v) {
        return v == null ? "-" : v.toString();
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
        java.net.URL u = FaceSmoothScreen.class.getResource(res);
        return u == null ? null : new File(u.toURI());
    }
}
