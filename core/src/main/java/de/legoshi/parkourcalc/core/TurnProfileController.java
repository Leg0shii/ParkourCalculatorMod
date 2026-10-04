package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class TurnProfileController {

    public static final class Current {
        public final int startTick;
        public final int tasFirstTick;
        public final int n;
        public final double[] facing;
        public final int[] keys;
        public final boolean[] checkKeys;
        public final int[] optionalKeys;
        public final boolean[] checkYaw;
        public final boolean[] still;
        public final boolean[] jumpTicks;
        public final TurnReference.Landing landing;
        public final TurnProfile profile;
        public final AttemptSampler.Stats attempts;
        public final double pixelDeg;
        final AngleSolverEngine.PathSnapshot snapshot;

        Current(int startTick, int tasFirstTick, double[] facing, int[] keys, boolean[] checkKeys, int[] optionalKeys,
                boolean[] checkYaw, boolean[] still, boolean[] jumpTicks, TurnReference.Landing landing,
                TurnProfile profile, AttemptSampler.Stats attempts, double pixelDeg,
                AngleSolverEngine.PathSnapshot snapshot) {
            this.startTick = startTick;
            this.tasFirstTick = tasFirstTick;
            this.n = facing.length;
            this.facing = facing;
            this.keys = keys;
            this.checkKeys = checkKeys;
            this.optionalKeys = optionalKeys;
            this.checkYaw = checkYaw;
            this.still = still;
            this.jumpTicks = jumpTicks;
            this.landing = landing;
            this.profile = profile;
            this.attempts = attempts;
            this.pixelDeg = pixelDeg;
            this.snapshot = snapshot;
        }

        Current withAttempts(AttemptSampler.Stats stats, double pixelDeg) {
            return new Current(startTick, tasFirstTick, facing, keys, checkKeys, optionalKeys, checkYaw, still,
                    jumpTicks, landing, profile, stats, pixelDeg, snapshot);
        }

        public boolean canRate() {
            return snapshot != null && profile != null && profile.lands;
        }

        public boolean pathLands() {
            return profile == null || profile.lands;
        }

        public int lastTick() {
            return Math.max(startTick + n - 1, landing == null ? -1 : landing.tick);
        }

        public int firstJumpRow() {
            for (int t = 0; t < n; t++) if (jumpTicks[t]) return t;
            return -1;
        }
    }

    private static final class Built {
        final TurnReference ref = new TurnReference();
        AngleSolverEngine.PathSnapshot snapshot;
    }

    private final AngleSolverEngine engine;
    private final AngleSolverState state;
    private final InputData inputs;
    private final BooleanSupplier enabled;
    private final Supplier<Float> sensitivity;
    private final Supplier<Integer> sampleCount;
    private final Supplier<Integer> spreadAttempts;
    private final TurnProfileStore store;
    private final Supplier<String> tasName;
    private final TurnProfileDocument document = new TurnProfileDocument();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pkc-turn-profile");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pkc-onejump-io");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService deep = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pkc-onejump-deep");
        t.setDaemon(true);
        return t;
    });
    private final Set<TurnAttempt> deepQueue = java.util.Collections.newSetFromMap(new ConcurrentHashMap<TurnAttempt, Boolean>());
    private final Set<TurnAttempt> deepDone = java.util.Collections.newSetFromMap(new ConcurrentHashMap<TurnAttempt, Boolean>());
    private volatile boolean deepDirty;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile AtomicBoolean cancelToken = new AtomicBoolean(false);
    private volatile Current current;
    private volatile boolean rating;
    private int ratedVersion = -1;
    private volatile int ratedSpread;
    private volatile double[][] ratePool;
    private volatile boolean rateMore;
    private static final int RATE_TARGET_LANDINGS = 100;
    private static final long RATE_MAX_SAMPLES = 5_000_000L;
    private boolean loaded;
    private boolean adopted;
    private String loadedName;
    private String lastError;
    private String storeError;
    private volatile int selectedNumber = -1;

    public TurnProfileController(AngleSolverEngine engine, AngleSolverState state, InputData inputs,
                                 BooleanSupplier enabled, Supplier<Float> sensitivity,
                                 Supplier<Integer> sampleCount, Supplier<Integer> spreadAttempts,
                                 TurnProfileStore store, Supplier<String> tasName) {
        this.engine = engine;
        this.state = state;
        this.inputs = inputs;
        this.enabled = enabled;
        this.sensitivity = sensitivity;
        this.sampleCount = sampleCount;
        this.spreadAttempts = spreadAttempts;
        this.store = store;
        this.tasName = tasName;
    }

    public TurnProfileDocument document() {
        ensureLoaded();
        return document;
    }

    public String name() {
        return loadedName;
    }

    public String lastError() {
        return lastError;
    }

    public String storeError() {
        return storeError;
    }

    public TurnProfileDocument.Stats stats() {
        return document.stats();
    }

    public void tick() {
        if (!enabled.getAsBoolean() && !document.hasPending() && !document.isReferenceDirty() && !deepDirty) return;
        sync();
        autoRate();
        requestDeepChecks();
    }

    public void sync() {
        ensureLoaded();
        if (deepDirty && deepQueue.isEmpty()) {
            deepDirty = false;
            document.markDirty();
        }
        if (store == null || loadedName == null || storeError != null) return;
        final String name = loadedName;
        if (document.isReferenceDirty()) {
            final TurnReference ref = document.reference().copy();
            final List<TurnAttempt> all = new ArrayList<TurnAttempt>(document.attempts());
            document.markClean();
            io.submit(() -> store.save(name, ref, all));
        } else if (document.hasPending()) {
            final List<TurnAttempt> added = document.drainPending();
            final TurnReference ref = document.reference().copy();
            final List<TurnAttempt> all = new ArrayList<TurnAttempt>(document.attempts());
            io.submit(() -> {
                if (!store.append(name, added)) store.save(name, ref, all);
            });
        }
    }

    public void flush() {
        try {
            io.submit(() -> { }).get();
        } catch (Exception ignored) {
        }
    }

    private void ensureLoaded() {
        String name = tasName.get();
        if (name != null && name.isEmpty()) name = null;
        if (loaded && Objects.equals(name, loadedName)) return;
        boolean carry = loaded && loadedName == null && name != null && store != null
                && !document.attempts().isEmpty() && !Files.exists(store.fileFor(name));
        loaded = true;
        adopted = false;
        loadedName = name;
        storeError = null;
        deepDone.clear();
        if (carry) {
            document.touch();
        } else if (store != null && name != null) {
            store.migrateLegacyFolder();
            boolean ok = store.load(name, document);
            if (!ok && store.lastError() != null) storeError = store.lastError();
            else if (ok && store.skippedLines() > 0) document.markDirty();
        } else {
            document.reset();
        }
        refresh();
    }

    public void refresh() {
        if (!enabled.getAsBoolean()) return;
        ensureLoaded();
        cancelToken.set(true);
        cancelToken = new AtomicBoolean(false);
        generation.incrementAndGet();
        rating = false;
        ratedVersion = -1;
        TurnReference ref = document.reference();
        if (!adopted) {
            adopted = true;
            if (!ref.isEmpty() && ref.tasFirstTick() >= 0 && !anyFlagged()) adoptFlags(ref);
        }
        Built built = buildReference();
        if (!ref.sameAs(built.ref)) {
            boolean spanMoved = !ref.isEmpty() && (ref.tasFirstTick() != built.ref.tasFirstTick()
                    || !TurnReference.sameLanding(ref.landing(), built.ref.landing()));
            ref.copyFrom(built.ref);
            if (spanMoved) {
                for (TurnAttempt a : document.attempts()) a.solvedOffset = null;
                deepDone.clear();
            }
            document.touch();
        }
        if (ref.isEmpty()) {
            current = null;
            return;
        }
        int n = ref.size();
        double[] facing = Angles.wrapAll(ref.facings());
        int[] keys = new int[n];
        boolean[] checkKeys = new boolean[n];
        int[] optional = new int[n];
        boolean[] checkYaw = new boolean[n];
        boolean[] still = new boolean[n];
        boolean[] jumps = new boolean[n];
        for (int k = 0; k < n; k++) {
            InputRow r = ref.row(k);
            keys[k] = TurnReference.mask(r);
            checkKeys[k] = ref.checkKeys(r);
            optional[k] = ref.optionalKeys(r);
            checkYaw[k] = ref.checkYaw(r);
            still[k] = ref.still(r);
            jumps[k] = (keys[k] & TurnReference.KEY_JUMP) != 0;
        }
        AngleSolverEngine.PathSnapshot snap = built.snapshot;
        TurnProfile profile = null;
        if (snap != null) {
            ForwardModel model = engine.forwardModel();
            profile = TurnProfile.compute(model, snap.spec, facing, null, false);
        }
        current = new Current(0, ref.tasFirstTick(), facing, keys, checkKeys, optional, checkYaw, still, jumps,
                ref.landing(), profile, null, TurnProfile.pixelDeg(sensitivity.get()), snap);
    }

    private boolean anyFlagged() {
        for (InputRow r : inputs.getRows()) if (r.isOnejumpFlagged()) return true;
        return false;
    }

    private void adoptFlags(TurnReference ref) {
        List<InputRow> rows = inputs.getRows();
        for (int k = 0; k < ref.size(); k++) {
            int t = ref.tasFirstTick() + k;
            if (t >= rows.size()) break;
            InputRow src = ref.row(k);
            InputRow row = rows.get(t);
            row.setOnejumpKeys(src.isOnejumpKeys());
            row.setOnejumpFace(src.getOnejumpFace());
            TurnReference.applyOptional(row, TurnReference.optionalMask(src));
        }
    }

    private Built buildReference() {
        Built built = new Built();
        TurnReference ref = built.ref;
        List<InputRow> rows = inputs.getRows();
        int first = -1;
        int last = -1;
        for (int t = 0; t < rows.size(); t++) {
            if (!rows.get(t).isOnejumpFlagged()) continue;
            if (first < 0) first = t;
            last = t;
        }
        if (first < 0) {
            lastError = null;
            return built;
        }
        AngleSolverEngine.PathSnapshot snap = engine.snapshotPath(first, last + 1);
        if (snap == null || snap.yaws.length != last - first + 1) {
            lastError = "no path for ticks " + (first + 1) + " to " + (last + 1) + " in the TAS";
            return built;
        }
        built.snapshot = snap;
        int n = last - first + 1;
        List<InputRow> next = new ArrayList<InputRow>();
        boolean[] checkKeys = new boolean[n];
        boolean[] checkYaw = new boolean[n];
        boolean[] still = new boolean[n];
        for (int k = 0; k < n; k++) {
            InputRow src = rows.get(first + k);
            InputRow r = src.copy();
            r.setYaw((float) Angles.wrap(snap.yaws[k]));
            r.setYawLocked(false);
            next.add(r);
            checkKeys[k] = src.isOnejumpKeys();
            checkYaw[k] = src.getOnejumpFace() != InputRow.ONEJUMP_FACE_OFF;
            still[k] = src.getOnejumpFace() == InputRow.ONEJUMP_FACE_STILL;
        }
        ref.replace(next, checkKeys, checkYaw, still);
        ref.setTasFirstTick(first);
        List<TurnReference.Landing> options = solverLandings(first, last + 1, first);
        if (!options.isEmpty()) ref.setLanding(options.get(options.size() - 1));
        lastError = null;
        return built;
    }

    private List<TurnReference.Landing> solverLandings(int from, int to, int base) {
        List<TurnReference.Landing> out = new ArrayList<TurnReference.Landing>();
        List<Integer> ticks = new ArrayList<Integer>(state.populatedTicks());
        java.util.Collections.sort(ticks);
        for (int tick : ticks) {
            if (tick < from || tick > to) continue;
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            double xLo = Double.NaN, xHi = Double.NaN, zLo = Double.NaN, zHi = Double.NaN;
            for (Constraint c : tc.getConstraints()) {
                if (!c.isEnabled()) continue;
                boolean x = c.getField() == Constraint.Field.X;
                if (!x && c.getField() != Constraint.Field.Z) continue;
                double lo = Double.NaN, hi = Double.NaN;
                switch (c.getOp()) {
                    case GE: case GT: lo = c.getValue(); break;
                    case LE: case LT: hi = c.getValue(); break;
                    case IN: lo = c.getLo(); hi = c.getHi(); break;
                    default: continue;
                }
                if (x) {
                    if (!Double.isNaN(lo)) xLo = lo;
                    if (!Double.isNaN(hi)) xHi = hi;
                } else {
                    if (!Double.isNaN(lo)) zLo = lo;
                    if (!Double.isNaN(hi)) zHi = hi;
                }
            }
            TurnReference.Landing l = new TurnReference.Landing(tick - base, xLo, xHi, zLo, zHi);
            if (!l.isEmpty()) out.add(l);
        }
        return out;
    }

    public void referenceChanged() {
        refresh();
    }

    public void clearAttempts() {
        document.clearAttempts();
        deepDone.clear();
    }

    public void rate() {
        Current cur = current;
        if (cur == null || !cur.canRate()) return;
        ratePool = spread(cur, Math.max(1, spreadAttempts.get()));
        ratedVersion = document.version();
        if (rating) {
            rateMore = true;
            return;
        }
        double pixelDeg = TurnProfile.pixelDeg(sensitivity.get());
        int chunk = Math.max(1000, sampleCount.get());
        ForwardModel model = engine.forwardModel();
        AtomicBoolean cancel = cancelToken;
        int gen = generation.get();
        rating = true;
        rateMore = false;
        worker.submit(() -> {
            AttemptSampler.Stats total = cur.attempts;
            long seed = AttemptSampler.SEED + (total == null ? 0L : total.attempts);
            while (!cancel.get() && gen == generation.get()) {
                rateMore = false;
                AttemptSampler.Stats part = AttemptSampler.sample(model, cur.snapshot.spec, cur.facing, ratePool, chunk, seed, cancel);
                if (cancel.get() || gen != generation.get()) return;
                seed += part.attempts;
                total = AttemptSampler.merge(total, part);
                current = cur.withAttempts(total, pixelDeg);
                boolean enough = total.landings >= RATE_TARGET_LANDINGS || total.attempts >= RATE_MAX_SAMPLES;
                if (enough && !rateMore) break;
            }
            rating = false;
        });
    }

    public void autoRate() {
        Current cur = current;
        if (rating || cur == null || !cur.canRate() || ratedVersion == document.version()) return;
        rate();
    }

    private double[][] spread(Current cur, int limit) {
        List<TurnAttempt> all = document.attempts();
        int n = cur.n;
        List<List<Double>> per = new ArrayList<List<Double>>();
        for (int t = 0; t < n; t++) per.add(new ArrayList<Double>());
        int used = 0;
        for (int i = all.size() - 1; i >= 0 && used < limit; i--) {
            TurnAttempt a = all.get(i);
            if (a.isMacro() || a.recorded <= 0 || a.yaws == null || !a.alignedTo(cur)) continue;
            used++;
            int m = Math.min(n, Math.min(a.recorded, a.yaws.length));
            for (int t = 0; t < m; t++) {
                if (Double.isNaN(a.yaws[t])) continue;
                per.get(t).add(Angles.wrapDelta(a.yaws[t] - cur.facing[t]));
            }
        }
        ratedSpread = used;
        double[][] errors = new double[n][];
        for (int t = 0; t < n; t++) {
            List<Double> l = per.get(t);
            errors[t] = new double[l.size()];
            for (int i = 0; i < errors[t].length; i++) errors[t][i] = l.get(i);
        }
        return errors;
    }

    public int ratedSpread() {
        return ratedSpread;
    }

    public void requestDeepChecks() {
        Current cur = current;
        ExactJumpModel exact = engine.exactModel();
        if (cur == null || cur.snapshot == null || exact == null) return;
        List<TurnAttempt> candidates = new ArrayList<TurnAttempt>(document.top());
        candidates.addAll(document.favourites());
        for (TurnAttempt a : candidates) {
            if (a.solved() || !a.hasState() || !a.alignedTo(cur) || deepDone.contains(a) || !deepQueue.add(a)) continue;
            deep.submit(() -> {
                double[] result = DeepCheck.solve(exact, cur, a, DeepCheck.BUDGET_NANOS, null);
                if (result != null) {
                    a.solvedOffset = result;
                    deepDirty = true;
                } else {
                    deepDone.add(a);
                }
                deepQueue.remove(a);
            });
        }
    }

    public boolean isDeepChecking(TurnAttempt a) {
        return deepQueue.contains(a);
    }

    public boolean isRating() {
        return rating;
    }

    public void select(int number) {
        selectedNumber = number;
    }

    public int selectedNumber() {
        return selectedNumber;
    }

    public TurnAttempt selectedAttempt() {
        int number = selectedNumber;
        if (number < 0) return null;
        List<TurnAttempt> all = document.attempts();
        if (number <= all.size() && all.get(number - 1).number == number) return all.get(number - 1);
        for (TurnAttempt a : all) if (a.number == number) return a;
        return null;
    }

    public ForwardModel forwardModel() {
        return engine.forwardModel();
    }

    public Current current() {
        return current;
    }
}
