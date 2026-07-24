package de.legoshi.parkourcalc.forge12;

import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;

final class FrozenMouseHelper extends MouseHelper {

    @Override
    public void mouseXYChange() {
        Mouse.getDX();
        Mouse.getDY();
        this.deltaX = 0;
        this.deltaY = 0;
    }
}
