package mednet.env;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jason.asSyntax.ASSyntax;
import mednet.env.probe.ProbeRegistry;
import mednet.model.patient.SeverityCode;
import mednet.testsupport.TestProbe;

class SimulationCompletionTest {

    private MedNetEnv env;
    private TestProbe probe;

    @BeforeEach
    void setUp() {
        probe = new TestProbe();
        ProbeRegistry.install(probe);
        env = new MedNetEnv();
        env.init(new String[] {"seed=1", "scenario=preemption", "manualClock"});
    }

    @AfterEach
    void tearDown() {
        env.stop();
        ProbeRegistry.reset();
    }

    private void ticks(final int n) {
        for (int i = 0; i < n; i++) {
            env.clock().tick();
        }
    }

    private void discharge(final String patient) throws Exception {
        env.hospital("h1").patientArrived(patient);
        env.hospital("h1").recordTriageResult(patient, SeverityCode.GREEN);
        assertThat(env.executeAction("doctor_h1_general",
                ASSyntax.parseStructure("discharge_patient(" + patient + ")"))).isTrue();
    }

    @Test
    void theRunIsNotOverWhileAnyPatientIsStillInTheSystem() throws Exception {
        assertThat(env.patients().all()).hasSize(2);

        discharge("patient1");
        ticks(3);

        assertThat(env.isSimulationFinished()).isFalse();
        assertThat(env.clock().currentTick()).isEqualTo(28);
        assertThat(probe.count(e -> e.type().equals("simulation_finished"))).isZero();
    }

    @Test
    void theRunIsNotOverWhileAHospitalStillHoldsABed() throws Exception {
        ticks(25);
        discharge("patient1");
        discharge("patient2");
        env.hospital("h2").reserveBed("patient1");

        ticks(3);
        assertThat(env.isSimulationFinished()).isFalse();
        assertThat(probe.count(e -> e.type().equals("simulation_finished"))).isZero();

        assertThat(env.hospital("h2").releaseBed("patient1")).isTrue();
        env.clock().tick();
        assertThat(env.isSimulationFinished()).isTrue();
    }

    @Test
    void theWorldFreezesOnceEveryPatientHasBeenDischarged() throws Exception {
        ticks(25);
        discharge("patient1");
        discharge("patient2");

        env.clock().tick();
        final long lastTick = env.clock().currentTick();

        assertThat(env.isSimulationFinished()).isTrue();
        assertThat(env.clock().isFinished()).isTrue();
        assertThat(probe.count(e -> e.is("simulation_finished", String.valueOf(lastTick)))).isEqualTo(1);

        ticks(5);
        env.clock().start(10);
        assertThat(env.clock().currentTick()).isEqualTo(lastTick);
        assertThat(probe.count(e -> e.type().equals("simulation_finished"))).isEqualTo(1);
    }
}
