package de.legoshi.parkourcalc.core.sim;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BlockOverride {

    public static final String DEFAULT_BLOCK = "minecraft:stone";
    public static final String AIR = "minecraft:air";
    public static final int NO_END = 0;
    public static final String PLAYER = "player";

    public final int startTick;
    public final int endTick;
    public final int x;
    public final int y;
    public final int z;
    public final String block;
    public final String before;
    public final boolean player;

    public BlockOverride(int startTick, int endTick, int x, int y, int z, String block, String before) {
        this(startTick, endTick, x, y, z, block, before, false);
    }

    public BlockOverride(int startTick, int endTick, int x, int y, int z, String block, String before, boolean player) {
        this.startTick = startTick;
        this.endTick = endTick;
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block == null ? "" : block.trim();
        this.before = before == null ? "" : before.trim();
        this.player = player;
    }

    public boolean activeAtRow(int row) {
        int tick = row + 1;
        return tick >= startTick && (endTick == NO_END || tick <= endTick);
    }

    public boolean aheadOfRow(int row) {
        return startTick > row + 1;
    }

    public BlockPatch patchAtRow(int row) {
        boolean active = activeAtRow(row);
        String spec = active ? block : before;
        if (!Spec.parse(spec).valid()) return null;
        return new BlockPatch(x, y, z, spec, active ? before : block);
    }

    public BlockOverride withTicks(int start, int end) {
        return new BlockOverride(start, end, x, y, z, block, before, player);
    }

    public BlockOverride withPos(int nx, int ny, int nz) {
        return new BlockOverride(startTick, endTick, nx, ny, nz, block, before, player);
    }

    public BlockOverride withBlock(String spec) {
        return new BlockOverride(startTick, endTick, x, y, z, spec, before, player);
    }

    public BlockOverride withBefore(String spec) {
        return new BlockOverride(startTick, endTick, x, y, z, block, spec, player);
    }

    public BlockOverride withPlayer(boolean byPlayer) {
        return new BlockOverride(startTick, endTick, x, y, z, block, before, byPlayer);
    }

    public Spec spec() {
        return Spec.parse(block);
    }

    public Spec beforeSpec() {
        return Spec.parse(before);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlockOverride)) return false;
        BlockOverride g = (BlockOverride) o;
        return startTick == g.startTick && endTick == g.endTick && x == g.x && y == g.y && z == g.z
                && block.equals(g.block) && before.equals(g.before) && player == g.player;
    }

    @Override
    public int hashCode() {
        int h = startTick;
        h = h * 31 + endTick;
        h = h * 31 + x;
        h = h * 31 + y;
        h = h * 31 + z;
        h = h * 31 + block.hashCode();
        h = h * 31 + before.hashCode();
        return h * 31 + (player ? 1 : 0);
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

        public boolean isAir() {
            return AIR.equals(id);
        }

        public String shortName() {
            int colon = id.indexOf(':');
            String name = colon >= 0 ? id.substring(colon + 1) : id;
            name = name.replace('_', ' ');
            if (name.isEmpty()) return name;
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        public String displayName(Spec defaults) {
            StringBuilder sb = new StringBuilder(shortName());
            for (Map.Entry<String, String> e : properties.entrySet()) {
                String base = defaults != null && defaults.valid() ? defaults.properties.get(e.getKey()) : null;
                if (e.getValue().equals(base)) continue;
                if ("true".equals(e.getValue())) {
                    sb.append(' ').append(e.getKey());
                } else if ("false".equals(e.getValue())) {
                    if (base != null) sb.append(" not ").append(e.getKey());
                } else {
                    sb.append(' ').append(e.getKey()).append(' ').append(e.getValue());
                }
            }
            return sb.toString();
        }

        public Spec withProperty(String key, String value) {
            Map<String, String> next = new LinkedHashMap<String, String>(properties);
            next.put(key, value);
            return new Spec(id, next, error);
        }

        public String text() {
            return format(id, properties);
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
