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

// Regression for leaked bed reservations in the divert scenario. One event, the winner
// losing its bed, reaches the control center twice: as a divert_request from the hospital
// and as a transport_failed from the ambulance. Only one round may open, since two rounds
// sharing a CnpId steal each other's propose beliefs.
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
        // round 1: the diverter wins and the ambulance is routed to it
        PROBE.awaitEvent(e -> report(e, "cnp_won(hospital_h1"), Duration.ofSeconds(30));
        // the ambulance fails the delivery and the hospital asks for a divert
        PROBE.awaitEvent(e -> report(e, "divert_sent(hospital_h1"), Duration.ofSeconds(30));
        // round 2: h1 is excluded, the cheapest remaining hospital wins, delivery succeeds
        PROBE.awaitEvent(e -> report(e, "cnp_won(hospital_h2"), Duration.ofSeconds(30));
        PROBE.awaitEvent(e -> report(e, "delivered(ambulance_a1"), Duration.ofSeconds(30));

        // the hospital that had the bed is told to release it
        PROBE.awaitEvent(e -> report(e, "cancelled(hospital_h1"), Duration.ofSeconds(30));

        // Give a stray second round the time it would need: one bid-collection deadline
        // of 2 s plus the no-winner back-off of 1.5 s.
        Thread.sleep(6000);

        // Two rounds only, one per event rather than one per messenger. Round 1 goes to
        // all three hospitals, round 2 only to the two not excluded: 3 + 2 = 5 cfps.
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

        // and nobody is awarded after the patient has been handed over
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
