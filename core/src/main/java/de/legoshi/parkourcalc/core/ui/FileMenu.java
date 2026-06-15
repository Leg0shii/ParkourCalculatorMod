package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.SaveController;
import de.legoshi.parkourcalc.core.ports.FilePickerPort;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveBrowseResult;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.Modal;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableRowFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** File-menu state, popups, and recent-files list. Owned by MainWindowOverlay. */
public final class FileMenu {

    private static final int MAX_RECENT = 5;
    private static final long STATUS_VISIBLE_MS = 4000L;
    private static final long STATUS_FADE_MS = 300L;

    private static final String POPUP_NAME_NEW = "###name_modal_new";
    private static final String POPUP_NAME_SAVEAS = "###name_modal_saveas";
    private static final String POPUP_OPEN = "###open_modal";
    private static final String TITLE_NAME_NEW = "New TAS";
    private static final String TITLE_NAME_SAVEAS = "Save TAS As";
    private static final String TITLE_OPEN = "Open TAS";
    private static final String POPUP_DISCARD = "###discard";
    private static final String POPUP_OVERWRITE = "###overwrite";
    private static final String POPUP_DELETE = "###delete";
    private static final String TITLE_DISCARD = "Discard unsaved changes?";
    private static final String TITLE_OVERWRITE = "Overwrite existing file?";
    private static final String TITLE_DELETE = "Move current TAS to recycle bin?";

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

    private static final long AUTO_SAVE_INTERVAL_NANOS = 30L * 1_000_000_000L;
    private long autoSaveIntervalNanos = AUTO_SAVE_INTERVAL_NANOS;
    private long autoSaveClockNanos;
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
    private String pendingNameTitle;
    private String activeNamePopupId;
    private String activeNameTitle;
    private boolean nameModalJustOpened;
    private Consumer<String> nameModalConfirm;
    private String nameModalError;
    private String lastNameInputSeen = "";
    private Runnable discardModalConfirm;
    private Runnable overwriteModalConfirm;
    private String overwriteCandidateName;

    private SaveBrowseResult cached = SaveBrowseResult.empty();
    private boolean cacheStale = true;
    private String openSelected;
    private String openCurrentDir = "";
    private static final int OPEN_MODAL_VISIBLE_ROWS = 12;
    private static final String PARENT_ENTRY = "..";

    private static String folderLabel(String name) {
        return name + "/";
    }

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
        ThemeManager.paddedSeparator();
        boolean hasName = controller.currentName() != null;
        if (ImGui.menuItem("Save", "Ctrl+S", false, hasName || controller.isDirty())) onSave();
        if (ImGui.menuItem("Save As...")) onSaveAs();
        if (ImGui.menuItem("Save debug values", null, settings.saveDebugValues)) {
            settings.saveDebugValues = !settings.saveDebugValues;
            onSettingsChanged.run();
        }
        ThemeManager.paddedSeparator();
        boolean hasPicker = filePicker != null;
        if (ImGui.menuItem("Import .json...", null, false, hasPicker)) onImport();
        ThemeManager.paddedSeparator();
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

    /** active=false means the main UI is closed; dismiss any open popup so it doesn't freeze on screen with no input to close it. */
    public void renderPopups(boolean active) {
        if (!active) {
            dismissPopups();
            return;
        }
        renderNameModal();
        renderOpenModal();
        renderDiscardModal();
        renderOverwriteModal();
        renderDeleteModal();
    }

    private void dismissPopups() {
        if (activeNamePopupId != null) {
            dismissIfOpen(activeNamePopupId, activeNameTitle);
            activeNamePopupId = null;
        }
        dismissIfOpen(POPUP_OPEN, TITLE_OPEN);
        dismissIfOpen(POPUP_DISCARD, TITLE_DISCARD);
        dismissIfOpen(POPUP_OVERWRITE, TITLE_OVERWRITE);
        dismissIfOpen(POPUP_DELETE, TITLE_DELETE);
    }

