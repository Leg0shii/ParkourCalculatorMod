package de.legoshi.parkourcalc.core.anglesolver.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import de.legoshi.parkourcalc.core.save.FileSystemSaveStore;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GraphPresetIO {

    private GraphPresetIO() {
    }

    public static String toJson(GraphPresetFile file) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(file);
    }

    public static Result<GraphPresetFile> parse(String contents) {
        GraphPresetFile file;
        try {
            file = new Gson().fromJson(contents, GraphPresetFile.class);
        } catch (JsonSyntaxException e) {
            return Result.failure("Graph preset is not valid JSON.");
        }
        if (file == null || file.nodes == null || file.edges == null) {
            return Result.failure("Graph preset is missing required fields.");
        }
        if (file.version != GraphPresetFile.FORMAT_VERSION) {
            return Result.failure("Unsupported graph preset version: " + file.version);
        }
        return Result.success(file);
    }

    public static Result<SolverGraph> materialize(GraphPresetFile file) {
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        Set<String> ids = new HashSet<String>();
        for (GraphPresetFile.Node n : file.nodes) {
            if (n == null || n.id == null) {
                return Result.failure("Graph preset has a node without an id.");
            }
            if (!ids.add(n.id)) {
                return Result.failure("Duplicate node id '" + n.id + "'.");
            }
            NodeType type = NodeCatalog.byId(n.type);
            if (type == null) {
                return Result.failure("Unknown node type '" + n.type + "' (node '" + n.id + "').");
            }
            ParamValues params = type.defaultParams();
            if (n.params != null) {
                for (GraphPresetFile.Param p : n.params) {
                    if (p == null || p.key == null) continue;
                    String error = applyParam(params, type, p, n.id);
                    if (error != null) return Result.failure(error);
                }
            }
            GraphNode node = new GraphNode(n.id, type, params);
            node.x = n.x;
            node.y = n.y;
            nodes.add(node);
        }

        List<GraphEdge> edges = new ArrayList<GraphEdge>();
        for (GraphPresetFile.Edge e : file.edges) {
            if (e == null || e.from == null || e.to == null) {
                return Result.failure("Graph preset has an edge without endpoints.");
            }
            Guarantee branch;
            try {
                branch = Guarantee.valueOf(e.branch);
            } catch (IllegalArgumentException ex) {
                return Result.failure("Unknown branch '" + e.branch + "' (edge from '" + e.from + "').");
            } catch (NullPointerException ex) {
                return Result.failure("Missing branch (edge from '" + e.from + "').");
            }
            edges.add(new GraphEdge(e.from, branch, e.to));
        }

        SolverGraph graph = new SolverGraph(file.name != null ? file.name : "Preset", false, nodes, edges);
        List<ValidationIssue> issues = GraphValidator.validate(graph);
        if (GraphValidator.hasErrors(issues)) {
            for (ValidationIssue issue : issues) {
                if (issue.severity == ValidationIssue.Severity.ERROR) {
                    return Result.failure("Invalid graph: " + issue);
                }
            }
        }
        return Result.success(graph);
    }

    private static String applyParam(ParamValues params, NodeType type, GraphPresetFile.Param p, String nodeId) {
        ParamSpec spec = null;
        for (ParamSpec s : type.params) {
            if (s.key.equals(p.key)) {
                spec = s;
                break;
            }
        }
        if (spec == null) {
            return "Unknown param '" + p.key + "' (node '" + nodeId + "').";
        }
        switch (spec.kind) {
            case INT:
            case DOUBLE:
                if (p.num != null) params.set(p.key, p.num);
                break;
            case BOOL:
                if (p.flag != null) params.set(p.key, p.flag);
                break;
            case ENUM:
            case STRING:
                if (p.str != null) params.set(p.key, p.str);
                break;
        }
        return null;
    }

    public static GraphPresetFile fromGraph(SolverGraph graph) {
        GraphPresetFile file = new GraphPresetFile();
        file.version = GraphPresetFile.FORMAT_VERSION;
        file.name = graph.name;
        for (GraphNode n : graph.nodes) {
            GraphPresetFile.Node node = new GraphPresetFile.Node();
            node.id = n.id;
            node.type = n.type.id;
            node.label = n.type.label;
            node.x = n.x;
            node.y = n.y;
            for (ParamSpec spec : n.type.params) {
                GraphPresetFile.Param p = new GraphPresetFile.Param();
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
                node.params.add(p);
            }
            file.nodes.add(node);
        }
        for (GraphEdge e : graph.edges) {
            GraphPresetFile.Edge edge = new GraphPresetFile.Edge();
            edge.from = e.fromNode;
            edge.branch = e.branch.name();
            edge.to = e.toNode;
            file.edges.add(edge);
        }
        return file;
    }

    public static Result<SolverGraph> loadGraph(FileSystemSaveStore store, String name) {
        String contents;
        try {
            contents = store.read(name);
        } catch (IOException e) {
            return Result.failure("Failed to read graph preset: " + e.getMessage());
        }
        Result<GraphPresetFile> parsed = parse(contents);
        if (!parsed.ok) return Result.failure(parsed.error);
        return materialize(parsed.value);
    }

    public static FileSystemSaveStore.InfoParser infoParser() {
        return new FileSystemSaveStore.InfoParser() {
            @Override
            public SaveInfo parse(String name, long lastModifiedMs, String contents) {
                return new SaveInfo(name, lastModifiedMs, null, null);
            }
        };
    }
}
