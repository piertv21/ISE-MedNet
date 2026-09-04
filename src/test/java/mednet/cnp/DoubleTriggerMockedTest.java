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
class DoubleTriggerMockedTest {

    private static final TestProbe PROBE = new TestProbe();

    @BeforeAll
    static void boot() {
        ProbeRegistry.install(PROBE);
        MasTestRunner.boot("src/test/mas2j/test_double_trigger.mas2j");
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
    void oneEventReportedTwiceOpensOneRoundAndLeavesOneHospitalHoldingTheCase()
            throws Exception {
        PROBE.awaitEvent(e -> report(e, "cnp_won(hospital_h1"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "divert_sent(hospital_h1"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "cnp_won(hospital_h2"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "delivered(ambulance_a1"), Duration.ofSeconds(30));

        PROBE.awaitEvent(e -> report(e, "cancelled(hospital_h1"), Duration.ofSeconds(30));

        Thread.sleep(6000);

        assertThat(PROBE.count(e -> report(e, "cfp_received(")))
                .as("call for proposals received across the whole run")
                .isEqualTo(5);
        assertThat(PROBE.count(e -> report(e, "cfp_received(hospital_h1"))).isEqualTo(1);
        assertThat(PROBE.count(e -> report(e, "cfp_received(hospital_h2"))).isEqualTo(2);
        assertThat(PROBE.count(e -> report(e, "cfp_received(hospital_h3"))).isEqualTo(2);

        assertThat(PROBE.count(e -> report(e, "cnp_won("))).isEqualTo(2);
        assertThat(PROBE.count(e -> report(e, "cnp_won(hospital_h1"))).isEqualTo(1);
        assertThat(PROBE.count(e -> report(e, "cnp_won(hospital_h2"))).isEqualTo(1);
        assertThat(PROBE.count(e -> report(e, "cnp_won(hospital_h3"))).isZero();

        assertThat(lastIndexOfAward())
                .as("the last award happens before the delivery")
                .isLessThan(PROBE.indexOf(e -> report(e, "delivered(ambulance_a1")));
    }

    private static int lastIndexOfAward() {
        final var events = PROBE.events();
        int last = -1;
        for (int i = 0; i < events.size(); i++) {
            if (report(events.get(i), "cnp_won(")) {
                last = i;
            }
        }
        assertThat(last).as("an award was recorded").isNotNegative();
        return last;
    }
}
