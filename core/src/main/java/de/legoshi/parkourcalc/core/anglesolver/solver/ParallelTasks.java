package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public final class ParallelTasks {

    public interface Task<T> {
        T run(int index, AtomicBoolean cancel);
    }

    private ParallelTasks() {
    }

    public static <T> List<T> run(int count, AtomicBoolean cancel, boolean inTurn, Task<T> task) {
        SolveClock clock = SolveClock.current();
        SolveClock[] forks = clock == null ? null : new SolveClock[count];
        AtomicBoolean[] tokens = new AtomicBoolean[count];
        long stop = clock == null ? Long.MAX_VALUE : clock.deadlineOf(cancel);
        for (int i = 0; i < count; i++) {
            tokens[i] = cancel;
            if (clock != null) {
                tokens[i] = new AtomicBoolean(cancel != null && cancel.get());
                forks[i] = clock.fork(tokens[i], stop);
            }
        }
        List<T> out = new ArrayList<T>(count);
        for (int i = 0; i < count; i++) out.add(null);
        RuntimeException[] errors = new RuntimeException[count];
        boolean serial = inTurn || count <= 1 || (clock != null && clock.workers() == 1);
        IntStream indices = IntStream.range(0, count);
        if (!serial) indices = indices.parallel();
        indices.forEach(i -> {
            SolveClock previous = forks == null ? null : forks[i].enter();
            try {
                T value = task.run(i, tokens[i]);
                synchronized (out) {
                    out.set(i, value);
                }
            } catch (RuntimeException e) {
                errors[i] = e;
            } finally {
                if (forks != null) SolveClock.restore(previous);
            }
        });
        if (clock != null) clock.join(forks);
        for (RuntimeException e : errors) {
            if (e != null) throw e;
        }
        return out;
    }
}
