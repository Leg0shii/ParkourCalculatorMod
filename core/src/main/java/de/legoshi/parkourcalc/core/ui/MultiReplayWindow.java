package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.multireplay.MultiReplay;
import de.legoshi.parkourcalc.core.multireplay.MultiReplayGeometry;
import de.legoshi.parkourcalc.core.multireplay.ReplayClock;
import de.legoshi.parkourcalc.core.multireplay.ReplayTrack;
import de.legoshi.parkourcalc.core.render.ArgbColor;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import de.legoshi.parkourcalc.core.ui.util.TooltipUtil;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class MultiReplayWindow implements RenderInterface {

    public interface Host {
        void load(String folder);

        Path pickFolder();

        boolean canPickFolder();
    }

    private static final String WINDOW_ID = "###multireplay";
    private static final String TITLE = "Multi Replay";
    private static final String LEGEND_ID = "###multireplay_legend";
    private static final float WIN_W = 440f;
    private static final float WIN_H = 400f;
    private static final float MIN_W = 360f;
    private static final float MIN_H = 260f;
    private static final float LEGEND_MARGIN_X = 300f;
    private static final float LEGEND_TOP = 140f;
    private static final int HIDDEN_SWATCH_ALPHA = 80;
    private static final String FOLDER_TIP = "Every .json in this folder is simulated in the current world and drawn"
            + " as its own colored track.";
    private static final String UPCOMING_TIP = "Also draw the ticks the replay has not reached yet, smaller and faded.";
    private static final String THROUGH_TIP = "Draw spheres and lines on top of blocks so nothing hides them.";
    private static final String PLAYERS_TIP = "Show each replay as a player model named after its file instead of"
            + " spheres. The model walks the path at the current tick; the lines stay.";

    private final MultiReplay state;
    private final Host host;
    private final ImString folderBuf = new ImString(1024);
    private final float[] seekBuf = new float[1];
    private final int[] tickMsBuf = new int[1];
    private final float[] radiusBuf = new float[1];
    private final float[] lineWidthBuf = new float[1];
    private boolean open;

    public MultiReplayWindow(MultiReplay state, Host host) {
        this.state = state;
        this.host = host;
    }

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void render(ImGuiIO io) {
        if (open) renderControls(io);
        if (legendVisible()) renderLegend(io);
    }

    @Override
    public void renderDetached(ImGuiIO io) {
        if (legendVisible()) renderLegend(io);
    }

    private boolean legendVisible() {
        return state.isShowLegend() && !state.isEmpty();
    }

    private void renderControls(ImGuiIO io) {
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowSize(WIN_W * scale, WIN_H * scale, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(io.getDisplaySizeX() * 0.5f - WIN_W * 0.5f * scale, 120f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(MIN_W * scale, MIN_H * scale, Float.MAX_VALUE, Float.MAX_VALUE);
        if (!ThemeManager.beginPanel(WINDOW_ID, TITLE, ImGuiWindowFlags.NoCollapse)) return;
        renderFolderRow();
        renderErrors();
        renderTracks();
        ThemeManager.sectionSpacing();
        renderTransport();
        renderStyle();
        ImGui.end();
    }

    private void renderFolderRow() {
        if (folderBuf.get().isEmpty() && !state.folder().isEmpty()) folderBuf.set(state.folder());
        float spacing = ImGui.getStyle().getItemSpacingX();
        float buttons = Controls.buttonWidth("Browse...") + Controls.buttonWidth("Load") + 2f * spacing;
        float width = Math.max(80f, ImGui.getContentRegionAvail().x - buttons);
        Controls.inputTextHint("##multireplay_folder", "Folder with .json files", folderBuf, width);
        TooltipUtil.onHover(FOLDER_TIP);
        ImGui.sameLine();
        boolean canPick = host.canPickFolder();
        if (!canPick) ImGui.beginDisabled(true);
        if (Controls.secondaryButton("Browse...")) {
            Path picked = host.pickFolder();
            if (picked != null) {
                folderBuf.set(picked.toString());
                host.load(folderBuf.get());
            }
        }
        if (!canPick) ImGui.endDisabled();
        ImGui.sameLine();
        if (Controls.primaryButton("Load")) host.load(folderBuf.get());
    }

    private void renderErrors() {
        List<String> errors = state.errors();
        if (errors.isEmpty()) return;
        ImGui.pushStyleColor(ImGuiCol.Text, ThemeManager.dangerColor());
        for (String e : errors) ImGui.textWrapped(e);
        ImGui.popStyleColor();
    }

    private void renderTracks() {
        List<ReplayTrack> tracks = state.tracks();
        if (tracks.isEmpty()) {
            ImGui.textDisabled("No replays loaded.");
            return;
        }
        for (int i = 0; i < tracks.size(); i++) {
            ReplayTrack t = tracks.get(i);
            if (Controls.checkbox("##multireplay_vis_" + i, t.isVisible())) t.setVisible(!t.isVisible());
            ImGui.sameLine();
            swatch(t.argb);
            ImGui.sameLine();
            ImGui.text(t.name);
            ImGui.sameLine();
            ImGui.textDisabled(t.lastTick() + " ticks");
        }
    }

    private void renderTransport() {
        ReplayClock clock = state.clock();
        boolean empty = state.isEmpty();
        if (empty) ImGui.beginDisabled(true);
        if (Controls.secondaryButton("|<")) clock.restart();
        TooltipUtil.onHover("Back to the start");
        ImGui.sameLine();
        if (Controls.secondaryButton("<")) clock.stepBackward();
        TooltipUtil.onHover("One tick back");
        ImGui.sameLine();
        if (Controls.primaryButton(clock.isPlaying() ? "Pause" : "Play")) clock.togglePlay();
        ImGui.sameLine();
        if (Controls.secondaryButton(">")) clock.stepForward();
        TooltipUtil.onHover("One tick forward");
        ImGui.sameLine();
        if (Controls.secondaryButton(">|")) {
            clock.pause();
            clock.seek(clock.lastTick());
        }
        TooltipUtil.onHover("Jump to the end");
        ImGui.sameLine();
        ImGui.alignTextToFramePadding();
        ImGui.text(tickLabel(clock));

        seekBuf[0] = (float) clock.time();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvail().x);
        if (Controls.sliderFloat("##multireplay_seek", seekBuf, 0f, Math.max(0, clock.lastTick()), "Tick %.2f")) {
            clock.pause();
            clock.seek(seekBuf[0]);
        }
        if (empty) ImGui.endDisabled();

        tickMsBuf[0] = clock.tickDurationMs();
        if (Controls.sliderInt("Tick duration", tickMsBuf, ReplayClock.MIN_TICK_MS, ReplayClock.MAX_TICK_MS, "%d ms")) {
            clock.setTickDurationMs(tickMsBuf[0]);
        }
        TooltipUtil.onHover("Real time one replay tick takes. 50 ms is game speed.");
    }

    private void renderStyle() {
        radiusBuf[0] = state.sphereRadius();
        if (Controls.sliderFloat("Sphere radius", radiusBuf, MultiReplay.MIN_RADIUS, MultiReplay.MAX_RADIUS, "%.2f blocks")) {
            state.setSphereRadius(radiusBuf[0]);
        }
        lineWidthBuf[0] = state.lineWidth();
        if (Controls.sliderFloat("Line width", lineWidthBuf, MultiReplay.MIN_LINE_WIDTH, MultiReplay.MAX_LINE_WIDTH, "%.1f px")) {
            state.setLineWidth(lineWidthBuf[0]);
        }
        if (Controls.checkbox("Show upcoming ticks", state.isShowUpcoming())) state.setShowUpcoming(!state.isShowUpcoming());
        TooltipUtil.onHover(UPCOMING_TIP);
        ImGui.sameLine();
        if (Controls.checkbox("Through blocks", state.isThroughBlocks())) state.setThroughBlocks(!state.isThroughBlocks());
        TooltipUtil.onHover(THROUGH_TIP);
        ImGui.sameLine();
        if (Controls.checkbox("Legend", state.isShowLegend())) state.setShowLegend(!state.isShowLegend());
        if (Controls.checkbox("Player models", state.isPlayerModels())) state.setPlayerModels(!state.isPlayerModels());
        TooltipUtil.onHover(PLAYERS_TIP);
    }

    private void renderLegend(ImGuiIO io) {
        float scale = ThemeManager.uiScale();
        ImGui.setNextWindowPos(Math.max(0f, io.getDisplaySizeX() - LEGEND_MARGIN_X * scale), LEGEND_TOP * scale, ImGuiCond.FirstUseEver);
        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoFocusOnAppearing;
        if (!ImGui.begin(LEGEND_ID, flags)) {
            ImGui.end();
            return;
        }
        Fonts.pushBold();
        ImGui.text("Replays");
        Fonts.popBold();
        ImGui.sameLine();
        ImGui.textDisabled(tickLabel(state.clock()));
        ImGui.separator();
        for (ReplayTrack t : state.tracks()) {
            swatch(t.isVisible() ? t.argb : MultiReplayGeometry.withAlpha(t.argb, HIDDEN_SWATCH_ALPHA));
            ImGui.sameLine();
            if (t.isVisible()) ImGui.text(t.name);
            else ImGui.textDisabled(t.name);
        }
        ImGui.end();
    }

    private static String tickLabel(ReplayClock clock) {
        return String.format(Locale.ROOT, "Tick %d / %d", clock.tick(), clock.lastTick());
    }

    private static void swatch(int argb) {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 p = ImGui.getCursorScreenPos();
        float h = ImGui.getFrameHeight();
        float side = h * 0.7f;
        float y0 = p.y + (h - side) * 0.5f;
        int col = ImGui.colorConvertFloat4ToU32(ArgbColor.red(argb), ArgbColor.green(argb), ArgbColor.blue(argb), ArgbColor.alpha(argb));
        dl.addRectFilled(p.x, y0, p.x + side, y0 + side, col, 3f);
        ImGui.dummy(side, h);
    }
}
