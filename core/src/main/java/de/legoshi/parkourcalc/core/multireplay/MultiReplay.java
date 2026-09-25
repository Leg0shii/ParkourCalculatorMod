package de.legoshi.parkourcalc.core.multireplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MultiReplay {

    public static final float MIN_RADIUS = 0.02f;
    public static final float MAX_RADIUS = 0.5f;
    public static final float DEFAULT_RADIUS = 0.08f;
    public static final float MIN_LINE_WIDTH = 1f;
    public static final float MAX_LINE_WIDTH = 8f;
    public static final float DEFAULT_LINE_WIDTH = 2.5f;

    private final List<ReplayTrack> tracks = new ArrayList<ReplayTrack>();
    private final List<String> errors = new ArrayList<String>();
    private final ReplayClock clock = new ReplayClock();
    private String folder = "";
    private float sphereRadius = DEFAULT_RADIUS;
    private float lineWidth = DEFAULT_LINE_WIDTH;
    private boolean showUpcoming;
    private boolean throughBlocks = true;
    private boolean showLegend = true;
    private boolean playerModels;
    private long geometryRev;

    public void replaceTracks(String folder, List<ReplayTrack> newTracks, List<String> newErrors) {
        tracks.clear();
        tracks.addAll(newTracks);
        errors.clear();
        errors.addAll(newErrors);
        this.folder = folder == null ? "" : folder;
        int last = 0;
        for (ReplayTrack t : tracks) last = Math.max(last, t.lastTick());
        clock.pause();
        clock.setLastTick(last);
        clock.restart();
        geometryRev++;
    }

    public void clear() {
        tracks.clear();
        errors.clear();
        clock.pause();
        clock.setLastTick(0);
        clock.restart();
        geometryRev++;
    }

    public long geometryRev() {
        return geometryRev;
    }

    public List<ReplayTrack> tracks() {
        return Collections.unmodifiableList(tracks);
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    public ReplayClock clock() {
        return clock;
    }

    public void advance(long nowNanos) {
        clock.advance(nowNanos);
    }

    public String folder() {
        return folder;
    }

    public float sphereRadius() {
        return sphereRadius;
    }

    public void setSphereRadius(float r) {
        float clamped = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
        if (clamped != sphereRadius) geometryRev++;
        sphereRadius = clamped;
    }

    public float lineWidth() {
        return lineWidth;
    }

    public void setLineWidth(float w) {
        lineWidth = Math.max(MIN_LINE_WIDTH, Math.min(MAX_LINE_WIDTH, w));
    }

    public boolean isShowUpcoming() {
        return showUpcoming;
    }

    public void setShowUpcoming(boolean v) {
        showUpcoming = v;
    }

    public boolean isThroughBlocks() {
        return throughBlocks;
    }

    public void setThroughBlocks(boolean v) {
        throughBlocks = v;
    }

    public boolean isShowLegend() {
        return showLegend;
    }

    public void setShowLegend(boolean v) {
        showLegend = v;
    }

    public boolean isPlayerModels() {
        return playerModels;
    }

    public void setPlayerModels(boolean v) {
        playerModels = v;
    }
}
