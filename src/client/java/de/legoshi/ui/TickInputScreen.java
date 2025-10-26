package de.legoshi.ui;

import de.legoshi.BoxController;
import de.legoshi.MovementSimulator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

public final class TickInputScreen extends Screen {

    private TickInputList list;
    private final BoxController boxController;
    private final MovementSimulator movementSimulator;

    private TextFieldWidget counter;
    private final int leftPadding = 8;
    private final int topPadding = 12;

    public TickInputScreen(BoxController boxController, MovementSimulator movementSimulator) {
        super(Text.of("Tick input"));
        this.boxController = boxController;
        this.movementSimulator = movementSimulator;
    }

    @Override
    protected void init() {
        if (list == null) {
            list = new TickInputList(client, width, height, boxController, movementSimulator);
            list.addRows(1);
        }

        addDrawableChild(list);
        drawHeader();
        createFooter();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext c, int x, int y, float d) {

    }

    private void drawHeader() {
        int x = leftPadding;
        int y = topPadding;

        String[] labels = {"W","A","S","D","J","P","N","F"};
        for (int i = 0; i < labels.length; i++) {
            addDrawableChild(new TextWidget(x + 5, y, 10, 10, Text.of(labels[i]), textRenderer));
            x += (i < 7 ? 24 : 44);
        }
    }

    private void createFooter() {
        int y = height - 24;
        addDrawableChild(
                ButtonWidget.builder(Text.of("+"), b -> list.addRows(delta()))
                        .dimensions(leftPadding, y, 20, 20)
                        .build()
        );

        counter = new NumberField(textRenderer, leftPadding + 22, y, 38, 20);
        counter.setText("1");
        addDrawableChild(counter);

        addDrawableChild(
                ButtonWidget.builder(Text.of("-"), b -> list.removeRows(delta()))
                        .dimensions(leftPadding + 22 + 40, y, 20, 20)
                        .build()
        );
    }

    private int delta() {
        try {
            return Math.max(1, Integer.parseInt(counter.getText()));
        } catch (NumberFormatException ignore) {
            return 1;
        }
    }
}
