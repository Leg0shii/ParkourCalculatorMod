package de.legoshi.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class NumberField extends TextFieldWidget {
    public NumberField(TextRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height, Text.empty());
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (Character.isDigit(ch) || ch == '-' || ch == '.') {
            return super.charTyped(ch, modifiers);
        }
        return false;
    }
}
