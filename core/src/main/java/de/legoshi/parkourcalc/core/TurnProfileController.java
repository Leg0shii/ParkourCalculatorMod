package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class TurnProfileController {

    public static final class Current {
        public final int startTick;
        public final int n;
        public final double[] facing;
        public final int[] keys;
        public final boolean[] checkKeys;
        public final boolean[] checkYaw;
        public final boolean[] still;
        public final boolean[] jumpTicks;
        public final TurnReference.Landing landing;
        public final TurnProfile profile;
        public final AttemptSampler.Stats attempts;
        public final double pixelDeg;
        final AngleSolverEngine.PathSnapshot snapshot;

        Current(int startTick, double[] facing, int[] keys, boolean[] checkKeys, boolean[] checkYaw, boolean[] still,
                boolean[] jumpTicks, TurnReference.Landing landing, TurnProfile profile, AttemptSampler.Stats attempts,
                double pixelDeg, AngleSolverEngine.PathSnapshot snapshot) {
            this.startTick = startTick;
            this.n = facing.length;
            this.facing = facing;
            this.keys = keys;
            this.checkKeys = checkKeys;
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
            return new Current(startTick, facing, keys, checkKeys, checkYaw, still, jumpTicks, landing, profile, stats,
                    pixelDeg, snapshot);
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
    }

    private final AngleSolverEngine engine;
    private final AngleSolverState state;
    private final InputData inputs;
    private final BooleanSupplier enabled;
    private final Supplier<Float> sensitivity;
    private final Supplier<AttemptSampler.Scatter> scatter;
    private final Supplier<Integer> attemptCount;
    private final TurnProfileStore store;
    private final Supplier<String> nameSetting;
    private final java.util.function.Consumer<String> nameSink;
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
    private final AtomicInteger generation = new AtomicInteger();
    private volatile AtomicBoolean cancelToken = new AtomicBoolean(false);
    private volatile Current current;
    private volatile boolean rating;
    private boolean loaded;
    private String loadedName;
    private String lastError;
    private volatile int selectedNumber = -1;

    public TurnProfileController(AngleSolverEngine engine, AngleSolverState state, InputData inputs,
                                 BooleanSupplier enabled, Supplier<Float> sensitivity,
                                 Supplier<AttemptSampler.Scatter> scatter, Supplier<Integer> attemptCount,
                                 TurnProfileStore store, Supplier<String> nameSetting,
                                 java.util.function.Consumer<String> nameSink) {
        this.engine = engine;
        this.state = state;
        this.inputs = inputs;
        this.enabled = enabled;
        this.sensitivity = sensitivity;
        this.scatter = scatter;
        this.attemptCount = attemptCount;
        this.store = store;
        this.nameSetting = nameSetting;
        this.nameSink = nameSink;
    }

    public TurnProfileDocument document() {
        ensureLoaded();
        return document;
    }

    public String name() {
        return loadedName;
    }

    public List<String> names() {
        return store == null ? new ArrayList<String>() : store.names();
    }

    public void open(String name) {
        nameSink.accept(name);
        ensureLoaded();
    }

    public void create(String name) {
        String safe = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) return;
        boolean exists = names().contains(safe);
        nameSink.accept(safe);
        ensureLoaded();
        if (!exists) {
            document.reset();
            document.touch();
            refresh();
            sync();
        }
    }

    public void delete() {
        String name = loadedName;
        if (name == null) return;
        if (store != null) store.delete(name);
        nameSink.accept("");
        ensureLoaded();
    }

    public String lastError() {
        return lastError;
    }

    public TurnProfileDocument.Stats stats() {
        return document.stats();
    }

    public void sync() {
        ensureLoaded();
        if (store == null || loadedName == null) return;
        final String name = loadedName;
        if (document.isReferenceDirty()) {
            final TurnReference ref = document.reference().copy();
            final List<TurnAttempt> all = new ArrayList<TurnAttempt>(document.attempts());
            document.markClean();
            io.submit(() -> store.save(name, ref, all));
        } else if (document.hasPending()) {
            final List<TurnAttempt> added = document.drainPending();
            io.submit(() -> {
                if (!store.append(name, added)) store.save(name, document.reference().copy(), new ArrayList<TurnAttempt>(document.attempts()));
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
        String name = nameSetting.get();
        if (name != null && name.isEmpty()) name = null;
        if (loaded && Objects.equals(name, loadedName)) return;
        loaded = true;
        loadedName = name;
        if (store != null && name != null) store.load(name, document);
        else document.reset();
        refresh();
    }

    public void refresh() {
        if (!enabled.getAsBoolean()) return;
        ensureLoaded();
        TurnReference ref = document.reference();
        cancelToken.set(true);
        cancelToken = new AtomicBoolean(false);
        generation.incrementAndGet();
        rating = false;
        if (ref.isEmpty()) {
            current = null;
            return;
        }
        int n = ref.size();
        double[] facing = Angles.wrapAll(ref.facings());
        int[] keys = new int[n];
        boolean[] checkKeys = new boolean[n];
        boolean[] checkYaw = new boolean[n];
        boolean[] still = new boolean[n];
        boolean[] jumps = new boolean[n];
        for (int k = 0; k < n; k++) {
            InputRow r = ref.row(k);
            keys[k] = TurnReference.mask(r);
            checkKeys[k] = ref.checkKeys(r);
            checkYaw[k] = ref.checkYaw(r);
            still[k] = ref.still(r);
            jumps[k] = (keys[k] & TurnReference.KEY_JUMP) != 0;
        }
        int tasFirst = ref.tasFirstTick();
        AngleSolverEngine.PathSnapshot snap = tasFirst >= 0 ? engine.snapshotPath(tasFirst, tasFirst + n) : null;
        if (snap != null && snap.yaws.length != n) snap = null;
        TurnProfile profile = null;
        if (snap != null) {
            ForwardModel model = engine.forwardModel();
            profile = TurnProfile.compute(model, snap.spec, facing, null, false);
        }
        current = new Current(0, facing, keys, checkKeys, checkYaw, still, jumps, ref.landing(), profile, null,
                TurnProfile.pixelDeg(sensitivity.get()), snap);
    }

    public boolean importFromTas(int first, int last) {
        ensureLoaded();
        return importFromTas(first, last, true);
    }

    private boolean importFromTas(int first, int last, boolean refresh) {
        List<InputRow> rows = inputs.getRows();
        if (first < 0 || last < first || last >= rows.size()) {
            lastError = "the TAS has no ticks " + (first + 1) + " to " + (last + 1);
            return false;
        }
        AngleSolverEngine.PathSnapshot snap = engine.snapshotPath(first, last + 1);
        if (snap == null || snap.yaws.length != last - first + 1) {
            lastError = "no path for ticks " + (first + 1) + " to " + (last + 1) + " in the TAS";
            return false;
        }
        List<InputRow> next = new ArrayList<InputRow>();
        for (int k = 0; k <= last - first; k++) {
            InputRow r = rows.get(first + k).copy();
            r.setYaw((float) Angles.wrap(snap.yaws[k]));
            r.setYawLocked(false);
            next.add(r);
        }
        TurnReference ref = document.reference();
        ref.replace(next, null, null);
        ref.setTasFirstTick(first);
        List<TurnReference.Landing> options = solverLandings(first, last + 1, first);
        if (!options.isEmpty()) ref.setLanding(options.get(options.size() - 1));
        lastError = null;
        document.touch();
        if (refresh) refresh();
        return true;
    }

    public List<TurnReference.Landing> solverLandings() {
        TurnReference ref = document.reference();
        int first = ref.tasFirstTick();
        if (ref.isEmpty() || first < 0) return new ArrayList<TurnReference.Landing>();
        return solverLandings(first, first + ref.size(), first);
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

    public void setLanding(TurnReference.Landing landing) {
        document.reference().setLanding(landing);
        document.touch();
        refresh();
    }

    public void referenceChanged() {
        document.touch();
        refresh();
    }

    public void clearAttempts() {
        document.clearAttempts();
    }

    public void rate() {
        Current cur = current;
        if (cur == null || !cur.canRate() || rating) return;
        double pixelDeg = TurnProfile.pixelDeg(sensitivity.get());
        AttemptSampler.Scatter sc = scatter.get();
        int count = attemptCount.get();
        ForwardModel model = engine.forwardModel();
        AtomicBoolean cancel = cancelToken;
        int gen = generation.get();
        rating = true;
        worker.submit(() -> {
            AttemptSampler.Stats stats = AttemptSampler.sample(model, cur.snapshot.spec, cur.facing,
                    cur.profile.held, pixelDeg, sc, count, cancel);
            if (gen != generation.get() || cancel.get()) return;
            current = cur.withAttempts(stats, pixelDeg);
            rating = false;
        });
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
