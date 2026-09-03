package mednet.env;

import mednet.env.probe.ProbeRegistry;
import mednet.model.patient.SeverityCode;
import mednet.testsupport.TestProbe;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Structure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MedNetEnvActionTest {

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

    private boolean act(final String agent, final String action) throws Exception {
        final Structure structure = ASSyntax.parseStructure(action);
        return env.executeAction(agent, structure);
    }

    private void ticks(final int n) {
        for (int i = 0; i < n; i++) {
            env.clock().tick();
        }
    }

    @Test
    void wrongRoleIsDeniedAndProbed() throws Exception {
        assertThat(act("doctor_h1_general", "move_to(1, 1)")).isFalse();
        assertThat(act("hospital_h1", "discharge_patient(patient1)")).isFalse();
        assertThat(act("ambulance_a1", "force_release(ct_scanner)")).isFalse();
        assertThat(probe.count(e -> e.type().equals("rbac_denied"))).isEqualTo(3);
    }

    @Test
    void unknownActionIsDenied() throws Exception {
        assertThat(act("ambulance_a1", "teleport(1, 1)")).isFalse();
    }

    @Test
    void malformedArgumentsFailWithoutModelChanges() throws Exception {
        assertThat(act("ambulance_a1", "move_to(somewhere, 1)")).isFalse();
        assertThat(act("hospital_h1", "reserve_bed(42)")).isFalse();
        assertThat(act("triage_nurse_h1", "enqueue_patient(patient1)")).isFalse();
    }

    @Test
    void fullAmbulancePipelineOnTheTerritorialLayer() throws Exception {
        assertThat(env.patients().find("patient1")).isPresent();

        assertThat(act("ambulance_a1", "preliminary_triage(patient1)")).isFalse();

        assertThat(act("ambulance_a1", "move_to(8, 8)")).isTrue();
        assertThat(act("ambulance_a1", "preliminary_triage(patient1)")).isTrue();
        assertThat(act("ambulance_a1", "load_patient(patient1)")).isTrue();

        assertThat(act("ambulance_a1", "unload_patient(patient1)")).isFalse();

        assertThat(act("ambulance_a1", "move_to(5, 5)")).isTrue();
        ticks(6);
        assertThat(act("ambulance_a1", "unload_patient(patient1)")).isTrue();
        assertThat(env.hospital("h1").isPresent("patient1")).isTrue();
        assertThat(env.patients().find("patient1").orElseThrow().hospitalId()).isEqualTo("h1");
    }

    @Test
    void bedActionsAreScopedToTheOwningHospital() throws Exception {
        assertThat(act("hospital_h1", "reserve_bed(patient1)")).isTrue();
        assertThat(env.hospital("h1").bedsFree()).isEqualTo(2);
        assertThat(env.hospital("h2").bedsFree()).isEqualTo(2);
        assertThat(act("hospital_h1", "release_bed(patient1)")).isTrue();
        assertThat(act("hospital_h1", "release_bed(patient1)")).isFalse();
    }

    @Test
    void equipmentMutexIsEnforcedThroughActions() throws Exception {
        assertThat(act("doctor_h1_general", "lock_equipment(ct_scanner)")).isTrue();
        assertThat(act("doctor_h1_cardiology", "lock_equipment(ct_scanner)")).isFalse();
        assertThat(act("doctor_h1_general", "lock_equipment(ct_scanner)")).isTrue();
        assertThat(act("doctor_h1_cardiology", "unlock_equipment(ct_scanner)")).isFalse();
        assertThat(act("doctor_h1_general", "unlock_equipment(ct_scanner)")).isTrue();
    }

    @Test
    void examsRequireTheLockOnTheRightEquipment() throws Exception {
        env.hospital("h1").patientArrived("patient1");
        assertThat(act("doctor_h1_general", "run_exam(patient1, xray, xray_room)")).isFalse();
        assertThat(act("doctor_h1_general", "lock_equipment(xray_room)")).isTrue();
        assertThat(act("doctor_h1_general", "run_exam(patient1, xray, xray_room)")).isTrue();
        assertThat(env.hospital("h1").examJobsOf("doctor_h1_general")).hasSize(1);
    }

    @Test
    void redTreatmentsRequireTheOperatingRoom() throws Exception {
        env.hospital("h1").patientArrived("patient1");
        env.hospital("h1").recordTriageResult("patient1", SeverityCode.RED);
        assertThat(act("doctor_h1_general", "start_treatment(patient1)")).isFalse();
        assertThat(act("doctor_h1_general", "lock_equipment(operating_room)")).isTrue();
        assertThat(act("doctor_h1_general", "start_treatment(patient1)")).isTrue();
    }

    @Test
    void dischargeFreesTheBedAndClosesTheCase() throws Exception {
        env.hospital("h1").patientArrived("patient1");
        env.hospital("h1").recordTriageResult("patient1", SeverityCode.GREEN);
        final int freeBefore = env.hospital("h1").bedsFree();
        assertThat(act("doctor_h1_general", "discharge_patient(patient1)")).isTrue();
        assertThat(env.hospital("h1").bedsFree()).isEqualTo(freeBefore + 1);
        assertThat(probe.count(e -> e.is("patient_discharged", "h1", "patient1"))).isEqualTo(1);
        assertThat(act("doctor_h1_general", "discharge_patient(patient1)")).isFalse();
    }

    @Test
    void forceReleaseIsReservedToTheEquipmentManager() throws Exception {
        act("doctor_h1_general", "lock_equipment(operating_room)");
        assertThat(act("equipment_manager_h1", "force_release(operating_room)")).isTrue();
        assertThat(env.hospital("h1").equipment("operating_room").orElseThrow().state().isFree()).isTrue();
    }
}
