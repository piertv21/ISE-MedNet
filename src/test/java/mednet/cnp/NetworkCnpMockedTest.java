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

// Network CNP against mock hospitals: lowest bid wins, losers get an explicit reject, a
// refusal is recorded, and the transport order goes to the winner.
@Tag("mas")
class NetworkCnpMockedTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_network_cnp.mas2j");
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
    void lowestBidWinsLosersRejectedRefusalRecorded() throws Exception {
        PROBE.awaitEvent(e -> report(e, "pickup_ordered(ambulance_a1"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "cnp_won(hospital_h1"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "cnp_lost(hospital_h2"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "cnp_refused(hospital_h3"), Duration.ofSeconds(30));
        final TestProbe.Event transport =
                PROBE.awaitEvent(e -> report(e, "transport_ordered(ambulance_a1"), Duration.ofSeconds(30));
        assertThat(transport.data().get(2)).contains("hospital_h1");
        assertThat(PROBE.count(e -> report(e, "cnp_won("))).isEqualTo(1);
    }
}
