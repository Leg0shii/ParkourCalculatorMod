package de.legoshi.parkourcalc.core;

import java.util.List;

/** Static debug flags. Flip in code to enable verbose state dumps. */
public final class DebugFlags {

    public static boolean DUMP_TICK_STATE = false;

    public static List<String> simTickSink = null;

    private DebugFlags() {}
}
