package mednet.model.clock;

/** Callback invoked by the {@link SimulationClock} on every simulation tick. */
@FunctionalInterface
public interface TickListener {
    void onTick(long tick);
}
