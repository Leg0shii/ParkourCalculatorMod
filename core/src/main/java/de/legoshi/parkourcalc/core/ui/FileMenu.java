package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.SaveController;
import de.legoshi.parkourcalc.core.ports.FilePickerPort;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableRowFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** File-menu state, popups, and recent-files list. Owned by MainWindowOverlay. */
public final class FileMenu {

    private static final int MAX_RECENT = 5;
    private static final long STATUS_VISIBLE_MS = 4000L;
    private static final long STATUS_FADE_MS = 300L;

    private static final String POPUP_NAME_NEW = "New TAS##name_modal_new";
    private static final String POPUP_NAME_SAVEAS = "Save TAS As##name_modal_saveas";
    private static final String POPUP_OPEN = "Open TAS##open_modal";
    private static final String POPUP_DISCARD = "Discard unsaved changes?##discard";
    private static final String POPUP_OVERWRITE = "Overwrite existing file?##overwrite";
    private static final String POPUP_DELETE = "Move current TAS to recycle bin?##delete";

    private static final String BTN_SAVE = "Save";
    private static final String BTN_OPEN = "Open";
    private static final String BTN_CANCEL = "Cancel";
    private static final String BTN_DISCARD = "Discard";
    private static final String BTN_OVERWRITE = "Overwrite";
    private static final String BTN_RECYCLE = "Move to Recycle";

    private static final String COL_FILENAME = "Filename";
    private static final String COL_DATE = "Date Modified";
    private static final String COL_MC = "MC";
    private static final String COL_WORLD = "World";

    private final SaveController controller;
    private final FilePickerPort filePicker;
    private final Settings settings;
    private final Runnable onSettingsChanged;

    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    private final ImString nameInput = new ImString(64);
    private final ImString filterInput = new ImString(64);

    private boolean openOpenModal;
    private boolean openDiscardModal;
    private boolean openOverwriteModal;
    private boolean openDeleteModal;

    private String pendingNamePopupId;
    private String activeNamePopupId;
    private boolean nameModalJustOpened;
    private Consumer<String> nameModalConfirm;
    private String nameModalError;
    private String lastNameInputSeen = "";
    private Runnable discardModalConfirm;
    private Runnable overwriteModalConfirm;
    private String overwriteCandidateName;

    private List<SaveInfo> cached = Collections.emptyList();
    private boolean cacheStale = true;
    private String openSelected;
    private static final int OPEN_MODAL_VISIBLE_ROWS = 12;

    private String statusMessage;
    private boolean statusIsError;
    private long statusActiveUntilMs;
    private long statusFadeUntilMs;

    public FileMenu(SaveController controller, FilePickerPort filePicker,
                    Settings settings, Runnable onSettingsChanged) {
        this.controller = controller;
        this.filePicker = filePicker;
        this.settings = settings;
        this.onSettingsChanged = onSettingsChanged;
    }

    public boolean hasOpenTas() {
        return controller.currentName() != null;
    }

    public String currentName() {
        return controller.currentName();
    }

    public boolean isDirty() {
        return controller.isDirty();
    }

    public void renderMenuItems() {
        if (ImGui.menuItem("New TAS")) onNewTas();
        if (ImGui.menuItem("Open...")) onOpen();
        renderRecentSubmenu();
        ImGui.separator();
        boolean hasName = controller.currentName() != null;
        if (ImGui.menuItem("Save", null, false, hasName || controller.isDirty())) onSave();
        if (ImGui.menuItem("Save As...")) onSaveAs();
        ImGui.separator();
        boolean hasPicker = filePicker != null;
        if (ImGui.menuItem("Import .tas...", null, false, hasPicker)) onImport();
        ImGui.separator();
        if (ImGui.menuItem("Delete current TAS", null, false, hasName)) onDelete();
    }

    private void renderRecentSubmenu() {
        String[] recent = settings.recentFiles;
        boolean any = recent != null && recent.length > 0;
        if (!ImGui.beginMenu("Open Recent", any)) return;
        for (String name : recent) {
            if (ImGui.menuItem(name)) onLoad(name);
        }
        ImGui.endMenu();
    }

    public void renderPopups() {
        renderNameModal();
        renderOpenModal();
        renderDiscardModal();
        renderOverwriteModal();
        renderDeleteModal();
    }

