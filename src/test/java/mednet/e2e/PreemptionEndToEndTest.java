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

        final List<TestProbe.Event> plans = PROBE.events().stream()
                .filter(e -> e.type().equals("care_plan") && e.data().get(0).equals("patient1"))
                .toList();
        assertThat(plans).hasSize(2);

        final int firstPlanSteps = Integer.parseInt(plans.get(0).data().get(3));
        final int replanSteps = Integer.parseInt(plans.get(1).data().get(3));
        assertThat(replanSteps).isLessThan(firstPlanSteps);
    }
}
