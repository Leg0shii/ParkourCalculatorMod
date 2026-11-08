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
    private static final String PLACE_HINT = "LMB place · RMB change the looked-at block · Esc stop";
    private static final String CHANGE_HINT = "RMB change · LMB place the block in hand · Esc stop";

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
    private BlockHit hit;
    private String held;

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
        hit = null;
        held = null;
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
        held = mc.getHeldBlock();
        BlockHit looked = mc.getLookedAtHit();
        hit = looked != null && inReach(looked) ? looked : null;
        int[] target = mode == Mode.PLACE ? placeTarget() : changeTarget();
        world.setPreview(target, BoxStyle.blockOverridePlacedArgb(settings));
        String banner = banner(target);
        hud.setStatus(banner, HudMessages.COLOR_DEFAULT, boldPrefix(), 1);
    }

    private int[] placeTarget() {
        return hit != null && held != null ? placementTarget(hit) : null;
    }

    private int[] changeTarget() {
        return hit != null && lookedAtBlock() != null ? new int[] {hit.x, hit.y, hit.z} : null;
    }

    private String lookedAtBlock() {
        return hit == null ? null : mc.describeBlockState(hit.x, hit.y, hit.z);
    }

    private boolean inReach(BlockHit hit) {
        Vec3dCore eye = mc.getEyePosition();
        double dx = hit.hitVec.x - eye.x;
        double dy = hit.hitVec.y - eye.y;
        double dz = hit.hitVec.z - eye.z;
        double reach = mc.getBlockReach();
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private String boldPrefix() {
        if (mode == Mode.PLACE) return held == null ? "Hold a block" : "Placing " + name(held);
        String looked = lookedAtBlock();
        return looked == null ? "Aim at a block" : "Changing " + name(looked);
    }

    private String banner(int[] target) {
        StringBuilder sb = new StringBuilder(boldPrefix());
        if (mode == Mode.PLACE) {
            if (held != null) sb.append(" from the main hand");
            sb.append('\n');
            sb.append(target == null ? "Aim at a block" : "at " + where(target) + ", tick " + startTick() + " onward");
            sb.append('\n').append(PLACE_HINT);
        } else {
            if (target != null) sb.append(" at the crosshair");
            sb.append('\n');
            sb.append(target == null ? "The looked-at block becomes the row" : "at " + where(target) + ", tick " + startTick() + " onward");
            sb.append('\n').append(CHANGE_HINT);
        }
        return sb.toString();
    }

    private static String where(int[] target) {
        return target[0] + " " + target[1] + " " + target[2];
    }

    private String name(String block) {
        BlockOverride.Spec spec = BlockOverride.Spec.parse(block);
        if (!spec.valid()) return block;
        return spec.displayName(BlockOverride.Spec.parse(mc.defaultBlockState(spec.id)));
    }

    public boolean onMouse(int button, boolean down) {
        if (!isActive()) return false;
        if (!down) return false;
        if (button != LEFT && button != RIGHT) return false;
        if (button == LEFT) {
            int[] target = placeTarget();
            if (target == null) return true;
            addRow(target, held, true);
        } else {
            int[] target = changeTarget();
            if (target == null) return true;
            addRow(target, lookedAtBlock(), false);
        }
        stop(true);
        return true;
    }

    public boolean onEscape() {
        if (!isActive()) return false;
        stop(true);
        return true;
    }

    private int startTick() {
        return Math.max(1, Math.min(world.viewRow() + 1, Math.max(1, inputData.size())));
    }

    private void addRow(int[] target, String block, boolean placed) {
        int start = startTick();
        String before = mc.describeBlockState(target[0], target[1], target[2]);
        if (before == null) before = BlockOverride.AIR;
        boolean player = placed && start - 1 < inputData.size()
                && inputData.getRows().get(start - 1).isKeyActive(InputRow.Key.RIGHT_CLICK);
        BlockOverrides blocks = inputData.getBlockOverrides();
        BlockOverride row = new BlockOverride(start, BlockOverride.NO_END, target[0], target[1], target[2], block, before, player);
        blocks.add(row);
        world.select(row);
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
