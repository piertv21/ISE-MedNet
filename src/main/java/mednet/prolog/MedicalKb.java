package mednet.prolog;

import java.util.List;
import java.util.Optional;

public final class MedicalKb {

    private MedicalKb() {
    }

    public static Optional<Integer> examDuration(final String exam) {
        return PrologKb.firstInt("exam_duration(" + PrologKb.quote(exam) + ", D)", "D");
    }

    public static Optional<Integer> treatmentDuration(final String code) {
        return PrologKb.firstInt("treatment_duration(" + PrologKb.quote(code) + ", D)", "D");
    }

    public static Optional<String> treatmentEquipment(final String code) {
        return PrologKb.first("treatment_equipment(" + PrologKb.quote(code) + ", Eq)", "Eq")
                .map(PrologKb::text);
    }

    public static Optional<String> defaultCode(final String pathology) {
        return PrologKb.first("default_code(" + PrologKb.quote(pathology) + ", C)", "C")
                .map(PrologKb::text);
    }

    public static List<String> requiredExams(final String pathology) {
        return PrologKb.allAtoms("E",
                "required_exams(" + PrologKb.quote(pathology) + ", Exams), member(E, Exams)");
    }

    public static Optional<String> requiredSpecialization(final String pathology) {
        return PrologKb.first("requires_specialization(" + PrologKb.quote(pathology) + ", S)", "S")
                .map(PrologKb::text);
    }

    public static Optional<Integer> codePriority(final String code) {
        return PrologKb.firstInt("code_priority(" + PrologKb.quote(code) + ", P)", "P");
    }

    public static List<String> severityCodes() {
        return PrologKb.allAtoms("C", "code_priority(C, _)");
    }

    public static List<String> pathologies() {
        return PrologKb.allAtoms("P", "pathology(P)");
    }
}
