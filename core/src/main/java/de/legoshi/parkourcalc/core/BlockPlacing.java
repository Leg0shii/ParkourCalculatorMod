package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ports.MinecraftAccess;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.BlockOverride;
import de.legoshi.parkourcalc.core.sim.BlockOverrides;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.BoxStyle;
import de.legoshi.parkourcalc.core.ui.HudMessages;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.HudMessageStyle;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class BlockPlacing {

    public enum Mode { OFF, PLACE, CHANGE }

    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    private static final String STOP_HINT = "Esc stop";

    private final MinecraftAccess mc;
    private final InputData inputData;
    private final BlockOverrideWorld world;
    private final Settings settings;
    private final HudMessages hud;
    private final IntConsumer onChanged;
    private Consumer<Boolean> uiOpener = new Consumer<Boolean>() {
        @Override
        public void accept(Boolean open) {
        }
    };
    private Mode mode = Mode.OFF;
    private int[] target;
    private String targetBlock;

    public BlockPlacing(MinecraftAccess mc, InputData inputData, BlockOverrideWorld world, Settings settings,
                        HudMessages hud, IntConsumer onChanged) {
        this.mc = mc;
        this.inputData = inputData;
        this.world = world;
        this.settings = settings;
        this.hud = hud;
        this.onChanged = onChanged;
    }

    public void setUiOpener(Consumer<Boolean> opener) {
        this.uiOpener = opener;
    }

    public Mode mode() {
        return mode;
    }

    public boolean isActive() {
        return mode != Mode.OFF;
    }

    public boolean startPlace() {
        if (!world.isAvailable()) return false;
        if (mc.getHeldBlock() == null) {
            hud.push("Hold a block to place", HudMessageStyle.COLOR_WARN, System.nanoTime());
            return false;
        }
        mode = Mode.PLACE;
        uiOpener.accept(false);
        return true;
    }

    public boolean startChange() {
        if (!world.isAvailable()) return false;
        mode = Mode.CHANGE;
        uiOpener.accept(false);
        return true;
    }

    public void toggle() {
        if (isActive()) {
            stop(false);
        } else {
            startPlace();
        }
    }

    public void reset() {
        if (isActive()) stop(false);
    }

    private void stop(boolean reopenUi) {
        mode = Mode.OFF;
        target = null;
        targetBlock = null;
        world.setPreview(null, 0);
        hud.clearStatus();
        if (reopenUi) uiOpener.accept(true);
    }

    public void tick() {
        if (!isActive()) return;
        if (!mc.isReady() || !world.isAvailable()) {
            stop(false);
            return;
        }
        target = null;
        targetBlock = null;
        BlockHit hit = mc.getLookedAtHit();
        if (hit != null && inReach(hit)) {
            if (mode == Mode.PLACE) {
                targetBlock = mc.getHeldBlock();
                if (targetBlock != null) target = placementTarget(hit);
            } else {
                targetBlock = mc.describeBlockState(hit.x, hit.y, hit.z);
                if (targetBlock != null) target = new int[] {hit.x, hit.y, hit.z};
            }
        }
        world.setPreview(target, BoxStyle.blockOverridePlacedArgb(settings));
        hud.setStatus(banner(), HudMessages.COLOR_DEFAULT);
    }

    private boolean inReach(BlockHit hit) {
        Vec3dCore eye = mc.getEyePosition();
        double dx = hit.hitVec.x - eye.x;
        double dy = hit.hitVec.y - eye.y;
        double dz = hit.hitVec.z - eye.z;
        double reach = mc.getBlockReach();
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private String banner() {
        String action = mode == Mode.PLACE ? "LMB place" : "RMB change";
        if (target == null) {
            return (mode == Mode.PLACE && mc.getHeldBlock() == null ? "Hold a block" : "Aim at a block") + "\n" + action + " · " + STOP_HINT;
        }
 BlockOverride.Spec spec = BlockOverride.Spec.parse(targetBlock);
        String name = spec.displayName(BlockOverride.Spec.parse(mc.defaultBlockState(spec.id)));
        return name + " at " + target[0] + " " + target[1] + " " + target[2] + " from tick " + startTick()
                + "\n" + action + " · " + STOP_HINT;
    }

    public boolean onMouse(int button, boolean down) {
        if (!isActive()) return false;
        if (!down) return false;
        if (button != LEFT && button != RIGHT) return false;
        boolean confirm = mode == Mode.PLACE ? button == LEFT : button == RIGHT;
        if (!confirm) return true;
        if (target != null && targetBlock != null) addRow();
        stop(true);
        return true;
    }

    public boolean onEscape() {
        if (!isActive()) return false;
        stop(true);
        return true;
    }

    private int startTick() {
        return Math.max(1, Math.min(world.viewRow() + 2, Math.max(1, inputData.size())));
    }

    private void addRow() {
        int start = startTick();
        String before = mc.describeBlockState(target[0], target[1], target[2]);
        if (before == null) before = BlockOverride.AIR;
        boolean player = mode == Mode.PLACE && start - 1 < inputData.size()
                && inputData.getRows().get(start - 1).isKeyActive(InputRow.Key.RIGHT_CLICK);
        BlockOverrides blocks = inputData.getBlockOverrides();
        blocks.add(new BlockOverride(start, BlockOverride.NO_END, target[0], target[1], target[2], targetBlock, before, player));
        onChanged.accept(0);
    }

    public static int[] placementTarget(BlockHit hit) {
        int x = hit.x;
        int y = hit.y;
        int z = hit.z;
        if (hit.face == Face.POS_X) x++;
        if (hit.face == Face.NEG_X) x--;
        if (hit.face == Face.POS_Y) y++;
        if (hit.face == Face.NEG_Y) y--;
        if (hit.face == Face.POS_Z) z++;
        if (hit.face == Face.NEG_Z) z--;
        return new int[] {x, y, z};
    }
}
