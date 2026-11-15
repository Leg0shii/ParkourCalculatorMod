package de.legoshi.parkourcalc.core.ui;

public final class WorldPick {

    public enum Kind {
        BOX,
        CONSTRAINT,
        BLOCK_OVERRIDE
    }

    public final Kind kind;
    public final int index;
    public final int[] constraintIndices;

    private WorldPick(Kind kind, int index, int[] constraintIndices) {
        this.kind = kind;
        this.index = index;
        this.constraintIndices = constraintIndices;
    }

    public static WorldPick box(int boxIndex) {
        return new WorldPick(Kind.BOX, boxIndex, null);
    }

    public static WorldPick blockOverride(int row) {
        return new WorldPick(Kind.BLOCK_OVERRIDE, row, null);
    }

    public static WorldPick constraint(int tick, int[] constraintIndices) {
        return new WorldPick(Kind.CONSTRAINT, tick, constraintIndices);
    }
}
