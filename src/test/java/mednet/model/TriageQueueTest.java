package mednet.model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import mednet.model.hospital.TriageEntry;
import mednet.model.hospital.TriageQueue;
import mednet.model.patient.SeverityCode;

class TriageQueueTest {

    @Test
    void ordersBySeverityThenArrival() {
        final TriageQueue queue = new TriageQueue();
        queue.enqueue("green1", SeverityCode.GREEN, 1);
        queue.enqueue("red1", SeverityCode.RED, 2);
        queue.enqueue("yellow1", SeverityCode.YELLOW, 3);
        queue.enqueue("green2", SeverityCode.GREEN, 4);
        assertThat(queue.snapshot().stream().map(TriageEntry::patient))
                .containsExactly("red1", "yellow1", "green1", "green2");
    }

    // A preempted patient re-enters ahead of everyone who queued while it was in
    // treatment, because the queue keeps the arrival sequence from the first entry.
    @Test
    void requeueFrontKeepsHeadOfItsPriorityClass() {
        final TriageQueue queue = new TriageQueue();
        queue.enqueue("victim", SeverityCode.YELLOW, 1);
        queue.remove("victim");
        queue.enqueue("later1", SeverityCode.YELLOW, 5);
        queue.enqueue("later2", SeverityCode.YELLOW, 6);
        queue.enqueue("victim", SeverityCode.YELLOW, 10);
        assertThat(queue.snapshot().stream().map(TriageEntry::patient))
                .containsExactly("victim", "later1", "later2");
    }

    @Test
    void requeuedPatientStaysBehindMoreUrgentCodes() {
        final TriageQueue queue = new TriageQueue();
        queue.enqueue("victim", SeverityCode.YELLOW, 1);
        queue.remove("victim");
        queue.enqueue("critical", SeverityCode.RED, 5);
        queue.enqueue("victim", SeverityCode.YELLOW, 10);
        assertThat(queue.snapshot().get(0).patient()).isEqualTo("critical");
    }

    @Test
    void removeReturnsEntryAndEmptiesQueue() {
        final TriageQueue queue = new TriageQueue();
        queue.enqueue("p", SeverityCode.WHITE, 1);
        assertThat(queue.remove("p")).isPresent();
        assertThat(queue.remove("p")).isEmpty();
        assertThat(queue.snapshot()).isEmpty();
    }

    @Test
    void reEnqueueDoesNotDuplicate() {
        final TriageQueue queue = new TriageQueue();
        queue.enqueue("p", SeverityCode.GREEN, 1);
        queue.enqueue("p", SeverityCode.YELLOW, 2);
        final List<TriageEntry> snapshot = queue.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).code()).isEqualTo(SeverityCode.YELLOW);
    }
}
