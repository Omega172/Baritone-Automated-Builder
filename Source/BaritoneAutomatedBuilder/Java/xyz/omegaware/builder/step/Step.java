package xyz.omegaware.builder.step;

public interface Step {
    enum Result { RUNNING, DONE, FAILED }

    String name();

    default int timeoutTicks() {
        return 20 * 30;
    }

    default void start() {}

    Result tick();

    default void stop() {}
}
