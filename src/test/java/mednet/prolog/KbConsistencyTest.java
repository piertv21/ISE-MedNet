package mednet.prolog;

import mednet.model.hospital.Equipment;
import mednet.model.hospital.HospitalModel;
import mednet.model.patient.SeverityCode;
import mednet.model.scenario.ScenarioConfig;
import mednet.rbac.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// No Java constant, enum or scenario spec may disagree with a clause of the theory. Every
// test here compares the two sides rather than restating a value.
class KbConsistencyTest {

    @Test
    void severityCodeEnumAgreesWithCodePriority() {
        for (final SeverityCode code : SeverityCode.values()) {
            assertThat(MedicalKb.codePriority(code.atom()))
                    .as("priority of %s", code.atom())
                    .contains(code.priority());
        }
        assertThat(MedicalKb.severityCodes())
                .containsExactlyElementsOf(List.of(SeverityCode.values()).stream()
                        .map(SeverityCode::atom)
                        .toList());
    }

    private static int admissionWeight(final String code, final String variable) {
        return PrologKb.firstInt("admission_weights(" + code + ", W, P)", variable)
                .orElseThrow(() -> new AssertionError("no admission_weights/3 for " + code));
    }

    @Test
    void everySeverityCodeHasAdmissionWeights() {
        assertThat(PrologKb.allAtoms("C", "admission_weights(C, _, _)"))
                .containsExactlyInAnyOrderElementsOf(MedicalKb.severityCodes());
        for (final SeverityCode code : SeverityCode.values()) {
            assertThat(admissionWeight(code.atom(), "W")).as("distance weight of %s", code.atom())
                    .isPositive();
            assertThat(admissionWeight(code.atom(), "P")).as("mismatch penalty of %s", code.atom())
                    .isPositive();
        }
    }

    // The monotonicity the bidding relies on: a less urgent case may never weigh distance
    // or a specialization mismatch more heavily than a more urgent one.
    @Test
    void admissionWeightsNeverGrowAsUrgencyDrops() {
        final List<String> byUrgency = MedicalKb.severityCodes();
        for (int i = 1; i < byUrgency.size(); i++) {
            final String moreUrgent = byUrgency.get(i - 1);
            final String lessUrgent = byUrgency.get(i);
            assertThat(MedicalKb.codePriority(moreUrgent).orElseThrow())
                    .as("%s is more urgent than %s", moreUrgent, lessUrgent)
                    .isLessThan(MedicalKb.codePriority(lessUrgent).orElseThrow());
            assertThat(admissionWeight(lessUrgent, "W"))
                    .as("distance weight: %s vs %s", lessUrgent, moreUrgent)
                    .isLessThanOrEqualTo(admissionWeight(moreUrgent, "W"));
            assertThat(admissionWeight(lessUrgent, "P"))
                    .as("mismatch penalty: %s vs %s", lessUrgent, moreUrgent)
                    .isLessThanOrEqualTo(admissionWeight(moreUrgent, "P"));
        }
    }

    @Test
    void everyJavaRoleExistsInTheTheory() {
        final Set<String> theoryRoles = Set.copyOf(PrologKb.allAtoms("R", "role_name(R)"));
        assertThat(List.of(Role.values()).stream().map(Role::prefix).collect(Collectors.toSet()))
                .isEqualTo(theoryRoles);
    }

    @Test
    void everyExamRunsOnEquipmentTheHospitalsActuallyHave() {
        final List<String> machines = PrologKb.allAtoms("Eq", "equipment_for(_, Eq)");
        assertThat(ScenarioConfig.STANDARD_EQUIPMENT).containsAll(machines);
        assertThat(ScenarioConfig.STANDARD_EQUIPMENT)
                .contains(MedicalKb.treatmentEquipment("red").orElseThrow());
    }

    @Test
    void jobDurationsComeFromTheTheory() {
        final HospitalModel hospital =
                new HospitalModel(ScenarioConfig.byName("empty", 1).hospitals().get(0));
        hospital.reserveBed("patient1");
        hospital.patientArrived("patient1");
        assertThat(hospital.startExam("patient1", "ct_scan", "ct_scanner", "doctor_h1_general")).isTrue();

        final int duration = MedicalKb.examDuration("ct_scan").orElseThrow();
        for (int tick = 1; tick < duration; tick++) {
            hospital.onTick(tick);
            assertThat(hospital.completedExams("patient1")).isEmpty();
        }
        hospital.onTick(duration);
        assertThat(hospital.completedExams("patient1")).containsExactly("ct_scan");
    }

    @Test
    void everyPathologyIsFullyDescribed() {
        for (final String pathology : MedicalKb.pathologies()) {
            assertThat(MedicalKb.requiredSpecialization(pathology)).as("specialization of %s", pathology)
                    .isPresent();
            assertThat(MedicalKb.defaultCode(pathology)).as("default code of %s", pathology).isPresent();
            assertThat(MedicalKb.requiredExams(pathology)).as("exams of %s", pathology).isNotEmpty();
            for (final String exam : MedicalKb.requiredExams(pathology)) {
                assertThat(MedicalKb.examDuration(exam)).as("duration of %s", exam).isPresent();
                assertThat(PrologKb.allAtoms("Eq", "equipment_for(" + PrologKb.quote(exam) + ", Eq)"))
                        .as("equipment of %s", exam)
                        .hasSize(1);
            }
        }
    }

    @Test
    void equipmentNamesAreConsistentBetweenTheoryAndModel() {
        for (final String machine : PrologKb.allAtoms("Eq", "equipment_for(_, Eq)")) {
            assertThat(new Equipment(machine).state().equipment()).isEqualTo(machine);
        }
    }
}
