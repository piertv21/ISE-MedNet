package mednet.cnp;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import mednet.env.probe.ProbeRegistry;
import mednet.testsupport.MasTestRunner;
import mednet.testsupport.TestProbe;
import mednet.testsupport.TestSupportEnv;

@Tag("mas")
class PreemptionMockedTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        TestSupportEnv.programTriage("patient1", "yellow");
        TestSupportEnv.programTriage("patient2", "red");
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_preemption.mas2j");
    }

    @AfterAll
    static void tearDown() {
        ProbeRegistry.reset();
        TestSupportEnv.resetProgram();
    }

    private static boolean report(final TestProbe.Event event, final String prefix) {
        return event.type().equals("action") && event.data().size() >= 3
                && event.data().get(1).equals("report") && event.data().get(2).startsWith(prefix);
    }

    private static boolean nurseAction(final TestProbe.Event event, final String functor,
            final String firstArg) {
        return event.type().equals("action") && event.data().size() >= 3
                && event.data().get(0).equals("triage_nurse_h1")
                && event.data().get(1).equals(functor) && event.data().get(2).equals(firstArg);
    }

    @Test
    void redCodePreemptsTheYellowCaseAndTheVictimIsRequeuedFront() throws Exception {
        PROBE.awaitEvent(e -> report(e, "treatment_assigned(doctor_h1_general,patient1"),
                Duration.ofSeconds(30));
        final var preempt = PROBE.awaitEvent(e -> report(e, "preempt_received(doctor_h1_general"),
                Duration.ofSeconds(30));
        assertThat(preempt.data().get(2)).contains("patient2").contains("patient1");
        PROBE.awaitEvent(e -> nurseAction(e, "requeue_front", "patient1"), Duration.ofSeconds(30));
        final int assigned = PROBE.indexOf(e -> report(e, "treatment_assigned(doctor_h1_general,patient1"));
        final int preempted = PROBE.indexOf(e -> report(e, "preempt_received("));
        final int requeued = PROBE.indexOf(e -> nurseAction(e, "requeue_front", "patient1"));
        assertThat(assigned).isLessThan(preempted);
        assertThat(preempted).isLessThan(requeued);
    }
}
