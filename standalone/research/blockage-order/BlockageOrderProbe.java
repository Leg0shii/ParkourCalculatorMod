import java.util.Arrays;
import net.minecraft.util.AxisAlignedBB;

public final class BlockageOrderProbe {
    private static final double HALF = (double) (0.6F / 2.0F);
    private static final double HEIGHT = (double) 1.8F;
    private static final String[] AXES = {"X", "Y", "Z"};
    private static final int[][] PAIRS = {{0, 2}, {1, 0}, {1, 2}, {2, 0}, {0, 1}, {2, 1}};
    private static final AxisAlignedBB SOLID = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

    private static String blockedAxis(double[] start, double[] end) {
        AxisAlignedBB body = new AxisAlignedBB(start[0] - HALF, start[1], start[2] - HALF,
                start[0] + HALF, start[1] + HEIGHT, start[2] + HALF);
        double dy = end[1] - start[1];
        double y = SOLID.calculateYOffset(body, dy);
        body = body.offset(0, y, 0);
        double dx = end[0] - start[0];
        double x = SOLID.calculateXOffset(body, dx);
        body = body.offset(x, 0, 0);
        double dz = end[2] - start[2];
        double z = SOLID.calculateZOffset(body, dz);
        return y != dy ? "Y" : x != dx ? "X" : z != dz ? "Z" : "none";
    }

    private static int order(int axis) {
        return axis == 1 ? 0 : axis == 0 ? 1 : 2;
    }

    private static double outside(int axis, int sign) {
        if (sign > 0) return 1 + (axis == 1 ? 0 : HALF) + .2;
        return -(axis == 1 ? HEIGHT : HALF) - .2;
    }

    public static void main(String[] args) {
        System.out.println("{\"profile\":\"minecraft-1.8.9\",\"halfWidth\":" + HALF
                + ",\"height\":" + HEIGHT + ",\"cases\":[");
        int count = 0;
        for (int[] pair : PAIRS) {
            for (int firstSign : new int[]{-1, 1}) {
                for (int secondSign : new int[]{-1, 1}) {
                    int first = pair[0], second = pair[1];
                    double[] start = {.5, .5, .5};
                    double[] end = start.clone();
                    start[first] = outside(first, firstSign);
                    end[second] = outside(second, secondSign);
                    String clipped = blockedAxis(start, end);
                    boolean sameState = order(first) < order(second);
                    if (sameState == "none".equals(clipped)) throw new AssertionError("Wrong edge timing");
                    double[] corner = start.clone();
                    corner[second] = end[second];
                    if (!"none".equals(blockedAxis(start, corner)) || !"none".equals(blockedAxis(corner, end))) {
                        throw new AssertionError("Outside-both control collided");
                    }
                    System.out.println((count++ == 0 ? "" : ",") + "{\"type\":\"" + AXES[first] + "->" + AXES[second]
                            + "\",\"firstSign\":" + firstSign + ",\"secondSign\":" + secondSign
                            + ",\"timing\":\"" + (sameState ? "same_state" : "consecutive_states")
                            + "\",\"start\":" + Arrays.toString(start) + ",\"end\":" + Arrays.toString(end)
                            + ",\"directClippedAxis\":\"" + clipped + "\",\"outsideBothControlClips\":false}");
                }
            }
        }
        System.out.println("],\"passed\":" + count + "}");
    }
}
