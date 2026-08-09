package de.legoshi.parkourcalc.core.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class HudMessages {

    public static final int COLOR_DEFAULT = 0;

    public static final long DISPLAY_NANOS = 4_000_000_000L;
    public static final long FADE_NANOS = 700_000_000L;

    public static final class Entry {
        public final String text;
        public final int colorArgb;
        public int count = 1;
        public long shownNanos;

        Entry(String text, int colorArgb, long nowNanos) {
            this.text = text;
            this.colorArgb = colorArgb;
            this.shownNanos = nowNanos;
        }

        public String display() {
            return count > 1 ? text + " (" + count + "x)" : text;
        }

        public float alphaAt(long nowNanos) {
            long left = DISPLAY_NANOS - (nowNanos - shownNanos);
            if (left <= 0) return 0f;
            if (left >= FADE_NANOS) return 1f;
            return (float) left / FADE_NANOS;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private String statusText;
    private int statusColor;

    public void setStatus(String text, int colorArgb) {
        statusText = text;
        statusColor = colorArgb;
    }

    public void clearStatus() {
        statusText = null;
    }

    public String getStatusText() {
        return statusText;
    }

    public int getStatusColor() {
        return statusColor;
    }

    public void push(String text, int colorArgb, long nowNanos) {
        if (text == null || text.isEmpty()) return;
        if (!entries.isEmpty()) {
            Entry newest = entries.get(0);
            if (newest.text.equals(text) && newest.colorArgb == colorArgb) {
                newest.count++;
                newest.shownNanos = nowNanos;
                return;
            }
        }
        entries.add(0, new Entry(text, colorArgb, nowNanos));
    }

    public List<Entry> visible(long nowNanos, int maxCount) {
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            if (nowNanos - it.next().shownNanos >= DISPLAY_NANOS) it.remove();
        }
        while (entries.size() > Math.max(1, maxCount)) {
            entries.remove(entries.size() - 1);
        }
        return entries;
    }
}
