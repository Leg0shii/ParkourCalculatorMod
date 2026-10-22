package de.legoshi.parkourcalc.forge.sim;

import de.legoshi.parkourcalc.core.anglesolver.solver.PhysicsVariant;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class BpkModBridge {

    public static final String MOD_ID = "bpk";
    public static final String SOURCE = "BPKMod";

    private static final String CONFIG_CLASS = "cn.xiaozi0721.bpk.config.ConfigHandler$GeneralConfig";
    private static final float IMPULSE_THRESHOLD = 1.0E-5F;

    public static final class Rules {
        public static final Rules VANILLA = new Rules(false, true, false, false, false, false, true, false, false, false, Double.NaN);

        public final boolean present;
        public final boolean strafeBoost;
        public final boolean newTouch;
        public final boolean byPitch;
        public final boolean sprintBackward;
        public final boolean sprintDelayOnGround;
        public final boolean sprintDelayInAir;
        public final boolean ignoreWallStop;
        public final boolean nonSprintingStrafe;
        public final boolean bedrockSneak;
        public final double inertiaThreshold;

        Rules(boolean present, boolean strafeBoost, boolean newTouch, boolean byPitch, boolean sprintBackward,
              boolean sprintDelayOnGround, boolean sprintDelayInAir, boolean ignoreWallStop, boolean nonSprintingStrafe,
              boolean bedrockSneak, double inertiaThreshold) {
            this.present = present;
            this.strafeBoost = strafeBoost;
            this.newTouch = newTouch;
            this.byPitch = byPitch;
            this.sprintBackward = sprintBackward;
            this.sprintDelayOnGround = sprintDelayOnGround;
            this.sprintDelayInAir = sprintDelayInAir;
            this.ignoreWallStop = ignoreWallStop;
            this.nonSprintingStrafe = nonSprintingStrafe;
            this.bedrockSneak = bedrockSneak;
            this.inertiaThreshold = inertiaThreshold;
        }

        public PlayerSprintMachine.Rules sprintRules() {
            if (!present) return PlayerSprintMachine.Rules.VANILLA;
            return new PlayerSprintMachine.Rules(!strafeBoost, IMPULSE_THRESHOLD,
                    nonSprintingStrafe ? 1.0F : IMPULSE_THRESHOLD, true, ignoreWallStop, sprintBackward);
        }

        public PhysicsVariant physicsVariant() {
            if (!present) return PhysicsVariant.VANILLA;
            return new PhysicsVariant(inertiaThreshold, Boolean.TRUE, strafeBoost, newTouch && !byPitch,
                    !sprintDelayInAir, SOURCE);
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("45 strafe ").append(strafeBoost ? "on" : "off");
            sb.append(", new touch ").append(newTouch ? "on" : "off");
            sb.append(", walls ").append(ignoreWallStop ? "keep sprint" : "stop sprint");
            sb.append(", inertia ").append(inertiaThreshold).append(" per axis");
            sb.append(", air sprint ").append(sprintDelayInAir ? "delayed" : "live");
            if (sprintBackward) sb.append(", sprint backward");
            if (nonSprintingStrafe) sb.append(", no sprint start while strafing");
            return sb.toString();
        }

        public List<String> unsupported() {
            List<String> out = new ArrayList<String>();
            if (newTouch && byPitch) out.add("new touch by pitch");
            if (bedrockSneak) out.add("Bedrock sneak");
            if (sprintDelayOnGround) out.add("sprint delay on ground");
            return out;
        }
    }

    private static final boolean PRESENT = Loader.isModLoaded(MOD_ID);

    private static boolean resolved;
    private static boolean broken;
    private static Field newTouchField;
    private static Field byPitchField;
    private static Field inertiaField;
    private static Field strafeField;
    private static Field ignoreWallField;
    private static Field backwardField;
    private static Field delayAirField;
    private static Field delayGroundField;
    private static Field nonSprintingStrafeField;
    private static Field bedrockSneakField;

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
            return new Rules(true,
                    strafeField.getBoolean(null),
                    newTouchField.getBoolean(null),
                    byPitchField.getBoolean(null),
                    backwardField.getBoolean(null),
                    delayGroundField.getBoolean(null),
                    delayAirField.getBoolean(null),
                    ignoreWallField.getBoolean(null),
                    nonSprintingStrafeField.getBoolean(null),
                    bedrockSneakField.getBoolean(null),
                    inertiaField.getDouble(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return Rules.VANILLA;
        }
    }

    public static PhysicsVariant physicsVariant() {
        return rules().physicsVariant();
    }

    public static PlayerSprintMachine.Rules sprintRules() {
        return PRESENT ? rules().sprintRules() : PlayerSprintMachine.Rules.VANILLA;
    }

    private static synchronized boolean resolve() {
        if (resolved) return !broken;
        resolved = true;
        try {
            Class<?> cfg = Class.forName(CONFIG_CLASS, true, BpkModBridge.class.getClassLoader());
            newTouchField = accessible(cfg, "isNewTouch");
            byPitchField = accessible(cfg, "byPitch");
            inertiaField = accessible(cfg, "inertiaThreshold");
            strafeField = accessible(cfg, "strafeAccelerateAllowed");
            ignoreWallField = accessible(cfg, "ignoreCollidedHorizontally");
            backwardField = accessible(cfg, "sprintBackward");
            delayAirField = accessible(cfg, "sprintDelayInAir");
            delayGroundField = accessible(cfg, "sprintDelayOnGround");
            nonSprintingStrafeField = accessible(cfg, "nonSprintingStrafe");
            bedrockSneakField = accessible(cfg, "isBESneak");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return false;
        }
    }

    private static Field accessible(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
