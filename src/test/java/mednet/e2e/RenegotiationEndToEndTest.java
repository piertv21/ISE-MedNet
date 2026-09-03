package mednet.e2e;

import mednet.env.probe.ProbeRegistry;
import mednet.testsupport.MasTestRunner;
import mednet.testsupport.TestProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
    }
}
