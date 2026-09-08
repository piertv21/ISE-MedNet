package mednet.model.hospital;

import mednet.model.patient.SeverityCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TriageQueue {

    private final List<TriageEntry> entries = new ArrayList<>();
    private final Map<String, Long> arrivalSeqs = new HashMap<>();
    private long nextSeq;

    public synchronized void enqueue(final String patient, final SeverityCode code, final long nowTick) {
        entries.removeIf(e -> e.patient().equals(patient));
        final long seq = arrivalSeqs.computeIfAbsent(patient, p -> nextSeq++);
        entries.add(new TriageEntry(patient, code, seq, nowTick));
        entries.sort(Comparator.comparingInt((TriageEntry e) -> e.code().priority())
                .thenComparingLong(TriageEntry::arrivalSeq));
    }

    public synchronized void requeueFront(final String patient, final SeverityCode code, final long nowTick) {
        enqueue(patient, code, nowTick);
    }

    public synchronized Optional<TriageEntry> remove(final String patient) {
        final Optional<TriageEntry> found =
                entries.stream().filter(e -> e.patient().equals(patient)).findFirst();
        found.ifPresent(entries::remove);
        return found;
    }

    public synchronized List<TriageEntry> snapshot() {
        return List.copyOf(entries);
    }
}
