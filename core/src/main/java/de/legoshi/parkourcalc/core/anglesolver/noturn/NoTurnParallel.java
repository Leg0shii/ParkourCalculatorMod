package de.legoshi.parkourcalc.core.anglesolver.noturn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class NoTurnParallel {

    private static final long POLL_MS = 40L;

    private NoTurnParallel() {
    }

    interface IndexedTask<T> {
        T run(int index, AtomicBoolean taskCancel);
    }

    static int resolveThreads(int cfgThreads) {
        if (cfgThreads > 0) return cfgThreads;
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }

    static <T> T firstNonNull(ExecutorService pool, int count, AtomicBoolean global, IndexedTask<T> task) {
        if (count <= 0) return null;
        List<AtomicBoolean> cancels = new ArrayList<>(count);
        List<Future<T>> futures = submit(pool, count, global, cancels, task);
        T winner = null;
        int winnerIdx = count;
        for (int i = 0; i < count; i++) {
            T r = await(futures.get(i), global, cancels, futures);
            if (r != null) {
                winner = r;
                winnerIdx = i;
                break;
            }
        }
        cancelFrom(cancels, futures, winnerIdx + 1);
        return winner;
    }

    static <T> List<T> collectAll(ExecutorService pool, int count, AtomicBoolean global, IndexedTask<T> task) {
        List<T> out = new ArrayList<>(count);
        if (count <= 0) return out;
        List<AtomicBoolean> cancels = new ArrayList<>(count);
        List<Future<T>> futures = submit(pool, count, global, cancels, task);
        for (int i = 0; i < count; i++) out.add(await(futures.get(i), global, cancels, futures));
        return out;
    }

    private static <T> List<Future<T>> submit(ExecutorService pool, int count, AtomicBoolean global,
                                              List<AtomicBoolean> cancels, IndexedTask<T> task) {
        List<Future<T>> futures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final int idx = i;
            final AtomicBoolean tc = new AtomicBoolean(false);
            cancels.add(tc);
            futures.add(pool.submit(() -> {
                if (global != null && global.get()) return null;
                return task.run(idx, tc);
            }));
        }
        return futures;
    }

    private static <T> T await(Future<T> f, AtomicBoolean global, List<AtomicBoolean> cancels,
                               List<Future<T>> futures) {
        while (true) {
            if (global != null && global.get()) {
                cancelFrom(cancels, futures, 0);
                return null;
            }
            try {
                return f.get(POLL_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                continue;
            } catch (CancellationException | InterruptedException e) {
                return null;
            } catch (ExecutionException e) {
                return null;
            }
        }
    }

    private static <T> void cancelFrom(List<AtomicBoolean> cancels, List<Future<T>> futures, int from) {
        for (int j = from; j < cancels.size(); j++) {
            cancels.get(j).set(true);
            futures.get(j).cancel(false);
        }
    }
}
