package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintText;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.profile.AttemptSampler;
import de.legoshi.parkourcalc.core.anglesolver.profile.TurnProfile;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.render.ConstraintShapes;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
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
        public final int[] speedAmp;
        public final int[] jumpAmp;
        public final int[] leadKeys;
        public final TurnReference.Landing landing;
        public final TurnProfile profile;
        public final AttemptSampler.Stats attempts;
        public final double pixelDeg;
        final AngleSolverEngine.PathSnapshot snapshot;

        Current(int startTick, int tasFirstTick, double[] facing, int[] keys, boolean[] checkKeys, int[] optionalKeys,
                boolean[] checkYaw, boolean[] still, boolean[] jumpTicks, int[] speedAmp, int[] jumpAmp,
                int[] leadKeys, TurnReference.Landing landing, TurnProfile profile, AttemptSampler.Stats attempts,
                double pixelDeg, AngleSolverEngine.PathSnapshot snapshot) {
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
            this.speedAmp = speedAmp;
            this.jumpAmp = jumpAmp;
            this.leadKeys = leadKeys;
            this.landing = landing;
            this.profile = profile;
            this.attempts = attempts;
            this.pixelDeg = pixelDeg;
            this.snapshot = snapshot;
        }

        Current withAttempts(AttemptSampler.Stats stats, double pixelDeg) {
            return new Current(startTick, tasFirstTick, facing, keys, checkKeys, optionalKeys, checkYaw, still,
                    jumpTicks, speedAmp, jumpAmp, leadKeys, landing, profile, stats, pixelDeg, snapshot);
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

        public int tasTick(int t) {
            return (tasFirstTick < 0 ? startTick : tasFirstTick) + t;
        }
    }

    private static final class Built {
        final TurnReference ref = new TurnReference();
        AngleSolverEngine.PathSnapshot snapshot;
    }

    public static final class SetupCheck {
        public static final class Item {
            public final String label;
            public final boolean ok;
            public final String detail;

            Item(String label, boolean ok, String detail) {
                this.label = label;
                this.ok = ok;
                this.detail = detail;
            }
        }

        public final List<Item> items;
        public final boolean ok;

        SetupCheck(List<Item> items) {
            this.items = java.util.Collections.unmodifiableList(items);
            boolean all = true;
            for (Item i : items) all &= i.ok;
            this.ok = all;
        }
    }

    public static final double MODEL_AGREEMENT = 1e-6;

    private final AngleSolverEngine engine;
    private final AngleSolverState state;
    private final InputData inputs;
    private final BooleanSupplier enabled;
    private final Supplier<Float> sensitivity;
    private final Supplier<Integer> sampleCount;
    private final Supplier<Integer> spreadAttempts;
    private final TurnProfileStore store;
    private final Supplier<String> tasName;
    private final IntFunction<TickState> tickState;
    private final Supplier<String> tasSignature;
    private java.util.function.BiConsumer<Integer, Integer> surfaceApplier = (from, to) -> { };
    private volatile SetupCheck lastCheck;
    private volatile String lastCheckSignature;
    private volatile String checkedSignature;
    private volatile String currentSignature;
    private long constraintSignature = Long.MIN_VALUE;
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
    private final AtomicInteger deepGeneration = new AtomicInteger();
    private volatile AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final AtomicReference<Current> current = new AtomicReference<Current>();
    private final Object rateLock = new Object();
    private volatile boolean rating;
    private int ratedVersion = -1;
    private int ratedSpreadSetting = -1;
    private int ratedChunkSetting = -1;
    private volatile int ratedSpread;
    private volatile double[][] ratePool;
    private volatile boolean rateMore;
    private static final int RATE_TARGET_LANDINGS = 100;
    private static final long RATE_MAX_SAMPLES = 5_000_000L;
    private boolean loaded;
    private boolean adopted;
    private boolean carryAllowed;
    private boolean wasEnabled;
    private String loadedName;
    private String lastError;
    private volatile String storeError;
    private volatile int selectedNumber = -1;

    public TurnProfileController(AngleSolverEngine engine, AngleSolverState state, InputData inputs,
                                 BooleanSupplier enabled, Supplier<Float> sensitivity,
                                 Supplier<Integer> sampleCount, Supplier<Integer> spreadAttempts,
                                 TurnProfileStore store, Supplier<String> tasName) {
        this(engine, state, inputs, enabled, sensitivity, sampleCount, spreadAttempts, store, tasName, t -> null,
                () -> "");
    }

    public TurnProfileController(AngleSolverEngine engine, AngleSolverState state, InputData inputs,
                                 BooleanSupplier enabled, Supplier<Float> sensitivity,
                                 Supplier<Integer> sampleCount, Supplier<Integer> spreadAttempts,
                                 TurnProfileStore store, Supplier<String> tasName, IntFunction<TickState> tickState,
                                 Supplier<String> tasSignature) {
        this.tickState = tickState;
        this.tasSignature = tasSignature;
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
        boolean on = enabled.getAsBoolean();
        if (on) currentSignature = tasSignature.get();
        if (on && (!wasEnabled || constraintSignature() != constraintSignature)) refresh();
        wasEnabled = on;
        if (!on && !document.hasPending() && !document.isReferenceDirty() && !deepDirty) return;
        sync();
        autoRate();
        requestDeepChecks();
    }

    private void sync() {
        ensureLoaded();
        if (deepDirty && deepQueue.isEmpty()) {
            deepDirty = false;
            document.recountFailed();
            document.markDirty();
        }
        if (store == null || loadedName == null || storeError != null) return;
        final String name = loadedName;
        if (document.isReferenceDirty()) {
            final TurnReference ref = document.reference().copy();
            final List<TurnAttempt> all = new ArrayList<TurnAttempt>(document.attempts());
            document.markClean();
            io.submit(() -> {
                if (!store.save(name, ref, all)) storeError = writeError();
            });
        } else if (document.hasPending()) {
            final List<TurnAttempt> added = document.drainPending();
            final TurnReference ref = document.reference().copy();
            final List<TurnAttempt> all = new ArrayList<TurnAttempt>(document.attempts());
            io.submit(() -> {
                if (!store.append(name, added) && !store.save(name, ref, all)) storeError = writeError();
            });
        }
    }

    private String writeError() {
        String e = store.lastError();
        return e == null ? "write failed" : e;
    }

    public void onTasSaved(String name) {
        carryAllowed = name != null && !name.isEmpty() && !Objects.equals(name, loadedName);
    }

    public void onTasReplaced() {
        carryAllowed = false;
        adopted = false;
        loaded = false;
        selectedNumber = -1;
        clearCheck();
    }

    private void clearCheck() {
        lastCheck = null;
        lastCheckSignature = null;
        checkedSignature = null;
    }

    public boolean lastCheckCurrent() {
        String at = lastCheckSignature;
        return lastCheck != null && at != null && at.equals(currentSignature);
    }

    public void onTasDeleted(String name) {
        if (store != null) store.moveToTrash(name);
        if (Objects.equals(name, loadedName)) onTasReplaced();
    }

    private void ensureLoaded() {
        String name = tasName.get();
        if (name != null && name.isEmpty()) name = null;
        if (loaded && Objects.equals(name, loadedName)) return;
        boolean carry = carryAllowed && loaded && name != null && store != null && !Objects.equals(name, loadedName);
        carryAllowed = false;
        loaded = true;
        adopted = false;
        loadedName = name;
        storeError = null;
        clearCheck();
        deepDirty = false;
        selectedNumber = -1;
        invalidateDeepChecks();
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

    private long constraintSignature() {
        long h = 17;
        List<Integer> ticks = new ArrayList<Integer>(state.populatedTicks());
        java.util.Collections.sort(ticks);
        for (int tick : ticks) {
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            h = h * 31 + tick;
            for (Constraint c : tc.getConstraints()) {
                h = h * 31 + c.getField().ordinal();
                h = h * 31 + c.getOp().ordinal();
                h = h * 31 + (c.isEnabled() ? 1 : 0);
                h = h * 31 + Double.doubleToLongBits(c.isRange() ? c.getLo() : c.getValue());
                h = h * 31 + Double.doubleToLongBits(c.isRange() ? c.getHi() : 0.0);
            }
        }
        return h;
    }

    public boolean isChecked() {
        String checked = checkedSignature;
        return checked != null && checked.equals(currentSignature);
    }

    public SetupCheck lastCheck() {
        return lastCheck;
    }

    public void setSurfaceApplier(java.util.function.BiConsumer<Integer, Integer> applier) {
        surfaceApplier = applier != null ? applier : (from, to) -> { };
    }

    public SetupCheck check() {
        refresh();
        List<InputRow> rows = inputs.getRows();
        int first = -1;
        int last = -1;
        for (int t = 0; t < rows.size(); t++) {
            if (!rows.get(t).isOnejumpFlagged()) continue;
            if (first < 0) first = t;
            last = t;
        }
        boolean flagged = first >= 0;
        TurnReference ref = document.reference();
        TurnReference.Landing stored = flagged ? ref.landing() : null;
        if (stored != null) {
            surfaceApplier.accept(first, ref.tasFirstTick() + stored.tick);
            refresh();
        }
        currentSignature = tasSignature.get();
        List<SetupCheck.Item> items = new ArrayList<SetupCheck.Item>();
        items.add(new SetupCheck.Item("Keys and Face ticks are flagged", flagged,
                flagged ? "ticks " + (first + 1) + " to " + (last + 1) : "flag them in the Keys and Face columns of the input table"));
        Current cur = current.get();
        boolean built = flagged && cur != null && cur.n == last - first + 1 && cur.tasFirstTick == first;
        items.add(new SetupCheck.Item("The TAS path covers the flagged ticks", built,
                built ? cur.n + " ticks" : lastError != null ? lastError : "the rows from the first to the last flagged tick could not be read as a path"));
        TurnReference.Landing landing = built ? ref.landing() : null;
        int landingTick = landing == null ? -1 : ref.tasFirstTick() + landing.tick;
        items.add(new SetupCheck.Item("A landing box follows the flagged ticks", landing != null,
                landing != null ? landing.label(ref.tasFirstTick()) : "select the landing tick, look at the landing block and press B"));
        TickState landState = landingTick >= 0 ? tickState.apply(landingTick) : null;
        items.add(new SetupCheck.Item("The rows reach the landing tick", landState != null,
                landState != null ? "tick " + (landingTick + 1) : landingTick < 0 ? "no landing tick"
                        : "add rows up to tick " + (landingTick + 1)));
        String violated = built && landingTick >= 0 ? simulationViolation(first, landingTick) : "no path";
        items.add(new SetupCheck.Item("The simulation meets every constraint", violated == null,
                violated == null ? "every constraint from tick " + (first + 2) + " to tick " + (landingTick + 1) : violated));
        boolean modelLands = built && cur.profile != null && cur.profile.lands;
        items.add(new SetupCheck.Item("The solver lands the TAS too", modelLands,
                modelLands ? "the solver replays the rows with its own physics and lands in the box"
                        : !built || cur.profile == null ? "the solver could not replay the rows"
                        : "the solver replays the rows with its own physics and misses the box, solve the facings again or fix the Slip column"));
        String disagree = built && cur.snapshot != null ? modelDisagreement(cur) : "the solver could not replay the rows";
        items.add(new SetupCheck.Item("The solver's replay matches the simulation", disagree == null,
                disagree == null ? "same position on every flagged tick" : disagree));
        TickState startState = flagged ? tickState.apply(first) : null;
        boolean grounded = startState != null && startState.onGround;
        items.add(new SetupCheck.Item("The first flagged tick is on the ground", grounded,
                grounded ? "tick " + (first + 1) : startState == null ? "no simulated state at the first flagged tick"
                        : "the first flagged tick is in the air, flag from a tick on the ground"));
        SetupCheck result = new SetupCheck(items);
        lastCheck = result;
        lastCheckSignature = currentSignature;
        checkedSignature = result.ok ? currentSignature : null;
        return result;
    }

    private String simulationViolation(int from, int to) {
        List<Integer> ticks = new ArrayList<Integer>(state.populatedTicks());
        java.util.Collections.sort(ticks);
        for (int tick : ticks) {
            if (tick <= from || tick > to) continue;
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            TickState st = tickState.apply(tick);
            if (st == null) return "no simulated state at tick " + (tick + 1);
            for (Constraint c : tc.getConstraints()) {
                if (!c.isEnabled()) continue;
                if (c.getField() != Constraint.Field.X && c.getField() != Constraint.Field.Z) continue;
                Constraint r = c;
                if (c.isRelative()) {
                    TickState refState = tickState.apply(c.getRefTick());
                    if (refState == null) return "tick " + (tick + 1) + ": relative constraint without a simulated reference tick";
                    double baseValue = c.getField() == Constraint.Field.X ? refState.position.x : refState.position.z;
                    r = c.copy();
                    r.setRefTick(null);
                    r.setValue(r.getValue() + baseValue);
                    double lo = r.getLo() + baseValue;
                    double hi = r.getHi() + baseValue;
                    r.setLo(lo);
                    r.setHi(hi);
                }
                if (!ConstraintShapes.satisfied(r, st.position)) {
                    double v = c.getField() == Constraint.Field.X ? st.position.x : st.position.z;
                    return "tick " + (tick + 1) + ": " + c.getField().label + " is " + ConstraintText.fixedStat(v)
                            + ", wanted " + bounds(r);
                }
            }
        }
        return null;
    }

    private static String bounds(Constraint c) {
        if (c.isRange()) return ConstraintText.fixedStat(c.getLo()) + " to " + ConstraintText.fixedStat(c.getHi());
        return c.getOp().name() + " " + ConstraintText.fixedStat(c.getValue());
    }

    private String modelDisagreement(Current cur) {
        JumpPhysicsInputs sc = cur.snapshot.spec.asScenario();
        ForwardPath p = engine.forwardModel().forward(sc, sc.toGameFacings(cur.facing.clone()));
        double worst = 0.0;
        int worstTick = -1;
        for (int k = 0; k < p.posX.length; k++) {
            int tick = cur.tasFirstTick + k;
            TickState st = tickState.apply(tick);
            if (st == null) return "no simulated state at tick " + (tick + 1);
            double d = Math.max(Math.abs(p.posX[k] - st.position.x), Math.abs(p.posZ[k] - st.position.z));
            if (d > worst) {
                worst = d;
                worstTick = tick;
            }
        }
        if (worst <= MODEL_AGREEMENT) return null;
        return "they differ by " + ConstraintText.fixedStat(worst) + " at tick " + (worstTick + 1)
                + ": the Slip column (ground or air per tick) or the start state does not match the simulation";
    }

    public void refresh() {
        if (!enabled.getAsBoolean()) return;
        constraintSignature = constraintSignature();
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
        if (built.ref.isEmpty() && !ref.isEmpty()) {
            if (lastError == null) {
                lastError = ref.tasFirstTick() < 0
                        ? "the onejump file predates tick alignment, flag the Keys and Face ticks again"
                        : "no Keys or Face ticks flagged, the stored onejump is kept";
            }
            current.set(null);
            return;
        }
        if (!ref.sameAs(built.ref)) {
            boolean spanMoved = !ref.isEmpty() && (ref.tasFirstTick() != built.ref.tasFirstTick()
                    || !TurnReference.sameLanding(ref.landing(), built.ref.landing()));
            ref.copyFrom(built.ref);
            if (spanMoved) {
                for (TurnAttempt a : document.attempts()) a.solvedOffset = null;
                invalidateDeepChecks();
            }
            document.touch();
        }
        if (ref.isEmpty()) {
            current.set(null);
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
        int[] speedAmp = new int[n];
        int[] jumpAmp = new int[n];
        AngleSolverEngine.PathSnapshot snap = built.snapshot;
        JumpPhysicsInputs sc = snap == null ? null : snap.spec.asScenario();
        for (int k = 0; k < n; k++) {
            InputRow r = ref.row(k);
            keys[k] = TurnReference.mask(r);
            checkKeys[k] = ref.checkKeys(r);
            optional[k] = ref.optionalKeys(r);
            checkYaw[k] = ref.checkYaw(r);
            still[k] = ref.still(r);
            jumps[k] = (keys[k] & TurnReference.KEY_JUMP) != 0 && (sc == null || !Double.isNaN(sc.slipAt(k)));
            speedAmp[k] = r.getSpeedAmplifier();
            jumpAmp[k] = r.getJumpBoostAmplifier();
        }
        TurnProfile profile = null;
        if (snap != null) {
            ForwardModel model = engine.forwardModel();
            profile = TurnProfile.compute(model, snap.spec, facing, null, false);
        }
        current.set(new Current(0, ref.tasFirstTick(), facing, keys, checkKeys, optional, checkYaw, still, jumps,
                speedAmp, jumpAmp, leadKeys(ref.tasFirstTick()), ref.landing(), profile, null,
                TurnProfile.pixelDeg(sensitivity.get()), snap));
    }

    private int[] leadKeys(int first) {
        List<InputRow> rows = inputs.getRows();
        int firstKeyed = -1;
        for (int t = 0; t < first && t < rows.size(); t++) {
            if ((TurnReference.mask(rows.get(t)) & ~TurnReference.KEY_SPRINT) != 0) {
                firstKeyed = t;
                break;
            }
        }
        int[] lead = new int[firstKeyed < 0 ? 0 : first - firstKeyed];
        for (int i = 0; i < lead.length; i++) lead[i] = TurnReference.mask(rows.get(firstKeyed + i));
        return lead;
    }

    private void invalidateDeepChecks() {
        deepGeneration.incrementAndGet();
        deepDone.clear();
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
        double[] facings = new double[n];
        for (int k = 0; k < n; k++) {
            InputRow r = rows.get(first + k).copy();
            r.setYawLocked(false);
            next.add(r);
            facings[k] = Angles.wrap(snap.yaws[k]);
        }
        ref.replace(next, facings);
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
                    if (!Double.isNaN(lo)) xLo = Double.isNaN(xLo) ? lo : Math.max(xLo, lo);
                    if (!Double.isNaN(hi)) xHi = Double.isNaN(xHi) ? hi : Math.min(xHi, hi);
                } else {
                    if (!Double.isNaN(lo)) zLo = Double.isNaN(zLo) ? lo : Math.max(zLo, lo);
                    if (!Double.isNaN(hi)) zHi = Double.isNaN(zHi) ? hi : Math.min(zHi, hi);
                }
            }
            TurnReference.Landing l = new TurnReference.Landing(tick - base, xLo, xHi, zLo, zHi);
            if (!l.isEmpty()) out.add(l);
        }
        return out;
    }

    public void clearAttempts() {
        document.clearAttempts();
        invalidateDeepChecks();
    }

    public void rate() {
        Current cur = current.get();
        if (cur == null || !cur.canRate()) return;
        int spreadLimit = spreadLimit();
        int chunk = chunkSize();
        ratePool = spread(cur, spreadLimit);
        ratedVersion = document.version();
        ratedSpreadSetting = spreadLimit;
        ratedChunkSetting = chunk;
        synchronized (rateLock) {
            if (rating) {
                rateMore = true;
                return;
            }
            rating = true;
            rateMore = false;
        }
        double pixelDeg = TurnProfile.pixelDeg(sensitivity.get());
        ForwardModel model = engine.forwardModel();
        AtomicBoolean cancel = cancelToken;
        int gen = generation.get();
        worker.submit(() -> {
            AttemptSampler.Stats total = cur.attempts;
            long seed = AttemptSampler.SEED + (total == null ? 0L : total.attempts);
            Current published = cur;
            while (!cancel.get() && gen == generation.get()) {
                rateMore = false;
                AttemptSampler.Stats part = AttemptSampler.sample(model, cur.snapshot.spec, cur.facing, ratePool, chunk, seed, cancel);
                if (cancel.get() || gen != generation.get()) return;
                seed += part.attempts;
                total = AttemptSampler.merge(total, part);
                Current next = cur.withAttempts(total, pixelDeg);
                if (!current.compareAndSet(published, next)) return;
                published = next;
                boolean enough = total.landings >= RATE_TARGET_LANDINGS || total.attempts >= RATE_MAX_SAMPLES;
                if (!enough) continue;
                synchronized (rateLock) {
                    if (rateMore) continue;
                    rating = false;
                    return;
                }
            }
            synchronized (rateLock) {
                if (gen == generation.get()) rating = false;
            }
        });
    }

    private int spreadLimit() {
        return Math.max(1, spreadAttempts.get());
    }

    private int chunkSize() {
        return Math.max(1000, sampleCount.get());
    }

    public void autoRate() {
        Current cur = current.get();
        if (rating || cur == null || !cur.canRate() || !isChecked()) return;
        if (ratedVersion == document.version() && ratedSpreadSetting == spreadLimit() && ratedChunkSetting == chunkSize()) return;
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
            for (int t = 0; t < n; t++) {
                double err = a.errorAt(cur, cur.startTick + t);
                if (!Double.isNaN(err)) per.get(t).add(err);
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
        Current cur = current.get();
        ExactJumpModel exact = engine.exactModel();
        if (cur == null || cur.snapshot == null || exact == null || !isChecked()) return;
        List<TurnAttempt> candidates = new ArrayList<TurnAttempt>(document.top());
        candidates.addAll(document.favourites());
        int gen = deepGeneration.get();
        for (TurnAttempt a : candidates) {
            if (a.solved() || !a.hasState() || !a.alignedTo(cur) || deepDone.contains(a) || !deepQueue.add(a)) continue;
            deep.submit(() -> {
                try {
                    double[] result = DeepCheck.solve(exact, cur, a, DeepCheck.BUDGET_NANOS, null);
                    if (gen != deepGeneration.get()) return;
                    if (result != null) {
                        a.solvedOffset = result;
                        deepDirty = true;
                    } else {
                        deepDone.add(a);
                    }
                } catch (RuntimeException e) {
                    deepDone.add(a);
                } finally {
                    deepQueue.remove(a);
                }
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
        if (number <= 0) return null;
        List<TurnAttempt> all = document.attempts();
        if (number <= all.size() && all.get(number - 1).number == number) return all.get(number - 1);
        for (TurnAttempt a : all) if (a.number == number) return a;
        return null;
    }

    public ForwardModel forwardModel() {
        return engine.forwardModel();
    }

    public Current current() {
        return current.get();
    }
}
