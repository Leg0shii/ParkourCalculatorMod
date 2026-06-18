package de.legoshi.parkourcalc.core.ui;

public enum TickInfoStat {

    TICK("tick", "Tick", Kind.INT,
            "Tick number (1-based), matching the input table's Tick column."),
    YAW("yaw", "Yaw", Kind.NUM,
            "Facing applied during this tick (drives this tick's movement). MC convention: 0 = +Z, increases CW looking down."),
    PITCH("pitch", "Pitch", Kind.NUM,
            "Camera pitch held during this tick (-90 up, 90 down). Defaults to 40; each row adds a relative turn unless locked to an absolute value. Display only; pitch does not affect movement."),
    SPEED_XZ("speedXZ", "Speed (XZ)", Kind.NUM,
            "Horizontal magnitude of actual displacement this tick, sqrt(dx^2 + dz^2), blocks/tick."),
    MOTION_XZ("motionXZ", "Motion (XZ)", Kind.NUM,
            "Horizontal magnitude of post-tick velocity, sqrt(vx^2 + vz^2), blocks/tick. Differs from Speed on collision ticks."),
    MOTION_XYZ("motionXYZ", "Motion (XYZ)", Kind.NUM,
            "Total magnitude of post-tick velocity, sqrt(vx^2 + vy^2 + vz^2), blocks/tick."),
    POSITION("position", "Position", Kind.TRIPLE,
            "Entity position entering this tick, before this tick's input is applied (the start seed for the Start row). This tick's movement shows on the next tick. World coords; anchor corner of the rendered tick box."),
    MOTION("motion", "Motion", Kind.TRIPLE,
            "Post-tick motionX/Y/Z (after MC's per-axis collision clamp). May read 0 on an axis where a wall was hit."),
    SPEED("speed", "Speed", Kind.TRIPLE,
            "Position(i) - position(i-1), the actual displacement vector this tick."),
    POST_MOTION_XZ("postMotionXZ", "Post motion (XZ)", Kind.XZ,
            "Per-axis horizontal displacement this tick: (deltaX, deltaZ). Differs from Motion on collision-clamp ticks."),
    ACCELERATION_XZ("accelerationXZ", "Acceleration (XZ)", Kind.XZ,
            "Per-axis change in post motion: (deltaX(i) - deltaX(i-1), deltaZ(i) - deltaZ(i-1))."),
    SPEED_ANGLE("speedAngle", "Speed (angle)", Kind.NUM,
            "Movement direction in XZ. MC yaw convention: 0 = +Z, increases CW looking down (atan2(-dx, dz))."),
    ON_GROUND("onGround", "On ground", Kind.BOOL,
            "Entity onGround flag at end of tick."),
    SNEAKING("sneaking", "Sneaking", Kind.BOOL,
            "Sneak input active during this tick."),
    COLLISION("collision", "Collision", Kind.BOOL,
            "Horizontal collision occurred this tick (MC horizontalCollision)."),
    SOFT_COLLISION("softCollision", "Soft collision", Kind.BOOL,
            "1.21.10 only: grazing wall hit that does NOT break sprint (Entity.collidedSoftly). Always false on 1.8.9/1.12.2.",
            "1.21.10 only: grazing wall hit that does NOT break sprint. n/a when no horizontal collision is happening."),
    COLLISION_ANGLE("collisionAngle", "Collision angle (deg)", Kind.NUM,
            "1.21.10 only: angle between intended motion and post-collision motion. MC keeps sprint when this is below ~8 deg (0.13962634 rad).",
            "1.21.10 only: angle between intended motion (forwardSpeed/sidewaysSpeed rotated by yaw) and post-collision motion. n/a on 1.8.9/1.12.2 or off-collision ticks.");

    public enum Kind {
        NUM,
        INT,
        BOOL,
        TRIPLE,
        XZ
    }

    private final String id;
    private final String label;
    private final Kind kind;
    private final String tooltip;
    private final String naTooltip;

    TickInfoStat(String id, String label, Kind kind, String tooltip) {
        this(id, label, kind, tooltip, null);
    }

    TickInfoStat(String id, String label, Kind kind, String tooltip, String naTooltip) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.tooltip = tooltip;
        this.naTooltip = naTooltip;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public String tooltip() {
        return tooltip;
    }

    public String naTooltip() {
        return naTooltip != null ? naTooltip : tooltip;
    }

    public static TickInfoStat byId(String id) {
        if (id == null) return null;
        for (TickInfoStat stat : values()) {
            if (stat.id.equals(id)) return stat;
        }
        return null;
    }
}
