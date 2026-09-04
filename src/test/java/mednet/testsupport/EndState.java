package mednet.testsupport;

import mednet.env.MedNetEnv;
import mednet.model.hospital.HospitalModel;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public final class EndState {

    private static final List<String> HOSPITALS = List.of("h1", "h2", "h3");

    private EndState() {
    }

    public static void assertQuiescent(final TestProbe probe) throws InterruptedException {
        probe.awaitEvent(e -> e.type().equals("simulation_finished"), Duration.ofSeconds(120));

        final MedNetEnv env = MedNetEnv.getInstance();
        assertThat(env).as("the environment is still up").isNotNull();
        for (final String id : HOSPITALS) {
            final HospitalModel hospital = env.hospital(id);
            assertThat(hospital.bedsReserved())
                    .as("beds still reserved at %s for patients who never arrived", id)
                    .isZero();
            assertThat(hospital.bedsFree())
                    .as("free beds at %s", id)
                    .isEqualTo(hospital.bedsCapacity());
        }

        assertThat(probe.count(e -> e.type().equals("reservation_expired")))
                .as("the reservation lease had to reclaim a leaked bed")
                .isZero();
    }
}
