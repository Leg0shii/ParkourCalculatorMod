package de.legoshi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controls the rendering and interaction of path visualization boxes.
 */
public class BoxController {

    private final MovementSimulator simulator;
    private final List<BoxInfo> boxes = new ArrayList<>();
    private Runnable onChange = () -> {};

    private DragState dragState = null;

    public BoxController(MovementSimulator simulator) {
        this.simulator = simulator;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void add(Vec3d position) {
        boxes.add(new BoxInfo(position));
    }

    public void addAll(List<Vec3d> positions) {
        positions.forEach(this::add);
    }

    public void clearAll() {
        boxes.clear();
    }

    public BoxInfo getFirst() {
        return boxes.isEmpty() ? null : boxes.getFirst();
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    /**
     * Performs a raycast to find the box under the cursor.
     */
    public Optional<BoxInfo> pick(Vec3d start, Vec3d end) {
        BoxInfo closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (BoxInfo box : boxes) {
            var hit = box.getAABB().expand(1e-3).raycast(start, end);
            if (hit.isEmpty()) continue;

            double distSq = start.squaredDistanceTo(hit.get());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = box;
            }
        }

        return Optional.ofNullable(closest);
    }

    /**
     * Starts dragging the first box (start position).
     */
    public void startDrag(BoxInfo box, Camera camera) {
        Vec3d boxPos = box.getMinPosition();
        Vec3d cursorOnPlane = projectCursorToPlane(camera, boxPos.y);

        if (cursorOnPlane != null) {
            dragState = new DragState(
                    boxPos.y,
                    boxPos.x, boxPos.z,
                    cursorOnPlane.x, cursorOnPlane.z
            );
        }
    }

    /**
     * Updates the drag each frame.
     */
    public void updateDrag(Camera camera) {
        if (dragState == null) return;

        if (!MinecraftClient.getInstance().options.attackKey.isPressed()) {
            dragState = null;
            return;
        }

        Vec3d cursorOnPlane = projectCursorToPlane(camera, dragState.planeY);
        if (cursorOnPlane == null) return;

        double deltaX = cursorOnPlane.x - dragState.startCursorX;
        double deltaZ = cursorOnPlane.z - dragState.startCursorZ;

        Vec3d newPosition = new Vec3d(
                dragState.startBoxX + deltaX,
                dragState.planeY,
                dragState.startBoxZ + deltaZ
        );

        simulator.getSimulatorEntity().startPosition = newPosition;
        onChange.run();
    }

    public boolean isDragging() {
        return dragState != null;
    }

    public void stopDrag() {
        dragState = null;
    }

    /**
     * Projects the camera's look direction onto a horizontal plane.
     */
    private Vec3d projectCursorToPlane(Camera camera, double planeY) {
        Vec3d start = camera.getPos();
        Vec3d direction = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());

        if (Math.abs(direction.y) < 1e-6) {
            return null; // Looking parallel to plane
        }

        double t = (planeY - start.y) / direction.y;
        if (t < 0) {
            return null; // Plane is behind camera
        }

        return start.add(direction.multiply(t));
    }

    /**
     * Renders all boxes.
     */
    public void render(MatrixStack matrices, VertexConsumerProvider consumers) {
        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (BoxInfo box : boxes) {
            box.render(matrices, consumers);
        }

        matrices.pop();
    }

    private record DragState(
            double planeY,
            double startBoxX, double startBoxZ,
            double startCursorX, double startCursorZ
    ) {}
}