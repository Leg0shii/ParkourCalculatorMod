package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver.Candidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CertifyLadder {

    static final int SOURCE_CHANGE = 0;
    static final int SOURCE_COST = 1;
    static final int SOURCE_VALUE = 2;
    static final int SOURCE_MOMENTUM = 3;
    static final int SOURCES = 4;

    static final Comparator<Candidate> BY_VALUE = (a, b) -> {
        int byViol = Double.compare(a.byteViol, b.byteViol);
        if (byViol != 0) return byViol;
        if (a.familyCost != b.familyCost) return Integer.compare(a.familyCost, b.familyCost);
        return Integer.compare(a.presses, b.presses);
    };

    interface Gate {
        boolean closed(String key);
    }

    static final class Ticket {
        final Candidate candidate;
        final String key;
        final int pass;
        final int source;
        final long budget;

        Ticket(Candidate candidate, String key, int pass, int source, long budget) {
            this.candidate = candidate;
            this.key = key;
            this.pass = pass;
            this.source = source;
            this.budget = budget;
        }
    }

    private final class Pass {
        final long budget;
        final double weight;
        final List<Candidate> change;
        final List<Candidate> byCost;
        final List<Candidate> value;
        final List<Candidate> momentum;
        boolean changeDirty;
        boolean costDirty;
        boolean valueDirty;
        boolean momentumDirty;
        int slot;
        double finish;
        int flown;

        Pass(long budget, double weight, List<Candidate> change, List<Candidate> byCost, List<Candidate> value,
             List<Candidate> momentum) {
            this.budget = budget;
            this.weight = weight;
            this.change = change;
            this.byCost = byCost;
            this.value = value;
            this.momentum = momentum;
        }

        void add(Candidate c) {
            if (c.momentum && ownMomentumTurn) {
                momentum.add(c);
                momentumDirty = true;
            } else if (!c.family) {
                change.add(c);
                changeDirty = true;
            } else {
                byCost.add(c);
                costDirty = true;
                if (StructurePoolDriver.certifyTier(cfg, c) == StructurePoolDriver.SCREEN_EXACT_TIER) {
                    value.add(c);
                    valueDirty = true;
                }
            }
        }

        void sort() {
            if (changeDirty) change.sort(rank);
            if (costDirty) byCost.sort(rank);
            if (valueDirty) value.sort(BY_VALUE);
            if (momentumDirty) momentum.sort(rank);
            changeDirty = false;
            costDirty = false;
            valueDirty = false;
            momentumDirty = false;
        }

        List<Candidate> list(int source) {
            return source == SOURCE_CHANGE ? change : source == SOURCE_COST ? byCost : source == SOURCE_VALUE ? value
                    : momentum;
        }
    }

    private final StructurePoolDriver.Config cfg;
    private final Comparator<Candidate> rank;
    private final boolean ownMomentumTurn;
    private final Pass[] passes;
    private final Map<String, Integer> passesDone = new HashMap<>();
    private double clock;

    CertifyLadder(StructurePoolDriver.Config cfg, long[] budgets, double[] weights, boolean ownMomentumTurn,
                  List<Candidate> change, List<Candidate> byCost, List<Candidate> value, List<Candidate> momentum) {
        this.cfg = cfg;
        this.rank = StructurePoolDriver.rankOrder(cfg);
        this.ownMomentumTurn = ownMomentumTurn;
        this.passes = new Pass[budgets.length];
        passes[0] = new Pass(budgets[0], weights[0], change, byCost, value, momentum);
        for (int index = 1; index < budgets.length; index++) {
            passes[index] = new Pass(budgets[index], weights[index], new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>());
        }
    }

    int passCount() {
        return passes.length;
    }

    long budget(int pass) {
        return passes[pass].budget;
    }

    int flown(int pass) {
        return passes[pass].flown;
    }

    int waiting(int pass) {
        Pass p = passes[pass];
        return p.change.size() + p.byCost.size() + p.momentum.size();
    }

    void offer(Candidate c) {
        passes[0].add(c);
    }

    void touchFirst() {
        passes[0].changeDirty = true;
        passes[0].costDirty = true;
        passes[0].valueDirty = true;
        passes[0].momentumDirty = true;
    }

    void sortFirst() {
        passes[0].sort();
    }

    void sort() {
        for (Pass pass : passes) pass.sort();
    }

    Ticket next(Gate gate, boolean changeOpen) {
        boolean[] asked = new boolean[passes.length];
        for (int k = 0; k < passes.length; k++) {
            int pick = -1;
            double start = 0.0;
            for (int index = 0; index < passes.length; index++) {
                if (asked[index]) continue;
                double at = Math.max(passes[index].finish, clock);
                if (pick < 0 || at < start) {
                    pick = index;
                    start = at;
                }
            }
            asked[pick] = true;
            Ticket t = from(pick, gate, changeOpen);
            if (t == null) continue;
            Pass pass = passes[pick];
            clock = start;
            pass.finish = start + t.budget / pass.weight;
            pass.flown++;
            return t;
        }
        return null;
    }

    Ticket anyOfFirst(Gate gate, boolean family) {
        int source = family ? SOURCE_COST : SOURCE_CHANGE;
        Candidate c = poll(passes[0].list(source), false, 0, gate);
        if (c == null) return null;
        passes[0].flown++;
        return ticket(c, 0, source);
    }

    private Ticket from(int index, Gate gate, boolean changeOpen) {
        Pass pass = passes[index];
        for (int k = 0; k < SOURCES; k++) {
            int source = pass.slot;
            pass.slot = (pass.slot + 1) % SOURCES;
            if (source == SOURCE_CHANGE && index == 0 && !changeOpen) continue;
            boolean goodOnly = index == 0 && (source == SOURCE_CHANGE || source == SOURCE_COST);
            Candidate c = poll(pass.list(source), goodOnly, index, gate);
            if (c != null) return ticket(c, index, source);
        }
        return null;
    }

    private Ticket ticket(Candidate c, int index, int source) {
        long budget = passes[index].budget;
        if (index == 0 && c.flash && cfg.repairFlashNanos > 0) budget = Math.min(budget, cfg.repairFlashNanos);
        return new Ticket(c, PoolCertifier.scheduleKey(c.combos, c.engage, false), index, source, budget);
    }

    private Candidate poll(List<Candidate> from, boolean goodOnly, int index, Gate gate) {
        int i = 0;
        Candidate pick = null;
        while (i < from.size()) {
            Candidate c = from.get(i);
            if (goodOnly && StructurePoolDriver.certifyTier(cfg, c) >= StructurePoolDriver.SCREEN_MISS_TIER) break;
            i++;
            String key = PoolCertifier.scheduleKey(c.combos, c.engage, false);
            if (gate.closed(key)) continue;
            Integer done = passesDone.get(key);
            if (done != null && done > index) continue;
            pick = c;
            break;
        }
        if (i > 0) from.subList(0, i).clear();
        return pick;
    }

    boolean missed(Ticket t) {
        int next = t.pass + 1;
        passesDone.put(t.key, next);
        if (next >= passes.length) return false;
        passes[next].add(t.candidate);
        return true;
    }

    boolean settle(long shortBudget, List<Candidate> change, List<Candidate> family, List<Candidate> retry) {
        Pass first = passes[0];
        java.util.Set<Candidate> queued = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        queued.addAll(change);
        queued.addAll(family);
        if (first.momentum != family) {
            for (Candidate c : first.momentum) if (queued.add(c)) family.add(c);
            first.momentum.clear();
        }
        boolean retried = false;
        for (int index = 1; index < passes.length; index++) {
            Pass pass = passes[index];
            if (pass.budget > shortBudget) {
                retry.addAll(pass.change);
                retry.addAll(pass.byCost);
                retry.addAll(pass.momentum);
                retried |= pass.change.size() + pass.byCost.size() + pass.momentum.size() > 0;
            } else {
                for (Candidate c : pass.change) if (queued.add(c)) change.add(c);
                for (Candidate c : pass.byCost) if (queued.add(c)) family.add(c);
                for (Candidate c : pass.momentum) if (queued.add(c)) family.add(c);
            }
            pass.change.clear();
            pass.byCost.clear();
            pass.value.clear();
            pass.momentum.clear();
        }
        first.changeDirty = true;
        first.costDirty = true;
        return retried;
    }
}
