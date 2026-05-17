package de.legoshi.parkourcalc.core.save;

import java.util.ArrayList;
import java.util.List;

public final class SaveFile {

    public static final int FORMAT_VERSION = 1;

    public int version;
    public String createdAt;
    public String modVersion;
    public String mcVersion;
    public World world;
    public Start start;
    public List<Row> rows = new ArrayList<Row>();

    public static final class World {
        public String dimension;
        public String worldName;
        public String serverAddress;
    }

    public static final class Start {
        public double[] pos;
        public double[] vel;
        public float yaw;
    }

    public static final class Row {
        public List<String> keys = new ArrayList<String>();
        public Float yaw;
    }
}
