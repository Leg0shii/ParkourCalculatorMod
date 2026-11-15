package de.legoshi.parkourcalc.core.save;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StructuralHash {

    private static final long SEED = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;
    private static final long NULL_MARK = 0x9e3779b97f4a7c15L;
    private static final Map<Class<?>, Field[]> FIELDS = new ConcurrentHashMap<>();

    private StructuralHash() {
    }

    public static long of(Object value) {
        return hash(SEED, value);
    }

    private static long mix(long h, long v) {
        return (h ^ v) * PRIME;
    }

    private static long hash(long h, Object o) {
        if (o == null) return mix(h, NULL_MARK);
        if (o instanceof String) {
            String s = (String) o;
            return mix(mix(h, s.length()), s.hashCode());
        }
        if (o instanceof Double) return mix(h, Double.doubleToLongBits((Double) o));
        if (o instanceof Float) return mix(h, Float.floatToIntBits((Float) o));
        if (o instanceof Long) return mix(h, (Long) o);
        if (o instanceof Number) return mix(h, ((Number) o).longValue());
        if (o instanceof Boolean) return mix(h, (Boolean) o ? 1231 : 1237);
        if (o instanceof Character) return mix(h, (Character) o);
        if (o instanceof Enum) return hash(h, ((Enum<?>) o).name());
        if (o instanceof Collection) {
            Collection<?> c = (Collection<?>) o;
            h = mix(h, c.size());
            for (Object e : c) h = hash(h, e);
            return h;
        }
        if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            h = mix(h, m.size());
            for (Map.Entry<?, ?> e : m.entrySet()) {
                h = hash(h, e.getKey());
                h = hash(h, e.getValue());
            }
            return h;
        }
        Class<?> type = o.getClass();
        if (type.isArray()) {
            int n = Array.getLength(o);
            h = mix(h, n);
            for (int i = 0; i < n; i++) h = hash(h, Array.get(o, i));
            return h;
        }
        h = mix(h, type.getName().hashCode());
        for (Field f : fieldsOf(type)) {
            try {
                h = hash(h, f.get(o));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return h;
    }

    private static Field[] fieldsOf(Class<?> type) {
        Field[] cached = FIELDS.get(type);
        if (cached != null) return cached;
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) continue;
                f.setAccessible(true);
                out.add(f);
            }
        }
        Field[] fields = out.toArray(new Field[0]);
        FIELDS.put(type, fields);
        return fields;
    }
}
