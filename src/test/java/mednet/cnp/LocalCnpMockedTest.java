package mednet.cnp;

import mednet.env.probe.ProbeRegistry;
import mednet.testsupport.MasTestRunner;
import mednet.testsupport.TestProbe;
import mednet.testsupport.TestSupportEnv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mas")
class LocalCnpMockedTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        TestSupportEnv.programTriage("patient1", "red");
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_local_cnp.mas2j");
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

    private static boolean nurseAction(final TestProbe.Event event, final String functor) {
        return event.type().equals("action") && event.data().size() >= 2
                && event.data().get(0).equals("triage_nurse_h1") && event.data().get(1).equals(functor);
    }

    @Test
    void specializedDoctorWinsAndCompletionIsHandled() throws Exception {
        PROBE.awaitEvent(e -> nurseAction(e, "secondary_triage"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> nurseAction(e, "enqueue_patient"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "treatment_assigned(doctor_h1_neurology"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "treatment_rejected(doctor_h1_general"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> nurseAction(e, "dequeue_patient"), Duration.ofSeconds(30));
        assertThat(PROBE.count(e -> report(e, "treatment_assigned("))).isEqualTo(1);
    }
}
