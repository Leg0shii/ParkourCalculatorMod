package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.anglesolver.solver.PhysicsVariant;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

public final class BpkModBridge {

    public static final String MOD_ID = "bpk";
    public static final String SOURCE = "BPKMod";

    private static final String CONFIG_HANDLER = "cn.xiaozi0721.bpk.config.ConfigHandler";
    private static final String CONFIG_FIELD = "generalConfig";

    public static final class Rules {
        public static final Rules VANILLA = new Rules(true, false, false, false, false, Double.NaN);

        public final boolean strafeBoost;
        public final boolean collisionStopsSprint;
        public final boolean sprintBackward;
        public final boolean fixedCollisionOrder;
        public final boolean perAxisInertia;
        public final double inertiaThreshold;

        Rules(boolean strafeBoost, boolean collisionStopsSprint, boolean sprintBackward, boolean fixedCollisionOrder,
              boolean perAxisInertia, double inertiaThreshold) {
            this.strafeBoost = strafeBoost;
            this.collisionStopsSprint = collisionStopsSprint;
            this.sprintBackward = sprintBackward;
            this.fixedCollisionOrder = fixedCollisionOrder;
            this.perAxisInertia = perAxisInertia;
            this.inertiaThreshold = inertiaThreshold;
        }

        public PhysicsVariant physicsVariant() {
            return new PhysicsVariant(perAxisInertia ? inertiaThreshold : null, perAxisInertia ? Boolean.TRUE : null,
                    strafeBoost, SOURCE);
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("45 strafe ").append(strafeBoost ? "on" : "off");
            sb.append(", walls ").append(collisionStopsSprint ? "keep sprint" : "stop sprint");
            sb.append(", inertia ");
            if (!perAxisInertia) sb.append("vanilla");
            else if (inertiaThreshold == 0.0) sb.append("off");
            else sb.append(inertiaThreshold).append(" per axis");
            if (sprintBackward) sb.append(", sprint backward");
            if (fixedCollisionOrder) sb.append(", Y X Z collision order");
            return sb.toString();
        }
    }

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private static boolean resolved;
    private static boolean broken;
    private static Field configField;
    private static Field strafeField;
    private static Field collisionField;
    private static Field backwardField;
    private static Field orderField;
    private static Field oldInertiaField;
    private static Field thresholdField;

    private static boolean playerFieldsResolved;
    private static Field playerPrevDelta;
    private static Field playerDeltaX;
    private static Field playerDeltaZ;

    private BpkModBridge() {
    }

    public static boolean present() {
        return PRESENT;
    }

    public static boolean broken() {
        return PRESENT && broken;
    }

    public static Rules rules() {
        if (!PRESENT || !resolve()) return Rules.VANILLA;
        try {
            Object cfg = configField.get(null);
            if (cfg == null) return Rules.VANILLA;
            boolean oldInertia = oldInertiaField.getBoolean(cfg);
            return new Rules(
                    strafeField.getBoolean(cfg),
                    collisionField.getBoolean(cfg),
                    backwardField.getBoolean(cfg),
                    orderField.getBoolean(cfg),
                    oldInertia,
                    oldInertia ? thresholdField.getDouble(cfg) : Double.NaN);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return Rules.VANILLA;
        }
    }

    public static PhysicsVariant physicsVariant() {
        return PRESENT ? rules().physicsVariant() : PhysicsVariant.VANILLA;
    }

    public static void applyMoveState(LocalPlayer player, Vec3 moveInput, float movedX, float movedZ) {
        if (!PRESENT || !resolvePlayerFields()) return;
        try {
            playerPrevDelta.set(player, moveInput == null ? Vec3.ZERO : moveInput);
            playerDeltaX.setFloat(player, movedX);
            playerDeltaZ.setFloat(player, movedZ);
        } catch (ReflectiveOperationException | RuntimeException e) {
            playerPrevDelta = null;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) return !broken;
        resolved = true;
        try {
            Class<?> handler = Class.forName(CONFIG_HANDLER, true, BpkModBridge.class.getClassLoader());
            configField = accessible(handler, CONFIG_FIELD);
            Class<?> cfg = configField.getType();
            strafeField = accessible(cfg, "strafeAccelerateAllowed");
            collisionField = accessible(cfg, "beCollisionStopsSprint");
            backwardField = accessible(cfg, "sprintBackward");
            orderField = accessible(cfg, "oldCollisionOrder");
            oldInertiaField = accessible(cfg, "oldInertiaThreshold");
            thresholdField = accessible(cfg, "inertiaThreshold");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return false;
        }
    }

    private static synchronized boolean resolvePlayerFields() {
        if (playerFieldsResolved) return playerPrevDelta != null;
        playerFieldsResolved = true;
        try {
            playerPrevDelta = accessible(LocalPlayer.class, "prevDelta");
            playerDeltaX = accessible(LocalPlayer.class, "deltaX");
            playerDeltaZ = accessible(LocalPlayer.class, "deltaZ");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            playerPrevDelta = null;
            return false;
        }
    }

    private static Field accessible(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
