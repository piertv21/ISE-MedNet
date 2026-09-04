package mednet.model.clock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class SimulationClock {

    private final List<TickListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong tick = new AtomicLong(0);
    private ScheduledExecutorService scheduler;
    private volatile boolean finished;

    public void register(final TickListener listener) {
        listeners.add(listener);
    }

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

    public boolean isFinished() {
        return finished;
    }
}