    private static void dismissIfOpen(String popupId, String title) {
        if (!ImGui.isPopupOpen(popupId)) return;
        if (Modal.begin(title, popupId)) {
            ImGui.closeCurrentPopup();
            Modal.end();
        }
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

        ThemeManager.pushStatusStripChrome(opacity);
        ThemeManager.pushStatusAreaChildBg();
        ImGui.beginChild("##status", 0f, childH, false, ImGuiWindowFlags.NoScrollbar);
        int color = statusIsError ? ThemeManager.dangerColor() : ThemeManager.okColor();
        ThemeManager.pushTextColor(color);
        ImGui.alignTextToFramePadding();
        ImGui.text(statusMessage);
        ThemeManager.popTextColor();
        ImGui.endChild();
        ThemeManager.popStatusAreaChildBg();
        ThemeManager.popStatusStripChrome();
    }

    public void renderEmptyStateCta() {
        String[] recent = settings.recentFiles;
        boolean hasRecent = recent != null && recent.length > 0;
        final int muted = ThemeManager.textMutedColor();

        ThemeManager.verticalSpace(ThemeManager.LG);

        Fonts.pushBold();
        ImGui.text("No TAS file loaded");
        Fonts.popBold();
        ThemeManager.verticalSpace(ThemeManager.XS);

        ThemeManager.pushTextColor(muted);
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + ImGui.getContentRegionAvail().x);
        ImGui.textWrapped("Create a new TAS or open an existing one to start editing tick data.");
        ImGui.popTextWrapPos();
        ThemeManager.popTextColor();

        ThemeManager.verticalSpace(ThemeManager.LG + ThemeManager.XS);

        if (Controls.primaryButton("+ New TAS")) onNewTas();
        ImGui.sameLine();
        if (Controls.secondaryButton("Open...")) onOpen();

        ThemeManager.verticalSpace(ThemeManager.LG + ThemeManager.LG);

        if (hasRecent) {
            ImGui.text("Recent");
            ThemeManager.verticalSpace(ThemeManager.SM);
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
            pendingNameTitle = TITLE_NAME_NEW;
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
        pendingNameTitle = TITLE_NAME_SAVEAS;
    }

    /** Ctrl+S: save under the current name, or fall into Save As when unnamed (gh-107). */
    public void quickSave() {
        onSave();
    }

    /** Call once per frame. While enabled, a named + dirty TAS is saved at most once per interval;
     *  the clock arms on the first dirty frame so a fresh edit is never written instantly (gh-107). */
    public void tickAutoSave() {
        if (!settings.autoSave) return;
        if (controller.currentName() == null || !controller.isDirty()) return;
        long now = System.nanoTime();
        if (autoSaveClockNanos == 0L) {
            autoSaveClockNanos = now;
            return;
        }
        if (now - autoSaveClockNanos < autoSaveIntervalNanos) return;
        Result<String> r = controller.save(controller.currentName());
        if (r.ok) {
            recordRecent(r.value);
            cacheStale = true;
        }
        else setStatus(r.error, true);
        autoSaveClockNanos = now;
    }

    public void setAutoSaveIntervalNanosForTests(long nanos) {
        this.autoSaveIntervalNanos = nanos;
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
        openCurrentDir = "";
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
        Path picked = filePicker.pickJsonFile();
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
        float opacity = (now <= statusActiveUntilMs) ? 1f : 1f - (float)(now - statusActiveUntilMs) / (float) STATUS_FADE_MS;
        float reservedH = ImGui.getFrameHeightWithSpacing() * Math.max(0f, opacity);
        if (reservedH - ImGui.getStyle().getItemSpacing().y < 1f) return 0f;
        return reservedH;
    }

