package de.legoshi.parkourcalc.core.anglesolver.runticks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RunTicksSearch<P> {

    public interface JumpOptions {
        boolean allows(int jumpIndex, int extraTicks);
        int maxAllowed(int jumpIndex);
    }

    public static final class Node<P> {
        private final int[] combo;
        private final int depth;
        private final int sum;
        private final double weight;
        private final P payload;

        private Node(int[] combo, int depth, int sum, double weight, P payload) {
            this.combo = combo;
            this.depth = depth;
            this.sum = sum;
            this.weight = weight;
            this.payload = payload;
        }

        public int[] combo() {
            return combo;
        }

        public int depth() {
            return depth;
        }

        public int sum() {
            return sum;
        }

        public P payload() {
            return payload;
        }
    }

    private final int jumpCount;
    private final int maxTicks;
    private final boolean minimize;
    private final JumpOptions options;

    public static final int MAX_UNCONSTRAINED_TICKS = 40;

    private final Deque<Node<P>> queue = new ArrayDeque<Node<P>>();
    private Node<P> current;
    private int target;
    private double completed;
    private int steps;
    private int successes;
    private int fullSolutions;
    private int furthestDepthReached;

    public RunTicksSearch(int jumpCount, int maxTicks, boolean minimize, JumpOptions options) {
        this.jumpCount = Math.max(0, jumpCount);
        this.maxTicks = Math.max(0, maxTicks);
        this.minimize = minimize;
        this.options = options;
        this.target = this.maxTicks;
        this.furthestDepthReached = 0;
        seed();
    }

    public int jumpCount() {
        return jumpCount;
    }

    public int target() {
        return target;
    }

    public int maxTicks() {
        return maxTicks;
    }

    public boolean isMinimizing() {
        return minimize;
    }

    public double progress() {
        return completed;
    }

    public int steps() {
        return steps;
    }

    public int successes() {
        return successes;
    }

    public int fullSolutions() {
        return fullSolutions;
    }

    public boolean hasNext() {
        return !queue.isEmpty();
    }

    public boolean nextRung() {
        if (!minimize) return false;
        int prefixCap = prefixCapFor(furthestDepthReached);
        if (target >= prefixCap || target >= MAX_UNCONSTRAINED_TICKS) {
            return false;
        }
        int maxTotal = maxRemaining(0);
        while (target < maxTotal && target < MAX_UNCONSTRAINED_TICKS) {
            target++;
            if (target > prefixCap) return false;
            seed();
            if (!queue.isEmpty()) return true;
        }
        return false;
    }

    private int prefixCapFor(int depth) {
        long sum = 0;
        for (int j = 0; j <= depth && j < jumpCount; j++) {
            int m = options.maxAllowed(j);
            if (m == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            sum += m;
            if (sum >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    private int maxRemaining(int fromJumpIndex) {
        long sum = 0;
        for (int j = fromJumpIndex; j < jumpCount; j++) {
            int m = options.maxAllowed(j);
            if (m == Integer.MAX_VALUE) return Integer.MAX_VALUE;
            sum += m;
            if (sum >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    public Node<P> take() {
        current = queue.removeFirst();
        steps++;
        return current;
    }

    public Node<P> current() {
        return current;
    }

    public void recordFailure() {
        if (current != null) completed += current.weight;
    }

    public void recordSuccess(P childPayload) {
        successes++;
        if (current == null) return;
        furthestDepthReached = Math.max(furthestDepthReached, current.depth);
        if (current.depth >= jumpCount) {
            fullSolutions++;
            completed += current.weight;
            return;
        }
        List<Integer> next = optionsFor(current.depth, current.sum);
        if (next.isEmpty()) {
            completed += current.weight;
            return;
        }
        double childWeight = current.weight / next.size();
        for (int extra : next) {
            int[] combo = current.combo.clone();
            combo[current.depth] = extra;
            queue.addFirst(new Node<P>(combo, current.depth + 1, current.sum + extra, childWeight, childPayload));
        }
    }

    private void seed() {
        queue.clear();
        current = null;
        completed = 0.0;
        if (jumpCount == 0) {
            completed = 1.0;
            return;
        }
        List<Integer> roots = optionsFor(0, 0);
        if (roots.isEmpty()) {
            completed = 1.0;
            return;
        }
        double weight = 1.0 / roots.size();
        for (int extra : roots) {
            int[] combo = new int[jumpCount];
            combo[0] = extra;
            queue.addFirst(new Node<P>(combo, 1, extra, weight, null));
        }
    }

    private List<Integer> optionsFor(int jumpIndex, int sum) {
        List<Integer> out = new ArrayList<Integer>();
        int budget = target - sum;
        if (budget < 0) return out;
        int maxRest = jumpIndex + 1 < jumpCount ? maxRemaining(jumpIndex + 1) : 0;
        if (minimize && jumpIndex == jumpCount - 1) {
            if (options.allows(jumpIndex, budget)) out.add(budget);
            return out;
        }
        for (int extra = budget; extra >= 0; extra--) {
            if (budget - extra > maxRest) continue;
            if (options.allows(jumpIndex, extra)) out.add(extra);
        }
        return out;
    }
}
