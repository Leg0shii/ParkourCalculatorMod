package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class StratPlans {

    public static final int BACK_ARC_TICKS = 12;

    private StratPlans() {
    }

    public static final class Plan {
        public final String label;
        public final int fire;
        public final Map<Integer, TreeSet<String>> preRows = new TreeMap<Integer, TreeSet<String>>();
        public final TreeMap<Integer, String[][]> post = new TreeMap<Integer, String[][]>();
        public final TreeSet<String> fireKeys = new TreeSet<String>();
        public final List<Integer> groundTicks = new ArrayList<Integer>();

        Plan(String label, int fire) {
            this.label = label;
            this.fire = fire;
        }

        Plan pre(int t, String... keys) {
            preRows.put(t, keys(keys));
            return this;
        }

        Plan patch(int rel, String[] on, String[] off) {
            post.put(rel, new String[][]{on, off});
            return this;
        }

        Plan atFire(String... keys) {
            fireKeys.addAll(keys(keys));
            return this;
        }

        Plan ground(Integer... ticks) {
            Collections.addAll(groundTicks, ticks);
            return this;
        }

        public int lastPatchRel() {
            return post.isEmpty() ? 0 : post.lastKey();
        }
    }

    private static TreeSet<String> keys(String... names) {
        TreeSet<String> set = new TreeSet<String>();
        Collections.addAll(set, names);
        return set;
    }

    public static List<Plan> plans(boolean wide) {
        List<Plan> out = new ArrayList<Plan>();
        for (int d = 0; d <= (wide ? 12 : 8); d++) {
            Plan p = new Plan("run" + d + "+jam", d).atFire("W", "SPRINT", "JUMP");
            for (int t = 0; t < d; t++) {
                p.pre(t, "W", "SPRINT");
                p.groundTicks.add(t);
            }
            p.groundTicks.add(d);
            out.add(p);
        }
        for (int k = 1; k <= (wide ? 8 : 6); k++) {
            Plan p = new Plan("fmm" + k, 0).atFire("W", "JUMP").ground(0);
            for (int rel = 1; rel < k; rel++) {
                p.patch(rel, new String[]{"W"}, new String[]{"SPRINT", "S", "SNEAK"});
            }
            p.patch(k, new String[]{"W", "SPRINT"}, new String[]{"S", "SNEAK"});
            out.add(p);
        }
        for (int k = 1; k <= (wide ? 11 : 8); k++) {
            Plan p = new Plan("pessi" + k, 0).atFire("JUMP").ground(0);
            for (int rel = 1; rel < k; rel++) {
                p.patch(rel, new String[]{}, new String[]{"W", "SPRINT", "S", "SNEAK"});
            }
            p.patch(k, new String[]{"W", "SPRINT"}, new String[]{"S", "SNEAK"});
            out.add(p);
        }
        for (String side : new String[]{"A", "D"}) {
            for (int k = 1; k <= (wide ? 6 : 4); k++) {
                Plan p = new Plan("mark" + side + k, 0).atFire("JUMP", side).ground(0);
                for (int rel = 1; rel < k; rel++) {
                    p.patch(rel, new String[]{side}, new String[]{"W", "SPRINT", "S", "SNEAK"});
                }
                p.patch(k, new String[]{"W", "SPRINT", side}, new String[]{"S", "SNEAK"});
                out.add(p);
            }
        }
        out.add(bwmm("bwmm+jam", 0, 0));
        out.add(bwmm("bwmm+fmm1", 0, 1));
        out.add(bwmm("bwmm+fmm2", 0, 2));
        out.add(bwmm("bwmm+pessi1", 1, 1));
        if (wide) {
            out.add(bwmm("bwmm+fmm3", 0, 3));
            out.add(bwmm("bwmm+fmm4", 0, 4));
            out.add(bwmm("bwmm+pessi2", 2, 2));
            out.add(bwmm("bwmm+pessi3", 3, 3));
        }
        return out;
    }

    private static Plan bwmm(String label, int wDelay, int sprintDelay) {
        int fire = BACK_ARC_TICKS + 2;
        Plan p = new Plan(label, fire);
        p.pre(0, "S", "JUMP");
        for (int t = 1; t < BACK_ARC_TICKS; t++) {
            p.pre(t, "S");
        }
        p.pre(BACK_ARC_TICKS, "W");
        p.pre(BACK_ARC_TICKS + 1, "W");
        p.ground(0, BACK_ARC_TICKS, BACK_ARC_TICKS + 1, fire);
        if (wDelay == 0 && sprintDelay == 0) {
            p.atFire("W", "SPRINT", "JUMP");
        } else if (wDelay == 0) {
            p.atFire("W", "JUMP");
            for (int rel = 1; rel < sprintDelay; rel++) {
                p.patch(rel, new String[]{"W"}, new String[]{"SPRINT", "S", "SNEAK"});
            }
            p.patch(sprintDelay, new String[]{"W", "SPRINT"}, new String[]{"S", "SNEAK"});
        } else {
            p.atFire("JUMP");
            for (int rel = 1; rel < wDelay; rel++) {
                p.patch(rel, new String[]{}, new String[]{"W", "SPRINT", "S", "SNEAK"});
            }
            p.patch(wDelay, new String[]{"W", "SPRINT"}, new String[]{"S", "SNEAK"});
        }
        return p;
    }
}
