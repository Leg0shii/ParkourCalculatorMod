package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.List;

public final class SprintReplay {

    private static final int WINDOW = 7;

    private SprintReplay() {
    }

    public static boolean[] run(List<InputRow> rows, int startTick, JumpPhysicsInputs sc, boolean airTap) {
        int n = sc.numTicks;
        boolean[] out = new boolean[n];
        int timer = 0;
        boolean sprint = sc.incomingSprint == null || sc.incomingSprint;
        boolean prevForward = sprint;
        boolean prevSneak = false;
        for (int k = 0; k < n; k++) {
            InputRow row = rows.get(startTick + k);
            boolean sneak = row.isKeyActive(InputRow.Key.SNEAK);
            boolean back = row.isKeyActive(InputRow.Key.S);
            boolean forward = row.isKeyActive(InputRow.Key.W) && !back;
            boolean key = row.isKeyActive(InputRow.Key.SPRINT);
            if (timer > 0) {
                timer--;
            }
            if (airTap) {
                if (prevSneak || back) {
                    timer = 0;
                }
                if (!sprint && forward && !sneak) {
                    if (!prevForward) {
                        if (timer > 0) {
                            sprint = true;
                        } else {
                            timer = WINDOW;
                        }
                    }
                    if (key) {
                        sprint = true;
                    }
                }
            } else {
                forward = forward && !sneak;
                if (prevSneak) {
                    timer = 0;
                }
                if (sc.contactAt(k) && !prevSneak && !prevForward && forward && !sprint) {
                    if (timer <= 0 && !key) {
                        timer = WINDOW;
                    } else {
                        sprint = true;
                    }
                }
                if (!sprint && forward && key) {
                    sprint = true;
                }
            }
            if (sprint && !forward) {
                sprint = false;
            }
            out[k] = sprint;
            prevForward = forward;
            prevSneak = sneak;
        }
        return out;
    }
}
