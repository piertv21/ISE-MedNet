package mednet.model.clock;

@FunctionalInterface
public interface TickListener {
    void onTick(long tick);
}
