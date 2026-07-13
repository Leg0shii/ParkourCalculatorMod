package de.legoshi.parkourcalc.core.ui.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.graph.Branch;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphEdge;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphValidator;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.InputRequirement;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCatalog;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeCategory;
import de.legoshi.parkourcalc.core.anglesolver.graph.NodeType;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamSpec;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.graph.ValidationIssue;
import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.extension.nodeditor.NodeEditor;
import imgui.extension.nodeditor.NodeEditorConfig;
import imgui.extension.nodeditor.NodeEditorContext;
import imgui.extension.nodeditor.NodeEditorStyle;
import imgui.extension.nodeditor.flag.NodeEditorPinKind;
import imgui.extension.nodeditor.flag.NodeEditorStyleColor;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImLong;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraphEditorWindow implements RenderInterface {

    public interface SaveHandler {
        boolean save(String name, SolverGraph graph);
    }

    private static final String WINDOW_ID = "###graph_editor";
    private static final String TITLE = "Graph Editor";
    private static final String CANVAS_ID = "graph_editor_canvas";
    private static final String ADD_POPUP_ID = "##graph_editor_add";
    private static final String ENUM_POPUP_ID = "##graph_editor_enum";
    private static final String SAVE_ERRORS_POPUP_ID = "###graph_editor_save_errors";
    private static final String CLOSE_POPUP_ID = "###graph_editor_close";

    private static final int PIN_STRIDE = 32;
    private static final int OUTPUT_PIN_LIMIT = 16;
    private static final int EDITOR_COLOR_PUSHES = 14;
    private static final int SHAPE_CIRCLE = 0;
    private static final int SHAPE_CIRCLE_FILLED = 1;
    private static final int SHAPE_TRIANGLE_FILLED = 2;

    private SaveHandler saveHandler;

    private boolean open;
    private String presetName;
    private boolean dirty;
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    private final Map<String, Integer> nodeInts = new HashMap<>();
    private final Map<Integer, GraphNode> nodesByInt = new HashMap<>();
    private final Map<GraphEdge, Integer> linkInts = new IdentityHashMap<>();
    private final Map<Integer, GraphEdge> linksByInt = new HashMap<>();
    private int nextNodeInt = 1;
    private int nextLinkInt = 1;

    private final Map<String, ImString> textBufs = new HashMap<>();
    private final ImInt intBuf = new ImInt();
    private final ImDouble doubleBuf = new ImDouble();
    private final ImString saveNameInput = new ImString(64);
    private final ImLong pinA = new ImLong();
    private final ImLong pinB = new ImLong();
    private final ImLong deletedLinkId = new ImLong();
    private final ImLong deletedLinkStart = new ImLong();
    private final ImLong deletedLinkEnd = new ImLong();
    private final ImLong deletedNodeId = new ImLong();

    private List<ValidationIssue> issues = new ArrayList<>();
    private final Set<String> errorNodeIds = new HashSet<>();
    private final Set<GraphEdge> errorEdges = java.util.Collections.newSetFromMap(new IdentityHashMap<GraphEdge, Boolean>());

    private NodeEditorContext context;
    private boolean applyPositions;
    private String saveError;
    private String pendingSaveName;
    private String focusNodeId;
    private boolean fitRequested;
    private boolean openAddPopupFromToolbar;
    private boolean openEnumPopup;
    private GraphNode enumPopupNode;
    private ParamSpec enumPopupSpec;
    private float addPosX;
    private float addPosY;
    private float canvasMouseX;
    private float canvasMouseY;
    private float canvasOriginX;
    private float canvasOriginY;
    private float viewScale = 1f;
    private float pinIconX;
    private float pinIconY;

    public void setSaveHandler(SaveHandler handler) {
        this.saveHandler = handler;
    }

    public void open(SolverGraph source, String sourcePresetName) {
        if (context != null) NodeEditor.destroyEditor(context);
        NodeEditorConfig config = new NodeEditorConfig();
        config.setSettingsFile(null);
        context = NodeEditor.createEditor(config);
        nodes.clear();
        edges.clear();
        nodeInts.clear();
        nodesByInt.clear();
        linkInts.clear();
        linksByInt.clear();
        textBufs.clear();
        nextNodeInt = 1;
        nextLinkInt = 1;
        for (GraphNode n : source.nodes) {
            GraphNode copy = new GraphNode(n.id, n.type, n.params.copy());
            copy.x = n.x;
            copy.y = n.y;
            nodes.add(copy);
            registerNode(copy);
        }
        for (GraphEdge e : source.edges) {
            edges.add(e);
            registerLink(e);
        }
        presetName = sourcePresetName;
        saveNameInput.set(sourcePresetName != null ? sourcePresetName : "");
        dirty = false;
        saveError = null;
        pendingSaveName = null;
        focusNodeId = null;
        fitRequested = false;
        openAddPopupFromToolbar = false;
        openEnumPopup = false;
        enumPopupNode = null;
        enumPopupSpec = null;
        viewScale = 1f;
        if (allPositionsUnset()) autoLayout();
        applyPositions = true;
        revalidate();
        open = true;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!open) return;
        NodeEditor.setCurrentEditor(context);
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(940f * scale, 640f * scale, ImGuiCond.FirstUseEver);
        int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        ThemeManager.pushHeaderChrome();
        boolean visible = ImGui.begin(WINDOW_ID, flags);
        if (visible) drawTitleBar(scale);
        ThemeManager.popHeaderChrome();
        if (visible) {
            renderToolbar();
            renderIssues();
            renderCanvas(scale);
            renderSaveErrorsModal();
            renderCloseModal();
        }
        ImGui.end();
    }

    private void drawTitleBar(float scale) {
        ThemeManager.drawModalTitle(TITLE);
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 wp = ImGui.getWindowPos();
        float titleH = ImGui.getFrameHeight();
        float fy = wp.y + (titleH - ImGui.getFontSize()) * 0.5f;
        Fonts.pushBold();
        float tw = ImGui.calcTextSize(TITLE).x;
        Fonts.popBold();
        String subtitle = (presetName != null ? presetName : "unsaved draft") + (dirty ? " *" : "");
        dl.addText(wp.x + ThemeManager.headerTextPadX() + tw + 10f * scale, fy,
                ThemeManager.textDimColor(), subtitle);
    }

    private void renderToolbar() {
        if (Controls.secondaryButton("Add node")) {
            openAddPopupFromToolbar = true;
        }
        TooltipUtil.onHover("Add a node to the graph. Right-clicking the canvas works too.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Delete selected")) deleteSelected();
        TooltipUtil.onHover("Remove the selected nodes and links. The Delete key works too."
                + " Entry and Emit cannot be deleted.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Auto layout")) {
            autoLayout();
            applyPositions = true;
            dirty = true;
        }
        TooltipUtil.onHover("Rearrange nodes left to right by distance from Entry.");
        ImGui.sameLine();
        if (Controls.secondaryButton(Math.round(viewScale * 100f) + "%")) {
            fitRequested = true;
        }
        TooltipUtil.onHover("Canvas zoom. Scroll the mouse wheel over the canvas to zoom; click to fit the graph."
                + " Drag with the right mouse button to pan.");

        ImGui.sameLine();
        float gap = ImGui.getStyle().getItemSpacing().x;
        float saveW = Controls.buttonWidth("Save");
        float saveAsW = Controls.buttonWidth("Save as");
        float closeW = Controls.buttonWidth("Close");
        float nameW = 180f * ThemeManager.uiScale();
        float total = nameW + saveAsW + closeW + 2f * gap + (presetName != null ? saveW + gap : 0f);
        float avail = ImGui.getContentRegionAvail().x;
        if (avail > total) ImGui.setCursorPosX(ImGui.getCursorPosX() + avail - total);

        if (presetName != null) {
            if (Controls.secondaryButton("Save")) trySave(presetName);
            TooltipUtil.onHover("Overwrite the preset '" + presetName + "' and select it.");
            ImGui.sameLine();
        }
        Controls.inputTextHint("##graphEditorSaveName", "preset name", saveNameInput, nameW);
        ImGui.sameLine();
        if (Controls.secondaryButton("Save as")) {
            String name = SaveIO.sanitize(saveNameInput.get());
            if (name == null) {
                saveError = "Invalid preset name. Use letters, numbers, dashes, or underscores.";
            } else {
                trySave(name);
            }
        }
        TooltipUtil.onHover("Save the graph as a preset under this name and select it.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Close")) {
            if (dirty) {
                ImGui.openPopup(CLOSE_POPUP_ID);
            } else {
                open = false;
            }
        }

        if (saveError != null) {
            ThemeManager.pushTextColor(ThemeManager.dangerColor());
            ImGui.textWrapped(saveError);
            ThemeManager.popTextColor();
        }
    }

    private void renderIssues() {
        int errors = 0;
        int warns = 0;
        for (ValidationIssue i : issues) {
            if (i.severity == ValidationIssue.Severity.ERROR) errors++;
            else warns++;
        }
        if (issues.isEmpty()) {
            ThemeManager.pushTextColor(ThemeManager.okColor());
            ImGui.text("Graph OK");
            ThemeManager.popTextColor();
            return;
        }
        ThemeManager.pushTextColor(errors > 0 ? ThemeManager.dangerColor() : ThemeManager.warningColor());
        ImGui.text(errors + (errors == 1 ? " error, " : " errors, ") + warns + (warns == 1 ? " warning" : " warnings"));
        ThemeManager.popTextColor();
        int shown = Math.min(issues.size(), 4);
        float h = shown * ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getWindowPaddingY();
        ImGui.beginChild("##graph_editor_issues", 0f, h, false);
        for (ValidationIssue issue : issues) {
            boolean error = issue.severity == ValidationIssue.Severity.ERROR;
            ThemeManager.pushTextColor(error ? ThemeManager.dangerColor() : ThemeManager.warningColor());
            ImGui.text(issue.toString());
            ThemeManager.popTextColor();
            if (issue.nodeId != null && ImGui.isItemClicked()) focusNodeId = issue.nodeId;
            if (issue.nodeId != null) TooltipUtil.onHover("Click to jump to '" + issue.nodeId + "'.");
        }
        ImGui.endChild();
    }

    private void renderCanvas(float scale) {
        ImVec2 origin = ImGui.getCursorScreenPos();
        canvasOriginX = origin.x;
        canvasOriginY = origin.y;
        pushEditorColors();
        NodeEditor.begin(CANVAS_ID);
        if (fitRequested) {
            NodeEditor.navigateToContent(0.25f);
            fitRequested = false;
        }
        if (focusNodeId != null) {
            Integer id = nodeInts.get(focusNodeId);
            if (id != null) {
                NodeEditor.selectNode(id, false);
                NodeEditor.navigateToSelection(false, 0.25f);
            }
            focusNodeId = null;
        }
        if (applyPositions) {
            for (GraphNode n : nodes) {
                NodeEditor.setNodePosition(nodeInts.get(n.id), n.x, n.y);
            }
            applyPositions = false;
        }
        for (GraphNode n : nodes) {
            drawNode(n, scale);
        }
        for (GraphEdge e : edges) {
            drawLink(e);
        }
        handleLinkCreation();
        handleDeletion();
        ImVec2 mouse = ImGui.getMousePos();
        canvasMouseX = mouse.x;
        canvasMouseY = mouse.y;
        NodeEditor.suspend();
        renderCanvasPopups(scale);
        NodeEditor.resume();
        NodeEditor.end();
        NodeEditor.popStyleColor(EDITOR_COLOR_PUSHES);
        readbackPositions();
        updateViewScale();
    }

    private void renderCanvasPopups(float scale) {
        if (NodeEditor.showBackgroundContextMenu()) {
            addPosX = canvasMouseX;
            addPosY = canvasMouseY;
            ImGui.openPopup(ADD_POPUP_ID);
        } else if (openAddPopupFromToolbar) {
            addPosX = NodeEditor.toCanvasX(canvasOriginX + 60f * scale);
            addPosY = NodeEditor.toCanvasY(canvasOriginY + 60f * scale);
            ImGui.openPopup(ADD_POPUP_ID);
        }
        openAddPopupFromToolbar = false;
        if (openEnumPopup) {
            ImGui.openPopup(ENUM_POPUP_ID);
            openEnumPopup = false;
        }
        renderAddPopup();
        renderEnumPopup();
    }

    private void updateViewScale() {
        float c0 = NodeEditor.toCanvasX(canvasOriginX);
        float c1 = NodeEditor.toCanvasX(canvasOriginX + 128f);
        float d = c1 - c0;
        if (d > 0.0001f) viewScale = 128f / d;
    }

    private void pushEditorColors() {
        pushColor(NodeEditorStyleColor.Bg, ThemeManager.bgDarkColor());
        pushColor(NodeEditorStyleColor.Grid, ThemeManager.bgTintColor(0.55f));
        pushColor(NodeEditorStyleColor.NodeBg, ThemeManager.panelColor());
        pushColor(NodeEditorStyleColor.NodeBorder, ThemeManager.borderColor());
        pushColor(NodeEditorStyleColor.HovNodeBorder, ThemeManager.accentColor());
        pushColor(NodeEditorStyleColor.SelNodeBorder, ThemeManager.focusColor());
        pushColor(NodeEditorStyleColor.NodeSelRect, ThemeManager.selectedTintColor(0.20f));
        pushColor(NodeEditorStyleColor.NodeSelRectBorder, ThemeManager.focusColor());
        pushColor(NodeEditorStyleColor.HovLinkBorder, ThemeManager.accentColor());
        pushColor(NodeEditorStyleColor.SelLinkBorder, ThemeManager.focusColor());
        pushColor(NodeEditorStyleColor.LinkSelRect, ThemeManager.selectedTintColor(0.20f));
        pushColor(NodeEditorStyleColor.LinkSelRectBorder, ThemeManager.focusColor());
        pushColor(NodeEditorStyleColor.PinRect, ThemeManager.accentTintColor(0.35f));
        pushColor(NodeEditorStyleColor.PinRectBorder, ThemeManager.accentColor());
    }

    private static void pushColor(int index, int color) {
        NodeEditor.pushStyleColor(index,
                (color & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                ((color >> 16) & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f);
    }

    private void drawNode(GraphNode n, float scale) {
        int id = nodeInts.get(n.id);
        boolean error = errorNodeIds.contains(n.id);
        if (error) pushColor(NodeEditorStyleColor.NodeBorder, ThemeManager.dangerColor());

        NodeEditor.beginNode(id);
        Fonts.pushBold();
        ImGui.text(n.type.label);
        Fonts.popBold();
        ImGui.sameLine();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text(n.id);
        ThemeManager.popTextColor();
        float headerBottom = ImGui.getItemRectMaxY() + 4f * scale;
        ImGui.dummy(0f, 4f * scale);

        float itemW = 150f * scale;
        if (!n.type.entryMarker) drawInputPin(n, id);
        List<ParamSpec> specs = n.type.params;
        for (int i = 0; i < specs.size(); i++) {
            drawParamWidget(n, specs.get(i), itemW);
        }
        List<Branch> branches = n.type.branches;
        for (int i = 0; i < branches.size(); i++) {
            drawOutputPin(id, branches.get(i), i, scale);
        }
        NodeEditor.endNode();

        if (error) NodeEditor.popStyleColor(1);
        drawNodeHeader(n, id, error, headerBottom);
    }

    private void drawNodeHeader(GraphNode n, int id, boolean error, float headerBottom) {
        float w = NodeEditor.getNodeSizeX(id);
        if (w <= 0f) return;
        float x = NodeEditor.getNodePositionX(id);
        float y = NodeEditor.getNodePositionY(id);
        NodeEditorStyle style = NodeEditor.getStyle();
        float inset = style.getNodeBorderWidth() * 0.5f;
        float rounding = Math.max(0f, style.getNodeRounding() - inset);
        int color = error ? ThemeManager.dangerTintColor(0.55f) : categoryColor(n.type.category);
        ImDrawList dl = NodeEditor.getNodeBackgroundDrawList(id);
        dl.addRectFilled(x + inset, y + inset, x + w - inset, headerBottom, color, rounding, ImDrawFlags.RoundCornersTop);
        dl.addLine(x + inset, headerBottom, x + w - inset, headerBottom, ThemeManager.borderColor(), 1f);
    }

    private void drawInputPin(GraphNode n, int id) {
        boolean feasible = n.type.requires == InputRequirement.FEASIBLE;
        int color = feasible ? ThemeManager.okColor() : ThemeManager.accentColor();
        NodeEditor.beginPin(id * PIN_STRIDE, NodeEditorPinKind.Input);
        drawPinIcon(color, feasible ? SHAPE_CIRCLE_FILLED : SHAPE_CIRCLE);
        NodeEditor.pinPivotRect(pinIconX, pinIconY, pinIconX, pinIconY);
        ImGui.sameLine();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(feasible ? "in (feasible)" : "in");
        ThemeManager.popTextColor();
        NodeEditor.endPin();
    }

    private void drawOutputPin(int id, Branch branch, int branchIndex, float scale) {
        int color;
        int shape;
        switch (branch.feas) {
            case FEASIBLE:
                color = ThemeManager.okColor();
                shape = SHAPE_CIRCLE_FILLED;
                break;
            case UNKNOWN:
                color = ThemeManager.warningColor();
                shape = SHAPE_TRIANGLE_FILLED;
                break;
            default:
                color = ThemeManager.accentColor();
                shape = SHAPE_CIRCLE;
                break;
        }
        String label = branch.label.name();
        float nodeW = NodeEditor.getNodeSizeX(id);
        if (nodeW > 0f) {
            float iconW = 9f * scale + 2f;
            float rowW = ImGui.calcTextSize(label).x + ImGui.getStyle().getItemSpacing().x + iconW;
            ImVec4 pad = NodeEditor.getStyle().getNodePadding();
            float target = NodeEditor.getNodePositionX(id) + nodeW - pad.z - rowW;
            if (target > ImGui.getCursorScreenPosX()) {
                ImGui.setCursorScreenPos(target, ImGui.getCursorScreenPosY());
            }
        }
        NodeEditor.beginPin(id * PIN_STRIDE + 1 + branchIndex, NodeEditorPinKind.Output);
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(label);
        ThemeManager.popTextColor();
        ImGui.sameLine();
        drawPinIcon(color, shape);
        NodeEditor.pinPivotRect(pinIconX, pinIconY, pinIconX, pinIconY);
        NodeEditor.endPin();
    }

    private void drawPinIcon(int color, int shape) {
        float scale = ThemeManager.uiScale();
        float r = 4.5f * scale;
        float h = ImGui.getTextLineHeight();
        float cx = ImGui.getCursorScreenPosX() + r + 1f;
        float cy = ImGui.getCursorScreenPosY() + h * 0.5f;
        ImDrawList dl = ImGui.getWindowDrawList();
        if (shape == SHAPE_CIRCLE) {
            dl.addCircle(cx, cy, r, color, 12, 1.5f * scale);
        } else if (shape == SHAPE_CIRCLE_FILLED) {
            dl.addCircleFilled(cx, cy, r, color, 12);
        } else {
            dl.addTriangleFilled(cx - r, cy - r, cx - r, cy + r, cx + r, cy, color);
        }
        ImGui.dummy(2f * r + 2f, h);
        pinIconX = cx;
        pinIconY = cy;
    }

    private void drawParamWidget(GraphNode n, ParamSpec spec, float itemW) {
        switch (spec.kind) {
            case INT: {
                intBuf.set(n.params.getInt(spec.key));
                ImGui.setNextItemWidth(itemW);
                if (ImGui.inputInt(spec.label, intBuf)) {
                    n.params.set(spec.key, intBuf.get());
                    onEdited();
                }
                break;
            }
            case DOUBLE: {
                doubleBuf.set(n.params.getDouble(spec.key));
                ImGui.setNextItemWidth(itemW);
                if (ImGui.inputDouble(spec.label, doubleBuf, 0.0, 0.0, "%.6g")) {
                    n.params.set(spec.key, doubleBuf.get());
                    onEdited();
                }
                break;
            }
            case BOOL: {
                boolean value = n.params.getBool(spec.key);
                if (Controls.checkbox(spec.label, value)) {
                    n.params.set(spec.key, !value);
                    onEdited();
                }
                break;
            }
            case ENUM: {
                String current = n.params.getString(spec.key);
                if (Controls.secondaryButton(current + "##enum_" + n.id + "_" + spec.key, itemW)) {
                    enumPopupNode = n;
                    enumPopupSpec = spec;
                    openEnumPopup = true;
                }
                ImGui.sameLine();
                ThemeManager.pushTextColor(ThemeManager.textMutedColor());
                ImGui.text(spec.label);
                ThemeManager.popTextColor();
                break;
            }
            case STRING: {
                String bufKey = n.id + "/" + spec.key;
                ImString buf = textBufs.get(bufKey);
                if (buf == null) {
                    buf = new ImString(128);
                    buf.set(n.params.getString(spec.key));
                    textBufs.put(bufKey, buf);
                }
                ImGui.setNextItemWidth(itemW);
                if (ImGui.inputText(spec.label, buf)) {
                    n.params.set(spec.key, buf.get());
                    onEdited();
                }
                break;
            }
        }
    }

    private void drawLink(GraphEdge e) {
        Integer linkId = linkInts.get(e);
        Integer fromInt = nodeInts.get(e.fromNode);
        Integer toInt = nodeInts.get(e.toNode);
        if (linkId == null || fromInt == null || toInt == null) return;
        GraphNode from = nodesByInt.get(fromInt);
        int branchIndex = branchIndexOf(from, e.branch);
        if (branchIndex < 0) return;
        int color = errorEdges.contains(e) ? ThemeManager.dangerColor() : ThemeManager.textDimColor();
        NodeEditor.link(linkId, fromInt * PIN_STRIDE + 1 + branchIndex, toInt * PIN_STRIDE,
                (color & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                ((color >> 16) & 0xFF) / 255f,
                ((color >>> 24) & 0xFF) / 255f,
                2f);
    }

    private void handleLinkCreation() {
        if (NodeEditor.beginCreate()) {
            if (NodeEditor.queryNewLink(pinA, pinB)) {
                long a = pinA.get();
                long b = pinB.get();
                if (a != 0 && b != 0) {
                    long outPin = 0;
                    long inPin = 0;
                    if (isOutputPin(a) && isInputPin(b)) {
                        outPin = a;
                        inPin = b;
                    } else if (isOutputPin(b) && isInputPin(a)) {
                        outPin = b;
                        inPin = a;
                    }
                    if (outPin == 0) {
                        NodeEditor.rejectNewItem();
                    } else if (NodeEditor.acceptNewItem()) {
                        createLink((int) outPin, (int) inPin);
                    }
                }
            }
        }
        NodeEditor.endCreate();
    }

    private void createLink(int outPin, int inPin) {
        GraphNode from = nodesByInt.get(outPin / PIN_STRIDE);
        GraphNode to = nodesByInt.get(inPin / PIN_STRIDE);
        if (from == null || to == null) return;
        int branchIndex = outPin % PIN_STRIDE - 1;
        if (branchIndex < 0 || branchIndex >= from.type.branches.size()) return;
        Guarantee branch = from.type.branches.get(branchIndex).label;
        for (Iterator<GraphEdge> it = edges.iterator(); it.hasNext(); ) {
            GraphEdge e = it.next();
            if (e.fromNode.equals(from.id) && e.branch == branch) {
                unregisterLink(e);
                it.remove();
            }
        }
        GraphEdge created = new GraphEdge(from.id, branch, to.id);
        edges.add(created);
        registerLink(created);
        onEdited();
    }

    private void handleDeletion() {
        boolean changed = false;
        if (NodeEditor.beginDelete()) {
            while (NodeEditor.queryDeletedLink(deletedLinkId, deletedLinkStart, deletedLinkEnd)) {
                GraphEdge e = linksByInt.get((int) deletedLinkId.get());
                if (e == null) {
                    NodeEditor.acceptDeletedItem();
                    continue;
                }
                if (NodeEditor.acceptDeletedItem()) {
                    unregisterLink(e);
                    edges.remove(e);
                    changed = true;
                }
            }
            while (NodeEditor.queryDeletedNode(deletedNodeId)) {
                GraphNode node = nodesByInt.get((int) deletedNodeId.get());
                if (node == null) {
                    NodeEditor.acceptDeletedItem();
                    continue;
                }
                if (node.type.entryMarker || node.type.emitMarker) {
                    NodeEditor.rejectDeletedItem();
                    continue;
                }
                if (NodeEditor.acceptDeletedItem()) {
                    removeNode(node);
                    changed = true;
                }
            }
        }
        NodeEditor.endDelete();
        if (changed) onEdited();
    }

    private static boolean isInputPin(long pin) {
        return pin % PIN_STRIDE == 0;
    }

    private static boolean isOutputPin(long pin) {
        long k = pin % PIN_STRIDE;
        return k >= 1 && k < OUTPUT_PIN_LIMIT;
    }

    private void deleteSelected() {
        boolean changed = false;
        int total = NodeEditor.getSelectedObjectCount();
        if (total > 0) {
            long[] selNodes = new long[total];
            int nodeCount = NodeEditor.getSelectedNodes(selNodes, total);
            for (int i = 0; i < nodeCount; i++) {
                GraphNode node = nodesByInt.get((int) selNodes[i]);
                if (node == null || node.type.entryMarker || node.type.emitMarker) continue;
                removeNode(node);
                changed = true;
            }
            long[] selLinks = new long[total];
            int linkCount = NodeEditor.getSelectedLinks(selLinks, total);
            for (int i = 0; i < linkCount; i++) {
                GraphEdge e = linksByInt.get((int) selLinks[i]);
                if (e == null) continue;
                unregisterLink(e);
                edges.remove(e);
                changed = true;
            }
            NodeEditor.clearSelection();
        }
        if (changed) onEdited();
    }

    private void removeNode(GraphNode node) {
        Integer id = nodeInts.remove(node.id);
        if (id != null) nodesByInt.remove(id);
        nodes.remove(node);
        for (Iterator<GraphEdge> it = edges.iterator(); it.hasNext(); ) {
            GraphEdge e = it.next();
            if (e.fromNode.equals(node.id) || e.toNode.equals(node.id)) {
                unregisterLink(e);
                it.remove();
            }
        }
        for (Iterator<String> it = textBufs.keySet().iterator(); it.hasNext(); ) {
            if (it.next().startsWith(node.id + "/")) it.remove();
        }
        if (enumPopupNode == node) {
            enumPopupNode = null;
            enumPopupSpec = null;
        }
    }

    private void renderAddPopup() {
        if (!ImGui.beginPopup(ADD_POPUP_ID)) return;
        NodeCategory last = null;
        for (NodeType t : NodeCatalog.all()) {
            if (t.entryMarker || t.emitMarker) continue;
            if (t.category != last) {
                if (last != null) ImGui.spacing();
                ThemeManager.pushTextColor(ThemeManager.textMutedColor());
                ImGui.text(t.category.name());
                ThemeManager.popTextColor();
                last = t.category;
            }
            if (ImGui.menuItem(t.label)) addNode(t);
        }
        ImGui.endPopup();
    }

    private void renderEnumPopup() {
        if (!ImGui.beginPopup(ENUM_POPUP_ID)) return;
        if (enumPopupNode != null && enumPopupSpec != null) {
            String current = enumPopupNode.params.getString(enumPopupSpec.key);
            for (String choice : enumPopupSpec.choices) {
                if (ImGui.menuItem(choice, "", choice.equals(current))) {
                    enumPopupNode.params.set(enumPopupSpec.key, choice);
                    onEdited();
                }
            }
        }
        ImGui.endPopup();
    }

    private void addNode(NodeType type) {
        String id = uniqueNodeId(type.id);
        GraphNode node = new GraphNode(id, type, type.defaultParams());
        node.x = addPosX;
        node.y = addPosY;
        nodes.add(node);
        registerNode(node);
        NodeEditor.setNodePosition(nodeInts.get(id), addPosX, addPosY);
        onEdited();
    }

    private String uniqueNodeId(String base) {
        if (nodeInts.get(base) == null) return base;
        int n = 2;
        while (nodeInts.get(base + "-" + n) != null) n++;
        return base + "-" + n;
    }

    private void renderSaveErrorsModal() {
        if (!Modal.begin("Save with errors?", SAVE_ERRORS_POPUP_ID)) return;
        ThemeManager.pushTextColor(ThemeManager.dangerColor());
        ImGui.text("This graph has validation errors.");
        ThemeManager.popTextColor();
        ImGui.textWrapped("The preset picker refuses to load a graph with errors, so the saved"
                + " preset will not be selectable until the errors are fixed.");
        Modal.footerSeparator();
        if (Controls.secondaryButton("Cancel")) {
            pendingSaveName = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        Controls.cursorToRightAlignedButton("Save anyway");
        if (Controls.dangerButton("Save anyway")) {
            if (pendingSaveName != null) doSave(pendingSaveName);
            pendingSaveName = null;
            ImGui.closeCurrentPopup();
        }
        Modal.end();
    }

    private void renderCloseModal() {
        if (!Modal.begin("Discard changes?", CLOSE_POPUP_ID)) return;
        ImGui.textWrapped("The graph has unsaved changes. Close the editor and discard them?");
        Modal.footerSeparator();
        if (Controls.secondaryButton("Keep editing")) ImGui.closeCurrentPopup();
        ImGui.sameLine();
        Controls.cursorToRightAlignedButton("Discard");
        if (Controls.dangerButton("Discard")) {
            open = false;
            ImGui.closeCurrentPopup();
        }
        Modal.end();
    }

    private void trySave(String name) {
        saveError = null;
        if (GraphValidator.hasErrors(issues)) {
            pendingSaveName = name;
            ImGui.openPopup(SAVE_ERRORS_POPUP_ID);
        } else {
            doSave(name);
        }
    }

    private void doSave(String name) {
        if (saveHandler == null) {
            saveError = "No save target wired.";
            return;
        }
        SolverGraph graph = new SolverGraph(name, false, nodes, edges);
        if (saveHandler.save(name, graph)) {
            presetName = name;
            saveNameInput.set(name);
            dirty = false;
            saveError = null;
        } else {
            saveError = "Failed to write preset '" + name + "'.";
        }
    }

    private void onEdited() {
        dirty = true;
        revalidate();
    }

    private void revalidate() {
        SolverGraph graph = new SolverGraph(presetName != null ? presetName : "draft", false, nodes, edges);
        issues = GraphValidator.validate(graph);
        errorNodeIds.clear();
        errorEdges.clear();
        for (ValidationIssue issue : issues) {
            if (issue.severity != ValidationIssue.Severity.ERROR) continue;
            if (issue.nodeId != null) errorNodeIds.add(issue.nodeId);
            if (issue.edgeIndex >= 0 && issue.edgeIndex < edges.size()) errorEdges.add(edges.get(issue.edgeIndex));
        }
    }

    private void readbackPositions() {
        for (GraphNode n : nodes) {
            Integer id = nodeInts.get(n.id);
            if (id == null) continue;
            float x = NodeEditor.getNodePositionX(id);
            float y = NodeEditor.getNodePositionY(id);
            if (Math.abs(x - n.x) > 0.5f || Math.abs(y - n.y) > 0.5f) {
                n.x = x;
                n.y = y;
                dirty = true;
            }
        }
    }

    private void registerNode(GraphNode node) {
        int id = nextNodeInt++;
        nodeInts.put(node.id, id);
        nodesByInt.put(id, node);
    }

    private void registerLink(GraphEdge e) {
        int id = nextLinkInt++;
        linkInts.put(e, id);
        linksByInt.put(id, e);
    }

    private void unregisterLink(GraphEdge e) {
        Integer id = linkInts.remove(e);
        if (id != null) linksByInt.remove(id);
    }

    private int categoryColor(NodeCategory category) {
        switch (category) {
            case SEED:
                return ThemeManager.accentTintColor(0.35f);
            case GLOBAL:
                return ThemeManager.warningTintColor(0.35f);
            case RECOVERY:
                return ThemeManager.okTintColor(0.30f);
            case POLISH:
                return ThemeManager.selectedTintColor(0.35f);
            case WINDOWING:
                return ThemeManager.peachTintColor(0.35f);
            default:
                return ThemeManager.panelColor();
        }
    }

    private static int branchIndexOf(GraphNode node, Guarantee branch) {
        if (node == null) return -1;
        List<Branch> branches = node.type.branches;
        for (int i = 0; i < branches.size(); i++) {
            if (branches.get(i).label == branch) return i;
        }
        return -1;
    }

    private boolean allPositionsUnset() {
        for (GraphNode n : nodes) {
            if (n.x != 0f || n.y != 0f) return false;
        }
        return true;
    }

    private void autoLayout() {
        float scale = ThemeManager.uiScale();
        Map<String, Integer> depth = new HashMap<>();
        GraphNode entry = null;
        for (GraphNode n : nodes) {
            if (n.type.entryMarker) entry = n;
        }
        if (entry != null) depth.put(entry.id, 0);
        int cap = nodes.size() + 1;
        for (int pass = 0; pass < cap; pass++) {
            boolean changed = false;
            for (GraphEdge e : edges) {
                Integer df = depth.get(e.fromNode);
                if (df == null) continue;
                GraphNode to = null;
                for (GraphNode n : nodes) {
                    if (n.id.equals(e.toNode)) to = n;
                }
                if (to == null || to.type.entryMarker) continue;
                int cand = Math.min(df + 1, cap);
                Integer dt = depth.get(e.toNode);
                if (dt == null || cand > dt) {
                    if (dt == null || dt < cap) {
                        depth.put(e.toNode, cand);
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        int maxDepth = 0;
        for (Integer d : depth.values()) maxDepth = Math.max(maxDepth, d);
        for (GraphNode n : nodes) {
            if (!depth.containsKey(n.id)) depth.put(n.id, maxDepth + 1);
        }
        Map<Integer, Float> columnY = new HashMap<>();
        for (GraphNode n : nodes) {
            int d = depth.get(n.id);
            Float y = columnY.get(d);
            if (y == null) y = 30f * scale;
            n.x = 30f * scale + d * 300f * scale;
            n.y = y;
            float estimated = (70f + 30f * (n.type.params.size() + n.type.branches.size())) * scale;
            columnY.put(d, y + estimated);
        }
    }
}
