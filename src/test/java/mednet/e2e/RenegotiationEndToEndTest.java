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

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end mid-transport renegotiation: walk-ins steal the bed reserved for a patient
// already in transport. The network CNP must reopen, the ambulance must be rerouted and
// the patient must still be treated.
@Tag("mas")
class RenegotiationEndToEndTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_divert_e2e.mas2j");
    }

    @AfterAll
    static void tearDown() {
        ProbeRegistry.reset();
    }

    @Test
    void divertedPatientIsReroutedAndStillTreated() throws Exception {
        PROBE.awaitEvent(e -> e.is("bed_reserved", "h1", "patient1"), Duration.ofSeconds(60));
        PROBE.awaitEvent(e -> e.is("walk_in", "h1", "patient1"), Duration.ofSeconds(60));
        final TestProbe.Event admitted = PROBE.awaitEvent(
                e -> e.type().equals("bed_reserved") && e.data().contains("patient1")
                        && !e.data().get(0).equals("h1"),
                Duration.ofSeconds(60));
        final String newHospital = admitted.data().get(0);
        assertThat(newHospital).isIn("h2", "h3");

        PROBE.awaitEvent(e -> e.is("patient_arrived", newHospital, "patient1"), Duration.ofSeconds(60));
        PROBE.awaitEvent(e -> e.type().equals("patient_discharged") && e.data().contains("patient1"),
                Duration.ofSeconds(120));

        assertThat(PROBE.count(e -> e.type().equals("rbac_denied"))).isZero();

        EndState.assertQuiescent(PROBE);

        // Every reservation for this patient must have been undone, released by its
        // hospital or stolen by a walk-in, except the last one, the bed the patient was
        // admitted into. Anything else is a leaked reservation.
        final long reservations =
                PROBE.count(e -> e.type().equals("bed_reserved") && e.data().contains("patient1"));
        final long releases = PROBE.count(e -> e.type().equals("bed_released")
                && e.data().contains("patient1"));
        final long walkInSteals =
                PROBE.count(e -> e.type().equals("walk_in") && e.data().contains("patient1"));
        assertThat(releases + walkInSteals)
                .as("reservations for patient1 that were undone")
                .isEqualTo(reservations - 1);
    }
}
