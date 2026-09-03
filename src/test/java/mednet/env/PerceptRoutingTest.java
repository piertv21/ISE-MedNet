package mednet.env;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jason.asSyntax.Literal;

class PerceptRoutingTest {

    private MedNetEnv env;

    @BeforeEach
    void setUp() {
        env = new MedNetEnv();
        env.init(new String[] {"seed=1", "scenario=preemption", "manualClock"});
    }

    @AfterEach
    void tearDown() {
        env.stop();
    }

    private List<String> perceptsOf(final String agent) {
        final Collection<Literal> percepts = env.getPercepts(agent);
        return percepts.stream().map(Literal::toString).toList();
    }

    private void ticks(final int n) {
        for (int i = 0; i < n; i++) {
            env.clock().tick();
        }
    }

    @Test
    void patientsPerceiveNothingUntilActivation() {
        assertThat(perceptsOf("patient1")).isEmpty();
        ticks(5);
        assertThat(perceptsOf("patient1"))
                .contains("active", "my_condition(fracture,green)", "at(pos(8,8))");
        assertThat(perceptsOf("patient2")).isEmpty();
    }

    @Test
    void controlCenterSeesTheFleetButNeverBedCounts() {
        final List<String> percepts = perceptsOf("control_center");
        assertThat(percepts).contains(
                "ambulance_pos(ambulance_a1,pos(15,15))",
                "hospital_info(hospital_h1,triage_nurse_h1,5,5)",
                "hospital_info(hospital_h2,triage_nurse_h2,24,24)");
        assertThat(percepts).noneMatch(p -> p.startsWith("beds_free"));
    }

    @Test
    void hospitalsPerceiveOnlyTheirOwnCapacityAndSpecializations() {
        assertThat(perceptsOf("hospital_h1")).contains(
                "my_hospital(h1)", "my_pos(5,5)", "beds_free(3)", "beds_capacity(3)",
                "my_specialization(cardiology)", "my_specialization(general)");
        assertThat(perceptsOf("hospital_h2"))
                .contains("my_hospital(h2)", "beds_free(2)")
                .doesNotContain("my_specialization(cardiology)");
    }

    @Test
    void ambulanceSeesTriageDataOnlyAfterItsOwnAssessment() throws Exception {
        ticks(5);
        assertThat(perceptsOf("ambulance_a1")).noneMatch(p -> p.startsWith("triage_data"));
        env.executeAction("ambulance_a1", jason.asSyntax.ASSyntax.parseStructure("move_to(8, 8)"));
        ticks(14);
        assertThat(perceptsOf("ambulance_a1")).contains("at_target(8,8)");
        env.executeAction("ambulance_a1",
                jason.asSyntax.ASSyntax.parseStructure("preliminary_triage(patient1)"));
        assertThat(perceptsOf("ambulance_a1")).contains("triage_data(patient1,fracture,green)");
        // the other ambulance perceives nothing about that assessment
        assertThat(perceptsOf("ambulance_a2")).noneMatch(p -> p.startsWith("triage_data"));
    }

    @Test
    void hospitalStaffOfOneHospitalNeverSeesAnotherHospital() {
        env.hospital("h1").patientArrived("patient1");
        ticks(6); // let patient records exist
        assertThat(perceptsOf("triage_nurse_h1")).contains("my_hospital(h1)");
        assertThat(perceptsOf("triage_nurse_h2")).contains("my_hospital(h2)")
                .noneMatch(p -> p.contains("patient1"));
        assertThat(perceptsOf("doctor_h2_neurology")).contains(
                "my_hospital(h2)", "my_specialization(neurology)");
        assertThat(perceptsOf("doctor_h2_neurology")).noneMatch(p -> p.contains("patient1"));
    }

    @Test
    void nurseSeesQueueAndTriageResults() {
        ticks(5);
        env.hospital("h1").patientArrived("patient1");
        env.hospital("h1").recordTriageResult("patient1",
                mednet.model.patient.SeverityCode.YELLOW);
        env.hospital("h1").triage().enqueue("patient1",
                mednet.model.patient.SeverityCode.YELLOW, 5);
        assertThat(perceptsOf("triage_nurse_h1")).contains(
                "waiting(patient1,yellow,5)",
                "triage_result(patient1,yellow)",
                "patient_pathology(patient1,fracture)");
    }

    @Test
    void equipmentManagerSeesLockStatesAndTicks() {
        ticks(3);
        env.hospital("h1").equipment("ct_scanner").orElseThrow().lock("doctor_h1_general", 3);
        final List<String> percepts = perceptsOf("equipment_manager_h1");
        assertThat(percepts).contains(
                "tick(3)",
                "equipment(ct_scanner)",
                "equipment_state(ct_scanner,locked(doctor_h1_general))",
                "equipment_state(operating_room,free)");
        // the manager of h2 sees only its own (all free) equipment
        assertThat(perceptsOf("equipment_manager_h2"))
                .contains("equipment_state(ct_scanner,free)");
    }

    @Test
    void unknownAgentsPerceiveNothing() {
        assertThat(perceptsOf("intruder")).isEmpty();
    }
}
