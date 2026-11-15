package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.function.Consumer;

public final class BoxSelectController {

    public interface Picker {
        WorldPick pick(Vec3dCore rayOrigin, Vec3dCore rayDirection);
    }

    private final Picker picker;
    private final Consumer<WorldPick> onTapCommit;

    private boolean wasMousePressed = false;
    private WorldPick pressPick;
    private double pressScreenX = 0.0;
    private double pressScreenY = 0.0;
    private boolean abandoned = false;

    private final boolean commitOnPress;

    public BoxSelectController(Picker picker, Consumer<WorldPick> onTapCommit) {
        this(picker, onTapCommit, false);
    }

    public BoxSelectController(Picker picker, Consumer<WorldPick> onTapCommit, boolean commitOnPress) {
        this.picker = picker;
        this.onTapCommit = onTapCommit;
        this.commitOnPress = commitOnPress;
    }

    public void tick(Vec3dCore rayOrigin, Vec3dCore rayDirection, boolean mousePressed, double cursorScreenX, double cursorScreenY, boolean uiFocused) {
        if (uiFocused) {
            resetState();
            wasMousePressed = false;
            return;
        }

        if (mousePressed && !wasMousePressed) {
            pressPick = picker.pick(rayOrigin, rayDirection);
            pressScreenX = cursorScreenX;
            pressScreenY = cursorScreenY;
            abandoned = false;
            if (commitOnPress) {
                if (pressPick != null) onTapCommit.accept(pressPick);
                resetState();
                wasMousePressed = true;
                return;
            }
        }

        if (mousePressed && pressPick != null && !abandoned
                && TapThreshold.exceeded(pressScreenX, pressScreenY, cursorScreenX, cursorScreenY)) {
            abandoned = true;
        }

        if (!mousePressed && wasMousePressed) {
            if (pressPick != null && !abandoned) {
                onTapCommit.accept(pressPick);
            }
            resetState();
        }

        wasMousePressed = mousePressed;
    }

    private void resetState() {
        pressPick = null;
        abandoned = false;
    }
}
