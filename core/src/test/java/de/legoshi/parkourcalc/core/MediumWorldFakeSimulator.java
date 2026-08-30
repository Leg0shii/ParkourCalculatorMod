package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class MediumWorldFakeSimulator extends LazyEntitySimulator<MediumWorldFakeSimulator.FakeEntity> {

    public enum Cell { SOLID, WATER, LAVA, WEB }

    public static final class World {

        private static final class Box {
            final Cell cell;
            final double x0, y0, z0, x1, y1, z1;

            Box(Cell cell, double x0, double y0, double z0, double x1, double y1, double z1) {
                this.cell = cell;
                this.x0 = x0;
                this.y0 = y0;
                this.z0 = z0;
                this.x1 = x1;
                this.y1 = y1;
                this.z1 = z1;
            }

            boolean column(double x, double z) {
                return x >= x0 && x < x1 && z >= z0 && z < z1;
            }

            boolean containsFeet(double x, double y, double z) {
                return column(x, z) && y >= y0 && y < y1;
            }

            boolean overlapsBody(double x, double y, double z) {
                return column(x, z) && y < y1 && y + FakeEntity.HEIGHT > y0;
            }
        }

        private final List<Box> boxes = new ArrayList<>();

        public World add(Cell cell, double x0, double y0, double z0, double x1, double y1, double z1) {
            boxes.add(new Box(cell, x0, y0, z0, x1, y1, z1));
            return this;
        }

        public Set<Cell> statesAt(double x, double y, double z) {
            Set<Cell> cells = EnumSet.noneOf(Cell.class);
            for (Box b : boxes) {
                if (b.cell == Cell.WEB && b.overlapsBody(x, y, z)) cells.add(Cell.WEB);
                if ((b.cell == Cell.WATER || b.cell == Cell.LAVA) && b.containsFeet(x, y, z)) cells.add(b.cell);
            }
            return cells;
        }

        boolean webAt(double x, double y, double z) {
            for (Box b : boxes) {
                if (b.cell == Cell.WEB && b.overlapsBody(x, y, z)) return true;
            }
            return false;
        }

        double floorPlane(double x, double z, double yFrom, double yTo) {
            double best = Double.NaN;
            for (Box b : boxes) {
                if (b.cell != Cell.SOLID || !b.column(x, z)) continue;
                if (yFrom >= b.y1 && yTo < b.y1 && (Double.isNaN(best) || b.y1 > best)) best = b.y1;
            }
            return best;
        }

        double ceilingPlane(double x, double z, double headFrom, double headTo) {
            double best = Double.NaN;
            for (Box b : boxes) {
                if (b.cell != Cell.SOLID || !b.column(x, z)) continue;
                if (headFrom <= b.y0 && headTo > b.y0 && (Double.isNaN(best) || b.y0 < best)) best = b.y0;
            }
            return best;
        }

        double wallPlaneX(double xFrom, double xTo, double y, double z) {
            double best = Double.NaN;
            for (Box b : boxes) {
                if (b.cell != Cell.SOLID) continue;
                if (z < b.z0 || z >= b.z1 || y >= b.y1 || y + FakeEntity.HEIGHT <= b.y0) continue;
                if (xTo > xFrom && xFrom <= b.x0 && xTo > b.x0 && (Double.isNaN(best) || b.x0 < best)) best = b.x0;
                if (xTo < xFrom && xFrom >= b.x1 && xTo < b.x1 && (Double.isNaN(best) || b.x1 > best)) best = b.x1;
            }
            return best;
        }

        double wallPlaneZ(double zFrom, double zTo, double x, double y) {
            double best = Double.NaN;
            for (Box b : boxes) {
                if (b.cell != Cell.SOLID) continue;
                if (x < b.x0 || x >= b.x1 || y >= b.y1 || y + FakeEntity.HEIGHT <= b.y0) continue;
                if (zTo > zFrom && zFrom <= b.z0 && zTo > b.z0 && (Double.isNaN(best) || b.z0 < best)) best = b.z0;
                if (zTo < zFrom && zFrom >= b.z1 && zTo < b.z1 && (Double.isNaN(best) || b.z1 > best)) best = b.z1;
            }
            return best;
        }
    }

    public static final class FakeEntity {

        static final double HEIGHT = 1.8;

        final World world;
        Vec3dCore startPos;
        Vec3dCore startVel;
        float startYaw;

        double x, y, z;
        double vx, vy, vz;
        float yaw;
        float pitch;
        boolean onGround;
        boolean horizontalCollision;
        boolean pendingStuck;
        boolean sprinting;
        int noJumpDelay;
        InputRow row = new InputRow();

        FakeEntity(World world, Vec3dCore startPos, Vec3dCore startVel, float startYaw) {
            this.world = world;
            this.startPos = startPos;
            this.startVel = startVel;
            this.startYaw = startYaw;
            reset(null);
        }

        void reset(StartResumeState resume) {
            x = startPos.x;
            y = startPos.y;
            z = startPos.z;
            vx = 0;
            vy = 0;
            vz = 0;
            yaw = startYaw;
            onGround = false;
            horizontalCollision = false;
            pendingStuck = false;
            sprinting = false;
            noJumpDelay = 0;
            row = new InputRow();
            tick();
            tick();
            x = startPos.x;
            y = startPos.y;
            z = startPos.z;
            vx = startVel.x;
            vy = startVel.y;
            vz = startVel.z;
            if (resume != null) {
                onGround = resume.onGround;
                horizontalCollision = resume.wallContact;
                sprinting = resume.sprinting;
                noJumpDelay = resume.jumpCooldown;
                pendingStuck = resume.stuckMultiplier != null;
            }
        }

        void tick() {
            if (noJumpDelay > 0) noJumpDelay--;
            boolean w = row.isKeyActive(InputRow.Key.W);
            boolean s = row.isKeyActive(InputRow.Key.S);
            boolean a = row.isKeyActive(InputRow.Key.A);
            boolean d = row.isKeyActive(InputRow.Key.D);
            boolean jump = row.isKeyActive(InputRow.Key.JUMP);
            boolean sneak = row.isKeyActive(InputRow.Key.SNEAK);
            boolean sprintKey = row.isKeyActive(InputRow.Key.SPRINT);

            Set<Cell> media = world.statesAt(x, y, z);
            boolean inWater = media.contains(Cell.WATER);
            boolean inLava = media.contains(Cell.LAVA);

            if (!sprinting && w && sprintKey && !sneak) sprinting = true;
            if (sprinting && (!w || sneak || horizontalCollision)) sprinting = false;

            if (jump) {
                if (inWater || inLava) {
                    vy += 0.04;
                } else if (onGround && noJumpDelay == 0) {
                    vy = 0.42;
                    noJumpDelay = 10;
                }
            } else {
                noJumpDelay = 0;
            }

            double forward = (w ? 1.0 : 0.0) - (s ? 1.0 : 0.0);
            double strafe = (a ? 1.0 : 0.0) - (d ? 1.0 : 0.0);
            double len = Math.sqrt(forward * forward + strafe * strafe);
            if (len > 1.0E-4) {
                if (len > 1.0) {
                    forward /= len;
                    strafe /= len;
                }
                double speed;
                if (inWater) {
                    speed = 0.04;
                } else if (inLava) {
                    speed = 0.04;
                } else if (onGround) {
                    speed = sprinting ? 0.13 : 0.1;
                } else {
                    speed = sprinting ? 0.026 : 0.02;
                }
                if (sneak) speed *= 0.3;
                double rad = Math.toRadians(yaw);
                double sin = Math.sin(rad);
                double cos = Math.cos(rad);
                vx += speed * (strafe * cos - forward * sin);
                vz += speed * (forward * cos + strafe * sin);
            }

            double mx = vx;
            double my = vy;
            double mz = vz;
            if (pendingStuck) {
                mx *= 0.25;
                my *= 0.05;
                mz *= 0.25;
                vx = 0;
                vy = 0;
                vz = 0;
                pendingStuck = false;
            }

            move(mx, my, mz);

            if (world.webAt(x, y, z)) pendingStuck = true;

            if (inWater) {
                vx *= 0.8;
                vz *= 0.8;
                vy = vy * 0.8 - 0.02;
            } else if (inLava) {
                vx *= 0.5;
                vz *= 0.5;
                vy = vy * 0.5 - 0.02;
            } else {
                double f = onGround ? 0.546 : 0.91;
                vx *= f;
                vz *= f;
                vy = (vy - 0.08) * 0.98;
            }
        }

        private void move(double mx, double my, double mz) {
            double ny = y + my;
            boolean landed = false;
            if (my < 0) {
                double floor = world.floorPlane(x, z, y, ny);
                if (!Double.isNaN(floor)) {
                    ny = floor;
                    landed = true;
                    vy = 0;
                }
            } else if (my > 0) {
                double ceiling = world.ceilingPlane(x, z, y + HEIGHT, ny + HEIGHT);
                if (!Double.isNaN(ceiling)) {
                    ny = ceiling - HEIGHT;
                    vy = 0;
                }
            }
            y = ny;
            onGround = landed;

            boolean hit = false;
            if (mx != 0) {
                double plane = world.wallPlaneX(x, x + mx, y, z);
                if (!Double.isNaN(plane)) {
                    x = plane;
                    vx = 0;
                    hit = true;
                } else {
                    x = x + mx;
                }
            }
            if (mz != 0) {
                double plane = world.wallPlaneZ(z, z + mz, x, y);
                if (!Double.isNaN(plane)) {
                    z = plane;
                    vz = 0;
                    hit = true;
                } else {
                    z = z + mz;
                }
            }
            horizontalCollision = hit;
        }
    }

    private static final class FakeCheckpoint implements Checkpoint {
        final double x, y, z, vx, vy, vz;
        final float yaw;
        final float pitch;
        final boolean onGround, horizontalCollision, pendingStuck, sprinting;
        final int noJumpDelay;
        final InputRow row;

        FakeCheckpoint(FakeEntity e) {
            x = e.x;
            y = e.y;
            z = e.z;
            vx = e.vx;
            vy = e.vy;
            vz = e.vz;
            yaw = e.yaw;
            pitch = e.pitch;
            onGround = e.onGround;
            horizontalCollision = e.horizontalCollision;
            pendingStuck = e.pendingStuck;
            sprinting = e.sprinting;
            noJumpDelay = e.noJumpDelay;
            row = e.row;
        }

        void applyTo(FakeEntity e) {
            e.x = x;
            e.y = y;
            e.z = z;
            e.vx = vx;
            e.vy = vy;
            e.vz = vz;
            e.yaw = yaw;
            e.pitch = pitch;
            e.onGround = onGround;
            e.horizontalCollision = horizontalCollision;
            e.pendingStuck = pendingStuck;
            e.sprinting = sprinting;
            e.noJumpDelay = noJumpDelay;
            e.row = row;
        }
    }

    private final World world;
    private float startPitch;

    public MediumWorldFakeSimulator(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    @Override public void setStartPitch(float pitch) { this.startPitch = pitch; }

    @Override
    protected FakeEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        Vec3dCore start = pendingStart != null ? pendingStart : Vec3dCore.ZERO;
        Vec3dCore vel = pendingVelocity != null ? pendingVelocity : Vec3dCore.GROUND_REST_VELOCITY;
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new FakeEntity(world, start, vel, yaw);
    }

    @Override protected void resetEntity(FakeEntity e, StartResumeState resume) {
        e.reset(resume);
        e.pitch = startPitch;
    }

    @Override
    protected StartResumeState describeResume(Checkpoint checkpoint) {
        if (!(checkpoint instanceof FakeCheckpoint)) return null;
        FakeCheckpoint c = (FakeCheckpoint) checkpoint;
        StartResumeState r = new StartResumeState();
        r.onGround = c.onGround;
        r.wallContact = c.horizontalCollision;
        r.sprinting = c.sprinting;
        r.jumpCooldown = c.noJumpDelay;
        if (c.pendingStuck) {
            r.stuckMultiplier = new Vec3dCore(0.25, 0.05, 0.25);
        }
        if (c.row != null) {
            for (InputRow.Key key : InputRow.Key.values()) {
                if (c.row.isKeyActive(key)) r.heldLastTick.add(key);
            }
        }
        return r;
    }

    @Override protected void setInput(FakeEntity e, InputRow row) { e.row = row; }

    @Override protected void teleportEntity(FakeEntity e, Vec3dCore pos, Vec3dCore velocity) {
        e.x = pos.x;
        e.y = pos.y;
        e.z = pos.z;
        e.vx = velocity.x;
        e.vy = velocity.y;
        e.vz = velocity.z;
    }

    @Override protected void applyYaw(FakeEntity e, float yaw) { e.yaw = e.yaw + yaw; }

    @Override protected void setYawAbsolute(FakeEntity e, float yaw) { e.yaw = yaw; }

    @Override protected void applyTickEffects(FakeEntity e, int speedAmplifier, int jumpBoostAmplifier) { }

    @Override protected void tickEntity(FakeEntity e) { e.tick(); }

    @Override protected Vec3dCore getPos(FakeEntity e) { return new Vec3dCore(e.x, e.y, e.z); }

    @Override protected boolean isOnGround(FakeEntity e) { return e.onGround; }

    @Override protected boolean isSneaking(FakeEntity e) { return e.row.isKeyActive(InputRow.Key.SNEAK); }

    @Override protected boolean isSprinting(FakeEntity e) { return e.sprinting; }

    @Override protected float getMoveForward(FakeEntity e) { return Float.NaN; }

    @Override protected float getMoveStrafe(FakeEntity e) { return Float.NaN; }

    @Override protected boolean isWallCollision(FakeEntity e) { return e.horizontalCollision; }

    @Override protected Vec3dCore getVelocity(FakeEntity e) { return new Vec3dCore(e.vx, e.vy, e.vz); }

    @Override protected boolean isSoftCollision(FakeEntity e) { return false; }

    @Override protected double getCollisionAngleDegrees(FakeEntity e) { return Double.NaN; }

    @Override protected de.legoshi.parkourcalc.core.anglesolver.Medium getTickMedium(FakeEntity e) { return null; }

    @Override protected double getTickGroundFriction(FakeEntity e) { return Double.NaN; }

    @Override protected int getTickSoulsandCells(FakeEntity e) { return 0; }

    @Override protected float getYaw(FakeEntity e) { return e.yaw; }

    @Override protected float getEntityPitch(FakeEntity e) { return e.pitch; }

    @Override protected void setEntityPitch(FakeEntity e, float pitch) { e.pitch = pitch; }

    @Override protected List<Vec3dCore> getSubtickPath(FakeEntity e) { return Collections.emptyList(); }

    @Override protected Vec3dCore getStart(FakeEntity e) { return e.startPos; }

    @Override protected void setStart(FakeEntity e, Vec3dCore pos) { e.startPos = pos; }

    @Override protected Vec3dCore getStartVel(FakeEntity e) { return e.startVel; }

    @Override protected void setStartVel(FakeEntity e, Vec3dCore vel) { e.startVel = vel; }

    @Override protected float getStartYawValue(FakeEntity e) { return e.startYaw; }

    @Override protected void setStartYawValue(FakeEntity e, float yaw) { e.startYaw = yaw; }

    @Override protected Checkpoint saveCheckpoint(FakeEntity e) { return new FakeCheckpoint(e); }

    @Override protected void restoreCheckpoint(FakeEntity e, Checkpoint checkpoint) { ((FakeCheckpoint) checkpoint).applyTo(e); }
}
