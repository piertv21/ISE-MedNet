package mednet.testsupport;

import mednet.env.probe.EnvProbe;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

public final class TestProbe implements EnvProbe {

    public record Event(String type, List<String> data) {

        public boolean is(final String type, final String... dataPrefix) {
            if (!this.type.equals(type) || dataPrefix.length > data.size()) {
                return false;
            }
            for (int i = 0; i < dataPrefix.length; i++) {
                if (!data.get(i).equals(dataPrefix[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();

    @Override
    public void onEvent(final String type, final Object... data) {
        events.add(new Event(type, Arrays.stream(data).map(String::valueOf).toList()));
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public long count(final Predicate<Event> predicate) {
        return events.stream().filter(predicate).count();
    }

    public Optional<Event> first(final Predicate<Event> predicate) {
        return events.stream().filter(predicate).findFirst();
    }

    public Event awaitEvent(final Predicate<Event> predicate, final Duration timeout)
            throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            final Optional<Event> found = first(predicate);
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for event; recorded so far: " + events());
    }

    public int indexOf(final Predicate<Event> predicate) {
        int i = 0;
        for (final Event event : events) {
            if (predicate.test(event)) {
                return i;
            }
            i++;
        }
        return -1;
    }
}
