package mednet.model;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import mednet.model.clock.SimulationClock;

class SimulationClockTest {

    @Test
    void manualTicksNotifyListenersInRegistrationOrder() {
        final SimulationClock clock = new SimulationClock();
        final List<String> calls = new ArrayList<>();
        clock.register(t -> calls.add("first@" + t));
        clock.register(t -> calls.add("second@" + t));
        clock.tick();
        clock.tick();
        assertThat(clock.currentTick()).isEqualTo(2);
        assertThat(calls).containsExactly("first@1", "second@1", "first@2", "second@2");
    }

    @Test
    void aFinishedClockDeliversNoFurtherTick() {
        final SimulationClock clock = new SimulationClock();
        final List<String> calls = new ArrayList<>();
        clock.register(t -> calls.add("tick@" + t));
        clock.tick();
        clock.finish();
        clock.tick();
        clock.start(1);
        assertThat(clock.isFinished()).isTrue();
        assertThat(clock.currentTick()).isEqualTo(1);
        assertThat(calls).containsExactly("tick@1");
    }

    @Test
    void startIsIdempotentAndStopIsSafe() {
        final SimulationClock clock = new SimulationClock();
        clock.stop();
        clock.start(10_000);
        clock.start(10_000);
        clock.stop();
        clock.stop();
        assertThat(clock.currentTick()).isLessThanOrEqualTo(1);
    }
}
