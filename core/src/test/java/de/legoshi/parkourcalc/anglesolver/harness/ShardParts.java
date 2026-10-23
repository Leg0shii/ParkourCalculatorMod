package de.legoshi.parkourcalc.anglesolver.harness;

import java.util.HashSet;
import java.util.Set;

public final class ShardParts {

    private final Set<Integer> parts;
    private final int count;

    private ShardParts(Set<Integer> parts, int count) {
        this.parts = parts;
        this.count = count;
    }

    public static ShardParts of(Class<?> testClass) {
        String spec = System.getProperty("pkc.parts." + testClass.getSimpleName());
        if (spec == null) return new ShardParts(null, 1);
        int slash = spec.indexOf('/');
        Set<Integer> parts = new HashSet<>();
        for (String p : spec.substring(0, slash).split(",")) parts.add(Integer.parseInt(p.trim()));
        return new ShardParts(parts, Integer.parseInt(spec.substring(slash + 1).trim()));
    }

    public boolean keeps(int index) {
        return parts == null || parts.contains(index % count);
    }
}
