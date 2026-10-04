package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.forge8.sim.GhostPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;

final class ReplicaMouseHelper extends MouseHelper {

    private final Forge8PlaybackBridge bridge;

    ReplicaMouseHelper(Forge8PlaybackBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void mouseXYChange() {
        int dx = Mouse.getDX();
        int dy = Mouse.getDY();
        this.deltaX = 0;
        this.deltaY = 0;
        GhostPlayerEntity g = bridge.ghostEntity();
        if (g == null) return;
        GameSettings o = Minecraft.getMinecraft().gameSettings;
        float f = o.mouseSensitivity * 0.6F + 0.2F;
        float f1 = f * f * f * 8.0F;
        g.setAngles(dx * f1, dy * f1 * (o.invertMouse ? -1 : 1));
    }
}
