package mednet.prolog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrologKbTest {

    @Test
    void medicalKnowledgeIsQueryable() {
        assertThat(MedicalKb.requiredSpecialization("stroke")).contains("neurology");
        assertThat(MedicalKb.requiredExams("stroke")).containsExactly("ct_scan", "blood_lab");
        assertThat(MedicalKb.defaultCode("major_trauma")).contains("yellow");
        assertThat(MedicalKb.examDuration("ct_scan")).contains(8);
        assertThat(MedicalKb.treatmentDuration("red")).contains(20);
        assertThat(MedicalKb.pathologies())
                .containsExactly("cardiac_arrest", "stroke", "major_trauma", "fracture", "abdominal_pain");
    }

    @Test
    void unknownTermsSimplyHaveNoProof() {
        assertThat(MedicalKb.requiredSpecialization("hangnail")).isEmpty();
        assertThat(MedicalKb.examDuration("mri")).isEmpty();
        assertThat(MedicalKb.requiredExams("hangnail")).isEmpty();
    }

    @Test
    void onlyRedTreatmentsAreConfinedToTheOperatingRoom() {
        assertThat(MedicalKb.treatmentEquipment("red")).contains("operating_room");
        assertThat(MedicalKb.treatmentEquipment("yellow")).isEmpty();
        assertThat(MedicalKb.treatmentEquipment("green")).isEmpty();
        assertThat(MedicalKb.treatmentEquipment("white")).isEmpty();
    }

    @Test
    void preemptabilityIsDerivedFromUrgencyNotStated() {
        assertThat(PrologKb.allAtoms("C", "protocol_preemptable(C)"))
                .containsExactly("yellow", "green", "white")
                .doesNotContain("red");
    }

    @Test
    void rolesFollowTheNamingConventionWithLongestPrefixWinning() {
        assertThat(PrologKb.allAtoms("R", "role_of('equipment_manager_h1', R)"))
                .containsExactly("equipment_manager");
        assertThat(PrologKb.allAtoms("R", "role_of('triage_nurse_h1', R)")).containsExactly("triage_nurse");
        assertThat(PrologKb.allAtoms("R", "role_of('doctor_h3_trauma_surgery', R)")).containsExactly("doctor");
        assertThat(PrologKb.allAtoms("R", "role_of('patient12', R)")).containsExactly("patient");
        assertThat(PrologKb.allAtoms("R", "role_of('control_center', R)")).containsExactly("control_center");
        assertThat(PrologKb.allAtoms("R", "role_of('patientX', R)")).isEmpty();
        assertThat(PrologKb.allAtoms("R", "role_of('someone_else', R)")).isEmpty();
    }

    @Test
    void engineIsUsableFromSeveralThreadsAtOnce() throws Exception {
        final Thread[] threads = new Thread[8];
        final boolean[] ok = new boolean[threads.length];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> ok[index] =
                    PrologKb.proves("can('doctor_h1_cardiology', run_exam)")
                            && MedicalKb.examDuration("xray").orElse(-1) == 5);
            threads[i].start();
        }
        for (final Thread thread : threads) {
            thread.join();
        }
        assertThat(ok).containsOnly(true);
    }
}
