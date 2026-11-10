package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayList;
import java.util.List;

public final class GraphPresetFile {

    public static final int FORMAT_VERSION = 1;

    public int version;
    public String name;
    public String createdAt;
    public String modVersion;
    public List<Node> nodes = new ArrayList<Node>();
    public List<Edge> edges = new ArrayList<Edge>();

    public static final class Node {
        public String id;
        public String type;
        public String label;
        public float x;
        public float y;
        public List<Param> params = new ArrayList<Param>();
    }

    public static final class Param {
        public String key;
        public Double num;
        public String str;
        public Boolean flag;

        static Param of(GraphNode n, ParamSpec spec) {
            Param p = new Param();
            p.key = spec.key;
            switch (spec.kind) {
                case INT:
                    p.num = (double) n.params.getInt(spec.key);
                    break;
                case DOUBLE:
                    p.num = n.params.getDouble(spec.key);
                    break;
                case BOOL:
                    p.flag = n.params.getBool(spec.key);
                    break;
                case ENUM:
                case STRING:
                    p.str = n.params.getString(spec.key);
                    break;
            }
            return p;
        }
    }

    public static final class Edge {
        public String from;
        public String branch;
        public String to;
    }
}
