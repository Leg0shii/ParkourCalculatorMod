package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.EnumSet;
import java.util.Set;

public final class StartResumeState {

    public boolean onGround;
    public boolean wallContact;
    public boolean softWallContact;
    public boolean sprinting;
    public int sprintWindow;
    public int sprintTicksLeft;
    public int jumpCooldown;
    public float airSprintFactor = Float.NaN;
    public Vec3dCore stuckMultiplier;
    public final Set<InputRow.Key> heldLastTick = EnumSet.noneOf(InputRow.Key.class);

    public StartResumeState copy() {
        StartResumeState c = new StartResumeState();
        c.onGround = onGround;
        c.wallContact = wallContact;
        c.softWallContact = softWallContact;
        c.sprinting = sprinting;
        c.sprintWindow = sprintWindow;
        c.sprintTicksLeft = sprintTicksLeft;
        c.jumpCooldown = jumpCooldown;
        c.airSprintFactor = airSprintFactor;
        c.stuckMultiplier = stuckMultiplier;
        c.heldLastTick.addAll(heldLastTick);
        return c;
    }

    public boolean sameAs(StartResumeState o) {
        if (o == null) return false;
        return onGround == o.onGround
                && wallContact == o.wallContact
                && softWallContact == o.softWallContact
                && sprinting == o.sprinting
                && sprintWindow == o.sprintWindow
                && sprintTicksLeft == o.sprintTicksLeft
                && jumpCooldown == o.jumpCooldown
                && Float.floatToIntBits(airSprintFactor) == Float.floatToIntBits(o.airSprintFactor)
                && (stuckMultiplier == null ? o.stuckMultiplier == null
                        : o.stuckMultiplier != null && stuckMultiplier.equals(o.stuckMultiplier))
                && heldLastTick.equals(o.heldLastTick);
    }

    public static boolean sameAs(StartResumeState a, StartResumeState b) {
        return a == null ? b == null : a.sameAs(b);
    }
}
