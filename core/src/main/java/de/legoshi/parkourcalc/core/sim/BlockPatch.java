package de.legoshi.parkourcalc.core.sim;

public final class BlockPatch {

    public final int x;
    public final int y;
    public final int z;
    public final String block;

    public BlockPatch(int x, int y, int z, String block) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block;
    }

    public long posKey() {
        return posKey(x, y, z);
    }

    public static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z & 0x3FFFFFF);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlockPatch)) return false;
        BlockPatch p = (BlockPatch) o;
        return x == p.x && y == p.y && z == p.z && block.equals(p.block);
    }

    @Override
    public int hashCode() {
        int h = x;
        h = h * 31 + y;
        h = h * 31 + z;
        return h * 31 + block.hashCode();
    }

    @Override
    public String toString() {
        return block + "@" + x + "," + y + "," + z;
    }
}
