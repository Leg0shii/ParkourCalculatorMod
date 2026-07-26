package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParamValues {

    private final List<ParamSpec> specs;
    private final Map<String, Object> values = new LinkedHashMap<>();

    public ParamValues(List<ParamSpec> specs) {
        this.specs = specs;
    }

    private ParamSpec spec(String key) {
        for (ParamSpec s : specs) {
            if (s.key.equals(key)) return s;
        }
        throw new IllegalArgumentException("unknown param: " + key);
    }

    public ParamValues set(String key, Object value) {
        ParamSpec s = spec(key);
        switch (s.kind) {
            case INT:
                values.put(key, (int) Math.round(s.clamp(((Number) value).doubleValue())));
                break;
            case DOUBLE:
                values.put(key, s.clamp(((Number) value).doubleValue()));
                break;
            case BOOL:
                values.put(key, (Boolean) value);
                break;
            case ENUM:
                String e = String.valueOf(value);
                values.put(key, s.validChoice(e) ? e : s.defString);
                break;
            case STRING:
                values.put(key, String.valueOf(value));
                break;
        }
        return this;
    }

    public int getInt(String key) {
        ParamSpec s = spec(key);
        Object v = values.get(key);
        return v == null ? (int) s.def : ((Number) v).intValue();
    }

    public double getDouble(String key) {
        ParamSpec s = spec(key);
        Object v = values.get(key);
        return v == null ? s.def : ((Number) v).doubleValue();
    }

    public boolean getBool(String key) {
        ParamSpec s = spec(key);
        Object v = values.get(key);
        return v == null ? s.defBool : (Boolean) v;
    }

    public String getString(String key) {
        ParamSpec s = spec(key);
        Object v = values.get(key);
        return v == null ? s.defString : (String) v;
    }

    public List<ParamSpec> specs() {
        return specs;
    }

    public ParamValues copy() {
        ParamValues out = new ParamValues(specs);
        out.values.putAll(values);
        return out;
    }
}
