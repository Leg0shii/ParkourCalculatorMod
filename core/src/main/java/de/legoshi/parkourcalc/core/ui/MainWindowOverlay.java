package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ports.SaveStore;
import de.legoshi.parkourcalc.core.ports.SystemBridgePort;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Root v1.3.0 window: menu bar + empty-state or input editor body. */
public final class MainWindowOverlay implements RenderInterface {

    // ### so the ID stays stable while the visible title (file name, dirty marker) changes.
    private static final String WINDOW_ID = "###main_window";
    private static final String APP_NAME = "Parkour Calculator";
    private static final String DIRTY_MARK = "[*]";
    private static final String TITLE_SEP = "-";
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.MenuBar
            | ImGuiWindowFlags.NoScrollbar
            | ImGuiWindowFlags.NoScrollWithMouse
            | ImGuiWindowFlags.NoCollapse;

    private static final float MAX_DISPLAY_WIDTH_FRACTION = 0.6f; // cap auto/initial width to 60% of display

    private static final String GITHUB_REPO = "https://github.com/Leg0shii/ParkourCalculatorMod";
    private static final String GITHUB_ISSUES = "https://github.com/Leg0shii/ParkourCalculatorMod/issues/new";
    private static final String POPUP_ABOUT = "###about_modal";
    private static final String BTN_CLOSE = "Close";


    private final InputOverlay inputOverlay;
    private final InputData inputData;
    private final FileMenu fileMenu;
    private final Settings settings;
    private final Runnable onSettingsChanged;
    private final TickInfoPanel tickInfoPanel;
    private final PerfOverlay perfOverlay;
    private final SettingsModal settingsModal;
    private final SystemBridgePort systemBridge;
    private final SaveStoreSupplier saveStoreSupplier;
    private final String modVersion;

    private boolean openAboutRequested;
    private float lastHeaderHeight;
    private float menuBandTop;
    private float menuBandBottom;
    // per-entry {hitMinX, hitMaxX, shown} carried one frame so the full-band highlight can be drawn behind ImGui's text
    private final Map<String, float[]> menuHi = new HashMap<>();

    public interface SaveStoreSupplier {
        SaveStore get();
    }

    public MainWindowOverlay(InputOverlay inputOverlay,
                             InputData inputData,
                             FileMenu fileMenu,
                             Settings settings,
                             Runnable onSettingsChanged,
                             TickInfoPanel tickInfoPanel,
                             PerfOverlay perfOverlay,
                             SettingsModal settingsModal,
                             SystemBridgePort systemBridge,
                             SaveStoreSupplier saveStoreSupplier,
                             String modVersion) {
        this.inputOverlay = inputOverlay;
        this.inputData = inputData;
        this.fileMenu = fileMenu;
        this.settings = settings;
        this.onSettingsChanged = onSettingsChanged;
        this.tickInfoPanel = tickInfoPanel;
        this.perfOverlay = perfOverlay;
        this.settingsModal = settingsModal;
        this.systemBridge = systemBridge;
        this.saveStoreSupplier = saveStoreSupplier;
        this.modVersion = modVersion;
        inputOverlay.setFooterHeightProvider(() -> fileMenu.statusStripHeight());
    }

