package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.checks.Checks;
import de.legoshi.parkourcalc.anglesolver.checks.ProblemCheck;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;

/**
 * The capture-driven suite: one parameterized run per file under {@code resources/problems/<check>/}. The
 * folder name is the check ({@link Checks} maps it to its class); each capture's optional {@code .expect.json}
 * tunes it. Add a capture by dropping it (or a sidecar pointing at the {@code /captures/} library) into a
 * folder; no Java change. The checks and their sidecar fields are listed in {@code resources/problems/README.md}.
 */
@RunWith(Parameterized.class)
@Category(SlowSolverTests.class)
public class ProblemsTest {

    @Parameters(name = "{0}/{1}")
    public static Collection<Object[]> problems() {
        String only = System.getProperty("pkc.problems");
        Set<String> keep = only == null ? null : new HashSet<String>(Arrays.asList(only.split(",")));
        Collection<Object[]> out = new ArrayList<Object[]>();
        for (String category : ProblemCatalog.categories()) {
            for (String name : ProblemCatalog.problemNames(category)) {
                if (keep == null || keep.contains(category + "/" + name) || keep.contains(category + "/*")) out.add(new Object[]{category, name});
            }
        }
        return out;
    }

    @Parameter(0)
    public String category;

    @Parameter(1)
    public String name;

    @Test
    public void check() {
        ProblemFixture pf = ProblemFixture.load(category, name);
        String check = pf.expect.check(category);
        ProblemCheck run = Checks.forName(check);
        assertNotNull(name + ": unknown check '" + check + "'; the known checks are " + Checks.all().keySet(), run);
        run.run(pf);
    }
}
