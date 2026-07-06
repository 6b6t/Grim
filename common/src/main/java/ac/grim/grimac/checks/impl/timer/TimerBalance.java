package ac.grim.grimac.checks.impl.timer;

final class TimerBalance {
    private static final double VANILLA_TICK_NANOS = 50e6;

    private TimerBalance() {
    }

    static long movementClockIncrement(double allowedTimerMultiplier) {
        return Math.max(1L, Math.round(VANILLA_TICK_NANOS / Math.max(1.0, allowedTimerMultiplier)));
    }
}
