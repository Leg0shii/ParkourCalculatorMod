package de.legoshi.parkourcalc.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TurnProfileDocument {

    public static final int TOP = 10;

    public static final class Stats {
        public final int attempts;
        public final int landings;
        public final int inputFailures;
        public final double closest;
        public final int mouseAttempts;
        public final int mouseClears;
        public final int inputAttempts;
        public final int inputClears;

        Stats(int attempts, int landings, int inputFailures, double closest, int mouseAttempts, int mouseClears,
              int inputAttempts, int inputClears) {
            this.attempts = attempts;
            this.landings = landings;
            this.inputFailures = inputFailures;
            this.closest = closest;
            this.mouseAttempts = mouseAttempts;
            this.mouseClears = mouseClears;
            this.inputAttempts = inputAttempts;
            this.inputClears = inputClears;
        }

        public boolean hasClosest() {
            return !Double.isNaN(closest);
        }
    }

    private final TurnReference reference = new TurnReference();
    private final List<TurnAttempt> attempts = new ArrayList<>();
    private final List<TurnAttempt> view = Collections.unmodifiableList(attempts);
    private final List<TurnAttempt> top = new ArrayList<>();
    private final List<TurnAttempt> topView = Collections.unmodifiableList(top);
    private final List<TurnAttempt> pending = new ArrayList<>();
    private boolean referenceDirty;
    private int real;
    private int landings;
    private int inputFailures;
    private double closest = Double.NaN;
    private int mouseAttempts;
    private int mouseClears;
    private int inputAttempts;
    private int inputClears;
    private volatile Stats stats = new Stats(0, 0, 0, Double.NaN, 0, 0, 0, 0);
    private volatile int version;

    public TurnReference reference() {
        return reference;
    }

    public List<TurnAttempt> attempts() {
        return view;
    }

    public List<TurnAttempt> top() {
        return topView;
    }

    public Stats stats() {
        return stats;
    }

    public int version() {
        return version;
    }

    public int nextNumber() {
        return attempts.size() + 1;
    }

    public void add(TurnAttempt attempt) {
        attempts.add(attempt);
        pending.add(attempt);
        account(attempt);
        stats = snapshot();
        version++;
    }

    private Stats snapshot() {
        return new Stats(real, landings, inputFailures, closest, mouseAttempts, mouseClears, inputAttempts, inputClears);
    }

    private void zero() {
        real = 0;
        landings = 0;
        inputFailures = 0;
        closest = Double.NaN;
        mouseAttempts = 0;
        mouseClears = 0;
        inputAttempts = 0;
        inputClears = 0;
    }

    public void clearAttempts() {
        attempts.clear();
        pending.clear();
        top.clear();
        zero();
        stats = snapshot();
        referenceDirty = true;
        version++;
    }

    public void touch() {
        referenceDirty = true;
        version++;
    }

    public boolean isReferenceDirty() {
        return referenceDirty;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public List<TurnAttempt> drainPending() {
        List<TurnAttempt> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    public void markClean() {
        referenceDirty = false;
        pending.clear();
    }

    public void reset() {
        load(new TurnReference(), Collections.<TurnAttempt>emptyList());
    }

    public void load(TurnReference ref, List<TurnAttempt> list) {
        reference.copyFrom(ref);
        attempts.clear();
        pending.clear();
        top.clear();
        zero();
        for (TurnAttempt a : list) {
            attempts.add(a);
            account(a);
        }
        stats = snapshot();
        referenceDirty = false;
        version++;
    }

    private void account(TurnAttempt a) {
        if (a.macro == 1) {
            mouseAttempts++;
            if (a.landed) mouseClears++;
            return;
        }
        if (a.macro == 2) {
            inputAttempts++;
            if (a.landed) inputClears++;
            return;
        }
        real++;
        if (a.inputFailure) inputFailures++;
        if (a.landed) landings++;
        if (!a.hasMargin()) return;
        if (Double.isNaN(closest) || a.margin < closest) closest = a.margin;
        int at = top.size();
        while (at > 0 && top.get(at - 1).margin > a.margin) at--;
        if (at >= TOP) return;
        top.add(at, a);
        if (top.size() > TOP) top.remove(top.size() - 1);
    }
}
