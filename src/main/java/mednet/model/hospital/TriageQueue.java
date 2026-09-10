package mednet.model.hospital;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mednet.model.patient.SeverityCode;

// The ED priority queue, ordered by severity priority then arrival sequence, both
// ascending. A patient gets an arrival sequence once and keeps it, so a patient requeued
// after a preemption returns to the head of its priority class instead of the tail.
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
