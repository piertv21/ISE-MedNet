package mednet.e2e;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import mednet.env.probe.ProbeRegistry;
import mednet.testsupport.EndState;
import mednet.testsupport.MasTestRunner;
import mednet.testsupport.TestProbe;

// End-to-end check that the preliminary severity code steers the network CNP. The
// severity scenario puts two patients 11 cells from h1 and 12 from h3, while two walk-ins
// hold h1 at one free bed of three. Both pathologies need only general, so the
// specialization penalty is 0 and the distance weight of admission_weights/3 decides:
//   green  h1 = 11*10 + 66.67 = 176.67   h3 = 12*10 +  0 = 120.00   h3 wins
//   red    h1 = 11*30 + 66.67 = 396.67   h3 = 12*30 + 50 = 410.00   h1 wins
// With one weight for every code the red case would score 176.67 against 170 and follow
// the green one to h3, so the split between hospitals comes from the severity term.
@Tag("mas")
class SeverityAwareAdmissionEndToEndTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_severity_cnp.mas2j");
    }

    @AfterAll
    static void tearDown() {
        ProbeRegistry.reset();
    }

    @Test
    void urgencyDecidesBetweenTheNearBusyHospitalAndTheFarEmptyOne() throws Exception {
        PROBE.awaitEvent(e -> e.is("bed_reserved", "h3", "patient1"), Duration.ofSeconds(90));
        PROBE.awaitEvent(e -> e.is("bed_reserved", "h1", "patient2"), Duration.ofSeconds(90));

        PROBE.awaitEvent(e -> e.is("patient_arrived", "h3", "patient1"), Duration.ofSeconds(90));
        PROBE.awaitEvent(e -> e.is("patient_arrived", "h1", "patient2"), Duration.ofSeconds(90));
        PROBE.awaitEvent(e -> e.type().equals("patient_discharged") && e.data().contains("patient1"),
                Duration.ofSeconds(120));
        PROBE.awaitEvent(e -> e.type().equals("patient_discharged") && e.data().contains("patient2"),
                Duration.ofSeconds(120));

        assertThat(PROBE.count(e -> e.is("bed_reserved", "h1", "patient1")))
                .as("the green case was never sent to the busy hospital")
                .isZero();
        assertThat(PROBE.count(e -> e.is("bed_reserved", "h3", "patient2")))
                .as("the red case was never sent to the far hospital")
                .isZero();

        assertThat(PROBE.count(e -> e.type().equals("rbac_denied"))).isZero();
        assertThat(PROBE.count(e -> e.type().equals("equipment_force_released"))).isZero();
        EndState.assertQuiescent(PROBE);
    }
}
