package de.legoshi.parkourcalc.sim;

import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.BlockHit;
import de.legoshi.parkourcalc.core.sim.CollisionRayTrace;
import de.legoshi.parkourcalc.core.sim.Face;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CollisionRayTraceFuzzTest {

    @Test
    public void walkMatchesBruteForceOverAllBoxes() {
        Random rng = new Random(7);
        int mismatches = 0;
        StringBuilder report = new StringBuilder();
        for (int scene = 0; scene < 300; scene++) {
            final Map<List<Integer>, AABB> blocks = new HashMap<>();
            int n = 1 + rng.nextInt(12);
            for (int i = 0; i < n; i++) {
                int x = rng.nextInt(8), y = 64 + rng.nextInt(3), z = rng.nextInt(8);
                blocks.put(Arrays.asList(x, y, z), new AABB(new Vec3dCore(x, y, z), new Vec3dCore(x + 1, y + 1, z + 1)));
            }
            CollisionRayTrace.World world = new CollisionRayTrace.World() {
                @Override
                public List<AABB> collisionBoxes(int x, int y, int z) {
                    AABB b = blocks.get(Arrays.asList(x, y, z));
                    return b == null ? new ArrayList<AABB>() : Arrays.asList(b);
                }

                @Override
                public boolean pickWithoutCollision(int x, int y, int z) {
                    return false;
                }
            };
            for (int ray = 0; ray < 50; ray++) {
                Vec3dCore o = new Vec3dCore(-3 + rng.nextDouble() * 14, 63 + rng.nextDouble() * 6, -3 + rng.nextDouble() * 14);
                if (blocks.containsKey(Arrays.asList((int) Math.floor(o.x), (int) Math.floor(o.y), (int) Math.floor(o.z)))) continue;
                double yaw = rng.nextDouble() * Math.PI * 2, pitch = (rng.nextDouble() - 0.5) * Math.PI * 0.9;
                Vec3dCore d = new Vec3dCore(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
                BlockHit got = CollisionRayTrace.trace(world, o, d, 64.0);
                Object[] want = brute(blocks, o, d);
                String g = got == null ? "null" : got.x + "," + got.y + "," + got.z + " " + got.face;
                String w = want == null ? "null" : want[0] + " " + want[1];
                if (!g.equals(w)) {
                    mismatches++;
                    if (report.length() < 3000) {
                        report.append("o=").append(o).append(" d=").append(d).append(" got=").append(g).append(" want=").append(w).append('\n');
                    }
                }
            }
        }
        if (mismatches > 0) fail(mismatches + " mismatches\n" + report);
        assertEquals(0, mismatches);
    }

    private static Object[] brute(Map<List<Integer>, AABB> blocks, Vec3dCore o, Vec3dCore d) {
        double bestT = 64.0;
        Object[] best = null;
        for (Map.Entry<List<Integer>, AABB> e : blocks.entrySet()) {
            AABB b = e.getValue();
            double tNear = Double.NEGATIVE_INFINITY, tFar = Double.POSITIVE_INFINITY;
            Face face = null;
            double[] oa = {o.x, o.y, o.z}, da = {d.x, d.y, d.z}, lo = {b.min.x, b.min.y, b.min.z}, hi = {b.max.x, b.max.y, b.max.z};
            Face[] neg = {Face.NEG_X, Face.NEG_Y, Face.NEG_Z}, pos = {Face.POS_X, Face.POS_Y, Face.POS_Z};
            boolean miss = false;
            for (int a = 0; a < 3; a++) {
                double t1 = (lo[a] - oa[a]) / da[a], t2 = (hi[a] - oa[a]) / da[a];
                double te = Math.min(t1, t2), tx = Math.max(t1, t2);
                if (te > tNear) { tNear = te; face = da[a] > 0 ? neg[a] : pos[a]; }
                tFar = Math.min(tFar, tx);
                if (tNear > tFar) { miss = true; break; }
            }
            if (miss || tNear < 0 || tNear >= bestT) continue;
            bestT = tNear;
            List<Integer> k = e.getKey();
            best = new Object[] {k.get(0) + "," + k.get(1) + "," + k.get(2), face};
        }
        return best;
    }
}
