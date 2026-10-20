package de.legoshi.parkourcalc.forge.sim;

import de.legoshi.parkourcalc.core.anglesolver.solver.PhysicsVariant;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BpkModBridge {

    public static final String MOD_ID = "bpk";
    public static final String SOURCE = "BPKMod";

    private static final String GENERAL_CONFIG = "cn.xiaozi0721.bpk.config.ConfigHandler$GeneralConfig";
    private static final String UNDER_BLOCK_FIELD = "BPKMod$underBlock";
    private static final String RESIZING_ALLOWED_FIELD = "BPKMod$resizingAllowed";
    private static final UUID SPRINT_BOOST_ID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final float VANILLA_SNEAK_HEIGHT = 1.65F;
    private static final float BEDROCK_SNEAK_HEIGHT = 1.5F;

    public static final class Rules {
        public static final Rules VANILLA = new Rules(false, true, false, false, false, false, true, false, false,
                false, false, true, Double.NaN, Float.NaN);

        public final boolean active;
        public final boolean strafeBoost;
        public final boolean ignoreWallCollision;
        public final boolean sprintBackward;
        public final boolean nonSprintingStrafe;
        public final boolean sprintDelayOnGround;
        public final boolean sprintDelayInAir;
        public final boolean newTouch;
        public final boolean newTouchByPitch;
        public final boolean bedrockSneak;
        public final boolean bedrockLadder;
        public final boolean climbableTrapdoor;
        public final double inertiaThreshold;
        public final float sneakHeight;

        Rules(boolean active, boolean strafeBoost, boolean ignoreWallCollision, boolean sprintBackward,
              boolean nonSprintingStrafe, boolean sprintDelayOnGround, boolean sprintDelayInAir, boolean newTouch,
              boolean newTouchByPitch, boolean bedrockSneak, boolean bedrockLadder, boolean climbableTrapdoor,
              double inertiaThreshold, float sneakHeight) {
            this.active = active;
            this.strafeBoost = strafeBoost;
            this.ignoreWallCollision = ignoreWallCollision;
            this.sprintBackward = sprintBackward;
            this.nonSprintingStrafe = nonSprintingStrafe;
            this.sprintDelayOnGround = sprintDelayOnGround;
            this.sprintDelayInAir = sprintDelayInAir;
            this.newTouch = newTouch;
            this.newTouchByPitch = newTouchByPitch;
            this.bedrockSneak = bedrockSneak;
            this.bedrockLadder = bedrockLadder;
            this.climbableTrapdoor = climbableTrapdoor;
            this.inertiaThreshold = inertiaThreshold;
            this.sneakHeight = sneakHeight;
        }

        public boolean normaliseInput() {
            return active && !strafeBoost;
        }

        public PlayerSprintMachine.Rules sprintRules() {
            if (!active) return PlayerSprintMachine.Rules.VANILLA;
            return new PlayerSprintMachine.Rules(true, !strafeBoost, nonSprintingStrafe, sprintBackward,
                    ignoreWallCollision, sprintDelayOnGround, bedrockSneak, sneakHeight);
        }

        public PhysicsVariant physicsVariant() {
            if (!active) return PhysicsVariant.VANILLA;
            return new PhysicsVariant(inertiaThreshold, Boolean.TRUE, strafeBoost, SOURCE);
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("45 strafe ").append(strafeBoost ? "on" : "off");
            sb.append(", walls ").append(ignoreWallCollision ? "keep sprint" : "stop sprint");
            sb.append(", inertia ");
            if (inertiaThreshold == 0.0) sb.append("off");
            else sb.append(inertiaThreshold).append(" per axis");
            if (nonSprintingStrafe) sb.append(", no sprint start while strafing");
            if (sprintBackward) sb.append(", sprint backward");
            if (!sprintDelayInAir) sb.append(", no air sprint delay");
            if (sprintDelayOnGround) sb.append(", ground sprint delay");
            if (newTouch) sb.append(", new touch").append(newTouchByPitch ? " by pitch" : "");
            if (bedrockSneak) sb.append(", BE sneak");
            if (bedrockLadder) sb.append(", BE ladder");
            return sb.toString();
        }

        public List<String> solverWarnings() {
            List<String> out = new ArrayList<String>();
            if (newTouch) {
                out.add("BPKMod new touch is on: the solver does not model the 11.48 degree strafe, trust the simulation only");
            }
            if (sprintDelayOnGround) {
                out.add("BPKMod ground sprint delay is simulated as one tick (the real delay is the server round trip)");
            }
            return out;
        }
    }

    private static final boolean PRESENT = Loader.isModLoaded(MOD_ID);

    private static boolean resolved;
    private static boolean broken;
    private static Field newTouchField;
    private static Field byPitchField;
    private static Field sprintBackwardField;
    private static Field sprintDelayInAirField;
    private static Field sprintDelayOnGroundField;
    private static Field inertiaThresholdField;
    private static Field bedrockSneakField;
    private static Field sneakHeightField;
    private static Field strafeBoostField;
    private static Field ignoreCollisionField;
    private static Field nonSprintingStrafeField;
    private static Field climbableTrapdoorField;
    private static Field bedrockLadderField;

    private static boolean playerFieldsResolved;
    private static Field underBlockField;
    private static Field resizingAllowedField;

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
            boolean bedrockSneak = bedrockSneakField.getBoolean(null);
            float sneakHeight = sneakHeightField.getFloat(null);
            if (sneakHeight == 0.0F) sneakHeight = bedrockSneak ? BEDROCK_SNEAK_HEIGHT : VANILLA_SNEAK_HEIGHT;
            return new Rules(
                    true,
                    strafeBoostField.getBoolean(null),
                    ignoreCollisionField.getBoolean(null),
                    sprintBackwardField.getBoolean(null),
                    nonSprintingStrafeField.getBoolean(null),
                    sprintDelayOnGroundField.getBoolean(null),
                    sprintDelayInAirField.getBoolean(null),
                    newTouchField.getBoolean(null),
                    byPitchField.getBoolean(null),
                    bedrockSneak,
                    bedrockLadderField.getBoolean(null),
                    climbableTrapdoorField.getBoolean(null),
                    inertiaThresholdField.getDouble(null),
                    sneakHeight);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return Rules.VANILLA;
        }
    }

    public static PhysicsVariant physicsVariant() {
        return PRESENT ? rules().physicsVariant() : PhysicsVariant.VANILLA;
    }

    public static boolean sneakForced(EntityPlayer player, Rules rules) {
        if (!rules.active || !rules.bedrockSneak || !resolvePlayerFields()) return false;
        try {
            return underBlockField.getBoolean(player) && !resizingAllowedField.getBoolean(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            underBlockField = null;
            return false;
        }
    }

    public static void dropSprintBoost(EntityLivingBase entity) {
        //? if >=1.12 {
        IAttributeInstance speed = entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        //?} else {
        /*IAttributeInstance speed = entity.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        *///?}
        AttributeModifier boost = speed.getModifier(SPRINT_BOOST_ID);
        if (boost != null) speed.removeModifier(boost);
    }

    private static synchronized boolean resolve() {
        if (resolved) return !broken;
        resolved = true;
        try {
            Class<?> cfg = Class.forName(GENERAL_CONFIG, true, BpkModBridge.class.getClassLoader());
            newTouchField = accessible(cfg, "isNewTouch");
            byPitchField = accessible(cfg, "byPitch");
            sprintBackwardField = accessible(cfg, "sprintBackward");
            sprintDelayInAirField = accessible(cfg, "sprintDelayInAir");
            sprintDelayOnGroundField = accessible(cfg, "sprintDelayOnGround");
            inertiaThresholdField = accessible(cfg, "inertiaThreshold");
            bedrockSneakField = accessible(cfg, "isBESneak");
            sneakHeightField = accessible(cfg, "sneakHeight");
            strafeBoostField = accessible(cfg, "strafeAccelerateAllowed");
            ignoreCollisionField = accessible(cfg, "ignoreCollidedHorizontally");
            nonSprintingStrafeField = accessible(cfg, "nonSprintingStrafe");
            climbableTrapdoorField = accessible(cfg, "climbableTrapdoor");
            bedrockLadderField = accessible(cfg, "isBELadder");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            return false;
        }
    }

    private static synchronized boolean resolvePlayerFields() {
        if (playerFieldsResolved) return underBlockField != null;
        playerFieldsResolved = true;
        try {
            underBlockField = accessible(EntityPlayer.class, UNDER_BLOCK_FIELD);
            resizingAllowedField = accessible(EntityPlayer.class, RESIZING_ALLOWED_FIELD);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            underBlockField = null;
            return false;
        }
    }

    private static Field accessible(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
