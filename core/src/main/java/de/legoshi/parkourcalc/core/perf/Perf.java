package de.legoshi.parkourcalc.core.perf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Perf {

    public static final class Sample {
        public final String name;
        public long lastNs;
        public long emaNs;
        public long maxNs;
        public int callsThisFrame;
        public int callsLastFrame;

        Sample(String name) {
            this.name = name;
        }
    }

    private static final Map<String, Sample> samples = new HashMap<String, Sample>();
    private static long frameStartNs;
    private static long frameDurationLastNs;
    private static int boxesDrawnThisFrame;
    private static int boxesDrawnLastFrame;

    private Perf() {}

    public static long now() {
        return System.nanoTime();
    }

    public static void stop(String name, long startNs) {
        long elapsed = System.nanoTime() - startNs;
        Sample s = samples.get(name);
        if (s == null) {
            s = new Sample(name);
            samples.put(name, s);
        }
        s.lastNs = elapsed;
        s.emaNs = s.emaNs == 0 ? elapsed : (s.emaNs * 9 + elapsed) / 10;
        if (elapsed > s.maxNs) s.maxNs = elapsed;
        s.callsThisFrame++;
    }

    public static void frame() {
        long now = System.nanoTime();
        if (frameStartNs != 0) {
            frameDurationLastNs = now - frameStartNs;
        }
        frameStartNs = now;
        boxesDrawnLastFrame = boxesDrawnThisFrame;
        boxesDrawnThisFrame = 0;
        for (Sample s : samples.values()) {
            s.callsLastFrame = s.callsThisFrame;
            s.callsThisFrame = 0;
        }
    }

    public static void addBoxes(int n) {
        boxesDrawnThisFrame += n;
    }

    public static int getBoxesLastFrame() {
        return boxesDrawnLastFrame;
    }

    public static long getFrameDurationNs() {
        return frameDurationLastNs;
    }

    public static void resetMax() {
        for (Sample s : samples.values()) {
            s.maxNs = 0;
        }
    }

    public static List<Sample> snapshot() {
        List<Sample> out = new ArrayList<Sample>(samples.values());
        out.sort(new Comparator<Sample>() {
            @Override
            public int compare(Sample a, Sample b) {
                return Long.compare(b.emaNs, a.emaNs);
            }
        });
        return out;
    }
}
