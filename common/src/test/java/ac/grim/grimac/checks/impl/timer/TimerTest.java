package ac.grim.grimac.checks.impl.timer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerTest {

    @Test
    void movementClockIncrementKeepsDefaultTimerStrict() {
        assertEquals(50_000_000L, TimerBalance.movementClockIncrement(1.0));
    }

    @Test
    void movementClockIncrementAllowsConfiguredTimerMultiplier() {
        assertEquals(44_642_857L, TimerBalance.movementClockIncrement(1.12));
    }

    @Test
    void movementClockIncrementDoesNotMakeTimerStricter() {
        assertEquals(50_000_000L, TimerBalance.movementClockIncrement(0.5));
    }
}
