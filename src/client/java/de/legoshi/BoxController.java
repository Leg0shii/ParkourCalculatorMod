package de.legoshi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BoxController {
    private final MovementSimulator movementSimulator;
    private final List<BoxInfo> boxes = new ArrayList<>();
    private Runnable onChange;

    private BoxInfo dragging;
    private double planeY;
    private double offX, offZ;

    public BoxController(MovementSimulator movementSimulator) {
        this.movementSimulator = movementSimulator;
    }

    public void add(Vec3d c) {
        boxes.add(new BoxInfo(c.x, c.y, c.z, c.x + 0.1, c.y + 0.1, c.z + 0.1));
    }

    public void addAll(List<Vec3d> coordinates) {
        for (Vec3d c : coordinates) {
            this.add(c);
        }
    }

    public void clearAll() {
        boxes.clear();
    }

    public Optional<BoxInfo> pick(Vec3d start, Vec3d end) {
        BoxInfo hitBox = null;
        double bestSq = Double.MAX_VALUE;

        for (BoxInfo b : boxes) {
            var hit = b.getAABB().expand(1e-3).raycast(start, end);
            if (hit.isEmpty()) continue;

            double dist  = start.squaredDistanceTo(hit.get());
            if (dist < bestSq) {
                bestSq = dist;
                hitBox = b;
                offX = hit.get().x - b.getAABB().minX;
                offZ = hit.get().z - b.getAABB().minZ;
            }
        }
        if (hitBox == null) return Optional.empty();

        planeY = hitBox.getAABB().minY;

        Vec3d dir = end.subtract(start).normalize();
        double t = (planeY - start.y) / dir.y;
        Vec3d anchor = start.add(dir.multiply(t));

        offX = anchor.x - hitBox.getAABB().minX;
        offZ = anchor.z - hitBox.getAABB().minZ;

        return Optional.of(hitBox);
    }

    public void dragFrame(Camera cam) {
        if (dragging == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!mc.options.attackKey.isPressed()) {
            dragging = null;
            return;
        }

        Vec3d start = cam.getPos();
        Vec3d dir = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());

        double denom = dir.y;
        if (Math.abs(denom) < 1e-6) return;

        double t = (planeY - start.y) / denom;
        if (t < 0) return;

        Vec3d hit = start.add(dir.multiply(t));
        double targetX = hit.x - offX;
        double targetZ = hit.z - offZ;

        Vec3d curMin = dragging.getAABB().getMinPos();
        Vec3d smoothMin = curMin.lerp(new Vec3d(targetX, planeY, targetZ), 0.35);
        dragging.setMin(smoothMin);

        this.movementSimulator.getSimulatorEntity().startPosition = smoothMin;
        this.onChange.run();
    }

    public void startDrag(BoxInfo box) {
        dragging = box;
        planeY = box.getAABB().minY;
    }

    public void renderAll(MatrixStack ms, VertexConsumerProvider consumers) {
        Vec3d eye = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        ms.push();
        ms.translate(-eye.x, -eye.y, -eye.z);
        boxes.forEach(b -> b.render(ms, consumers));
        ms.pop();
    }

    public BoxInfo getFirst() {
        return boxes.getFirst();
    }

    public void setOnChange(Runnable runnable) {
        this.onChange = runnable;
    }
}
