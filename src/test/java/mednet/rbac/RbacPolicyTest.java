package mednet.rbac;

import mednet.prolog.PrologKb;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RbacPolicyTest {

    private static final Map<Role, String> REPRESENTATIVE = Map.of(
            Role.CONTROL_CENTER, "control_center",
            Role.AMBULANCE, "ambulance_a1",
            Role.HOSPITAL, "hospital_h1",
            Role.TRIAGE_NURSE, "triage_nurse_h1",
            Role.DOCTOR, "doctor_h1_cardiology",
            Role.EQUIPMENT_MANAGER, "equipment_manager_h1",
            Role.PATIENT, "patient1");

    private static final Map<String, Role> EXPECTED_OWNER = Map.ofEntries(
            Map.entry("move_to", Role.AMBULANCE),
            Map.entry("preliminary_triage", Role.AMBULANCE),
            Map.entry("load_patient", Role.AMBULANCE),
            Map.entry("unload_patient", Role.AMBULANCE),
            Map.entry("reserve_bed", Role.HOSPITAL),
            Map.entry("release_bed", Role.HOSPITAL),
            Map.entry("secondary_triage", Role.TRIAGE_NURSE),
            Map.entry("enqueue_patient", Role.TRIAGE_NURSE),
            Map.entry("dequeue_patient", Role.TRIAGE_NURSE),
            Map.entry("requeue_front", Role.TRIAGE_NURSE),
            Map.entry("lock_equipment", Role.DOCTOR),
            Map.entry("unlock_equipment", Role.DOCTOR),
            Map.entry("run_exam", Role.DOCTOR),
            Map.entry("start_treatment", Role.DOCTOR),
            Map.entry("abort_treatment", Role.DOCTOR),
            Map.entry("discharge_patient", Role.DOCTOR),
            Map.entry("force_release", Role.EQUIPMENT_MANAGER));

    @Test
    void fullDenialMatrix() {
        for (final var actionEntry : EXPECTED_OWNER.entrySet()) {
            for (final var roleEntry : REPRESENTATIVE.entrySet()) {
                final boolean expected = roleEntry.getKey() == actionEntry.getValue();
                assertThat(RbacPolicy.check(roleEntry.getValue(), actionEntry.getKey()))
                        .as("%s doing %s", roleEntry.getValue(), actionEntry.getKey())
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void policyCoversEveryKnownAction() {
        assertThat(PrologKb.allAtoms("Action", "permitted(_, Action)"))
                .containsExactlyInAnyOrderElementsOf(EXPECTED_OWNER.keySet());
    }

    @Test
    void unknownActionIsDeniedForEveryone() {
        for (final String agent : REPRESENTATIVE.values()) {
            assertThat(RbacPolicy.check(agent, "self_destruct")).isFalse();
        }
    }

    @Test
    void unknownAgentIsDeniedEverything() {
        for (final String action : EXPECTED_OWNER.keySet()) {
            assertThat(RbacPolicy.check("intruder", action)).isFalse();
        }
    }

    @Test
    void roleParsingFollowsNamingConvention() {
        REPRESENTATIVE.forEach((role, name) ->
                assertThat(Role.fromAgentName(name)).contains(role));
        assertThat(Role.fromAgentName("someone_else")).isEmpty();
    }

    @Test
    void agentNameParsingExtractsHospitalAndSpecialization() {
        assertThat(AgentNames.hospitalIdOf("doctor_h2_neurology")).contains("h2");
        assertThat(AgentNames.hospitalIdOf("triage_nurse_h3")).contains("h3");
        assertThat(AgentNames.hospitalIdOf("equipment_manager_h1")).contains("h1");
        assertThat(AgentNames.hospitalIdOf("control_center")).isEmpty();
        assertThat(AgentNames.specializationOf("doctor_h3_trauma_surgery")).contains("trauma_surgery");
        assertThat(AgentNames.specializationOf("hospital_h1")).isEmpty();
    }

    @Test
    void deniedRolesForSensitiveActions() {
        assertThat(RbacPolicy.check("doctor_h1_general", "force_release")).isFalse();
        assertThat(RbacPolicy.check("equipment_manager_h1", "lock_equipment")).isFalse();
        for (final String action : Set.of("move_to", "discharge_patient", "reserve_bed")) {
            assertThat(RbacPolicy.check("patient1", action)).isFalse();
        }
    }
}