    @Override
    public void render(ImGuiIO io) {
        float desired = inputOverlay.desiredPaneWidth();
        float minW = inputOverlay.minUsablePaneWidth();
        float displayW = io.getDisplaySizeX();
        float cap = displayW > 0f ? MAX_DISPLAY_WIDTH_FRACTION * displayW : Float.MAX_VALUE;
        float target = Math.min(Math.max(desired, minW), Math.max(minW, cap));
        ImGui.setNextWindowSize(target, 640, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(16, 16, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(minW, 420, Float.MAX_VALUE, Float.MAX_VALUE);

        ThemeManager.pushHeaderChrome();
        if (!ImGui.begin(WINDOW_ID, WINDOW_FLAGS)) {
            ImGui.end();
            ThemeManager.popHeaderChrome();
            return;
        }

        lastHeaderHeight = ImGui.getFrameHeight(); // captured under header chrome; title bar and menu-bar rows share this height
        renderStyledTitleBar();
        ThemeManager.popHeaderChrome();
        renderMenuBar();
        renderBody();
        fileMenu.renderStatusLine();
        fileMenu.renderPopups();
        settingsModal.render();
        renderAboutModal();
        ImGui.end();

        if (settings.viewTickInfo) tickInfoPanel.render(io);
        if (settings.viewPerf) perfOverlay.render(io);
    }

    /** ImGui native title text can't be styled per-span, so we keep the (empty) native bar for drag/fill/border and draw the spans over it. */
    private void renderStyledTitleBar() {
        ImDrawList dl = ImGui.getWindowDrawList();
        ImVec2 winPos = ImGui.getWindowPos();
        float winW = ImGui.getWindowWidth();
        float titleH = ImGui.getFrameHeight();
        float fontSize = ImGui.getFontSize();
        float padX = ThemeManager.headerTextPadX();
        float gap = ImGui.getStyle().getItemSpacing().x;
        float y = winPos.y + (titleH - fontSize) * 0.5f;

        dl.pushClipRect(winPos.x, winPos.y, winPos.x + winW, winPos.y + titleH, false);
        float x = winPos.x + padX;
        if (fileMenu.isDirty()) {
            addBoldSpan(dl, x, y, ThemeManager.warningColor(), DIRTY_MARK);
            x += measureBold(DIRTY_MARK) + gap;
        }
        String name = fileMenu.currentName();
        if (name != null) {
            addBoldSpan(dl, x, y, ThemeManager.textColor(), name);
            x += measureBold(name) + gap;
            dl.addText(x, y, ThemeManager.textDimColor(), TITLE_SEP);
            x += ImGui.calcTextSize(TITLE_SEP).x + gap;
        }
        dl.addText(x, y, ThemeManager.textMutedColor(), APP_NAME);
        if (fileMenu.hasOpenTas()) {
            String rows = inputData.size() + " rows";
            float rw = ImGui.calcTextSize(rows).x;
            dl.addText(winPos.x + winW - padX - rw, y, ThemeManager.textMutedColor(), rows);
        }
        dl.popClipRect();
    }

    private static void addBoldSpan(ImDrawList dl, float x, float y, int col, String text) {
        Fonts.pushBold();
        dl.addText(x, y, col, text);
        Fonts.popBold();
    }

    private static float measureBold(String text) {
        Fonts.pushBold();
        float w = ImGui.calcTextSize(text).x;
        Fonts.popBold();
        return w;
    }

    private void renderBody() {
        if (!fileMenu.hasOpenTas()) {
            fileMenu.renderEmptyStateCta();
        } else {
            inputOverlay.renderBody();
        }
    }

    private void renderMenuBar() {
        ThemeManager.pushMenuChrome();
        if (!ImGui.beginMenuBar()) {
            ThemeManager.popMenuChrome();
            return;
        }
        // The title bar and menu-bar rows share lastHeaderHeight; the band is the slice right below the title bar.
        ImVec2 winPos = ImGui.getWindowPos();
        menuBandTop = winPos.y + lastHeaderHeight;
        menuBandBottom = menuBandTop + lastHeaderHeight;

        // ImGui offsets the first entry by half the item spacing; pre-subtract it so "File" lines up under the title text.
        ImGui.setCursorPosX(ThemeManager.headerTextPadX() - ImGui.getStyle().getItemSpacing().x * 0.5f);
        menu("File", fileMenu::renderMenuItems);
        menu("View", this::renderViewMenuItems);
        menu("Settings", this::renderSettingsMenuItems);
        menu("Help", this::renderHelpMenuItems);

        ImGui.endMenuBar();
        ThemeManager.popMenuChrome();
    }

    /**
     * A menu-bar entry whose hover/open highlight fills the full band height and the entry's whole clickable cell.
     * ImGui's own (text-height) highlight is suppressed; ours uses the real item rect (which absorbs the inter-entry
     * spacing, so neighbouring cells tile flush) and is drawn one frame behind so it sits under the label text. The
     * rect is only sampled while the menu is closed, since opening it makes getItemRect report the popup instead.
     */
    private void menu(String label, Runnable items) {
        float[] cell = menuHi.get(label); // {minX, maxX, shown}
        if (cell != null && cell[2] != 0f && cell[1] > cell[0]) {
            ImGui.getWindowDrawList().addRectFilled(cell[0], menuBandTop, cell[1], menuBandBottom, ThemeManager.hoverColor());
        }

        ThemeManager.pushTransparentMenuHeader();
        boolean open = ImGui.beginMenu(label);
        ThemeManager.popTransparentMenuHeader();
        if (cell == null) {
            cell = new float[3];
            menuHi.put(label, cell);
        }
        if (!open) {
            cell[0] = ImGui.getItemRectMin().x;
            cell[1] = ImGui.getItemRectMax().x;
        }
        cell[2] = (ImGui.isItemHovered() || open) ? 1f : 0f;
        if (open) {
            ThemeManager.pushMenuPopupChrome();
            items.run();
            ThemeManager.popMenuPopupChrome();
            ImGui.endMenu();
        }
    }

    private void renderViewMenuItems() {
        if (ImGui.menuItem("Tick Info", null, settings.viewTickInfo)) {
            settings.viewTickInfo = !settings.viewTickInfo;
            onSettingsChanged.run();
        }
        if (ImGui.menuItem("Performance", null, settings.viewPerf)) {
            settings.viewPerf = !settings.viewPerf;
            onSettingsChanged.run();
        }
    }

    private void renderSettingsMenuItems() {
        if (ImGui.menuItem("Preferences...")) settingsModal.open();
    }

    private void renderHelpMenuItems() {
        boolean hasBridge = systemBridge != null;
        boolean hasStore = saveStoreSupplier != null && saveStoreSupplier.get() != null;
        if (ImGui.menuItem("Open save folder", null, false, hasBridge && hasStore)) {
            Path dir = saveStoreSupplier.get().getSaveDir();
            systemBridge.openFolder(dir);
        }
        if (ImGui.menuItem("Report bug", null, false, hasBridge)) {
            systemBridge.openUrl(GITHUB_ISSUES);
        }
        ThemeManager.paddedSeparator();
        if (ImGui.menuItem("About")) openAboutRequested = true;
    }

    private void renderAboutModal() {
        if (openAboutRequested) {
            ImGui.openPopup(POPUP_ABOUT);
            openAboutRequested = false;
        }
        if (!Modal.begin("About", POPUP_ABOUT)) return;

        Fonts.pushBold();
        ImGui.text(APP_NAME);
        Fonts.popBold();
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("Version: " + modVersion);
        ImGui.text("By Leg0shi_");
        ThemeManager.popTextColor();

        ThemeManager.paddedSeparator();

        if (Controls.hyperlink(GITHUB_REPO) && systemBridge != null) {
            systemBridge.openUrl(GITHUB_REPO);
        }

        Modal.footerSeparator();
        if (Modal.footerButton(BTN_CLOSE)) ImGui.closeCurrentPopup();
        Modal.end();
    }
}