    private void renderNameModal() {
        if (pendingNamePopupId != null) {
            ImGui.openPopup(pendingNamePopupId);
            activeNamePopupId = pendingNamePopupId;
            activeNameTitle = pendingNameTitle;
            pendingNamePopupId = null;
            nameModalJustOpened = true;
        }
        if (activeNamePopupId == null) return;
        if (!Modal.begin(activeNameTitle, activeNamePopupId)) {
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

        Modal.footerSeparator();

        boolean canSave = !currentTrim.isEmpty();
        boolean save;
        if (canSave) {
            save = enterPressed || Controls.primaryButton(BTN_SAVE);
        } else {
            Controls.disabledButton(BTN_SAVE);
            save = false;
        }
        if (save) {
            Consumer<String> action = nameModalConfirm;
            if (action != null) action.accept(currentTrim);
            if (nameModalError == null) {
                ImGui.closeCurrentPopup();
                activeNamePopupId = null;
            }
        }
        ImGui.sameLine();
        if (Modal.footerButton(BTN_CANCEL)) {
            ImGui.closeCurrentPopup();
            activeNamePopupId = null;
            nameModalError = null;
        }
        nameModalJustOpened = false;
        Modal.end();
    }

    private void renderOpenModal() {
        if (openOpenModal) {
            ImGui.openPopup(POPUP_OPEN);
            openOpenModal = false;
        }
        if (!Modal.begin(TITLE_OPEN, POPUP_OPEN, ImGuiWindowFlags.NoSavedSettings)) return;

        if (cacheStale) {
            cached = controller.browse(openCurrentDir);
            cacheStale = false;
        }

        renderOpenBreadcrumb();

        Controls.inputTextHint("Search", "Filter by name...", filterInput, 320);

        ThemeManager.sectionSpacing();

        List<String> folders = sortedFilteredFolders();
        List<SaveInfo> files = sortedFilteredFiles();
        boolean atRoot = openCurrentDir.isEmpty();
        float tableH = OPEN_MODAL_VISIBLE_ROWS * ImGui.getFrameHeightWithSpacing();

        String navigateInto = null;
        boolean navigateUp = false;
        String doubleClickedToOpen = null;

        float maxFilenameW = 0f, maxDateW = 0f, maxMcW = 0f, maxWorldW = 0f;
        if (!atRoot) maxFilenameW = Math.max(maxFilenameW, ImGui.calcTextSize(PARENT_ENTRY).x);
        for (String folder : folders) {
            maxFilenameW = Math.max(maxFilenameW, ImGui.calcTextSize(folderLabel(folder)).x);
        }
        for (SaveInfo info : files) {
            maxFilenameW = Math.max(maxFilenameW, ImGui.calcTextSize(info.name).x);
            String d = info.lastModifiedMs > 0 ? dateFmt.format(new Date(info.lastModifiedMs)) : "";
            maxDateW = Math.max(maxDateW, ImGui.calcTextSize(d).x);
            maxMcW = Math.max(maxMcW, ImGui.calcTextSize(info.mcVersion != null ? info.mcVersion : "?").x);
            maxWorldW = Math.max(maxWorldW, ImGui.calcTextSize(info.worldLabel != null ? info.worldLabel : "?").x);
        }

        if (ThemeManager.beginStandardClickableRowsTable("##open_table", 4, 0, 0f, tableH)) {
            ImGui.tableSetupScrollFreeze(0, 1);
            ImGui.tableSetupColumn(COL_FILENAME, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableLeftmostColumnWidth(COL_FILENAME, maxFilenameW));
            ImGui.tableSetupColumn(COL_DATE, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableColumnWidth(COL_DATE, maxDateW));
            ImGui.tableSetupColumn(COL_MC, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableColumnWidth(COL_MC, maxMcW));
            ImGui.tableSetupColumn(COL_WORLD, ImGuiTableColumnFlags.WidthFixed, ThemeManager.tableRightmostColumnWidth(COL_WORLD, maxWorldW, ThemeManager.tableScrollbarSlack()));
            renderOpenTableHeader();

            float rowH = ThemeManager.tableRowHeight();
            int rowIndex = 0;

            if (!atRoot) {
                if (renderFolderRow("##open_up", PARENT_ENTRY, rowIndex++, rowH)) navigateUp = true;
            }
            for (String folder : folders) {
                if (renderFolderRow("##open_dir_" + folder, folderLabel(folder), rowIndex++, rowH)) {
                    navigateInto = folder;
                }
            }

            for (SaveInfo info : files) {
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
                ImGui.getWindowDrawList().addText(cellOrigin.x, labelY, ThemeManager.tableCellText(selected), info.name);

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

        if (navigateUp) {
            goUpFolder();
            Modal.end();
            return;
        }
        if (navigateInto != null) {
            enterFolder(navigateInto);
            Modal.end();
            return;
        }
        if (doubleClickedToOpen != null) {
            ImGui.closeCurrentPopup();
            onLoad(joinPath(openCurrentDir, doubleClickedToOpen));
            Modal.end();
            return;
        }

        Modal.footerSeparator();
        boolean canOpen = openSelected != null;
        boolean enterPressed = canOpen && ImGui.isKeyPressed(ImGuiKey.Enter, false);
        boolean openClicked;
        if (canOpen) {
            openClicked = Controls.primaryButton(BTN_OPEN) || enterPressed;
        } else {
            Controls.disabledButton(BTN_OPEN);
            openClicked = false;
        }
        if (openClicked) {
            String key = joinPath(openCurrentDir, openSelected);
            ImGui.closeCurrentPopup();
            onLoad(key);
            Modal.end();
            return;
        }
        ImGui.sameLine();
        if (Modal.footerButton(BTN_CANCEL)) ImGui.closeCurrentPopup();

        Modal.end();
    }

    private boolean renderFolderRow(String idSuffix, String label, int rowIndex, float rowH) {
        ImGui.tableNextRow(0, rowH);
        ThemeManager.paintTableRowBg(rowIndex);
        ImGui.tableSetColumnIndex(0);
        ThemeManager.tableLeftmostCellPad();
        ImVec2 cellOrigin = ImGui.getCursorScreenPos();
        ImGui.alignTextToFramePadding();
        int selFlags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowDoubleClick;
        boolean activated = ImGui.selectable(idSuffix, false, selFlags);
        float labelY = cellOrigin.y + ImGui.getStyle().getFramePadding().y;
        ImGui.getWindowDrawList().addText(cellOrigin.x, labelY, ThemeManager.accentColor(), label);
        ImGui.tableSetColumnIndex(3);
        ThemeManager.tableRightmostCellTrailingPad();
        return activated;
    }

    private void renderOpenBreadcrumb() {
        ThemeManager.pushTextColor(ThemeManager.textMutedColor());
        ImGui.text("saves");
        ThemeManager.popTextColor();
        if (openCurrentDir.isEmpty()) {
            ThemeManager.sectionSpacing();
            return;
        }
        String[] parts = openCurrentDir.split("/");
        StringBuilder accum = new StringBuilder();
        String jumpTo = null;
        for (int i = 0; i < parts.length; i++) {
            if (accum.length() > 0) accum.append('/');
            accum.append(parts[i]);
            ImGui.sameLine();
            ThemeManager.pushTextColor(ThemeManager.textMutedColor());
            ImGui.text("/");
            ThemeManager.popTextColor();
            ImGui.sameLine();
            boolean last = i == parts.length - 1;
            if (last) {
                ImGui.text(parts[i]);
            } else {
                ThemeManager.pushTextColor(ThemeManager.accentColor());
                if (ImGui.selectable(parts[i] + "##crumb_" + i, false, 0, ImGui.calcTextSize(parts[i]).x, 0f)) {
                    jumpTo = accum.toString();
                }
                ThemeManager.popTextColor();
            }
        }
        if (jumpTo != null) navigateToFolder(jumpTo);
        ThemeManager.sectionSpacing();
    }

    private void enterFolder(String folderName) {
        navigateToFolder(joinPath(openCurrentDir, folderName));
    }

    private void goUpFolder() {
        int slash = openCurrentDir.lastIndexOf('/');
        navigateToFolder(slash < 0 ? "" : openCurrentDir.substring(0, slash));
    }

    private void navigateToFolder(String relDir) {
        openCurrentDir = relDir == null ? "" : relDir;
        openSelected = null;
        cacheStale = true;
    }

    private static String joinPath(String dir, String name) {
        return (dir == null || dir.isEmpty()) ? name : dir + "/" + name;
    }

    private void renderDiscardModal() {
        if (openDiscardModal) {
            ImGui.openPopup(POPUP_DISCARD);
            openDiscardModal = false;
        }
        if (!Modal.begin(TITLE_DISCARD, POPUP_DISCARD)) return;
        String current = controller.currentName();
        ImGui.text(current != null
                ? "You have unsaved changes to '" + current + "'."
                : "You have unsaved changes that have not been saved.");
        ImGui.text("Discard them and continue?");
        Modal.footerSeparator();
        if (Controls.dangerButton(BTN_DISCARD)) {
            Runnable action = discardModalConfirm;
            ImGui.closeCurrentPopup();
            if (action != null) {
                controller.discardCurrent();
                action.run();
            }
        }
        ImGui.sameLine();
        if (Modal.footerButton(BTN_CANCEL)) ImGui.closeCurrentPopup();
        Modal.end();
    }

    private void renderOverwriteModal() {
        if (openOverwriteModal) {
            ImGui.openPopup(POPUP_OVERWRITE);
            openOverwriteModal = false;
        }
        if (!Modal.begin(TITLE_OVERWRITE, POPUP_OVERWRITE)) return;
        ImGui.text("A save named '" + overwriteCandidateName + "' already exists.");
        ImGui.text("Overwrite it?");
        Modal.footerSeparator();
        if (Controls.dangerButton(BTN_OVERWRITE)) {
            Runnable action = overwriteModalConfirm;
            ImGui.closeCurrentPopup();
            if (action != null) action.run();
        }
        ImGui.sameLine();
        if (Modal.footerButton(BTN_CANCEL)) ImGui.closeCurrentPopup();
        Modal.end();
    }

    private void renderDeleteModal() {
        if (openDeleteModal) {
            ImGui.openPopup(POPUP_DELETE);
            openDeleteModal = false;
        }
        if (!Modal.begin(TITLE_DELETE, POPUP_DELETE)) return;
        String current = controller.currentName();
        ImGui.text("Move '" + (current != null ? current : "?") + "' to <save dir>/.trash/?");
        ImGui.textDisabled("Not the OS recycle bin. Restore by hand if needed.");
        Modal.footerSeparator();
        if (Controls.dangerButton(BTN_RECYCLE)) {
            ImGui.closeCurrentPopup();
            doDelete();
        }
        ImGui.sameLine();
        if (Modal.footerButton(BTN_CANCEL)) ImGui.closeCurrentPopup();
        Modal.end();
    }

    private void renderOpenTableHeader() {
        ThemeManager.tableHeaderRow();
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

    private List<SaveInfo> sortedFilteredFiles() {
        String needle = filterInput.get().toLowerCase(Locale.US).trim();
        List<SaveInfo> out = new ArrayList<>(cached.files.size());
        for (SaveInfo info : cached.files) {
            if (needle.isEmpty() || info.name.toLowerCase(Locale.US).contains(needle)) out.add(info);
        }
        out.sort((a, b) -> Long.compare(b.lastModifiedMs, a.lastModifiedMs));
        return out;
    }

    private List<String> sortedFilteredFolders() {
        String needle = filterInput.get().toLowerCase(Locale.US).trim();
        List<String> out = new ArrayList<>(cached.folders.size());
        for (String folder : cached.folders) {
            if (needle.isEmpty() || folder.toLowerCase(Locale.US).contains(needle)) out.add(folder);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

}
