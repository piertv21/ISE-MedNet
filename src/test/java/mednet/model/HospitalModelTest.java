package mednet.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mednet.model.hospital.HospitalModel;
import mednet.model.patient.SeverityCode;
import mednet.model.scenario.ScenarioConfig;

class HospitalModelTest {

    private HospitalModel h1;

    @BeforeEach
    void setUp() {
        h1 = new HospitalModel(ScenarioConfig.byName("empty", 1).hospitals().get(0));
    }

    @Test
    void bedReservationsAreBoundedByCapacity() {
        assertThat(h1.reserveBed("p1")).isTrue();
        assertThat(h1.reserveBed("p2")).isTrue();
        assertThat(h1.reserveBed("p3")).isTrue();
        assertThat(h1.reserveBed("p4")).isFalse();
        assertThat(h1.bedsFree()).isZero();
        assertThat(h1.releaseBed("p2")).isTrue();
        assertThat(h1.bedsFree()).isEqualTo(1);
    }

    @Test
    void arrivalConvertsReservationIntoOccupancy() {
        h1.reserveBed("p1");
        assertThat(h1.patientArrived("p1")).isTrue();
        assertThat(h1.isPresent("p1")).isTrue();
        assertThat(h1.bedsFree()).isEqualTo(2);
        assertThat(h1.dischargePatient("p1")).isTrue();
        assertThat(h1.bedsFree()).isEqualTo(3);
        assertThat(h1.isPresent("p1")).isFalse();
    }

    @Test
    void walkInStealsAReservationOnlyWhenFull() {
        h1.reserveBed("p1");
        assertThat(h1.walkIn(0)).isEmpty();
        h1.reserveBed("p2");
        assertThat(h1.bedsFree()).isZero();
        assertThat(h1.walkIn(0)).contains("p1");
        assertThat(h1.lostReservations()).containsExactly("p1");
        assertThat(h1.reserveBed("p1")).isFalse();
    }

    @Test
    void walkInBedsAreReleasedAfterTheirStay() {
        assertThat(h1.walkIn(0)).isEmpty();
        assertThat(h1.bedsFree()).isEqualTo(2);
        h1.onTick(HospitalModel.WALK_IN_STAY_TICKS - 1);
        assertThat(h1.bedsFree()).isEqualTo(2);
        h1.onTick(HospitalModel.WALK_IN_STAY_TICKS);
        assertThat(h1.bedsFree()).isEqualTo(3);
    }

    @Test
    void reReservationClearsTheDivertedFlag() {
        h1.reserveBed("p1");
        h1.walkIn(0);
        h1.walkIn(0);
        h1.walkIn(0);
        assertThat(h1.lostReservations()).containsExactly("p1");
        h1.onTick(HospitalModel.WALK_IN_STAY_TICKS);
        assertThat(h1.reserveBed("p1")).isTrue();
        assertThat(h1.lostReservations()).isEmpty();
    }

    @Test
    void examJobsCompleteAfterTheirDuration() {
        h1.reserveBed("p1");
        h1.patientArrived("p1");
        assertThat(h1.startExam("p1", "xray", "xray_room", "doctor_h1_general")).isTrue();
        for (int t = 1; t <= 4; t++) {
            h1.onTick(t);
            assertThat(h1.examJobsOf("doctor_h1_general").get(0).isDone()).isFalse();
        }
        h1.onTick(5);
        assertThat(h1.examJobsOf("doctor_h1_general").get(0).isDone()).isTrue();
    }

    @Test
    void examsRequirePresenceAndKnownExamType() {
        assertThat(h1.startExam("ghost", "xray", "xray_room", "d")).isFalse();
        h1.reserveBed("p1");
        h1.patientArrived("p1");
        assertThat(h1.startExam("p1", "colonoscopy", "xray_room", "d")).isFalse();
    }

    @Test
    void treatmentRequiresATriageResult() {
        h1.reserveBed("p1");
        h1.patientArrived("p1");
        assertThat(h1.startTreatment("p1", "doctor_h1_general")).isFalse();
        h1.recordTriageResult("p1", SeverityCode.GREEN);
        assertThat(h1.startTreatment("p1", "doctor_h1_general")).isTrue();
    }

    @Test
    void abortRemovesEveryJobOfThePatient() {
        h1.reserveBed("p1");
        h1.patientArrived("p1");
        h1.startExam("p1", "xray", "xray_room", "doc");
        h1.recordTriageResult("p1", SeverityCode.YELLOW);
        h1.startTreatment("p1", "doc");
        h1.abortJobsFor("p1");
        assertThat(h1.examJobsOf("doc")).isEmpty();
        assertThat(h1.treatmentJobsOf("doc")).isEmpty();
    }

    @Test
    void expiredLeasesAreFlaggedForTheEquipmentManager() {
        final var ct = h1.equipment("ct_scanner").orElseThrow();
        ct.lock("doctor_h1_general", 0);
        h1.onTick(HospitalModel.LEASE_TICKS);
        assertThat(h1.leaseExpirations()).isEmpty();
        h1.onTick(HospitalModel.LEASE_TICKS + 1);
        assertThat(h1.leaseExpirations()).containsEntry("ct_scanner", "doctor_h1_general");
        ct.unlock("doctor_h1_general");
        h1.clearLease("ct_scanner");
        assertThat(h1.leaseExpirations()).isEmpty();
    }
}