package de.legoshi.parkourcalc.core.anglesolver.runticks;

public interface RunTicksControls {

    RunTicksControls NONE = new RunTicksControls() {
        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public void start() {
        }

        @Override
        public void cancel() {
        }

        @Override
        public int liveTimeoutMs() {
            return RunTicksSettings.DEFAULT_TIMEOUT_MS;
        }
    };

    boolean isRunning();

    void start();

    void cancel();

    int liveTimeoutMs();
}
