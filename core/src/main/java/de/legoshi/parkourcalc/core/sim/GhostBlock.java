package de.legoshi.parkourcalc.core.sim;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class GhostBlock {

    public static final String DEFAULT_BLOCK = "minecraft:air";

    public final int startTick;
    public final int endTick;
    public final int x;
    public final int y;
    public final int z;
    public final String block;

    public GhostBlock(int startTick, int endTick, int x, int y, int z, String block) {
        this.startTick = startTick;
        this.endTick = endTick;
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block == null ? "" : block.trim();
    }

    public boolean activeAtRow(int row) {
        int tick = row + 1;
        return tick >= startTick && tick <= endTick;
    }

    public GhostBlock withTicks(int start, int end) {
        return new GhostBlock(start, end, x, y, z, block);
    }

    public GhostBlock withPos(int nx, int ny, int nz) {
        return new GhostBlock(startTick, endTick, nx, ny, nz, block);
    }

    public GhostBlock withBlock(String spec) {
        return new GhostBlock(startTick, endTick, x, y, z, spec);
    }

    public Spec spec() {
        return Spec.parse(block);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GhostBlock)) return false;
        GhostBlock g = (GhostBlock) o;
        return startTick == g.startTick && endTick == g.endTick && x == g.x && y == g.y && z == g.z
                && block.equals(g.block);
    }

    @Override
    public int hashCode() {
        int h = startTick;
        h = h * 31 + endTick;
        h = h * 31 + x;
        h = h * 31 + y;
        h = h * 31 + z;
        return h * 31 + block.hashCode();
    }

    public static final class Spec {

        public final String id;
        public final Map<String, String> properties;
        public final String error;

        private Spec(String id, Map<String, String> properties, String error) {
            this.id = id;
            this.properties = Collections.unmodifiableMap(properties);
            this.error = error;
        }

        public boolean valid() {
            return error == null;
        }

        public static Spec parse(String text) {
            Map<String, String> props = new LinkedHashMap<String, String>();
            String s = text == null ? "" : text.trim();
            if (s.isEmpty()) return new Spec("", props, "Block is empty");
            String name = s;
            int open = s.indexOf('[');
            if (open >= 0) {
                if (!s.endsWith("]")) return new Spec("", props, "Missing ] after the block states");
                name = s.substring(0, open).trim();
                String body = s.substring(open + 1, s.length() - 1).trim();
                if (!body.isEmpty()) {
                    for (String part : body.split(",")) {
                        int eq = part.indexOf('=');
                        if (eq <= 0 || eq == part.length() - 1) {
                            return new Spec("", props, "Block state '" + part.trim() + "' is not key=value");
                        }
                        props.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT),
                                part.substring(eq + 1).trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            name = name.toLowerCase(Locale.ROOT);
            if (name.isEmpty()) return new Spec("", props, "Block id is empty");
            if (name.indexOf(':') < 0) name = "minecraft:" + name;
            return new Spec(name, props, null);
        }

        public static String format(String id, Map<String, String> properties) {
            StringBuilder sb = new StringBuilder(id);
            if (properties != null && !properties.isEmpty()) {
                sb.append('[');
                boolean first = true;
                for (Map.Entry<String, String> e : properties.entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(e.getKey()).append('=').append(e.getValue());
                    first = false;
                }
                sb.append(']');
            }
            return sb.toString();
        }
    }
}
