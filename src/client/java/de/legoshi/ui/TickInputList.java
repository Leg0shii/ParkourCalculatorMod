package de.legoshi.ui;

import de.legoshi.BoxController;
import de.legoshi.MovementSimulator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class TickInputList extends AlwaysSelectedEntryListWidget<TickInputList.Entry> {

    private final BoxController boxController;
    private final MovementSimulator simulator;

    public TickInputList(MinecraftClient mc, int screenWidth, int screenHeight, BoxController boxController, MovementSimulator simulator) {
        super(mc, screenWidth / 3, screenHeight / 2, 22, 28, 0);
        this.boxController = boxController;
        this.boxController.setOnChange(this::onInputChanged);

        this.simulator = simulator;
    }

    public void addRows(int n) {
        int totalCount = collect().size();
        for (int i = 0; i < n; i++) {
            addEntry(new Entry(totalCount + i + 1));
        }
    }

    public void removeRows(int n) {
        while (n-- > 0 && !children().isEmpty()) {
            remove(children().size() - 1);
        }
    }

    public List<InputTick> collect() {
        return children().stream().map(e -> e.row().toData()).toList();
    }

    private void onInputChanged() {
        boxController.clearAll();
        boxController.add(simulator.getSimulatorEntity().startPosition);
        boxController.addAll(simulator.simulatePlayerMovement(collect()));
    }

    public final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        private final TickInputRow row;

        public Entry(int count) {
            this.row = new TickInputRow(client.textRenderer, TickInputList.this::onInputChanged, count);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickProgress) {
            row.setPosition(x + 8, y + 1);
            row.render(context, mouseX, mouseY, tickProgress);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return row.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return row.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            return row.mouseDragged(mx, my, button, dx, dy);
        }

        @Override
        public boolean keyPressed(int key, int scancode, int modifiers) {
            return row.keyPressed(key, scancode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return row.charTyped(chr, modifiers);
        }

        @Override
        public Text getNarration() {
            return Text.of("TODO");
        }

        public TickInputRow row() {
            return row;
        }
    }

}
