package de.legoshi.ui;

import de.legoshi.Utils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TickInputRow extends AbstractParentElement implements Drawable {
    private static final int HEIGHT = 22;

    public final TextWidget tickWidget;
    public final CheckboxWidget[] checkboxes = new CheckboxWidget[7];
    public final NumberField numberField;

    public TickInputRow(TextRenderer font, Runnable onChange, int count) {
        int x = 8;
        tickWidget = new TextWidget(0, 0, x, 8, Text.of(count + "."), font);

        for (int i = 0; i < 7; i++) {
            checkboxes[i] = CheckboxWidget.builder(Text.empty(), font)
                    .pos(x, 0)
                    .maxWidth(10)
                    .callback((cb, checked) -> onChange.run())
                    .build();
            x += 24;
        }

        numberField = new NumberField(font, x, 0, 24, 16);
        numberField.setChangedListener(t -> onChange.run());
    }

    public void setPosition(int x, int y) {
        int cx = x + 8;
        tickWidget.setPosition(x, y);
        for (CheckboxWidget box : checkboxes) {
            box.setPosition(cx, y);
            cx += 24;
        }
        numberField.setPosition(x + 7 * 24, y);
    }

    public InputTick toData() {
        return new InputTick(
                checkboxes[0].isChecked(), checkboxes[1].isChecked(), checkboxes[2].isChecked(),
                checkboxes[3].isChecked(), checkboxes[4].isChecked(), checkboxes[5].isChecked(),
                checkboxes[6].isChecked(), Utils.parse(numberField.getText())
        );
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        for (CheckboxWidget box : checkboxes) {
            box.render(ctx, mouseX, mouseY, delta);
        }
        tickWidget.render(ctx, mouseX, mouseY, delta);
        numberField.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public List<Element> children() {
        List<Element> children = new ArrayList<>(Arrays.asList(checkboxes));
        children.add(numberField);
        children.add(tickWidget);
        return children;
    }
}
