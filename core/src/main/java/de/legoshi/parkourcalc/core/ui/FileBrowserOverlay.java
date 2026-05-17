package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.imgui.RenderInterface;
import de.legoshi.parkourcalc.core.save.LoadResult;
import de.legoshi.parkourcalc.core.save.SaveInfo;
import de.legoshi.parkourcalc.core.save.SaveResult;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class FileBrowserOverlay implements RenderInterface {

    private static final String WINDOW_TITLE = "Files";

    private static final String MENU_FILE = "File";
    private static final String MENU_NEW = "New";
    private static final String MENU_SAVE = "Save";
    private static final String MENU_SAVE_AS = "Save As...";
    private static final String MENU_LOAD = "Load";
    private static final String MENU_RECYCLE = "Move to Recycle Bin";

    private static final String LABEL_SEARCH = "Search";
    private static final String LABEL_REFRESH = "Refresh";
    private static final String LABEL_FILE_NAME = "File name";
    private static final String LABEL_EDITING_PREFIX = "Editing: ";
    private static final String LABEL_EDITING_UNSAVED = "(unsaved)";
    private static final String LABEL_DIRTY_MARK = " *";

    private static final String BTN_SAVE = "Save";
    private static final String BTN_CANCEL = "Cancel";
    private static final String BTN_DISCARD = "Discard";
    private static final String BTN_OVERWRITE = "Overwrite";
    private static final String BTN_RECYCLE = "Move to Recycle Bin";

    private static final String COL_FILENAME = "Filename";
    private static final String COL_DATE = "Date Modified";
    private static final String COL_MC = "MC";
    private static final String COL_WORLD = "World";

    private static final String POPUP_CREATE = "Create New Input File";
    private static final String POPUP_CONFIRM_NEW = "Discard unsaved changes?";
    private static final String POPUP_CONFIRM_OVERWRITE = "Overwrite existing file?";
    private static final String POPUP_CONFIRM_DELETE = "Move to recycle bin?";
    private static final String POPUP_CONFIRM_LOAD = "Discard unsaved changes before loading?";

    private static final String ID_FILTER_INPUT = "##file_filter";
    private static final String ID_TABLE = "##file_table";
    private static final String ID_NAME_INPUT = "##new_name";
    private static final String ID_ROW_PREFIX = "##row_";

    private static final String TOOLTIP_NEW = "Clear inputs back to default and snap start to your position. Prompts for confirmation if you have unsaved changes.";
    private static final String TOOLTIP_SAVE_NAMED = "Overwrite '%s' with the current inputs.";
    private static final String TOOLTIP_SAVE_UNNAMED = "Nothing loaded yet, opens Save As...";
    private static final String TOOLTIP_SAVE_AS = "Save the current inputs to a new file with a name you choose.";
    private static final String TOOLTIP_LOAD = "Load the file selected in the table below. Replaces current inputs and start.";
    private static final String TOOLTIP_RECYCLE = "Move the selected file to <save dir>/.trash/. This is NOT the OS recycle bin; restore by hand if needed.";

    private static final String CONFIRM_NEW_BODY_NAMED = "You have unsaved changes to '%s'.";
    private static final String CONFIRM_NEW_BODY_UNNAMED = "You have unsaved changes that have not been saved to a file.";
    private static final String CONFIRM_NEW_BODY_DISCARD = "Starting a new run will discard them.";
    private static final String CONFIRM_OVERWRITE_BODY = "A save named '%s' already exists.";
    private static final String CONFIRM_OVERWRITE_PROMPT = "Overwrite it?";
    private static final String CONFIRM_DELETE_BODY = "Move '%s' to <save dir>/.trash/?";
    private static final String CONFIRM_DELETE_NOTE = "This is NOT the OS recycle bin; restore by hand if needed.";
    private static final String CONFIRM_LOAD_BODY_LOAD = "Loading '%s' will replace the current inputs and start.";
    private static final String CONFIRM_LOAD_BODY_DISCARD = "Your unsaved changes will be lost.";

    private static final String STATUS_NEW_SESSION = "Cleared inputs; start set to your position";
    private static final String STATUS_SAVED = "Saved '%s'";
    private static final String STATUS_SAVED_AS = "Saved as '%s'";
    private static final String STATUS_LOADED = "Loaded '%s'";
    private static final String STATUS_RECYCLED = "Moved '%s' to recycle bin";
    private static final String STATUS_RECYCLE_FAILED = "Failed to recycle '%s'";

    private static final String UNKNOWN = "?";
    private static final String SORT_ARROW_DESC = " v";
    private static final String SORT_ARROW_ASC = " ^";

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm";

    public interface Backend {
        SaveResult save(String name);
        LoadResult load(String name);
        boolean delete(String name);
        void newSession();
        List<SaveInfo> list();
        String currentName();
        boolean isDirty();
        boolean exists(String name);
    }

    private final Backend backend;
    private final ImString filterInput = new ImString(64);
    private final ImString newNameInput = new ImString(64);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat(DATE_PATTERN, Locale.US);

    private List<SaveInfo> cached = new ArrayList<SaveInfo>();
    private boolean needsRefresh = true;
    private int sortColumn = 1;
    private boolean sortDescending = true;
    private String selected;
    private String pendingLoad;
    private String statusMessage;
    private boolean statusIsError;
    private boolean shouldOpenCreatePopup;
    private boolean shouldOpenConfirmNew;
    private boolean shouldOpenConfirmOverwrite;
    private boolean shouldOpenConfirmDelete;
    private boolean shouldOpenConfirmLoad;
    private String pendingOverwriteName;
    private String pendingDeleteName;
    private String pendingConfirmLoadName;

    public FileBrowserOverlay(Backend backend) {
        this.backend = backend;
    }

    @Override
    public void render(ImGuiIO io) {
        ImGui.setNextWindowSize(560, 380, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(420, 200, Float.MAX_VALUE, Float.MAX_VALUE);
        if (!ImGui.begin(WINDOW_TITLE, ImGuiWindowFlags.MenuBar)) {
            ImGui.end();
            return;
        }

        if (needsRefresh) {
            cached = backend.list();
            needsRefresh = false;
        }

        renderMenuBar();
        renderToolbar();
        renderTable();
        renderEditingLabel();
        renderStatus();
        renderCreateModal();
        renderConfirmNewModal();
        renderConfirmOverwriteModal();
        renderConfirmDeleteModal();
        renderConfirmLoadModal();

        if (pendingLoad != null) {
            doLoad(pendingLoad);
            pendingLoad = null;
        }

        ImGui.end();
    }

    private void renderToolbar() {
        ImGui.text(LABEL_SEARCH);
        ImGui.sameLine();
        ImGui.setNextItemWidth(220);
        ImGui.inputText(ID_FILTER_INPUT, filterInput);
        ImGui.sameLine();
        if (ImGui.button(LABEL_REFRESH)) needsRefresh = true;
    }

    private void renderTable() {
        int flags = ImGuiTableFlags.RowBg
                | ImGuiTableFlags.Resizable
                | ImGuiTableFlags.Borders
                | ImGuiTableFlags.ScrollY
                | ImGuiTableFlags.SizingFixedFit;
        float lineH = ImGui.getTextLineHeightWithSpacing();
        float reserveBottom = lineH * 2 + 16;
        float tableHeight = Math.max(120, ImGui.getContentRegionAvail().y - reserveBottom);
        if (!ImGui.beginTable(ID_TABLE, 4, flags, 0, tableHeight)) return;

        ImGui.tableSetupScrollFreeze(0, 1);
        ImGui.tableSetupColumn(COL_FILENAME);
        ImGui.tableSetupColumn(COL_DATE);
        ImGui.tableSetupColumn(COL_MC);
        ImGui.tableSetupColumn(COL_WORLD, ImGuiTableColumnFlags.WidthStretch);

        renderHeaders();
        for (SaveInfo info : sortedFiltered()) {
            renderRow(info);
        }
        ImGui.endTable();
    }

    private void renderHeaders() {
        ImGui.tableNextRow();
        renderHeaderCell(0, COL_FILENAME);
        renderHeaderCell(1, COL_DATE);
        renderHeaderCell(2, COL_MC);
        renderHeaderCell(3, COL_WORLD);
    }

    private void renderHeaderCell(int col, String label) {
        ImGui.tableNextColumn();
        String arrow = sortColumn == col ? (sortDescending ? SORT_ARROW_DESC : SORT_ARROW_ASC) : "";
        ImGui.tableHeader(label + arrow);
        if (ImGui.isItemClicked()) {
            if (sortColumn == col) {
                sortDescending = !sortDescending;
            } else {
                sortColumn = col;
                sortDescending = (col == 1);
            }
        }
    }

    private void renderRow(SaveInfo info) {
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);

        boolean isSelected = info.name.equals(selected);
        int selFlags = ImGuiSelectableFlags.SpanAllColumns | ImGuiSelectableFlags.AllowDoubleClick;
        if (ImGui.selectable(info.name + ID_ROW_PREFIX + info.name, isSelected, selFlags)) {
            selected = info.name;
            if (ImGui.isMouseDoubleClicked(0)) {
                requestLoad(info.name);
            }
        }

        ImGui.tableSetColumnIndex(1);
        ImGui.text(formatDate(info.lastModifiedMs));

        ImGui.tableSetColumnIndex(2);
        ImGui.text(info.mcVersion != null ? info.mcVersion : UNKNOWN);

        ImGui.tableSetColumnIndex(3);
        ImGui.text(info.worldLabel != null ? info.worldLabel : UNKNOWN);
    }

    private void renderMenuBar() {
        if (!ImGui.beginMenuBar()) return;

        if (ImGui.beginMenu(MENU_FILE)) {
            boolean hasSelection = selected != null && containsName(cached, selected);
            String current = backend.currentName();

            if (ImGui.menuItem(MENU_NEW)) {
                if (backend.isDirty()) {
                    shouldOpenConfirmNew = true;
                } else {
                    applyNewSession();
                }
            }
            tooltip(TOOLTIP_NEW);

            if (ImGui.menuItem(MENU_SAVE)) {
                if (current != null) {
                    doSave(current);
                } else {
                    newNameInput.set("");
                    shouldOpenCreatePopup = true;
                }
            }
            tooltip(current != null ? String.format(TOOLTIP_SAVE_NAMED, current) : TOOLTIP_SAVE_UNNAMED);

            if (ImGui.menuItem(MENU_SAVE_AS)) {
                newNameInput.set("");
                shouldOpenCreatePopup = true;
            }
            tooltip(TOOLTIP_SAVE_AS);

            ImGui.separator();

            if (!hasSelection) ImGui.beginDisabled();
            if (ImGui.menuItem(MENU_LOAD)) {
                requestLoad(selected);
            }
            if (!hasSelection) ImGui.endDisabled();
            tooltip(TOOLTIP_LOAD);

            if (!hasSelection) ImGui.beginDisabled();
            if (ImGui.menuItem(MENU_RECYCLE)) {
                pendingDeleteName = selected;
                shouldOpenConfirmDelete = true;
            }
            if (!hasSelection) ImGui.endDisabled();
            tooltip(TOOLTIP_RECYCLE);

            ImGui.endMenu();
        }

        ImGui.endMenuBar();
    }

    private void renderEditingLabel() {
        String current = backend.currentName();
        ImGui.textDisabled(LABEL_EDITING_PREFIX + (current != null ? current : LABEL_EDITING_UNSAVED)
                + (backend.isDirty() ? LABEL_DIRTY_MARK : ""));
    }

    private static void tooltip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    private void applyNewSession() {
        backend.newSession();
        statusMessage = STATUS_NEW_SESSION;
        statusIsError = false;
    }

    private void renderConfirmNewModal() {
        if (shouldOpenConfirmNew) {
            ImGui.openPopup(POPUP_CONFIRM_NEW);
            shouldOpenConfirmNew = false;
        }
        if (!ImGui.beginPopupModal(POPUP_CONFIRM_NEW, ImGuiWindowFlags.AlwaysAutoResize)) return;

        String current = backend.currentName();
        ImGui.text(current != null ? String.format(CONFIRM_NEW_BODY_NAMED, current) : CONFIRM_NEW_BODY_UNNAMED);
        ImGui.text(CONFIRM_NEW_BODY_DISCARD);

        pushButtonColor(0.65f, 0.20f, 0.20f);
        if (ImGui.button(BTN_DISCARD)) {
            applyNewSession();
            ImGui.closeCurrentPopup();
        }
        popButtonColor();
        ImGui.sameLine();
        if (ImGui.button(BTN_CANCEL)) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderConfirmOverwriteModal() {
        if (shouldOpenConfirmOverwrite) {
            ImGui.openPopup(POPUP_CONFIRM_OVERWRITE);
            shouldOpenConfirmOverwrite = false;
        }
        if (!ImGui.beginPopupModal(POPUP_CONFIRM_OVERWRITE, ImGuiWindowFlags.AlwaysAutoResize)) return;

        ImGui.text(String.format(CONFIRM_OVERWRITE_BODY, pendingOverwriteName));
        ImGui.text(CONFIRM_OVERWRITE_PROMPT);

        pushButtonColor(0.65f, 0.20f, 0.20f);
        if (ImGui.button(BTN_OVERWRITE)) {
            doSave(pendingOverwriteName);
            pendingOverwriteName = null;
            ImGui.closeCurrentPopup();
        }
        popButtonColor();
        ImGui.sameLine();
        if (ImGui.button(BTN_CANCEL)) {
            pendingOverwriteName = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void requestLoad(String name) {
        if (backend.isDirty()) {
            pendingConfirmLoadName = name;
            shouldOpenConfirmLoad = true;
        } else {
            pendingLoad = name;
        }
    }

    private void renderConfirmLoadModal() {
        if (shouldOpenConfirmLoad) {
            ImGui.openPopup(POPUP_CONFIRM_LOAD);
            shouldOpenConfirmLoad = false;
        }
        if (!ImGui.beginPopupModal(POPUP_CONFIRM_LOAD, ImGuiWindowFlags.AlwaysAutoResize)) return;

        String current = backend.currentName();
        ImGui.text(current != null ? String.format(CONFIRM_NEW_BODY_NAMED, current) : CONFIRM_NEW_BODY_UNNAMED);
        ImGui.text(String.format(CONFIRM_LOAD_BODY_LOAD, pendingConfirmLoadName));
        ImGui.text(CONFIRM_LOAD_BODY_DISCARD);

        pushButtonColor(0.65f, 0.20f, 0.20f);
        if (ImGui.button(BTN_DISCARD)) {
            pendingLoad = pendingConfirmLoadName;
            pendingConfirmLoadName = null;
            ImGui.closeCurrentPopup();
        }
        popButtonColor();
        ImGui.sameLine();
        if (ImGui.button(BTN_CANCEL)) {
            pendingConfirmLoadName = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void renderConfirmDeleteModal() {
        if (shouldOpenConfirmDelete) {
            ImGui.openPopup(POPUP_CONFIRM_DELETE);
            shouldOpenConfirmDelete = false;
        }
        if (!ImGui.beginPopupModal(POPUP_CONFIRM_DELETE, ImGuiWindowFlags.AlwaysAutoResize)) return;

        ImGui.text(String.format(CONFIRM_DELETE_BODY, pendingDeleteName));
        ImGui.text(CONFIRM_DELETE_NOTE);

        pushButtonColor(0.65f, 0.20f, 0.20f);
        if (ImGui.button(BTN_RECYCLE)) {
            doDelete(pendingDeleteName);
            pendingDeleteName = null;
            ImGui.closeCurrentPopup();
        }
        popButtonColor();
        ImGui.sameLine();
        if (ImGui.button(BTN_CANCEL)) {
            pendingDeleteName = null;
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void doSave(String name) {
        SaveResult r = backend.save(name);
        if (r.ok) {
            statusMessage = String.format(STATUS_SAVED, r.name);
            statusIsError = false;
            selected = r.name;
            needsRefresh = true;
        } else {
            statusMessage = r.error;
            statusIsError = true;
        }
    }

    private void renderStatus() {
        if (statusMessage == null) return;
        ImGui.separator();
        if (statusIsError) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.45f, 0.45f, 1.0f);
        }
        ImGui.text(statusMessage);
        if (statusIsError) {
            ImGui.popStyleColor();
        }
    }

    private void renderCreateModal() {
        if (shouldOpenCreatePopup) {
            ImGui.openPopup(POPUP_CREATE);
            shouldOpenCreatePopup = false;
        }

        if (!ImGui.beginPopupModal(POPUP_CREATE, ImGuiWindowFlags.AlwaysAutoResize)) return;

        ImGui.text(LABEL_FILE_NAME);
        ImGui.setNextItemWidth(240);
        ImGui.inputText(ID_NAME_INPUT, newNameInput);

        if (ImGui.button(BTN_SAVE)) {
            String typed = newNameInput.get();
            if (backend.exists(typed)) {
                pendingOverwriteName = typed;
                shouldOpenConfirmOverwrite = true;
                ImGui.closeCurrentPopup();
            } else {
                SaveResult r = backend.save(typed);
                if (r.ok) {
                    statusMessage = String.format(STATUS_SAVED_AS, r.name);
                    statusIsError = false;
                    selected = r.name;
                    needsRefresh = true;
                    ImGui.closeCurrentPopup();
                } else {
                    statusMessage = r.error;
                    statusIsError = true;
                }
            }
        }
        ImGui.sameLine();
        if (ImGui.button(BTN_CANCEL)) {
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }

    private void doLoad(String name) {
        LoadResult r = backend.load(name);
        if (r.ok) {
            statusMessage = String.format(STATUS_LOADED, name);
            statusIsError = false;
            selected = name;
        } else {
            statusMessage = r.error;
            statusIsError = true;
        }
    }

    private void doDelete(String name) {
        if (backend.delete(name)) {
            statusMessage = String.format(STATUS_RECYCLED, name);
            statusIsError = false;
            if (name.equals(selected)) selected = null;
            needsRefresh = true;
        } else {
            statusMessage = String.format(STATUS_RECYCLE_FAILED, name);
            statusIsError = true;
        }
    }

    private List<SaveInfo> sortedFiltered() {
        String needle = filterInput.get().toLowerCase(Locale.US).trim();
        List<SaveInfo> out = new ArrayList<SaveInfo>(cached.size());
        for (SaveInfo info : cached) {
            if (needle.isEmpty() || info.name.toLowerCase(Locale.US).contains(needle)) {
                out.add(info);
            }
        }
        Comparator<SaveInfo> cmp;
        switch (sortColumn) {
            case 0:
                cmp = new Comparator<SaveInfo>() {
                    @Override public int compare(SaveInfo a, SaveInfo b) {
                        return a.name.compareToIgnoreCase(b.name);
                    }
                };
                break;
            case 2:
                cmp = new Comparator<SaveInfo>() {
                    @Override public int compare(SaveInfo a, SaveInfo b) {
                        return nullSafe(a.mcVersion).compareToIgnoreCase(nullSafe(b.mcVersion));
                    }
                };
                break;
            case 3:
                cmp = new Comparator<SaveInfo>() {
                    @Override public int compare(SaveInfo a, SaveInfo b) {
                        return nullSafe(a.worldLabel).compareToIgnoreCase(nullSafe(b.worldLabel));
                    }
                };
                break;
            default:
                cmp = new Comparator<SaveInfo>() {
                    @Override public int compare(SaveInfo a, SaveInfo b) {
                        return Long.compare(a.lastModifiedMs, b.lastModifiedMs);
                    }
                };
        }
        if (sortDescending) cmp = Collections.reverseOrder(cmp);
        Collections.sort(out, cmp);
        return out;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private String formatDate(long ms) {
        if (ms <= 0) return "";
        return dateFmt.format(new Date(ms));
    }

    private static boolean containsName(List<SaveInfo> list, String name) {
        for (SaveInfo i : list) if (i.name.equals(name)) return true;
        return false;
    }

    private static void pushButtonColor(float r, float g, float b) {
        ImGui.pushStyleColor(ImGuiCol.Button, r, g, b, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r * 1.2f, g * 1.2f, b * 1.2f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, r * 0.8f, g * 0.8f, b * 0.8f, 1.0f);
    }

    private static void popButtonColor() {
        ImGui.popStyleColor(3);
    }
}
