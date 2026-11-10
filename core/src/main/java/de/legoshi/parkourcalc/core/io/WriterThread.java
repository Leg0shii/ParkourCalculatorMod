package de.legoshi.parkourcalc.core.io;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class WriterThread {

    private WriterThread() {
    }

    public static ExecutorService create(String threadName, String flushHookName) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            try {
                executor.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }, flushHookName));
        return executor;
    }

    public static void drain(ExecutorService executor, long seconds) {
        if (executor == null) return;
        try {
            executor.submit(() -> { }).get(seconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
