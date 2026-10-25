package de.legoshi.parkourcalc.anglesolver.checks;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** The check behind each {@code problems/<check>/} folder. A new folder needs one entry here and one class. */
public final class Checks {

    private static final Map<String, ProblemCheck> BY_NAME = new TreeMap<String, ProblemCheck>();

    static {
        BY_NAME.put("solve", new SolveCheck());
        BY_NAME.put("closedform", new ClosedFormCheck());
        BY_NAME.put("dualrecovery", new DualRecoveryCheck());
        BY_NAME.put("replay", new ReplayCheck());
        BY_NAME.put("transitions", new TransitionsCheck());
        BY_NAME.put("planpath", new PlanPathCheck());
        BY_NAME.put("legal", new LegalCheck());
        BY_NAME.put("optimize", new OptimizeCheck());
        BY_NAME.put("multistart", new MultiStartCheck());
        BY_NAME.put("smooth", new SmoothCheck());
        BY_NAME.put("freestart", new FreeStartCheck());
        BY_NAME.put("levelset", new LevelSetCheck());
        BY_NAME.put("ils", new IlsCheck());
        BY_NAME.put("translation", new TranslationCheck());
        BY_NAME.put("gapattribution", new GapAttributionCheck());
        BY_NAME.put("strafeside", new StrafeSideCheck());
    }

    private Checks() {
    }

    public static ProblemCheck forName(String check) {
        return BY_NAME.get(check);
    }

    public static Map<String, ProblemCheck> all() {
        return Collections.unmodifiableMap(BY_NAME);
    }
}
