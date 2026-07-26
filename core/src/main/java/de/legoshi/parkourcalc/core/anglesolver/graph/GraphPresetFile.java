package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayList;
import java.util.List;

public final class GraphPresetFile {

    public static final int FORMAT_VERSION = 1;

    public int version;
    public String name;
    public String description;
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
    }

    public static final class Edge {
        public String from;
        public String branch;
        public String to;
    }
}
