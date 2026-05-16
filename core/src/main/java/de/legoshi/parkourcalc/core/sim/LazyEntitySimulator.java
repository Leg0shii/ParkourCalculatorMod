package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.ui.InputRow;

/**
 * Shared lazy-create + pending-start orchestration for Simulator impls that wrap
 * a real MC entity. The state machine (when to construct, how to dispatch the
 * Simulator interface methods) lives here once; per-MC-version field access and
 * entity construction live in subclass hooks.
 *
 * pendingStart holds the start position set before the entity exists; once
 * createEntity runs, the entity owns its own start and pendingStart is unused.
 */
public abstract class LazyEntitySimulator<E> implements Simulator {

    private E entity;
    private Vec3dCore pendingStart;

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

    private E ensureEntity() {
        if (entity == null) {
            entity = createEntity(pendingStart);
        }
        return entity;
    }

    /** May be null; subclass falls back to live player position when null. */
    protected abstract E createEntity(Vec3dCore pendingStart);

    protected abstract void resetEntity(E entity);

    protected abstract void setInput(E entity, InputRow row);

    protected abstract void applyYaw(E entity, float yaw);

    protected abstract void tickEntity(E entity);

    protected abstract Vec3dCore getPos(E entity);

    protected abstract Vec3dCore getStart(E entity);

    protected abstract void setStart(E entity, Vec3dCore pos);
}
