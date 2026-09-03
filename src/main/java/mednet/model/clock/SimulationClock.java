package mednet.model.clock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global discrete simulation clock. In production it is driven by a single-thread
 * scheduler; in tests {@link #tick()} can be called manually for full determinism.
 */
public final class SimulationClock {

    private final List<TickListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong tick = new AtomicLong(0);
    private ScheduledExecutorService scheduler;
    private volatile boolean finished;

    public void register(final TickListener listener) {
        listeners.add(listener);
    }

    /**
     * Advances the clock by one tick and notifies every listener, in registration
     * order. Once the clock is {@link #finish() finished} this is a no-op.
     */
    public synchronized void tick() {
        if (finished) {
            return;
        }
        final long now = tick.incrementAndGet();
        for (final TickListener listener : listeners) {
            listener.onTick(now);
        }
    }

    public long currentTick() {
        return tick.get();
    }

    public synchronized void start(final long periodMs) {
        if (scheduler != null || finished) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "mednet-clock");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Ends the run for good: no further tick is delivered, neither by the scheduler
     * nor by a manual {@link #tick()}, and the clock can no longer be restarted, so
     * the world keeps its final state. Idempotent, and safe to call from inside a
     * tick — the scheduler is shut down gracefully, letting the current tick finish.
     */
    public synchronized void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /** Whether the run is over and the world is frozen on its final state. */
    public boolean isFinished() {
        return finished;
    }
}
