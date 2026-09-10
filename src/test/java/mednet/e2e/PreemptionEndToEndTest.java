package mednet.e2e;

import mednet.env.probe.ProbeRegistry;
import mednet.testsupport.EndState;
import mednet.testsupport.MasTestRunner;
import mednet.testsupport.TestProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end preemption: one hospital, one general doctor. A yellow fracture is under
// treatment when a red cardiac arrest arrives. The red case must preempt it with the
// equipment released and no forced lease recovery, and both patients must be discharged.
@Tag("mas")
class PreemptionEndToEndTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_preemption_e2e.mas2j");
    }

    @AfterAll
    static void tearDown() {
        ProbeRegistry.reset();
    }

    @Test
    void redCasePreemptsAndBothPatientsAreEventuallyDischarged() throws Exception {
        PROBE.awaitEvent(e -> e.type().equals("treatment_aborted") && e.data().contains("patient1"),
                Duration.ofSeconds(90));
        PROBE.awaitEvent(e -> e.type().equals("patient_requeued") && e.data().contains("patient1"),
                Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> e.type().equals("patient_discharged") && e.data().contains("patient2"),
                Duration.ofSeconds(90));
        PROBE.awaitEvent(e -> e.type().equals("patient_discharged") && e.data().contains("patient1"),
                Duration.ofSeconds(120));

        final int redDischarged =
                PROBE.indexOf(e -> e.type().equals("patient_discharged") && e.data().contains("patient2"));
        final int victimDischarged =
                PROBE.indexOf(e -> e.type().equals("patient_discharged") && e.data().contains("patient1"));
        assertThat(redDischarged).isLessThan(victimDischarged);

        assertThat(PROBE.count(e -> e.type().equals("equipment_force_released"))).isZero();
        assertThat(PROBE.count(e -> e.type().equals("rbac_denied"))).isZero();

        EndState.assertQuiescent(PROBE);
    }

    @Test
    void theInterruptedCaseIsReplannedInsteadOfRestarted() throws Exception {
        PROBE.awaitEvent(e -> e.type().equals("patient_discharged") && e.data().contains("patient1"),
                Duration.ofSeconds(120));

        // The victim is planned twice: once when first taken in charge, once when resumed.
        final List<TestProbe.Event> plans = PROBE.events().stream()
                .filter(e -> e.type().equals("care_plan") && e.data().get(0).equals("patient1"))
                .toList();
        assertThat(plans).hasSize(2);

        // The second plan is strictly shorter: the exams already performed are perceived
        // and the planner skips them.
        final int firstPlanSteps = Integer.parseInt(plans.get(0).data().get(3));
        final int replanSteps = Integer.parseInt(plans.get(1).data().get(3));
        assertThat(replanSteps).isLessThan(firstPlanSteps);
    }
}
