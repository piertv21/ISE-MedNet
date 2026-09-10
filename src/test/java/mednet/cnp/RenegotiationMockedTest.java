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

// Mid-transport renegotiation: the winner loses its bed, asks for a divert and the CNP
// reopens with that hospital excluded, so it cannot win the same call twice.
@Tag("mas")
class RenegotiationMockedTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_renegotiation.mas2j");
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

    @Test
    void divertReopensTheCnpExcludingTheSaturatedHospital() throws Exception {
        PROBE.awaitEvent(e -> report(e, "cnp_won(hospital_h1"), Duration.ofSeconds(30));
        PROBE.awaitEvent(
                e -> report(e, "transport_ordered(ambulance_a1") && e.data().get(2).contains("hospital_h1"),
                Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "divert_sent(hospital_h1"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "cnp_won(hospital_h2"), Duration.ofSeconds(30));
        PROBE.awaitEvent(
                e -> report(e, "transport_ordered(ambulance_a1") && e.data().get(2).contains("hospital_h2"),
                Duration.ofSeconds(30));

        final int divert = PROBE.indexOf(e -> report(e, "divert_sent("));
        final int secondAward = PROBE.indexOf(e -> report(e, "cnp_won(hospital_h2"));
        assertThat(divert).isLessThan(secondAward);
        assertThat(PROBE.count(e -> report(e, "cnp_won(hospital_h1"))).isEqualTo(1);
    }
}
