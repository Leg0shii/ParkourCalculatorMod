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

    private static final String CREATE_POPUP_ID = "Create New Input File";
    private static final String CONFIRM_NEW_POPUP_ID = "Discard unsaved changes?";

    public interface Backend {
        SaveResult save(String name);
        LoadResult load(String name);
        boolean delete(String name);
        void newSession();
        List<SaveInfo> list();
        String currentName();
        boolean isDirty();
    }

    private final Backend backend;
    private final ImString filterInput = new ImString(64);
    private final ImString newNameInput = new ImString(64);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

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

    public FileBrowserOverlay(Backend backend) {
        this.backend = backend;
    }

    @Override
    public void render(ImGuiIO io) {
        ImGui.setNextWindowSize(560, 380, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(420, 200, Float.MAX_VALUE, Float.MAX_VALUE);
        if (!ImGui.begin("Files", ImGuiWindowFlags.MenuBar)) {
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

        if (pendingLoad != null) {
            doLoad(pendingLoad);
            pendingLoad = null;
        }

        ImGui.end();
    }

    private void renderToolbar() {
        ImGui.text("Search");
        ImGui.sameLine();
        ImGui.setNextItemWidth(220);
        ImGui.inputText("##file_filter", filterInput);
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
        if (!ImGui.beginTable("##file_table", 4, flags, 0, tableHeight)) return;

        ImGui.tableSetupScrollFreeze(0, 1);
        ImGui.tableSetupColumn("Filename");
        ImGui.tableSetupColumn("Date Modified");
        ImGui.tableSetupColumn("MC");
        ImGui.tableSetupColumn("World", ImGuiTableColumnFlags.WidthStretch);

        renderHeaders();
        for (SaveInfo info : sortedFiltered()) {
            renderRow(info);
        }
        ImGui.endTable();
    }

    private void renderHeaders() {
        ImGui.tableNextRow();
        renderHeaderCell(0, "Filename");
        renderHeaderCell(1, "Date Modified");
        renderHeaderCell(2, "MC");
        renderHeaderCell(3, "World");
    }

    private void renderHeaderCell(int col, String label) {
        ImGui.tableNextColumn();
        String arrow = sortColumn == col ? (sortDescending ? " v" : " ^") : "";
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
        if (ImGui.selectable(info.name + "##row_" + info.name, isSelected, selFlags)) {
            selected = info.name;
            if (ImGui.isMouseDoubleClicked(0)) {
                pendingLoad = info.name;
            }
        }

        ImGui.tableSetColumnIndex(1);
        ImGui.text(formatDate(info.lastModifiedMs));

        ImGui.tableSetColumnIndex(2);
        ImGui.text(info.mcVersion != null ? info.mcVersion : "?");

        ImGui.tableSetColumnIndex(3);
        ImGui.text(info.worldLabel != null ? info.worldLabel : "?");
    }

    private void renderMenuBar() {
        if (!ImGui.beginMenuBar()) return;

        if (ImGui.beginMenu("File")) {
            boolean hasSelection = selected != null && containsName(cached, selected);
            String current = backend.currentName();

            if (ImGui.menuItem("New")) {
                if (backend.isDirty()) {
                    shouldOpenConfirmNew = true;
                } else {
                    applyNewSession();
                }
            }
            tooltip("Clear inputs back to default and snap start to your position. Prompts for confirmation if you have unsaved changes.");

            if (ImGui.menuItem("Save")) {
                if (current != null) {
                    doSave(current);
                } else {
                    newNameInput.set("");
                    shouldOpenCreatePopup = true;
                }
            }
            tooltip(current != null
                    ? "Overwrite '" + current + "' with the current inputs."
                    : "Nothing loaded yet, opens Save As...");

            if (ImGui.menuItem("Save As...")) {
                newNameInput.set("");
                shouldOpenCreatePopup = true;
            }
            tooltip("Save the current inputs to a new file with a name you choose.");

            ImGui.separator();

            if (!hasSelection) ImGui.beginDisabled();
            if (ImGui.menuItem("Load")) {
                pendingLoad = selected;
            }
            if (!hasSelection) ImGui.endDisabled();
            tooltip("Load the file selected in the table below. Replaces current inputs and start.");

            if (!hasSelection) ImGui.beginDisabled();
            if (ImGui.menuItem("Move to Recycle Bin")) {
                doDelete(selected);
            }
            if (!hasSelection) ImGui.endDisabled();
            tooltip("Move the selected file to <save dir>/.trash/. This is NOT the OS recycle bin; restore by hand if needed.");

            ImGui.endMenu();
        }

        ImGui.endMenuBar();
    }

    private void renderEditingLabel() {
        String current = backend.currentName();
        ImGui.textDisabled("Editing: " + (current != null ? current : "(unsaved)") + (backend.isDirty() ? " *" : ""));
    }

    private static void tooltip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    private void applyNewSession() {
        backend.newSession();
        statusMessage = "Cleared inputs; start set to your position";
        statusIsError = false;
    }

    private void renderConfirmNewModal() {
        if (shouldOpenConfirmNew) {
            ImGui.openPopup(CONFIRM_NEW_POPUP_ID);
            shouldOpenConfirmNew = false;
        }
        if (!ImGui.beginPopupModal(CONFIRM_NEW_POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) return;

        String current = backend.currentName();
        ImGui.text(current != null
                ? "You have unsaved changes to '" + current + "'."
                : "You have unsaved changes that have not been saved to a file.");
        ImGui.text("Starting a new run will discard them.");

        pushButtonColor(0.65f, 0.20f, 0.20f);
        if (ImGui.button("Discard")) {
            applyNewSession();
            ImGui.closeCurrentPopup();
        }
        popButtonColor();
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
        ImGui.endPopup();
    }

    private void doSave(String name) {
        SaveResult r = backend.save(name);
        if (r.ok) {
            statusMessage = "Saved '" + r.name + "'";
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
            ImGui.openPopup(CREATE_POPUP_ID);
            shouldOpenCreatePopup = false;
        }

        if (!ImGui.beginPopupModal(CREATE_POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) return;

        ImGui.text("File name");
        ImGui.setNextItemWidth(240);
        ImGui.inputText("##new_name", newNameInput);

        if (ImGui.button("Save")) {
            SaveResult r = backend.save(newNameInput.get());
            if (r.ok) {
                statusMessage = "Saved as '" + r.name + "'";
                statusIsError = false;
                selected = r.name;
                needsRefresh = true;
                ImGui.closeCurrentPopup();
            } else {
                statusMessage = r.error;
                statusIsError = true;
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }

    private void doLoad(String name) {
        LoadResult r = backend.load(name);
        if (r.ok) {
            statusMessage = "Loaded '" + name + "'";
            statusIsError = false;
            selected = name;
        } else {
            statusMessage = r.error;
            statusIsError = true;
        }
    }

    private void doDelete(String name) {
        if (backend.delete(name)) {
            statusMessage = "Moved '" + name + "' to recycle bin";
            statusIsError = false;
            if (name.equals(selected)) selected = null;
            needsRefresh = true;
        } else {
            statusMessage = "Failed to recycle '" + name + "'";
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
