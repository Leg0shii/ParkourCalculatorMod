package de.legoshi.parkourcalc.core.anglesolver.noturn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class StratFamilies {

    public static final int RUN_UP = 0;
    public static final int MIDDLE = 1;
    public static final int FINAL = 2;
    public static final int COST_PER_BIT = 2;
    public static final String RESOURCE = "/strats/schedules.txt";

    private static final int OPEN = Integer.MIN_VALUE;
    private static final int[] MIRROR = {NoTurnKeys.NONE, NoTurnKeys.W, NoTurnKeys.WD, NoTurnKeys.WA, NoTurnKeys.D,
            NoTurnKeys.A, NoTurnKeys.S, NoTurnKeys.SD, NoTurnKeys.SA};

    private static volatile List<Schedule> shippedSchedules;
    private static volatile StratFamilies shipped;

    public static final class Schedule {
        public final double weight;
        public final int[] origins;
        public final int[] jumpTicks;
        public final int[] combos;

        public Schedule(double weight, int[] origins, int[] jumpTicks, int[] combos) {
            this.weight = weight;
            this.origins = origins;
            this.jumpTicks = jumpTicks.clone();
            Arrays.sort(this.jumpTicks);
            this.combos = combos;
        }

        boolean from(int... jumpNumbers) {
            for (int o : origins) for (int number : jumpNumbers) if (o == number) return true;
            return false;
        }

        String keyText() {
            return Arrays.toString(combos);
        }
    }

    public static final class Segment {
        public final int type;
        public final int start;
        public final int length;
        public final int[] cost;
        public final int[][] keys;

        Segment(int type, int start, int length, int[] cost, int[][] keys) {
            this.type = type;
            this.start = start;
            this.length = length;
            this.cost = cost;
            this.keys = keys;
        }

        public int indexOf(int[] combos) {
            for (int i = 0; i < keys.length; i++) {
                boolean same = true;
                for (int t = 0; t < length && same; t++) same = keys[i][t] == combos[start + t];
                if (same) return i;
            }
            return -1;
        }
    }

    static final class Family {
        final int type;
        final int[] keys;
        final int[] offsets;

        Family(int type, int[] keys, int[] offsets) {
            this.type = type;
            this.keys = keys;
            this.offsets = offsets;
        }

        Family mirrored() {
            int[] k = new int[keys.length];
            for (int i = 0; i < k.length; i++) k[i] = MIRROR[keys[i]];
            return new Family(type, k, offsets);
        }

        String id() {
            StringBuilder sb = new StringBuilder();
            sb.append(type);
            for (int i = 0; i < keys.length; i++) {
                sb.append(' ').append(NoTurnKeys.label(keys[i])).append('@');
                if (offsets[i] == OPEN) sb.append('*');
                else sb.append(offsets[i]);
            }
            return sb.toString();
        }

        int[] place(int length) {
            int events = keys.length;
            if (events == 0 || length == 0) return null;
            int[] at = new int[events];
            for (int i = 0; i < events; i++) {
                int o = offsets[i];
                if (o == OPEN) at[i] = 0;
                else if (type == RUN_UP || o < 0) at[i] = length + o;
                else at[i] = o;
            }
            if (at[0] != 0) return null;
            for (int i = 1; i < events; i++) if (at[i] <= at[i - 1] || at[i] >= length) return null;
            int[] out = new int[length];
            for (int i = 0; i < events; i++) {
                int end = i + 1 < events ? at[i + 1] : length;
                for (int t = at[i]; t < end; t++) out[t] = keys[i];
            }
            return out;
        }
    }

    private final List<Map<String, Family>> families = new ArrayList<>();
    private final List<Map<String, Double>> weights = new ArrayList<>();

    private StratFamilies() {
        for (int type = 0; type < 3; type++) {
            families.add(new TreeMap<String, Family>());
            weights.add(new HashMap<String, Double>());
        }
    }

    public static StratFamilies shipped() {
        StratFamilies s = shipped;
        if (s == null) {
            synchronized (StratFamilies.class) {
                s = shipped;
                if (s == null) {
                    s = fromSchedules(shippedSchedules(), -1);
                    shipped = s;
                }
            }
        }
        return s;
    }

    public static boolean available() {
        try {
            return !shippedSchedules().isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static StratFamilies shippedWithout(int... jumpNumbers) {
        return fromSchedules(shippedSchedules(), jumpNumbers);
    }

    public static List<Schedule> shippedSchedules() {
        List<Schedule> rows = shippedSchedules;
        if (rows == null) {
            synchronized (StratFamilies.class) {
                rows = shippedSchedules;
                if (rows == null) {
                    try (InputStream in = StratFamilies.class.getResourceAsStream(RESOURCE)) {
                        if (in == null) throw new IllegalStateException("missing resource " + RESOURCE);
                        rows = readSchedules(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    shippedSchedules = rows;
                }
            }
        }
        return rows;
    }

    public static List<Schedule> readSchedules(BufferedReader reader) throws IOException {
        List<Schedule> rows = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] part = line.split(";", -1);
            if (part.length != 4) throw new IllegalArgumentException("bad schedule row " + line);
            int[] jumps = ints(part[2]);
            if (jumps.length == 0) continue;
            rows.add(new Schedule(Double.parseDouble(part[0]), ints(part[1]), jumps, expand(part[3])));
        }
        return rows;
    }

    private static int[] ints(String text) {
        String t = text.trim();
        if (t.isEmpty()) return new int[0];
        String[] tok = t.split(",");
        int[] out = new int[tok.length];
        for (int i = 0; i < tok.length; i++) out[i] = Integer.parseInt(tok[i].trim());
        return out;
    }

    private static int[] expand(String keys) {
        int length = 0;
        for (String tok : keys.trim().split(" +")) {
            int x = tok.indexOf('x');
            length += x > 0 ? Integer.parseInt(tok.substring(x + 1)) : 1;
        }
        return NoTurnKeys.parse(keys, length);
    }

    public static StratFamilies fromSchedules(List<Schedule> rows, int... withoutJumps) {
        Set<String> heldOut = new HashSet<>();
        for (Schedule row : rows) if (row.from(withoutJumps)) heldOut.add(row.keyText());
        StratFamilies out = new StratFamilies();
        for (Schedule row : rows) {
            if (heldOut.contains(row.keyText())) continue;
            for (Family f : familiesOf(row.combos, row.jumpTicks)) out.add(f, row.weight);
        }
        return out;
    }

    private void add(Family f, double weight) {
        String a = f.id();
        Family m = f.mirrored();
        String b = m.id();
        boolean keep = a.compareTo(b) <= 0;
        String id = keep ? a : b;
        families.get(f.type).put(id, keep ? f : m);
        Double w = weights.get(f.type).get(id);
        weights.get(f.type).put(id, (w == null ? 0.0 : w) + weight);
    }

    public int count(int type) {
        return families.get(type).size();
    }

    static List<Family> familiesOf(int[] combos, int[] jumpTicks) {
        List<Family> out = new ArrayList<>();
        int n = combos.length;
        int first = jumpTicks[0];
        if (first > 0) {
            List<int[]> runs = runs(combos, 0, first);
            int[] keys = new int[runs.size()];
            int[] offsets = new int[runs.size()];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = runs.get(i)[0];
                offsets[i] = i == 0 ? OPEN : runs.get(i)[1] - first;
            }
            out.add(new Family(RUN_UP, keys, offsets));
        }
        for (int j = 0; j < jumpTicks.length; j++) {
            int from = jumpTicks[j];
            boolean last = j + 1 == jumpTicks.length;
            int to = last ? n : jumpTicks[j + 1];
            int length = to - from;
            if (length <= 0) continue;
            List<int[]> runs = runs(combos, from, to);
            int[] keys = new int[runs.size()];
            int[] offsets = new int[runs.size()];
            for (int i = 0; i < keys.length; i++) {
                int at = runs.get(i)[1] - from;
                keys[i] = runs.get(i)[0];
                offsets[i] = last || at <= length / 2 ? at : at - length;
            }
            out.add(new Family(last ? FINAL : MIDDLE, keys, offsets));
        }
        return out;
    }

    private static List<int[]> runs(int[] combos, int from, int to) {
        List<int[]> out = new ArrayList<>();
        for (int t = from; t < to; t++) {
            if (t == from || combos[t] != combos[t - 1]) out.add(new int[]{combos[t], t});
        }
        return out;
    }

    public Segment[] segmentsFor(int n, int[] jumpTicks) {
        if (jumpTicks == null || jumpTicks.length == 0) return null;
        int[] jumps = jumpTicks.clone();
        Arrays.sort(jumps);
        if (jumps[0] < 0 || jumps[jumps.length - 1] >= n) return null;
        List<Segment> out = new ArrayList<>();
        if (jumps[0] > 0) out.add(segment(RUN_UP, 0, jumps[0]));
        for (int j = 0; j < jumps.length; j++) {
            boolean last = j + 1 == jumps.length;
            int to = last ? n : jumps[j + 1];
            if (to <= jumps[j]) return null;
            out.add(segment(last ? FINAL : MIDDLE, jumps[j], to - jumps[j]));
        }
        for (Segment s : out) if (s.cost.length == 0) return null;
        return out.toArray(new Segment[0]);
    }

    private Segment segment(int type, int start, int length) {
        Map<String, Double> placed = new TreeMap<>();
        Map<String, int[]> keysOf = new HashMap<>();
        for (Map.Entry<String, Family> e : families.get(type).entrySet()) {
            double weight = weights.get(type).get(e.getKey());
            if (weight <= 0.0) continue;
            Family f = e.getValue();
            Family m = f.mirrored();
            boolean symmetric = f.id().equals(m.id());
            place(placed, keysOf, f, length, symmetric ? weight : weight / 2.0);
            if (!symmetric) place(placed, keysOf, m, length, weight / 2.0);
        }
        double total = 0.0;
        for (double w : placed.values()) total += w;
        final List<String> order = new ArrayList<>(placed.keySet());
        final Map<String, Integer> costOf = new HashMap<>();
        for (String k : order) {
            double bits = -StrictMath.log(placed.get(k) / total) / StrictMath.log(2.0);
            costOf.put(k, (int) Math.rint(bits * COST_PER_BIT));
        }
        order.sort((a, b) -> {
            int c = Integer.compare(costOf.get(a), costOf.get(b));
            return c != 0 ? c : a.compareTo(b);
        });
        int[] cost = new int[order.size()];
        int[][] keys = new int[order.size()][];
        for (int i = 0; i < cost.length; i++) {
            cost[i] = costOf.get(order.get(i));
            keys[i] = keysOf.get(order.get(i));
        }
        return new Segment(type, start, length, cost, keys);
    }

    private static void place(Map<String, Double> placed, Map<String, int[]> keysOf, Family f, int length, double weight) {
        int[] keys = f.place(length);
        if (keys == null) return;
        String id = NoTurnKeys.key(keys);
        Double w = placed.get(id);
        placed.put(id, (w == null ? 0.0 : w) + weight);
        keysOf.put(id, keys);
    }

    public static int minCost(Segment[] segments) {
        int sum = 0;
        for (Segment s : segments) sum += s.cost[0];
        return sum;
    }

    public static int maxCost(Segment[] segments) {
        int sum = 0;
        for (Segment s : segments) sum += s.cost[s.cost.length - 1];
        return sum;
    }

    public static int costOf(Segment[] segments, int[] combos) {
        int sum = 0;
        for (Segment s : segments) {
            int i = s.indexOf(combos);
            if (i < 0) return -1;
            sum += s.cost[i];
        }
        return sum;
    }
}
