package de.legoshi.parkourcalc.anglesolver.bench;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class SolverBenchReport {

    public static final String RULE =
            "Rule, fixed before the runs: two settings are compared on the same problem and the same seed. A seed is further when "
            + "the landing moves more than 1e-9 blocks toward the goal or when only the second setting lands, shorter the other way, "
            + "equal otherwise. A problem is a win when every seed is further, a loss when every seed is shorter; further on some "
            + "seeds and never shorter is a lean win, the mirror a lean loss, both directions is mixed. The second setting counts as "
            + "better only when wins exceed losses with an exact two-sided sign test over wins and losses at p <= 0.05 and no problem "
            + "that landed on every seed before fails on any seed after.";

    @Test
    public void report() throws Exception {
        String mode = System.getProperty("pkc.bench.report");
        Assume.assumeTrue("set -Dpkc.bench.report=<mode> and -Dpkc.bench.in=<files> to run", mode != null);
        runTo(mode);
    }

    private static void runTo(String mode) throws Exception {
        String path = System.getProperty("pkc.bench.reportOut");
        if (path == null) {
            run(mode, System.out);
            return;
        }
        try (PrintStream out = new PrintStream(new File(path), "UTF-8")) {
            run(mode, out);
        }
    }

    public static void main(String[] args) throws Exception {
        String mode = System.getProperty("pkc.bench.report");
        if (mode == null) throw new IllegalArgumentException("set -Dpkc.bench.report=sheet|seeds|stages|compare|scaling|portfolio|reference|identity");
        runTo(mode);
        System.exit(0);
    }

    static void run(String mode, PrintStream out) throws Exception {
        BenchData data = BenchData.load(required("pkc.bench.in"));
        String refPath = System.getProperty("pkc.bench.reference");
        BenchReference ref = BenchReference.load(refPath == null ? null : new File(refPath));
        if ("sheet".equals(mode)) sheet(data, out);
        else if ("seeds".equals(mode)) seeds(data, BenchData.config(required("pkc.bench.a")), out);
        else if ("stages".equals(mode)) stages(data, BenchData.config(required("pkc.bench.a")), out);
        else if ("compare".equals(mode)) compare(data, BenchData.config(required("pkc.bench.a")), BenchData.config(required("pkc.bench.b")), out);
        else if ("scaling".equals(mode)) scaling(data, ref, out);
        else if ("ladder".equals(mode)) ladder(data, out);
        else if ("distance".equals(mode)) distance(data, ref, BenchData.config(required("pkc.bench.a")), out);
        else if ("portfolio".equals(mode)) portfolio(data, required("pkc.bench.variant"), out);
        else if ("reference".equals(mode)) reference(data, ref, new File(required("pkc.bench.reference")), out);
        else if ("identity".equals(mode)) identity(data, BenchData.load(required("pkc.bench.other")), out);
        else throw new IllegalArgumentException("unknown report mode: " + mode);
    }

    private static String required(String key) {
        String v = System.getProperty(key);
        if (v == null) throw new IllegalArgumentException("set -D" + key);
        return v;
    }

    static void sheet(BenchData data, PrintStream out) {
        String variant = System.getProperty("pkc.bench.variant", "base");
        String mode = System.getProperty("pkc.bench.mode", SolverBench.MODE_START);
        TreeSet<Integer> budgets = new TreeSet<Integer>();
        TreeSet<String> categories = new TreeSet<String>();
        Map<String, BenchRecord.Jump> jumps = new LinkedHashMap<String, BenchRecord.Jump>();
        Map<String, List<BenchRecord>> cells = new LinkedHashMap<String, List<BenchRecord>>();
        for (BenchRecord r : data.solves) {
            if (!variant.equals(r.variant) || !mode.equals(r.mode)) continue;
            budgets.add(r.strength.budgetMs);
            categories.add(r.category);
            jumps.put(r.jump.key, r.jump);
            String key = r.jump.key + "|" + r.category + "|" + r.strength.budgetMs;
            List<BenchRecord> list = cells.get(key);
            if (list == null) {
                list = new ArrayList<BenchRecord>();
                cells.put(key, list);
            }
            list.add(r);
        }
        List<BenchRecord.Jump> order = new ArrayList<BenchRecord.Jump>(jumps.values());
        Collections.sort(order, (a, b) -> {
            int w = Integer.compare(a.warp == null ? Integer.MAX_VALUE : a.warp, b.warp == null ? Integer.MAX_VALUE : b.warp);
            return w != 0 ? w : a.key.compareTo(b.key);
        });
        out.println("Offset past the goal wall in blocks (median over the seeds run; `-` no landing), variant `" + variant + "`, mode `" + mode + "`.");
        out.println();
        StringBuilder head = new StringBuilder("| Jump | Warp | Difficulty |");
        StringBuilder rule = new StringBuilder("| --- | --- | --- |");
        for (String c : categories) {
            for (int b : budgets) {
                head.append(' ').append(c).append(' ').append(b == 0 ? "Fast" : b / 1000 + " s").append(" |");
                rule.append(" ---: |");
            }
            if (budgets.contains(0)) {
                head.append(' ').append(c).append(" Fast work ms | ").append(c).append(" Fast wall ms |");
                rule.append(" ---: | ---: |");
            }
        }
        out.println(head);
        out.println(rule);
        for (BenchRecord.Jump j : order) {
            StringBuilder row = new StringBuilder("| " + j.name + " | " + (j.warp == null ? j.id : String.valueOf(j.warp)) + " | "
                    + (j.difficulty == null ? "" : j.difficulty) + " |");
            for (String c : categories) {
                for (int b : budgets) row.append(' ').append(offsetCell(cells.get(j.key + "|" + c + "|" + b))).append(" |");
                if (budgets.contains(0)) {
                    List<BenchRecord> fast = cells.get(j.key + "|" + c + "|0");
                    row.append(' ').append(timeCell(fast, true)).append(" | ").append(timeCell(fast, false)).append(" |");
                }
            }
            out.println(row);
        }
        out.println();
        out.println("| Category | Strength | Jumps | Land | With an offset | Median offset | Sum of offsets |");
        out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: |");
        for (String c : categories) {
            for (int b : budgets) {
                int n = 0;
                int land = 0;
                double sum = 0.0;
                List<Double> offs = new ArrayList<Double>();
                for (BenchRecord.Jump j : order) {
                    List<BenchRecord> list = cells.get(j.key + "|" + c + "|" + b);
                    if (list == null) continue;
                    n++;
                    boolean all = true;
                    for (BenchRecord r : list) all &= BenchData.lands(r);
                    if (all) land++;
                    double o = medianOffset(list);
                    if (Double.isNaN(o)) continue;
                    sum += o;
                    offs.add(o);
                }
                out.println(String.format(Locale.ROOT, "| %s | %s | %d | %d | %d | %.6f | %.6f |", c, b == 0 ? "Fast" : b / 1000 + " s", n, land,
                        offs.size(), BenchData.median(offs), sum));
            }
        }
    }

    private static double medianOffset(List<BenchRecord> list) {
        List<Double> offs = new ArrayList<Double>();
        boolean any = false;
        for (BenchRecord r : list) {
            if (!BenchData.lands(r)) return Double.NaN;
            any = true;
            if (r.result.offset != null) offs.add(r.result.offset);
        }
        return any && !offs.isEmpty() ? BenchData.median(offs) : Double.NaN;
    }

    private static String offsetCell(List<BenchRecord> list) {
        if (list == null) return "";
        int landed = 0;
        List<Double> offs = new ArrayList<Double>();
        for (BenchRecord r : list) {
            if (!BenchData.lands(r)) continue;
            landed++;
            if (r.result.offset != null) offs.add(r.result.offset);
        }
        if (landed == 0) return "-";
        String v = offs.isEmpty() ? "lands" : String.format(Locale.ROOT, "%.6f", BenchData.median(offs));
        return landed < list.size() ? v + " (" + landed + "/" + list.size() + ")" : v;
    }

    private static String timeCell(List<BenchRecord> list, boolean work) {
        if (list == null) return "";
        List<Double> ms = new ArrayList<Double>();
        for (BenchRecord r : list) ms.add(work ? r.time.workMs : (double) r.time.wallMs);
        return String.format(Locale.ROOT, work ? "%.1f" : "%.0f", BenchData.median(ms));
    }

    static void seeds(BenchData data, String config, PrintStream out) {
        Map<String, Map<Long, BenchRecord>> problems = data.byProblem(config);
        out.println("Spread over the seeds, config `" + config + "`. Landing values are signed toward the goal.");
        out.println();
        out.println("| Problem | Seeds | Land | Best | Median minus best | Worst minus best | Distinct facings |");
        out.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: |");
        int moved = 0;
        int total = 0;
        List<Double> spreads = new ArrayList<Double>();
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, Map<Long, BenchRecord>> e : problems.entrySet()) {
            List<Double> vals = new ArrayList<Double>();
            TreeSet<String> hashes = new TreeSet<String>();
            int land = 0;
            for (BenchRecord r : e.getValue().values()) {
                if (BenchData.lands(r)) {
                    land++;
                    vals.add(BenchData.value(r));
                }
                hashes.add(String.valueOf(r.result == null ? null : r.result.yawHash));
            }
            total++;
            if (hashes.size() > 1) moved++;
            if (vals.isEmpty()) {
                rows.add(String.format(Locale.ROOT, "%020.12f| %s | %d | 0 | - | - | - | %d |", 0.0, e.getKey(), e.getValue().size(), hashes.size()));
                continue;
            }
            double best = Collections.max(vals);
            double worst = Collections.min(vals);
            double med = BenchData.median(vals);
            spreads.add(best - worst);
            rows.add(String.format(Locale.ROOT, "%020.12f| %s | %d | %d | %.9f | %.2e | %.2e | %d |", 1.0e6 - (best - worst), e.getKey(),
                    e.getValue().size(), land, best, med - best, worst - best, hashes.size()));
        }
        Collections.sort(rows);
        for (String r : rows) out.println(r.substring(r.indexOf('|')));
        out.println();
        int over6 = 0;
        int over4 = 0;
        double max = 0.0;
        for (double s : spreads) {
            if (s > 1e-6) over6++;
            if (s > 1e-4) over4++;
            max = Math.max(max, s);
        }
        out.println(String.format(Locale.ROOT, "%d problems, the seed changes the facings on %d. Spread best to worst: median %.2e, above 1e-6 on %d, above 1e-4 on %d, largest %.2e.",
                total, moved, BenchData.median(spreads), over6, over4, max));
    }

    static void stages(BenchData data, String config, PrintStream out) {
        Map<String, double[]> acc = new LinkedHashMap<String, double[]>();
        Map<String, List<Double>> gains = new LinkedHashMap<String, List<Double>>();
        double totalWork = 0.0;
        int runs = 0;
        for (BenchRecord r : data.solves) {
            if (!r.configKey().equals(config) || r.steps.isEmpty()) continue;
            runs++;
            boolean max = r.problem == null || !"MIN".equals(r.problem.sense);
            double prevWork = 0.0;
            double prevValue = Double.NaN;
            for (BenchRecord.Step s : r.steps) {
                double[] a = acc.get(s.id);
                if (a == null) {
                    a = new double[7];
                    acc.put(s.id, a);
                    gains.put(s.id, new ArrayList<Double>());
                }
                double work = Math.max(0.0, s.workMs - prevWork);
                prevWork = Math.max(prevWork, s.workMs);
                double value = s.feasible && s.objective != null ? (max ? s.objective : -s.objective) : Double.NaN;
                a[0] += 1;
                a[1] += work;
                if (work >= 1.0) a[2] += 1;
                if (!Double.isNaN(value) && Double.isNaN(prevValue)) a[3] += 1;
                if (!Double.isNaN(value) && !Double.isNaN(prevValue)) {
                    double gain = value - prevValue;
                    if (gain > BenchData.EPS) {
                        a[4] += 1;
                        a[5] += gain;
                        gains.get(s.id).add(gain);
                    } else if (gain < -BenchData.EPS) {
                        a[6] += 1;
                    }
                }
                if (!Double.isNaN(value)) prevValue = value;
                totalWork += work;
            }
        }
        out.println("Stages, config `" + config + "`, " + runs + " solves. Work is counted work time; a gain is the landing after the stage minus the landing before it.");
        out.println();
        out.println("| Stage | Visits | Visits over 1 ms | Work share | Mean work ms | First landing | Improved | Median gain | Sum of gains | Gain per work second | Shorter after |");
        out.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            double[] a = e.getValue();
            out.println(String.format(Locale.ROOT, "| %s | %.0f | %.0f | %.1f%% | %.1f | %.0f | %.0f | %.1e | %.3e | %.3e | %.0f |", e.getKey(), a[0], a[2],
                    100.0 * a[1] / Math.max(1e-9, totalWork), a[1] / Math.max(1.0, a[0]), a[3], a[4],
                    gains.get(e.getKey()).isEmpty() ? 0.0 : BenchData.median(gains.get(e.getKey())), a[5], a[1] > 0 ? a[5] / (a[1] / 1000.0) : 0.0, a[6]));
        }
    }

    static final class Verdicts {
        int wins;
        int leanWins;
        int equal;
        int leanLosses;
        int losses;
        int mixed;
        int lostLandings;
        int gainedLandings;
        double sumGain;
        final List<Double> moved = new ArrayList<Double>();
        final List<String> rows = new ArrayList<String>();
    }

    static Verdicts verdicts(BenchData data, String a, String b) {
        Map<String, Map<Long, BenchRecord>> pa = data.byProblem(a);
        Map<String, Map<Long, BenchRecord>> pb = data.byProblem(b);
        Verdicts v = new Verdicts();
        for (Map.Entry<String, Map<Long, BenchRecord>> e : pa.entrySet()) {
            Map<Long, BenchRecord> sb = pb.get(e.getKey());
            if (sb == null) continue;
            int further = 0;
            int shorter = 0;
            int same = 0;
            int landedA = 0;
            int landedB = 0;
            int pairs = 0;
            double sum = 0.0;
            int finite = 0;
            StringBuilder diffs = new StringBuilder();
            for (Map.Entry<Long, BenchRecord> s : e.getValue().entrySet()) {
                BenchRecord rb = sb.get(s.getKey());
                if (rb == null) continue;
                pairs++;
                double va = BenchData.value(s.getValue());
                double vb = BenchData.value(rb);
                boolean la = BenchData.lands(s.getValue());
                boolean lb = BenchData.lands(rb);
                if (la) landedA++;
                if (lb) landedB++;
                if (diffs.length() > 0) diffs.append(' ');
                if (la && lb) {
                    double d = vb - va;
                    sum += d;
                    finite++;
                    if (d > BenchData.EPS) further++;
                    else if (d < -BenchData.EPS) shorter++;
                    else same++;
                    diffs.append(String.format(Locale.ROOT, "%+.2e", d));
                } else if (lb) {
                    further++;
                    diffs.append("lands");
                } else if (la) {
                    shorter++;
                    diffs.append("fails");
                } else {
                    same++;
                    diffs.append("none");
                }
            }
            if (pairs == 0) continue;
            String verdict;
            if (further == pairs) {
                verdict = "win";
                v.wins++;
            } else if (shorter == pairs) {
                verdict = "loss";
                v.losses++;
            } else if (same == pairs) {
                verdict = "equal";
                v.equal++;
            } else if (shorter == 0) {
                verdict = "lean win";
                v.leanWins++;
            } else if (further == 0) {
                verdict = "lean loss";
                v.leanLosses++;
            } else {
                verdict = "mixed";
                v.mixed++;
            }
            if (landedA == pairs && landedB < pairs) v.lostLandings++;
            if (landedB == pairs && landedA < pairs) v.gainedLandings++;
            double mean = finite == 0 ? 0.0 : sum / finite;
            v.sumGain += mean;
            if (!"equal".equals(verdict)) {
                v.moved.add(mean);
                v.rows.add(String.format(Locale.ROOT, "%020.12f| %s | %s | %+.3e | %s |", 1.0e6 - mean, e.getKey(), verdict, mean, diffs));
            }
        }
        Collections.sort(v.rows);
        return v;
    }

    static void compare(BenchData data, String a, String b, PrintStream out) {
        Verdicts v = verdicts(data, a, b);
        out.println("Comparison of `" + b + "` against `" + a + "`. Positive is further with `" + b + "`.");
        out.println();
        out.println(RULE);
        out.println();
        out.println("| Problem | Verdict | Mean difference | Per seed |");
        out.println("| --- | --- | ---: | --- |");
        for (String r : v.rows) out.println(r.substring(r.indexOf('|')));
        out.println();
        out.println(summary(v));
    }

    static String summary(Verdicts v) {
        double p = BenchData.signTest(v.wins, v.losses);
        boolean better = v.wins > v.losses && p <= 0.05 && v.lostLandings == 0;
        boolean worse = v.losses > v.wins && p <= 0.05;
        return String.format(Locale.ROOT, "Wins %d, lean wins %d, equal %d, lean losses %d, losses %d, mixed %d. Landings gained %d, lost %d. "
                        + "Sum of mean differences %+.3e, median of the moved problems %+.3e. Sign test p = %.4f. Verdict by the rule: %s.",
                v.wins, v.leanWins, v.equal, v.leanLosses, v.losses, v.mixed, v.gainedLandings, v.lostLandings, v.sumGain,
                v.moved.isEmpty() ? 0.0 : BenchData.median(v.moved), p, better ? "better" : worse ? "worse" : "no difference shown");
    }

    static void scaling(BenchData data, BenchReference ref, PrintStream out) {
        String variant = System.getProperty("pkc.bench.variant", "base");
        TreeMap<Integer, String> configs = new TreeMap<Integer, String>();
        for (BenchRecord r : data.solves) {
            if (variant.equals(r.variant)) configs.put(r.strength.budgetMs, r.configKey());
        }
        out.println("Budget scaling, variant `" + variant + "`. Distance is the best known landing minus the median landing over the seeds, on the problems every budget lands.");
        out.println();
        Map<Integer, Map<String, Double>> med = new TreeMap<Integer, Map<String, Double>>();
        TreeSet<String> common = null;
        for (Map.Entry<Integer, String> c : configs.entrySet()) {
            Map<String, Double> m = new LinkedHashMap<String, Double>();
            for (Map.Entry<String, Map<Long, BenchRecord>> e : data.byProblem(c.getValue()).entrySet()) {
                List<Double> vals = new ArrayList<Double>();
                boolean all = true;
                for (BenchRecord r : e.getValue().values()) {
                    if (BenchData.lands(r)) vals.add(BenchData.value(r));
                    else all = false;
                }
                if (all && !vals.isEmpty()) m.put(e.getKey(), BenchData.median(vals));
            }
            med.put(c.getKey(), m);
            if (common == null) common = new TreeSet<String>(m.keySet());
            else common.retainAll(m.keySet());
        }
        if (common == null) return;
        out.println("| Budget | Problems run | Land on every seed | Sum of distance | Median distance | At best known | Within 1e-6 | Within 1e-4 |");
        out.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        for (Map.Entry<Integer, String> c : configs.entrySet()) {
            Map<String, Double> m = med.get(c.getKey());
            double sum = 0.0;
            int at = 0;
            int in6 = 0;
            int in4 = 0;
            List<Double> dist = new ArrayList<Double>();
            for (String k : common) {
                double best = ref.value(k);
                if (Double.isNaN(best)) continue;
                double d = Math.max(0.0, best - m.get(k));
                sum += d;
                dist.add(d);
                if (d <= BenchData.EPS) at++;
                if (d <= 1e-6) in6++;
                if (d <= 1e-4) in4++;
            }
            out.println(String.format(Locale.ROOT, "| %s | %d | %d | %.6f | %.3e | %d | %d | %d |", c.getKey() == 0 ? "Fast" : c.getKey() / 1000.0 + " s",
                    data.byProblem(c.getValue()).size(), m.size(), sum, BenchData.median(dist), at, in6, in4));
        }
        out.println();
        out.println(common.size() + " problems land at every budget and enter the distance columns.");
    }

    static void distance(BenchData data, BenchReference ref, String config, PrintStream out) {
        out.println("Distance to the best known landing, config `" + config + "` (median over its seeds).");
        out.println();
        out.println("| Problem | Ticks | Landing | Best known | Distance | Source of the best known |");
        out.println("| --- | ---: | ---: | ---: | ---: | --- |");
        List<String> rows = new ArrayList<String>();
        Map<String, List<Double>> byCategory = new TreeMap<String, List<Double>>();
        for (Map.Entry<String, Map<Long, BenchRecord>> e : data.byProblem(config).entrySet()) {
            double best = ref.value(e.getKey());
            if (Double.isNaN(best)) continue;
            List<Double> vals = new ArrayList<Double>();
            BenchRecord any = null;
            for (BenchRecord r : e.getValue().values()) {
                any = r;
                if (BenchData.lands(r)) vals.add(BenchData.value(r));
            }
            if (any == null) continue;
            BenchReference.Entry entry = ref.entries.get(e.getKey());
            double d = vals.size() < e.getValue().size() ? Double.POSITIVE_INFINITY : Math.max(0.0, best - BenchData.median(vals));
            List<Double> list = byCategory.get(any.category);
            if (list == null) {
                list = new ArrayList<Double>();
                byCategory.put(any.category, list);
            }
            list.add(d);
            if (d <= BenchData.EPS) continue;
            rows.add(String.format(Locale.ROOT, "%020.12f| %s | %d | %s | %.9f | %s | %s |", Double.isInfinite(d) ? 0.0 : 1.0e6 - d, e.getKey(),
                    any.problem == null ? 0 : any.problem.numTicks,
                    vals.isEmpty() ? "-" : String.format(Locale.ROOT, "%.9f", BenchData.median(vals)), best,
                    Double.isInfinite(d) ? "no landing" : String.format(Locale.ROOT, "%.3e", d), entry.source));
        }
        Collections.sort(rows);
        for (String r : rows) out.println(r.substring(r.indexOf('|')));
        out.println();
        out.println("| Category | Problems | At best known | Within 1e-6 | Within 1e-4 | Within 1e-3 | No landing | Sum of distance | Median distance |");
        out.println("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        for (Map.Entry<String, List<Double>> e : byCategory.entrySet()) {
            int at = 0;
            int in6 = 0;
            int in4 = 0;
            int in3 = 0;
            int none = 0;
            double sum = 0.0;
            List<Double> finite = new ArrayList<Double>();
            for (double d : e.getValue()) {
                if (Double.isInfinite(d)) {
                    none++;
                    continue;
                }
                finite.add(d);
                sum += d;
                if (d <= BenchData.EPS) at++;
                if (d <= 1e-6) in6++;
                if (d <= 1e-4) in4++;
                if (d <= 1e-3) in3++;
            }
            out.println(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d | %d | %.6f | %.3e |", e.getKey(), e.getValue().size(), at, in6, in4, in3,
                    none, sum, BenchData.median(finite)));
        }
    }

    static void ladder(BenchData data, PrintStream out) {
        String variant = System.getProperty("pkc.bench.variant", "base");
        TreeMap<Integer, String> configs = new TreeMap<Integer, String>();
        for (BenchRecord r : data.solves) {
            if (variant.equals(r.variant)) configs.put(r.strength.budgetMs, r.configKey());
        }
        out.println("Strength ladder, variant `" + variant + "`: each strength against the next lower one, on the seeds both ran. A loss is a problem that ends shorter with more time.");
        out.println();
        out.println("| From | To | Wins | Lean wins | Equal | Lean losses | Losses | Mixed | Landings gained | Landings lost | Sum of mean differences | Largest loss |");
        out.println("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
        Map.Entry<Integer, String> prev = null;
        for (Map.Entry<Integer, String> c : configs.entrySet()) {
            if (prev != null) {
                Verdicts v = verdicts(data, prev.getValue(), c.getValue());
                double worst = 0.0;
                for (double m : v.moved) worst = Math.min(worst, m);
                out.println(String.format(Locale.ROOT, "| %s | %s | %d | %d | %d | %d | %d | %d | %d | %d | %+.3e | %.2e |", strengthName(prev.getKey()),
                        strengthName(c.getKey()), v.wins, v.leanWins, v.equal, v.leanLosses, v.losses, v.mixed, v.gainedLandings, v.lostLandings,
                        v.sumGain, worst));
            }
            prev = c;
        }
    }

    private static String strengthName(int budgetMs) {
        return budgetMs == 0 ? "Fast" : budgetMs % 1000 == 0 ? budgetMs / 1000 + " s" : budgetMs + " ms";
    }

    static void portfolio(BenchData data, String variant, PrintStream out) {
        TreeMap<Integer, String> configs = new TreeMap<Integer, String>();
        for (BenchRecord r : data.solves) {
            if (variant.equals(r.variant) && r.strength.budgetMs > 0) configs.put(r.strength.budgetMs, r.configKey());
        }
        out.println("Seeds as a portfolio, variant `" + variant + "`: the best of K seeds at a budget against the lowest seed at K times the budget.");
        out.println();
        out.println("| Small budget | K | Large budget | Problems | Portfolio further | Equal | Single run further | Sum of differences | Sign test p |");
        out.println("| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: |");
        for (Map.Entry<Integer, String> small : configs.entrySet()) {
            for (Map.Entry<Integer, String> large : configs.entrySet()) {
                if (large.getKey() <= small.getKey() || large.getKey() % small.getKey() != 0) continue;
                int k = large.getKey() / small.getKey();
                Map<String, Map<Long, BenchRecord>> ps = data.byProblem(small.getValue());
                Map<String, Map<Long, BenchRecord>> pl = data.byProblem(large.getValue());
                int n = 0;
                int further = 0;
                int same = 0;
                int shorter = 0;
                double sum = 0.0;
                for (Map.Entry<String, Map<Long, BenchRecord>> e : ps.entrySet()) {
                    Map<Long, BenchRecord> big = pl.get(e.getKey());
                    if (big == null || big.isEmpty() || e.getValue().size() < k) continue;
                    double best = Double.NEGATIVE_INFINITY;
                    int used = 0;
                    for (BenchRecord r : e.getValue().values()) {
                        if (used++ >= k) break;
                        best = Math.max(best, BenchData.value(r));
                    }
                    double single = BenchData.value(big.values().iterator().next());
                    n++;
                    if (Double.isInfinite(best) && Double.isInfinite(single)) {
                        same++;
                        continue;
                    }
                    if (Double.isInfinite(single)) {
                        further++;
                        continue;
                    }
                    if (Double.isInfinite(best)) {
                        shorter++;
                        continue;
                    }
                    double d = best - single;
                    sum += d;
                    if (d > BenchData.EPS) further++;
                    else if (d < -BenchData.EPS) shorter++;
                    else same++;
                }
                if (n == 0) continue;
                out.println(String.format(Locale.ROOT, "| %s s | %d | %s s | %d | %d | %d | %d | %+.3e | %.4f |", small.getKey() / 1000.0, k,
                        large.getKey() / 1000.0, n, further, same, shorter, sum, BenchData.signTest(further, shorter)));
            }
        }
    }

    static void reference(BenchData data, BenchReference ref, File file, PrintStream out) throws Exception {
        int before = ref.entries.size();
        int improved = ref.absorb(data);
        ref.save(file);
        out.println("best known landings: " + before + " before, " + ref.entries.size() + " now, " + improved + " records raised an entry; written to " + file.getPath());
    }

    static void identity(BenchData data, BenchData other, PrintStream out) {
        Map<String, BenchRecord> first = new LinkedHashMap<String, BenchRecord>();
        for (BenchRecord r : data.solves) first.put(r.runKey(), r);
        int compared = 0;
        int identical = 0;
        for (BenchRecord r : other.solves) {
            BenchRecord a = first.get(r.runKey());
            if (a == null || !a.repeatable || !r.repeatable) continue;
            compared++;
            if (a.identity().equals(r.identity())) identical++;
            else out.println("DIFFERENT " + r.runKey() + ": " + a.identity() + " against " + r.identity());
        }
        int broken = 0;
        for (BenchRecord r : data.verifies) {
            if (r.error != null) broken++;
        }
        out.println("identity across the two inputs: " + identical + " of " + compared + " solves have the same facings, objective and work counters; in-run checks: "
                + (data.verifies.size() - broken) + " of " + data.verifies.size() + " identical");
        if (identical != compared || broken > 0) throw new AssertionError("repeatability broke");
    }
}
