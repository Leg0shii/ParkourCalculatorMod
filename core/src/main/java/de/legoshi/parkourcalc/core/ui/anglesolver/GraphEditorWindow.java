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
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.ImNodesStyle;
import imgui.extension.imnodes.flag.ImNodesColorStyle;
import imgui.extension.imnodes.flag.ImNodesPinShape;
import imgui.extension.imnodes.flag.ImNodesStyleVar;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import imgui.type.ImInt;
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
    private static final String ADD_POPUP_ID = "##graph_editor_add";
    private static final String SAVE_ERRORS_POPUP_ID = "###graph_editor_save_errors";
    private static final String CLOSE_POPUP_ID = "###graph_editor_close";

    private static final int PIN_STRIDE = 32;
    private static final int STATIC_PIN_BASE = 16;
    private static final int EDITOR_COLOR_PUSHES = 16;
    private static final int ZOOM_STYLE_PUSHES = 12;
    private static final float ZOOM_MIN = 0.4f;
    private static final float ZOOM_MAX = 1.6f;
    private static final float ZOOM_WHEEL_FACTOR = 1.1f;
    private static final float PAN_DRAG_THRESHOLD = 4f;

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
    private final ImInt comboBuf = new ImInt();
    private final ImString saveNameInput = new ImString(64);
    private final ImInt linkStartBuf = new ImInt();
    private final ImInt linkEndBuf = new ImInt();

    private List<ValidationIssue> issues = new ArrayList<>();
    private final Set<String> errorNodeIds = new HashSet<>();
    private final Set<GraphEdge> errorEdges = java.util.Collections.newSetFromMap(new IdentityHashMap<GraphEdge, Boolean>());

    private boolean applyPositions;
    private String saveError;
    private String pendingSaveName;
    private String focusNodeId;
    private float addPosX;
    private float addPosY;
    private float zoom = 1f;
    private boolean rightDragOnCanvas;
    private float[] styleBase;
    private float canvasOriginX;
    private float canvasOriginY;
    private float canvasSizeX;
    private float canvasSizeY;

    public void setSaveHandler(SaveHandler handler) {
        this.saveHandler = handler;
    }

    public void open(SolverGraph source, String sourcePresetName) {
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
        if (allPositionsUnset()) autoLayout();
        applyPositions = true;
        revalidate();
        open = true;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!open) return;
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
            renderAddPopup();
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
            ImVec2 mouse = ImGui.getMousePos();
            addPosX = mouse.x;
            addPosY = mouse.y;
            ImGui.openPopup(ADD_POPUP_ID);
        }
        TooltipUtil.onHover("Add a node to the graph. Right-clicking the canvas works too.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Delete selected")) deleteSelected();
        TooltipUtil.onHover("Remove the selected nodes and links. Entry and Emit cannot be deleted.");
        ImGui.sameLine();
        if (Controls.secondaryButton("Auto layout")) {
            autoLayout();
            applyPositions = true;
            dirty = true;
        }
        TooltipUtil.onHover("Rearrange nodes left to right by distance from Entry.");
        ImGui.sameLine();
        if (Controls.secondaryButton(Math.round(zoom * 100f) + "%")) {
            setZoom(1f, canvasOriginX + canvasSizeX * 0.5f, canvasOriginY + canvasSizeY * 0.5f);
        }
        TooltipUtil.onHover("Canvas zoom. Scroll the mouse wheel over the canvas to zoom; click to reset."
                + " Drag with the right or middle mouse button to pan.");

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
        if (focusNodeId != null) {
            Integer id = nodeInts.get(focusNodeId);
            if (id != null) ImNodes.editorMoveToNode(id);
            focusNodeId = null;
        }
        captureStyleBase();
        ImVec2 origin = ImGui.getCursorScreenPos();
        canvasOriginX = origin.x;
        canvasOriginY = origin.y;
        pushEditorColors();
        pushZoomStyle();
        ImNodes.beginNodeEditor();
        ImFont font = ImGui.getFont();
        font.setScale(zoom);
        Fonts.setBoldScale(zoom);
        ImGui.pushFont(font);
        if (applyPositions) {
            for (GraphNode n : nodes) {
                ImNodes.setNodeGridSpacePos(nodeInts.get(n.id), n.x * zoom, n.y * zoom);
            }
            applyPositions = false;
        }
        for (GraphNode n : nodes) {
            drawNode(n, scale);
        }
        for (GraphEdge e : edges) {
            drawLink(e);
        }
        font.setScale(1f);
        Fonts.setBoldScale(1f);
        ImGui.popFont();
        ImNodes.endNodeEditor();
        for (int i = 0; i < ZOOM_STYLE_PUSHES; i++) {
            ImNodes.popStyleVar();
        }
        for (int i = 0; i < EDITOR_COLOR_PUSHES; i++) {
            ImNodes.popColorStyle();
        }
        ImVec2 size = ImGui.getItemRectSize();
        canvasSizeX = size.x;
        canvasSizeY = size.y;
        handleLinkCreation();
        readbackPositions();
        handleZoomInput();
        handleCanvasDrag();
    }

    private void handleCanvasDrag() {
        if (ImNodes.isEditorHovered() && ImGui.isMouseClicked(1)) {
            rightDragOnCanvas = true;
            ImVec2 mouse = ImGui.getMousePos();
            addPosX = mouse.x;
            addPosY = mouse.y;
        }
        if (!rightDragOnCanvas) return;
        if (ImGui.isMouseDown(1)) {
            if (ImGui.isMouseDragging(1, PAN_DRAG_THRESHOLD)) {
                float dx = ImGui.getIO().getMouseDeltaX();
                float dy = ImGui.getIO().getMouseDeltaY();
                if (dx != 0f || dy != 0f) {
                    ImVec2 pan = new ImVec2();
                    ImNodes.editorContextGetPanning(pan);
                    ImNodes.editorResetPanning(pan.x + dx, pan.y + dy);
                }
            }
        } else if (ImGui.isMouseReleased(1)) {
            rightDragOnCanvas = false;
            if (Math.abs(ImGui.getMouseDragDeltaX(1)) < PAN_DRAG_THRESHOLD
                    && Math.abs(ImGui.getMouseDragDeltaY(1)) < PAN_DRAG_THRESHOLD) {
                ImGui.openPopup(ADD_POPUP_ID);
            }
        } else {
            rightDragOnCanvas = false;
        }
    }

    private void captureStyleBase() {
        if (styleBase != null) return;
        ImNodesStyle style = ImNodes.getStyle();
        ImVec2 pad = new ImVec2();
        style.getNodePadding(pad);
        styleBase = new float[] {
                style.getGridSpacing(), style.getNodeCornerRounding(), pad.x, pad.y,
                style.getNodeBorderThickness(), style.getLinkThickness(), style.getLinkHoverDistance(),
                style.getPinCircleRadius(), style.getPinQuadSideLength(), style.getPinTriangleSideLength(),
                style.getPinLineThickness(), style.getPinHoverRadius(), style.getPinOffset()
        };
    }

    private void pushZoomStyle() {
        ImNodes.pushStyleVar(ImNodesStyleVar.GridSpacing, styleBase[0] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.NodeCornerRounding, styleBase[1] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.NodePadding, styleBase[2] * zoom, styleBase[3] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.NodeBorderThickness, styleBase[4] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.LinkThickness, styleBase[5] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.LinkHoverDistance, styleBase[6] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinCircleRadius, styleBase[7] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinQuadSideLength, styleBase[8] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinTriangleSideLength, styleBase[9] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinLineThickness, styleBase[10] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinHoverRadius, styleBase[11] * zoom);
        ImNodes.pushStyleVar(ImNodesStyleVar.PinOffset, styleBase[12] * zoom);
    }

    private void handleZoomInput() {
        if (!ImNodes.isEditorHovered()) return;
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel == 0f) return;
        ImVec2 mouse = ImGui.getMousePos();
        setZoom(zoom * (float) Math.pow(ZOOM_WHEEL_FACTOR, wheel), mouse.x, mouse.y);
    }

    private void setZoom(float target, float fixedScreenX, float fixedScreenY) {
        target = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, target));
        if (target == zoom) return;
        ImVec2 pan = new ImVec2();
        ImNodes.editorContextGetPanning(pan);
        float ratio = target / zoom;
        float gridX = fixedScreenX - canvasOriginX - pan.x;
        float gridY = fixedScreenY - canvasOriginY - pan.y;
        ImNodes.editorResetPanning(pan.x + gridX * (1f - ratio), pan.y + gridY * (1f - ratio));
        zoom = target;
        applyPositions = true;
    }

    private void pushEditorColors() {
        ImNodes.pushColorStyle(ImNodesColorStyle.NodeBackground, ThemeManager.panelColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.NodeBackgroundHovered, ThemeManager.hoverColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.NodeBackgroundSelected, ThemeManager.hoverColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.NodeOutline, ThemeManager.borderColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.TitleBar, ThemeManager.panelColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.TitleBarHovered, ThemeManager.hoverColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.TitleBarSelected, ThemeManager.focusColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.Link, ThemeManager.textDimColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.LinkHovered, ThemeManager.accentColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.LinkSelected, ThemeManager.focusColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.Pin, ThemeManager.accentColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.PinHovered, ThemeManager.focusColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.BoxSelector, ThemeManager.selectedTintColor(0.20f));
        ImNodes.pushColorStyle(ImNodesColorStyle.BoxSelectorOutline, ThemeManager.focusColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.GridBackground, ThemeManager.bgDarkColor());
        ImNodes.pushColorStyle(ImNodesColorStyle.GridLine, ThemeManager.bgTintColor(0.55f));
    }

    private void drawNode(GraphNode n, float scale) {
        int id = nodeInts.get(n.id);
        boolean error = errorNodeIds.contains(n.id);
        ImNodes.pushColorStyle(ImNodesColorStyle.TitleBar,
                error ? ThemeManager.dangerTintColor(0.55f) : categoryColor(n.type.category));
        if (error) ImNodes.pushColorStyle(ImNodesColorStyle.NodeOutline, ThemeManager.dangerColor());

        ImNodes.beginNode(id);
        ImNodes.beginNodeTitleBar();
        Fonts.pushBold();
        ImGui.text(n.type.label);
        Fonts.popBold();
        ImGui.sameLine();
        ThemeManager.pushTextColor(ThemeManager.textDimColor());
        ImGui.text(n.id);
        ThemeManager.popTextColor();
        ImNodes.endNodeTitleBar();

        float itemW = 150f * scale * zoom;
        if (!n.type.entryMarker) drawInputPin(n, id);
        List<ParamSpec> specs = n.type.params;
        for (int i = 0; i < specs.size(); i++) {
            ImNodes.beginStaticAttribute(id * PIN_STRIDE + STATIC_PIN_BASE + i);
            drawParamWidget(n, specs.get(i), itemW);
            ImNodes.endStaticAttribute();
        }
        List<Branch> branches = n.type.branches;
        for (int i = 0; i < branches.size(); i++) {
            drawOutputPin(id, branches.get(i), i);
        }
        ImNodes.endNode();

        if (error) ImNodes.popColorStyle();
        ImNodes.popColorStyle();
    }

    private void drawInputPin(GraphNode n, int id) {
        boolean feasible = n.type.requires == InputRequirement.FEASIBLE;
        ImNodes.pushColorStyle(ImNodesColorStyle.Pin,
                feasible ? ThemeManager.okColor() : ThemeManager.accentColor());
        ImNodes.beginInputAttribute(id * PIN_STRIDE, feasible ? ImNodesPinShape.CircleFilled : ImNodesPinShape.Circle);
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(feasible ? "in (feasible)" : "in");
        ThemeManager.popTextColor();
        ImNodes.endInputAttribute();
        ImNodes.popColorStyle();
    }

    private void drawOutputPin(int id, Branch branch, int branchIndex) {
        int color;
        int shape;
        switch (branch.feas) {
            case FEASIBLE:
                color = ThemeManager.okColor();
                shape = ImNodesPinShape.CircleFilled;
                break;
            case UNKNOWN:
                color = ThemeManager.warningColor();
                shape = ImNodesPinShape.TriangleFilled;
                break;
            default:
                color = ThemeManager.accentColor();
                shape = ImNodesPinShape.Circle;
                break;
        }
        ImNodes.pushColorStyle(ImNodesColorStyle.Pin, color);
        ImNodes.beginOutputAttribute(id * PIN_STRIDE + 1 + branchIndex, shape);
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text(branch.label.name());
        ThemeManager.popTextColor();
        ImNodes.endOutputAttribute();
        ImNodes.popColorStyle();
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
                int idx = 0;
                String current = n.params.getString(spec.key);
                for (int i = 0; i < spec.choices.length; i++) {
                    if (spec.choices[i].equals(current)) idx = i;
                }
                comboBuf.set(idx);
                if (Controls.combo(spec.label, comboBuf, spec.choices, itemW)) {
                    n.params.set(spec.key, spec.choices[comboBuf.get()]);
                    onEdited();
                }
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
        boolean error = errorEdges.contains(e);
        if (error) ImNodes.pushColorStyle(ImNodesColorStyle.Link, ThemeManager.dangerColor());
        ImNodes.link(linkId, fromInt * PIN_STRIDE + 1 + branchIndex, toInt * PIN_STRIDE);
        if (error) ImNodes.popColorStyle();
    }

    private void handleLinkCreation() {
        if (!ImNodes.isLinkCreated(linkStartBuf, linkEndBuf)) return;
        int a = linkStartBuf.get();
        int b = linkEndBuf.get();
        int outPin;
        int inPin;
        if (isOutputPin(a) && isInputPin(b)) {
            outPin = a;
            inPin = b;
        } else if (isOutputPin(b) && isInputPin(a)) {
            outPin = b;
            inPin = a;
        } else {
            return;
        }
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

    private static boolean isInputPin(int pin) {
        return pin % PIN_STRIDE == 0;
    }

    private static boolean isOutputPin(int pin) {
        int k = pin % PIN_STRIDE;
        return k >= 1 && k < STATIC_PIN_BASE;
    }

    private void deleteSelected() {
        boolean changed = false;
        int nodeCount = ImNodes.numSelectedNodes();
        if (nodeCount > 0) {
            int[] sel = new int[nodeCount];
            ImNodes.getSelectedNodes(sel);
            for (int id : sel) {
                GraphNode node = nodesByInt.get(id);
                if (node == null || node.type.entryMarker || node.type.emitMarker) continue;
                removeNode(node);
                changed = true;
            }
        }
        int linkCount = ImNodes.numSelectedLinks();
        if (linkCount > 0) {
            int[] sel = new int[linkCount];
            ImNodes.getSelectedLinks(sel);
            for (int id : sel) {
                GraphEdge e = linksByInt.get(id);
                if (e == null) continue;
                unregisterLink(e);
                edges.remove(e);
                changed = true;
            }
        }
        ImNodes.clearNodeSelection();
        ImNodes.clearLinkSelection();
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

    private void addNode(NodeType type) {
        String id = uniqueNodeId(type.id);
        GraphNode node = new GraphNode(id, type, type.defaultParams());
        nodes.add(node);
        registerNode(node);
        ImNodes.setNodeScreenSpacePos(nodeInts.get(id), addPosX, addPosY);
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
            float x = ImNodes.getNodeGridSpacePosX(id) / zoom;
            float y = ImNodes.getNodeGridSpacePosY(id) / zoom;
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