    public void renderStatusLine() {
        if (statusMessage == null) return;
        long now = System.currentTimeMillis();
        if (now >= statusFadeUntilMs) {
            statusMessage = null;
            return;
        }
        float opacity = (now <= statusActiveUntilMs) ? 1f
                : 1f - (float)(now - statusActiveUntilMs) / (float) STATUS_FADE_MS;
        if (opacity <= 0f) {
            statusMessage = null;
            return;
        }
        float reservedH = ImGui.getFrameHeightWithSpacing() * opacity;
        float childH = reservedH - ImGui.getStyle().getItemSpacing().y;
        if (childH < 1f) return;

        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, opacity);
        ThemeManager.pushStatusAreaChildBg();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 0f);
        ImGui.beginChild("##status", 0f, childH, false, ImGuiWindowFlags.NoScrollbar);
        int color = statusIsError ? ThemeManager.dangerColor() : ThemeManager.okColor();
        ThemeManager.pushTextColor(color);
        ImGui.alignTextToFramePadding();
        ImGui.text(statusMessage);
        ThemeManager.popTextColor();
        ImGui.endChild();
        ImGui.popStyleVar();
        ThemeManager.popStatusAreaChildBg();
        ImGui.popStyleVar();
    }

    public void renderEmptyStateCta() {
        String[] recent = settings.recentFiles;
        boolean hasRecent = recent != null && recent.length > 0;
        final int muted = ThemeManager.textMutedColor();

        ImGui.dummy(0f, 16f);

        Fonts.pushBold();
        ImGui.text("No TAS file loaded");
        Fonts.popBold();
        ImGui.dummy(0f, 4f);

        ThemeManager.pushTextColor(muted);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + ImGui.getContentRegionAvail().x);
        ImGui.textWrapped("Create a new TAS or open an existing one to start editing tick data.");
        ImGui.popTextWrapPos();
        ThemeManager.popTextColor();

        ImGui.dummy(0f, 20f);

        if (Controls.primaryButton("+ New TAS")) onNewTas();
        ImGui.sameLine();
        if (Controls.secondaryButton("Open...")) onOpen();

        ImGui.dummy(0f, 32f);

        if (hasRecent) {
            ImGui.text("Recent");
            ImGui.dummy(0f, 8f);
            ThemeManager.pushTextColor(ThemeManager.accentColor());
            for (String name : recent) {
                if (ImGui.selectable(name + "##cta_rec_" + name, false)) onLoad(name);
            }
            ThemeManager.popTextColor();
        }
    }

    private void onNewTas() {
        Runnable proceed = () -> {
            nameInput.set("");
            nameModalError = null;
            lastNameInputSeen = "";
            nameModalConfirm = this::doNewTas;
            pendingNamePopupId = POPUP_NAME_NEW;
        };
        if (controller.isDirty()) requestDiscardConfirm(proceed);
        else proceed.run();
    }

    private void onSaveAs() {
        nameInput.set(controller.currentName() == null ? "" : controller.currentName());
        nameModalError = null;
        lastNameInputSeen = nameInput.get();
        nameModalConfirm = this::doSaveAs;
        pendingNamePopupId = POPUP_NAME_SAVEAS;
    }

    private void onSave() {
        String current = controller.currentName();
        if (current == null) {
            onSaveAs();
            return;
        }
        Result<String> r = controller.save(current);
        applyResult(r, "Saved '%s'");
    }

    private void onOpen() {
        cacheStale = true;
        openSelected = null;
        filterInput.set("");
        openOpenModal = true;
    }

    private void onLoad(String name) {
        Runnable proceed = () -> doLoad(name);
        if (controller.isDirty()) requestDiscardConfirm(proceed);
        else proceed.run();
    }

    private void onImport() {
        if (filePicker == null) {
            setStatus("File picker not available.", true);
            return;
        }
        Path picked = filePicker.pickTasFile();
        if (picked == null) return;
        Result<String> r = controller.importFromPath(picked);
        applyResult(r, "Imported '%s'");
    }

    private void onDelete() {
        if (controller.currentName() == null) return;
        openDeleteModal = true;
    }

    private void requestDiscardConfirm(Runnable onConfirmed) {
        discardModalConfirm = onConfirmed;
        openDiscardModal = true;
    }

    private void doNewTas(String name) {
        if (name.isEmpty()) { nameModalError = "Name cannot be empty."; return; }
        if (controller.exists(name)) {
            overwriteCandidateName = name;
            overwriteModalConfirm = () -> finalizeNewTas(name);
            openOverwriteModal = true;
            return;
        }
        finalizeNewTas(name);
    }

    private void finalizeNewTas(String name) {
        controller.newSession();
        Result<String> r = controller.save(name);
        applyResult(r, "Created '%s'");
    }

    private void doSaveAs(String name) {
        if (name.isEmpty()) { nameModalError = "Name cannot be empty."; return; }
        if (controller.exists(name)) {
            overwriteCandidateName = name;
            overwriteModalConfirm = () -> finalizeSaveAs(name);
            openOverwriteModal = true;
            return;
        }
        finalizeSaveAs(name);
    }

    private void finalizeSaveAs(String name) {
        Result<String> r = controller.save(name);
        applyResult(r, "Saved as '%s'");
    }

    private void doLoad(String name) {
        Result<SaveFile> r = controller.load(name);
        if (r.ok) {
            recordRecent(name);
            setStatus("Loaded '" + name + "'", false);
        } else {
            setStatus(r.error, true);
        }
    }

    private void doDelete() {
        String name = controller.currentName();
        if (name == null) return;
        if (controller.delete(name)) {
            removeRecent(name);
            setStatus("Moved '" + name + "' to recycle bin.", false);
            cacheStale = true;
        } else {
            setStatus("Failed to delete '" + name + "'.", true);
        }
    }

    private void applyResult(Result<String> r, String successFmt) {
        if (r.ok) {
            recordRecent(r.value);
            setStatus(String.format(successFmt, r.value), false);
            cacheStale = true;
        } else {
            setStatus(r.error, true);
        }
    }

    private void recordRecent(String name) {
        if (name == null || name.isEmpty()) return;
        List<String> next = new ArrayList<>();
        next.add(name);
        if (settings.recentFiles != null) {
            for (String existing : settings.recentFiles) {
                if (!existing.equals(name) && next.size() < MAX_RECENT) next.add(existing);
            }
        }
        settings.recentFiles = next.toArray(new String[0]);
        onSettingsChanged.run();
    }

    private void removeRecent(String name) {
        if (name == null || settings.recentFiles == null) return;
        List<String> next = new ArrayList<>();
        for (String existing : settings.recentFiles) {
            if (!existing.equals(name)) next.add(existing);
        }
        if (next.size() != settings.recentFiles.length) {
            settings.recentFiles = next.toArray(new String[0]);
            onSettingsChanged.run();
        }
    }

    private void setStatus(String message, boolean error) {
        this.statusMessage = message;
        this.statusIsError = error;
        long now = System.currentTimeMillis();
        this.statusActiveUntilMs = now + STATUS_VISIBLE_MS;
        this.statusFadeUntilMs = this.statusActiveUntilMs + STATUS_FADE_MS;
    }

    public float statusStripHeight() {
        if (statusMessage == null) return 0f;
        long now = System.currentTimeMillis();
        if (now >= statusFadeUntilMs) return 0f;
        float opacity = (now <= statusActiveUntilMs) ? 1f
                : 1f - (float)(now - statusActiveUntilMs) / (float) STATUS_FADE_MS;
        float reservedH = ImGui.getFrameHeightWithSpacing() * Math.max(0f, opacity);
        if (reservedH - ImGui.getStyle().getItemSpacing().y < 1f) return 0f;
        return reservedH;
    }

    private void renderNameModal() {
        if (pendingNamePopupId != null) {
            ImGui.openPopup(pendingNamePopupId);
            activeNamePopupId = pendingNamePopupId;
            pendingNamePopupId = null;
            nameModalJustOpened = true;
        }
        if (activeNamePopupId == null) return;
        if (!ImGui.beginPopupModal(activeNamePopupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            activeNamePopupId = null;
            return;
        }

        if (nameModalJustOpened) {
            ImGui.setKeyboardFocusHere();
        }
        Controls.inputTextHint("Name", "e.g. any-name", nameInput, 320);
        boolean enterPressed = !nameModalJustOpened
                && ImGui.isItemFocused()
                && ImGui.isKeyPressed(ImGuiKey.Enter, false);

        String currentTrim = nameInput.get().trim();
        if (!currentTrim.equals(lastNameInputSeen)) {
            nameModalError = null;
            lastNameInputSeen = currentTrim;
        }
        if (!currentTrim.isEmpty() && "Name cannot be empty.".equals(nameModalError)) {
            nameModalError = null;
        }

        if (nameModalError != null) {
            ThemeManager.pushTextColor(ThemeManager.dangerColor());
            ImGui.text(nameModalError);
            ThemeManager.popTextColor();
        }

        ThemeManager.sectionSpacing();
        ImGui.separator();

        boolean canSave = !currentTrim.isEmpty();
        ImGui.beginDisabled(!canSave);
        boolean save = (enterPressed && canSave) || Controls.primaryButton(BTN_SAVE);
        ImGui.endDisabled();
        if (save) {
            Consumer<String> action = nameModalConfirm;
            if (action != null) action.accept(currentTrim);
            if (nameModalError == null) {
                ImGui.closeCurrentPopup();
                activeNamePopupId = null;
            }
        }
        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_CANCEL)) {
            ImGui.closeCurrentPopup();
            activeNamePopupId = null;
            nameModalError = null;
        }
        nameModalJustOpened = false;
        ImGui.endPopup();
    }

    private void renderOpenModal() {
        if (openOpenModal) {
            ImGui.openPopup(POPUP_OPEN);
            openOpenModal = false;
        }
        int modalFlags = ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize;
        if (!ImGui.beginPopupModal(POPUP_OPEN, modalFlags)) return;

        if (cacheStale) {
            cached = controller.list();
            cacheStale = false;
        }

        Controls.inputTextHint("Search", "Filter by name...", filterInput, 320);

        ThemeManager.sectionSpacing();

        java.util.List<SaveInfo> rows = sortedFiltered();
        float tableH = OPEN_MODAL_VISIBLE_ROWS * ImGui.getFrameHeightWithSpacing();
        String doubleClickedToOpen = null;

        float maxFilenameW = 0f, maxDateW = 0f, maxMcW = 0f, maxWorldW = 0f;
        for (SaveInfo info : rows) {
            maxFilenameW = Math.max(maxFilenameW, ImGui.calcTextSize(info.name).x);
            String d = info.lastModifiedMs > 0 ? dateFmt.format(new Date(info.lastModifiedMs)) : "";
            maxDateW = Math.max(maxDateW, ImGui.calcTextSize(d).x);
            maxMcW = Math.max(maxMcW, ImGui.calcTextSize(info.mcVersion != null ? info.mcVersion : "?").x);
            maxWorldW = Math.max(maxWorldW, ImGui.calcTextSize(info.worldLabel != null ? info.worldLabel : "?").x);
        }

        if (ThemeManager.beginStandardClickableRowsTable("##open_table", 4, 0, 0f, tableH)) {
            ImGui.tableSetupScrollFreeze(0, 1);
            ImGui.tableSetupColumn(COL_FILENAME, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableLeftmostColumnWidth(COL_FILENAME, maxFilenameW));
            ImGui.tableSetupColumn(COL_DATE, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(COL_DATE, maxDateW));
            ImGui.tableSetupColumn(COL_MC, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(COL_MC, maxMcW));
            ImGui.tableSetupColumn(COL_WORLD, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableRightmostColumnWidth(COL_WORLD, maxWorldW, ThemeManager.tableScrollbarSlack()));
            renderOpenTableHeader();

            float rowH = ImGui.getFrameHeight() + ImGui.getStyle().getCellPadding().y * 2f;
            int rowIndex = 0;
            for (SaveInfo info : rows) {
                ImGui.tableNextRow(0, rowH);
                ThemeManager.paintTableRowBg(rowIndex++);
                boolean selected = info.name.equals(openSelected);
                ImGui.tableSetColumnIndex(0);
                ThemeManager.tableLeftmostCellPad();
                ImVec2 cellOrigin = ImGui.getCursorScreenPos();
                ImGui.alignTextToFramePadding();
                int selFlags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowDoubleClick;
                if (ImGui.selectable("##open_row_" + info.name, selected, selFlags)) {
                    openSelected = info.name;
                    if (ImGui.isMouseDoubleClicked(0)) doubleClickedToOpen = info.name;
                }
                float labelY = cellOrigin.y + ImGui.getStyle().getFramePadding().y;
                ImGui.getWindowDrawList().addText(cellOrigin.x, labelY,
                        ThemeManager.tableCellText(selected), info.name);

                ImGui.tableSetColumnIndex(1);
                ImGui.alignTextToFramePadding();
                ImGui.text(info.lastModifiedMs > 0 ? dateFmt.format(new Date(info.lastModifiedMs)) : "");
                ImGui.tableSetColumnIndex(2);
                ImGui.alignTextToFramePadding();
                ImGui.text(info.mcVersion != null ? info.mcVersion : "?");
                ImGui.tableSetColumnIndex(3);
                ImGui.alignTextToFramePadding();
                ImGui.text(info.worldLabel != null ? info.worldLabel : "?");
                ThemeManager.tableRightmostCellTrailingPad();
            }
            ThemeManager.endStandardTable();
        }

        if (doubleClickedToOpen != null) {
            String name = doubleClickedToOpen;
            ImGui.closeCurrentPopup();
            onLoad(name);
            ImGui.endPopup();
            return;
        }

        ThemeManager.sectionSpacing();
        ImGui.separator();
        boolean canOpen = openSelected != null;
        boolean enterPressed = canOpen && ImGui.isKeyPressed(ImGuiKey.Enter, false);
        ImGui.beginDisabled(!canOpen);
        if (Controls.primaryButton(BTN_OPEN) || enterPressed) {
            String name = openSelected;
            ImGui.closeCurrentPopup();
            onLoad(name);
            ImGui.endDisabled();
            ImGui.endPopup();
            return;
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_CANCEL)) ImGui.closeCurrentPopup();

        ImGui.endPopup();
    }

    private void renderDiscardModal() {
        if (openDiscardModal) {
            ImGui.openPopup(POPUP_DISCARD);
            openDiscardModal = false;
        }
        if (!ImGui.beginPopupModal(POPUP_DISCARD, ImGuiWindowFlags.AlwaysAutoResize)) return;
        String current = controller.currentName();
        ImGui.text(current != null
                ? "You have unsaved changes to '" + current + "'."
                : "You have unsaved changes that have not been saved.");
        ImGui.text("Discard them and continue?");
        ThemeManager.sectionSpacing();
        ImGui.separator();
        if (Controls.dangerButton(BTN_DISCARD)) {
            Runnable action = discardModalConfirm;
            ImGui.closeCurrentPopup();
            if (action != null) {
                controller.discardCurrent();
                action.run();
            }
        }
        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_CANCEL)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    private void renderOverwriteModal() {
        if (openOverwriteModal) {
            ImGui.openPopup(POPUP_OVERWRITE);
            openOverwriteModal = false;
        }
        if (!ImGui.beginPopupModal(POPUP_OVERWRITE, ImGuiWindowFlags.AlwaysAutoResize)) return;
        ImGui.text("A save named '" + overwriteCandidateName + "' already exists.");
        ImGui.text("Overwrite it?");
        ThemeManager.sectionSpacing();
        ImGui.separator();
        if (Controls.dangerButton(BTN_OVERWRITE)) {
            Runnable action = overwriteModalConfirm;
            ImGui.closeCurrentPopup();
            if (action != null) action.run();
        }
        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_CANCEL)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    private void renderDeleteModal() {
        if (openDeleteModal) {
            ImGui.openPopup(POPUP_DELETE);
            openDeleteModal = false;
        }
        if (!ImGui.beginPopupModal(POPUP_DELETE, ImGuiWindowFlags.AlwaysAutoResize)) return;
        String current = controller.currentName();
        ImGui.text("Move '" + (current != null ? current : "?") + "' to <save dir>/.trash/?");
        ImGui.textDisabled("Not the OS recycle bin. Restore by hand if needed.");
        ThemeManager.sectionSpacing();
        ImGui.separator();
        if (Controls.dangerButton(BTN_RECYCLE)) {
            ImGui.closeCurrentPopup();
            doDelete();
        }
        ImGui.sameLine();
        if (Controls.secondaryButton(BTN_CANCEL)) ImGui.closeCurrentPopup();
        ImGui.endPopup();
    }

    private void renderOpenTableHeader() {
        ImGui.tableNextRow(ImGuiTableRowFlags.Headers);
        ThemeManager.paintTableHeader();
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ThemeManager.tableHeader(COL_FILENAME);
        ImGui.tableSetColumnIndex(1);
        ThemeManager.tableHeader(COL_DATE);
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeader(COL_MC);
        ImGui.tableSetColumnIndex(3);
        ThemeManager.tableHeader(COL_WORLD);
        ThemeManager.tableRightmostCellTrailingPad();
    }

    private List<SaveInfo> sortedFiltered() {
        String needle = filterInput.get().toLowerCase(Locale.US).trim();
        List<SaveInfo> out = new ArrayList<>(cached.size());
        for (SaveInfo info : cached) {
            if (needle.isEmpty() || info.name.toLowerCase(Locale.US).contains(needle)) out.add(info);
        }
        out.sort(new Comparator<SaveInfo>() {
            @Override public int compare(SaveInfo a, SaveInfo b) {
                return Long.compare(b.lastModifiedMs, a.lastModifiedMs);
            }
        });
        return out;
    }

}
