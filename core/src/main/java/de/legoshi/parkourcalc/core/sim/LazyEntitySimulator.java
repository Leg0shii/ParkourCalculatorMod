package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.Collections;
import java.util.List;

/**
 * Shared lazy-create + pending-start orchestration for Simulator impls that wrap
 * a real MC entity. pendingStart/pendingVelocity/pendingYaw hold the values set
 * before the entity exists; once createEntity runs, the entity owns them.
 */
public abstract class LazyEntitySimulator<E> implements Simulator {

    private E entity;
    private Vec3dCore pendingStart;
    private Vec3dCore pendingVelocity;
    private Float pendingYaw;

    @Override
    public final void resetToStart() {
        resetEntity(ensureEntity());
    }

    @Override
    public final void applyInput(InputRow row) {
        E e = ensureEntity();
        setInput(e, row);
        if (row.getYaw() != null) {
            applyYaw(e, row.getYaw());
        }
    }

    @Override
    public final void tick() {
        tickEntity(ensureEntity());
    }

    @Override
    public final Vec3dCore getCurrentPosition() {
        return getPos(ensureEntity());
    }

    @Override
    public final boolean isCurrentOnGround() {
        return isOnGround(ensureEntity());
    }

    @Override
    public final boolean isCurrentSneaking() {
        return isSneaking(ensureEntity());
    }

    @Override
    public final boolean isCurrentWallCollision() {
        return isWallCollision(ensureEntity());
    }

    @Override
    public final Vec3dCore getCurrentVelocity() {
        return getVelocity(ensureEntity());
    }

    @Override
    public final boolean isCurrentSoftCollision() {
        return isSoftCollision(ensureEntity());
    }

    @Override
    public final float getCurrentYaw() {
        return getYaw(ensureEntity());
    }

    @Override
    public final List<Vec3dCore> getCurrentSubtickPath() {
        if (entity == null) return Collections.emptyList();
        return getSubtickPath(entity);
    }

    @Override
    public final Vec3dCore getStartPosition() {
        if (entity != null) return getStart(entity);
        return pendingStart != null ? pendingStart : Vec3dCore.ZERO;
    }

    @Override
    public final void setStartPosition(Vec3dCore pos) {
        if (entity != null) {
            setStart(entity, pos);
        } else {
            pendingStart = pos;
        }
    }

    @Override
    public final Vec3dCore getStartVelocity() {
        if (entity != null) return getStartVel(entity);
        return pendingVelocity != null ? pendingVelocity : Vec3dCore.ZERO;
    }

    @Override
    public final void setStartVelocity(Vec3dCore vel) {
        if (entity != null) {
            setStartVel(entity, vel);
        } else {
            pendingVelocity = vel;
        }
    }

    @Override
    public final float getStartYaw() {
        if (entity != null) return getStartYawValue(entity);
        return pendingYaw != null ? pendingYaw : 0.0F;
    }

    @Override
    public final void setStartYaw(float yaw) {
        if (entity != null) {
            setStartYawValue(entity, yaw);
        } else {
            pendingYaw = yaw;
        }
    }

    @Override
    public final Checkpoint saveCheckpoint() {
        return saveCheckpoint(ensureEntity());
    }

    @Override
    public final void restoreCheckpoint(Checkpoint checkpoint) {
        restoreCheckpoint(ensureEntity(), checkpoint);
    }

    @Override
    public final void invalidate() {
        entity = null;
        pendingStart = null;
        pendingVelocity = null;
        pendingYaw = null;
    }

    private E ensureEntity() {
        if (entity == null) {
            entity = createEntity(pendingStart, pendingVelocity, pendingYaw);
        }
        return entity;
    }

    /** Any of pendingStart/pendingVelocity/pendingYaw may be null; subclass falls back to defaults. */
    protected abstract E createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw);

    protected abstract void resetEntity(E entity);

    protected abstract void setInput(E entity, InputRow row);

    protected abstract void applyYaw(E entity, float yaw);

    protected abstract void tickEntity(E entity);

    protected abstract Vec3dCore getPos(E entity);

    protected abstract boolean isOnGround(E entity);

    protected abstract boolean isSneaking(E entity);

    protected abstract boolean isWallCollision(E entity);

    protected abstract Vec3dCore getVelocity(E entity);

    protected abstract boolean isSoftCollision(E entity);

    protected abstract float getYaw(E entity);

    protected abstract List<Vec3dCore> getSubtickPath(E entity);

    protected abstract Vec3dCore getStart(E entity);

    protected abstract void setStart(E entity, Vec3dCore pos);

    protected abstract Vec3dCore getStartVel(E entity);

    protected abstract void setStartVel(E entity, Vec3dCore vel);

    protected abstract float getStartYawValue(E entity);

    protected abstract void setStartYawValue(E entity, float yaw);

    /** Capture all loader-specific entity state needed to resume ticking. */
    protected abstract Checkpoint saveCheckpoint(E entity);

    /** Restore a checkpoint previously taken by saveCheckpoint(E). */
    protected abstract void restoreCheckpoint(E entity, Checkpoint checkpoint);
}
