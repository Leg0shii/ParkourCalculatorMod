package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.SaveController;
import de.legoshi.parkourcalc.core.ports.FilePickerPort;
import de.legoshi.parkourcalc.core.save.Result;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.ui.theme.Controls;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImGui;
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

/**
 * Owns all File-menu state: popup flags, name input, file list cache, recent files
 * tracking, and modal rendering. MainWindowOverlay delegates menu rendering and popup
 * rendering here.
 */
public final class FileMenu {

    private static final int MAX_RECENT = 5;
    private static final long STATUS_LIFETIME_MS = 4000L;

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
    private long statusUntilMs;

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
        if (ImGui.menuItem("New TAS", "Ctrl+N")) onNewTas();
        if (ImGui.menuItem("Open...", "Ctrl+O")) onOpen();
        renderRecentSubmenu();
        ImGui.separator();
        boolean hasName = controller.currentName() != null;
        if (ImGui.menuItem("Save", "Ctrl+S", false, hasName || controller.isDirty())) onSave();
        if (ImGui.menuItem("Save As...", "Ctrl+Shift+S")) onSaveAs();
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
        // Child height = FrameHeight (NOT WithSpacing) because ImGui auto-inserts
        // ItemSpacing.y between the table above and this child; the parent's
        // footer reservation of FrameHeightWithSpacing covers spacing + frame
        // once. WithSpacing here would double-count and overflow the parent.
        // Darker bg marks the output zone without a frame border (which read as
        // "input you can type into"). Zero vertical WindowPadding so the text
        // fits inside one frame height.
        ThemeManager.pushStatusAreaChildBg();
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 0f);
        ImGui.beginChild("##status", 0f, ImGui.getFrameHeight(), false, ImGuiWindowFlags.NoScrollbar);
        if (statusMessage != null && System.currentTimeMillis() <= statusUntilMs) {
            int color = statusIsError ? ThemeManager.dangerColor() : ThemeManager.okColor();
            ThemeManager.pushTextColor(color);
            ImGui.alignTextToFramePadding();
            ImGui.text(statusMessage);
            ThemeManager.popTextColor();
        } else {
            statusMessage = null;
        }
        ImGui.endChild();
        ImGui.popStyleVar();
        ThemeManager.popStatusAreaChildBg();
    }

    public void renderEmptyStateCta() {
        float avail = ImGui.getContentRegionAvail().y;
        if (avail > 80) ImGui.dummy(0, Math.min(40, avail * 0.15f));

        centerText("Parkour Calculator", 1.4f);
        ImGui.dummy(0, 16);

        float btnW = 280f;
        if (centerButton("+ New TAS (Ctrl+N)", btnW)) onNewTas();
        ImGui.dummy(0, 6);
        if (centerButton("Open... (Ctrl+O)", btnW)) onOpen();

        ImGui.dummy(0, 20);

        String[] recent = settings.recentFiles;
        if (recent != null && recent.length > 0) {
            indent(btnW);
            ImGui.text("Open Recent:");
            for (String name : recent) {
                indent(btnW);
                if (ImGui.selectable("  " + name + "##cta_" + name)) onLoad(name);
            }
            ImGui.dummy(0, 16);
        }

        renderWrappedTip(btnW, "Create a new TAS or open an existing one to start editing rows.");
        renderWrappedTip(btnW, "TAS files are loaded manually; they don't reload when you rejoin a world.");
    }

    private static void renderWrappedTip(float width, String text) {
        indent(width);
        float startX = ImGui.getCursorPosX();
        ImGui.pushTextWrapPos(startX + width);
        ImGui.textDisabled(text);
        ImGui.popTextWrapPos();
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
        this.statusUntilMs = System.currentTimeMillis() + STATUS_LIFETIME_MS;
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

        // Claim focus on the first frame so a stray Enter from the menu activation
        // can't slip into the input and trigger commit on a single keystroke.
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
        // Any non-empty value clears a stale "name cannot be empty" error even if the
        // trimmed buffer matches what we last saw (user typed then deleted then retyped).
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
        if (ImGui.beginTable("##open_table", 4, ThemeManager.standardTableFlags(), 0, tableH)) {
            ImGui.tableSetupScrollFreeze(0, 1);
            ImGui.tableSetupColumn(COL_FILENAME, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableLeftmostColumnWidth(COL_FILENAME, 240));
            ImGui.tableSetupColumn(COL_DATE, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(COL_DATE, 140));
            ImGui.tableSetupColumn(COL_MC, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableColumnWidth(COL_MC, 60));
            ImGui.tableSetupColumn(COL_WORLD, ImGuiTableColumnFlags.WidthFixed,
                    ThemeManager.tableRightmostColumnWidth(COL_WORLD, 160));
            renderOpenTableHeader();

            String doubleClickedToOpen = null;
            int rowIndex = 0;
            for (SaveInfo info : rows) {
                ImGui.tableNextRow();
                ThemeManager.paintTableRowBg(rowIndex++);
                ImGui.tableSetColumnIndex(0);
                ThemeManager.emitTableLeftmostCellPad();
                boolean selected = info.name.equals(openSelected);
                int selFlags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowDoubleClick;
                if (ImGui.selectable(info.name + "##open_row_" + info.name, selected, selFlags)) {
                    openSelected = info.name;
                    if (ImGui.isMouseDoubleClicked(0)) doubleClickedToOpen = info.name;
                }
                ImGui.tableSetColumnIndex(1);
                ImGui.alignTextToFramePadding();
                ImGui.text(info.lastModifiedMs > 0 ? dateFmt.format(new Date(info.lastModifiedMs)) : "");
                ImGui.tableSetColumnIndex(2);
                ImGui.alignTextToFramePadding();
                ImGui.text(info.mcVersion != null ? info.mcVersion : "?");
                ImGui.tableSetColumnIndex(3);
                ImGui.alignTextToFramePadding();
                ImGui.text(info.worldLabel != null ? info.worldLabel : "?");
                ThemeManager.emitTableRightmostCellTrailingPad();
            }
            ImGui.endTable();

            if (doubleClickedToOpen != null) {
                String name = doubleClickedToOpen;
                ImGui.closeCurrentPopup();
                onLoad(name);
                ImGui.endPopup();
                return;
            }
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
        ThemeManager.emitTableLeftmostCellPad();
        ThemeManager.tableHeader(COL_FILENAME);
        ImGui.tableSetColumnIndex(1);
        ThemeManager.tableHeader(COL_DATE);
        ImGui.tableSetColumnIndex(2);
        ThemeManager.tableHeader(COL_MC);
        ImGui.tableSetColumnIndex(3);
        ThemeManager.tableHeader(COL_WORLD);
        ThemeManager.emitTableRightmostCellTrailingPad();
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

    private static void centerText(String text, float scale) {
        float w = ImGui.calcTextSize(text).x * scale;
        float avail = ImGui.getContentRegionAvail().x;
        if (avail > w) ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - w) * 0.5f);
        ImGui.text(text);
    }

    private static boolean centerButton(String label, float width) {
        float avail = ImGui.getContentRegionAvail().x;
        if (avail > width) ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - width) * 0.5f);
        return ImGui.button(label, width, 0);
    }

    private static void indent(float width) {
        float avail = ImGui.getContentRegionAvail().x;
        if (avail > width) ImGui.setCursorPosX(ImGui.getCursorPosX() + (avail - width) * 0.5f);
    }
}
