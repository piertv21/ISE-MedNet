package mednet.e2e;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import mednet.env.MedNetEnv;
import mednet.env.probe.ProbeRegistry;
import mednet.testsupport.EndState;
import mednet.testsupport.MasTestRunner;
import mednet.testsupport.TestProbe;

// Full end-to-end simulation: 4 seeded patients, 3 hospitals, the whole agent set,
// headless. Checks that every patient is eventually discharged and that the per-patient
// lifecycle keeps the order asserted below.
@Tag("mas")
class MedNetEndToEndTest {

    private static final TestProbe PROBE = new TestProbe();
    private static final List<String> PATIENTS =
            List.of("patient1", "patient2", "patient3", "patient4");

    @BeforeAll
    static void boot() {
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_e2e.mas2j");
    }

    @AfterAll
    static void tearDown() {
        ProbeRegistry.reset();
    }

    @Test
    void everyPatientCompletesTheFullLifecycleInOrder() throws Exception {
        for (final String patient : PATIENTS) {
            PROBE.awaitEvent(e -> e.type().equals("patient_discharged") && e.data().contains(patient),
                    Duration.ofSeconds(120));
        }

        for (final String patient : PATIENTS) {
            final int activated = PROBE.indexOf(e -> e.type().equals("patient_activated")
                    && e.data().contains(patient));
            final int assessed = PROBE.indexOf(e -> e.type().equals("preliminary_triage")
                    && e.data().contains(patient));
            final int reserved = PROBE.indexOf(e -> e.type().equals("bed_reserved")
                    && e.data().contains(patient));
            final int arrived = PROBE.indexOf(e -> e.type().equals("patient_arrived")
                    && e.data().contains(patient));
            final int enqueued = PROBE.indexOf(e -> e.type().equals("patient_enqueued")
                    && e.data().contains(patient));
            final int examStarted = PROBE.indexOf(e -> e.type().equals("exam_started")
                    && e.data().contains(patient));
            final int discharged = PROBE.indexOf(e -> e.type().equals("patient_discharged")
                    && e.data().contains(patient));

            assertThat(activated).as("%s activated", patient).isNotNegative();
            assertThat(assessed).as("%s assessed on site", patient).isGreaterThan(activated);
            assertThat(reserved).as("%s bed reserved after assessment", patient).isGreaterThan(assessed);
            assertThat(arrived).as("%s arrived after reservation", patient).isGreaterThan(reserved);
            assertThat(enqueued).as("%s enqueued after arrival", patient).isGreaterThan(arrived);
            assertThat(examStarted).as("%s examined after triage", patient).isGreaterThan(enqueued);
            assertThat(discharged).as("%s discharged last", patient).isGreaterThan(examStarted);
        }

        // An RBAC denial means an agent attempted an action outside its role; a forced
        // release means a lock was leaked instead of being handed back.
        assertThat(PROBE.count(e -> e.type().equals("rbac_denied"))).isZero();
        assertThat(PROBE.count(e -> e.type().equals("equipment_force_released"))).isZero();

        final MedNetEnv env = MedNetEnv.getInstance();
        assertThat(env).isNotNull();
        for (final String h : List.of("h1", "h2", "h3")) {
            final int free = env.hospital(h).bedsFree();
            assertThat(free).isBetween(0, env.hospital(h).bedsCapacity());
        }

        EndState.assertQuiescent(PROBE);
    }
}
