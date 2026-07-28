package de.legoshi.parkourcalc.anglesolver.metriclab;

import com.google.gson.Gson;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class SampleMeta {

    public String subTier;
    public String jumpClass;
    public String rung;
    public String notes;

    public static SampleMeta loadFor(File capture) {
        String path = capture.getPath();
        File meta = new File(path.substring(0, path.length() - ".json".length()) + ".meta.json");
        if (!meta.isFile()) {
            return new SampleMeta();
        }
        try {
            String json = new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8);
            SampleMeta parsed = new Gson().fromJson(json, SampleMeta.class);
            return parsed != null ? parsed : new SampleMeta();
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + meta, e);
        }
    }
}
