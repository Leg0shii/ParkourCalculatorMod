package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.ports.SaveStore;
import de.legoshi.parkourcalc.core.ports.SystemBridgePort;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.nio.file.Path;

/**
 * Root window for v1.3.0. Menu bar (File / Edit / View / Settings / Help) plus the
 * body, which switches between an empty-state CTA and the input editor. Tick Info
 * and Performance are floating side panels controlled by View menu booleans.
 */
public final class MainWindowOverlay implements RenderInterface {

    private static final String WINDOW_ID = "##main_window";
    private static final String APP_NAME = "Parkour Calculator";
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.MenuBar;

    private static final String GITHUB_REPO = "https://github.com/Leg0shii/ParkourCalculatorMod";
    private static final String GITHUB_ISSUES = "https://github.com/Leg0shii/ParkourCalculatorMod/issues/new";
    private static final String POPUP_ABOUT = "About##about_modal";

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
    }

    @Override
    public void render(ImGuiIO io) {
        ImGui.setNextWindowSize(800, 600, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(16, 16, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(560, 360, Float.MAX_VALUE, Float.MAX_VALUE);

        if (!ImGui.begin(buildWindowTitle(), WINDOW_FLAGS)) {
            ImGui.end();
            return;
        }

        renderMenuBar();
        fileMenu.renderStatusLine();
        renderBody();
        fileMenu.renderPopups();
        settingsModal.render();
        renderAboutModal();
        ImGui.end();

        if (settings.viewTickInfo) tickInfoPanel.render(io);
        if (settings.viewPerf) perfOverlay.render(io);
    }

    private String buildWindowTitle() {
        String name = fileMenu.currentName();
        if (name == null) return APP_NAME + WINDOW_ID;
        String dirty = fileMenu.isDirty() ? " *" : "";
        return name + dirty + "  -  " + APP_NAME + WINDOW_ID;
    }

    private void renderBody() {
        if (inputData.size() == 0 && !fileMenu.hasOpenTas()) {
            fileMenu.renderEmptyStateCta();
        } else {
            inputOverlay.renderBody(0);
        }
    }

    private void renderMenuBar() {
        if (!ImGui.beginMenuBar()) return;
        if (ImGui.beginMenu("File")) {
            fileMenu.renderMenuItems();
            ImGui.endMenu();
        }
        renderEditMenu();
        renderViewMenu();
        renderSettingsMenu();
        renderHelpMenu();
        ImGui.endMenuBar();
    }

    private void renderEditMenu() {
        if (!ImGui.beginMenu("Edit")) return;
        ImGui.menuItem("Undo", "Ctrl+Z", false, false);
        ImGui.menuItem("Redo", "Ctrl+Y", false, false);
        ImGui.separator();
        if (ImGui.menuItem("Add row", "Insert")) inputOverlay.addRowsAtEnd(1);
        if (ImGui.menuItem("Delete selected", "Delete")) inputOverlay.deleteSelectedRows();
        if (ImGui.menuItem("Duplicate selected", "Ctrl+D")) inputOverlay.duplicateSelectedRows();
        ImGui.separator();
        if (ImGui.menuItem("Clear all rows")) inputOverlay.requestClearAll();
        ImGui.endMenu();
    }

    private void renderViewMenu() {
        if (!ImGui.beginMenu("View")) return;
        if (ImGui.menuItem("Tick Info", null, settings.viewTickInfo)) {
            settings.viewTickInfo = !settings.viewTickInfo;
            onSettingsChanged.run();
        }
        if (ImGui.menuItem("Performance", null, settings.viewPerf)) {
            settings.viewPerf = !settings.viewPerf;
            onSettingsChanged.run();
        }
        ImGui.separator();
        if (ImGui.menuItem("Show potion columns", null, settings.showPotionColumns)) {
            settings.showPotionColumns = !settings.showPotionColumns;
            onSettingsChanged.run();
        }
        if (ImGui.menuItem("Show yaw arrows", null, settings.showYawArrows)) {
            settings.showYawArrows = !settings.showYawArrows;
            onSettingsChanged.run();
        }
        if (ImGui.menuItem("Show hitbox", null, settings.showHitbox)) {
            settings.showHitbox = !settings.showHitbox;
            onSettingsChanged.run();
        }
        if (ImGui.menuItem("Show full hitbox", null, settings.showFullHitbox)) {
            settings.showFullHitbox = !settings.showFullHitbox;
            onSettingsChanged.run();
        }
        if (ImGui.menuItem("Subtick visualization", null, settings.showSubtick)) {
            settings.showSubtick = !settings.showSubtick;
            onSettingsChanged.run();
        }
        ImGui.endMenu();
    }

    private void renderSettingsMenu() {
        if (!ImGui.beginMenu("Settings")) return;
        if (ImGui.menuItem("Preferences...")) settingsModal.open();
        ImGui.endMenu();
    }

    private void renderHelpMenu() {
        if (!ImGui.beginMenu("Help")) return;
        boolean hasBridge = systemBridge != null;
        boolean hasStore = saveStoreSupplier != null && saveStoreSupplier.get() != null;
        if (ImGui.menuItem("Open save folder", null, false, hasBridge && hasStore)) {
            Path dir = saveStoreSupplier.get().getSaveDir();
            systemBridge.openFolder(dir);
        }
        if (ImGui.menuItem("Report bug", null, false, hasBridge)) {
            systemBridge.openUrl(GITHUB_ISSUES);
        }
        ImGui.separator();
        if (ImGui.menuItem("About")) openAboutRequested = true;
        ImGui.endMenu();
    }

    private void renderAboutModal() {
        if (openAboutRequested) {
            ImGui.openPopup(POPUP_ABOUT);
            openAboutRequested = false;
        }
        if (!ImGui.beginPopupModal(POPUP_ABOUT, ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.text("Parkour Calculator");
        ImGui.text("Version: " + modVersion);
        ImGui.separator();
        ImGui.text(GITHUB_REPO);
        ImGui.sameLine();
        if (systemBridge != null && ImGui.smallButton("Open")) systemBridge.openUrl(GITHUB_REPO);
        ImGui.separator();
        if (Controls.secondaryButton("Close")) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }
}
