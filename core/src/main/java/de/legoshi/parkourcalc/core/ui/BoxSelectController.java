package de.legoshi.parkourcalc.core.ui;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.function.IntConsumer;

public final class BoxSelectController {

    private static final int NOT_PRESSED = -2;

    private final BoxController boxController;
    private final IntConsumer onTapCommit;

    private boolean wasMousePressed = false;
    private int pressBoxIndex = NOT_PRESSED;
    private double pressScreenX = 0.0;
    private double pressScreenY = 0.0;
    private boolean abandoned = false;

    public BoxSelectController(BoxController boxController, IntConsumer onTapCommit) {
        this.boxController = boxController;
        this.onTapCommit = onTapCommit;
    }

    public void tick(Vec3dCore rayOrigin, Vec3dCore rayDirection, boolean mousePressed,
                     double cursorScreenX, double cursorScreenY, boolean uiFocused) {
        if (uiFocused) {
            resetState();
            wasMousePressed = false;
            return;
        }

        if (mousePressed && !wasMousePressed) {
            pressBoxIndex = boxController.pickBoxIndex(rayOrigin, rayDirection);
            pressScreenX = cursorScreenX;
            pressScreenY = cursorScreenY;
            abandoned = false;
        }

        if (mousePressed && pressBoxIndex != NOT_PRESSED && !abandoned
                && TapThreshold.exceeded(pressScreenX, pressScreenY, cursorScreenX, cursorScreenY)) {
            abandoned = true;
        }

        if (!mousePressed && wasMousePressed) {
            if (pressBoxIndex != NOT_PRESSED && !abandoned) {
                onTapCommit.accept(pressBoxIndex);
            }
            resetState();
        }

        wasMousePressed = mousePressed;
    }

    private void resetState() {
        pressBoxIndex = NOT_PRESSED;
        abandoned = false;
    }
}
