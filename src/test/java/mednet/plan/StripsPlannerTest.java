package mednet.plan;

import mednet.prolog.MedicalKb;
import mednet.prolog.PrologKb;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StripsPlannerTest {

    private static List<String> plan(final String pathology, final String code, final String done) {
        final String goal = "care_plan(" + pathology + ", " + code + ", " + done + ", Plan), member(S, Plan)";
        return PrologKb.all("S", goal).stream().map(Object::toString).toList();
    }

    private static String stages(final String pathology, final String code, final String done) {
        return PrologKb.first("care_stages(" + pathology + ", " + code + ", " + done + ", S)", "S")
                .orElseThrow()
                .toString();
    }

    @Test
    void aSingleExamPathologyIsPlannedEndToEnd() {
        assertThat(plan("fracture", "green", "[]")).containsExactly(
                "acquire(xray_room)",
                "exam(xray,xray_room)",
                "release(xray_room)",
                "treat",
                "discharge");
    }

    @Test
    void redCasesAcquireTheOperatingRoomBeforeTreatingAndReleaseItAfter() {
        final List<String> steps = plan("stroke", "red", "[]");
        assertThat(steps).containsExactly(
                "acquire(ct_scanner)",
                "exam(ct_scan,ct_scanner)",
                "release(ct_scanner)",
                "acquire(lab)",
                "exam(blood_lab,lab)",
                "release(lab)",
                "acquire(operating_room)",
                "treat",
                "release(operating_room)",
                "discharge");
        assertThat(steps.indexOf("acquire(operating_room)")).isLessThan(steps.indexOf("treat"));
        assertThat(steps.indexOf("treat")).isLessThan(steps.indexOf("release(operating_room)"));
    }

    @Test
    void nonRedCasesNeedNoOperatingRoom() {
        assertThat(plan("major_trauma", "yellow", "[]")).doesNotContain("acquire(operating_room)");
        assertThat(plan("abdominal_pain", "green", "[]")).containsExactly(
                "acquire(lab)", "exam(blood_lab,lab)", "release(lab)", "treat", "discharge");
    }

    @Test
    void everyPathologyIsPlannableAndNeverHoldsTwoMachinesAtOnce() {
        for (final String pathology : MedicalKb.pathologies()) {
            final String code = MedicalKb.defaultCode(pathology).orElseThrow();
            final List<String> steps = plan(pathology, code, "[]");

            assertThat(steps).as("plan of %s", pathology).isNotEmpty();
            assertThat(steps).as("plan of %s ends by closing the case", pathology).endsWith("discharge");
            assertThat(steps).as("plan of %s treats the patient", pathology).contains("treat");

            final List<String> held = new ArrayList<>();
            for (final String step : steps) {
                if (step.startsWith("acquire(")) {
                    assertThat(held).as("%s acquires %s while holding %s", pathology, step, held).isEmpty();
                    held.add(step.substring("acquire(".length(), step.length() - 1));
                } else if (step.startsWith("release(")) {
                    assertThat(held).contains(step.substring("release(".length(), step.length() - 1));
                    held.clear();
                }
            }
            assertThat(held).as("%s leaves nothing held", pathology).isEmpty();

            for (final String exam : MedicalKb.requiredExams(pathology)) {
                assertThat(steps).as("%s plans %s", pathology, exam)
                        .anyMatch(step -> step.startsWith("exam(" + exam + ","));
            }
        }
    }

    @Test
    void anExamRunsOnTheMachineTheKnowledgeBaseAssignsToIt() {
        for (final String pathology : MedicalKb.pathologies()) {
            final String code = MedicalKb.defaultCode(pathology).orElseThrow();
            for (final String step : plan(pathology, code, "[]")) {
                if (step.startsWith("exam(")) {
                    final String[] parts = step.substring("exam(".length(), step.length() - 1).split(",");
                    assertThat(PrologKb.allAtoms("Eq", "equipment_for(" + parts[0] + ", Eq)"))
                            .as("machine of %s", parts[0])
                            .containsExactly(parts[1]);
                }
            }
        }
    }

    @Test
    void replanningSkipsTheExamsAlreadyPerformed() {
        final List<String> fromScratch = plan("stroke", "red", "[]");
        final List<String> afterCtScan = plan("stroke", "red", "[ct_scan]");

        assertThat(afterCtScan).isNotEmpty().hasSizeLessThan(fromScratch.size());
        assertThat(afterCtScan).doesNotContain("exam(ct_scan,ct_scanner)", "acquire(ct_scanner)");
        assertThat(afterCtScan).contains("exam(blood_lab,lab)");
    }

    @Test
    void aFullyDiagnosedPatientIsPlannedStraightToTreatment() {
        assertThat(plan("stroke", "red", "[ct_scan, blood_lab]")).containsExactly(
                "acquire(operating_room)", "treat", "release(operating_room)", "discharge");
        assertThat(plan("major_trauma", "yellow", "[ct_scan, xray]")).containsExactly("treat", "discharge");
    }

    @Test
    void independentExamsAreGroupedIntoOneParallelStage() {
        assertThat(stages("stroke", "red", "[]")).isEqualTo(
                "[[[acquire(ct_scanner),exam(ct_scan,ct_scanner),release(ct_scanner)],"
                        + "[acquire(lab),exam(blood_lab,lab),release(lab)]],"
                        + "[[acquire(operating_room),treat,release(operating_room)]],"
                        + "[[discharge]]]");
    }

    @Test
    void aSingleExamGivesASingleSegmentPerStage() {
        assertThat(stages("fracture", "green", "[]")).isEqualTo(
                "[[[acquire(xray_room),exam(xray,xray_room),release(xray_room)]],"
                        + "[[treat]],"
                        + "[[discharge]]]");
    }

    @Test
    void theTreatmentNeverSharesAStageWithAnExam() {
        for (final String pathology : MedicalKb.pathologies()) {
            final String code = MedicalKb.defaultCode(pathology).orElseThrow();
            final String[] stageList = stages(pathology, code, "[]").split("\\],\\[\\[");
            for (final String stage : stageList) {
                assertThat(stage.contains("exam(") && stage.contains("treat"))
                        .as("stage of %s mixing exams and treatment: %s", pathology, stage)
                        .isFalse();
            }
        }
    }

    @Test
    void anUnknownPathologyHasNoPlan() {
        assertThat(PrologKb.first("care_plan(hangnail, green, [], Plan)", "Plan")).isEmpty();
    }
}
