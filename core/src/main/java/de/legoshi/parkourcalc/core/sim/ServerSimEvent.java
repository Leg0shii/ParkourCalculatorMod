package de.legoshi.parkourcalc.core.sim;

public final class ServerSimEvent {

    public enum Kind {
        VELOCITY_SET("velocity-set"),
        POSITION_CORRECTION("position-correction"),
        DAMAGE_RULED("damage-ruled"),
        BLOCK_CHANGED("block-changed"),
        INTERACTION_REJECTED("interaction-rejected");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public final int tick;
    public final Kind kind;
    public final String detail;

    public ServerSimEvent(int tick, Kind kind, String detail) {
        this.tick = tick;
        this.kind = kind;
        this.detail = detail == null ? "" : detail;
    }
}
