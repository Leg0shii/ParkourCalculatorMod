package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NodeType {

    public interface Factory {
        NodeRuntime create(ParamValues params);
    }

    public final String id;
    public final String label;
    public final NodeCategory category;
    public final InputRequirement requires;
    public final List<Branch> branches;
    public final List<ParamSpec> params;
    public final String budgetParam;
    public final Guarantee fallbackBranch;
    public final boolean entryMarker;
    public final boolean emitMarker;
    public final Factory factory;

    private NodeType(Builder b) {
        this.id = b.id;
        this.label = b.label;
        this.category = b.category;
        this.requires = b.requires;
        this.branches = Collections.unmodifiableList(new ArrayList<>(b.branches));
        this.params = Collections.unmodifiableList(new ArrayList<>(b.params));
        this.budgetParam = b.budgetParam;
        this.fallbackBranch = b.fallbackBranch;
        this.entryMarker = b.entryMarker;
        this.emitMarker = b.emitMarker;
        this.factory = b.factory;
    }

    public Branch branch(Guarantee g) {
        for (Branch br : branches) {
            if (br.label == g) return br;
        }
        return null;
    }

    public boolean budgetGuarded(ParamValues v) {
        return budgetParam != null && v.getInt(budgetParam) > 0;
    }

    public ParamValues defaultParams() {
        return new ParamValues(params);
    }

    public static Builder builder(String id, String label, NodeCategory category) {
        return new Builder(id, label, category);
    }

    public static final class Builder {
        private final String id;
        private final String label;
        private final NodeCategory category;
        private InputRequirement requires = InputRequirement.ANY;
        private final List<Branch> branches = new ArrayList<>();
        private final List<ParamSpec> params = new ArrayList<>();
        private String budgetParam;
        private Guarantee fallbackBranch;
        private boolean entryMarker;
        private boolean emitMarker;
        private Factory factory;

        private Builder(String id, String label, NodeCategory category) {
            this.id = id;
            this.label = label;
            this.category = category;
        }

        public Builder requires(InputRequirement r) {
            this.requires = r;
            return this;
        }

        public Builder branch(Branch b) {
            branches.add(b);
            return this;
        }

        public Builder param(ParamSpec p) {
            params.add(p);
            return this;
        }

        public Builder budgetParam(String key) {
            this.budgetParam = key;
            return this;
        }

        public Builder fallback(Guarantee g) {
            this.fallbackBranch = g;
            return this;
        }

        public Builder entry() {
            this.entryMarker = true;
            return this;
        }

        public Builder emit() {
            this.emitMarker = true;
            return this;
        }

        public Builder factory(Factory f) {
            this.factory = f;
            return this;
        }

        public NodeType build() {
            return new NodeType(this);
        }
    }
}
