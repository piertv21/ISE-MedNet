package mednet.model.hospital;

import mednet.model.patient.SeverityCode;
import mednet.model.scenario.ScenarioConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HospitalModel {

    public static final long LEASE_TICKS = 60;

    public static final int WALK_IN_STAY_TICKS = 50;

    private static final Map<String, Integer> EXAM_TICKS = Map.of(
            "ecg", 4,
            "blood_lab", 6,
            "ct_scan", 8,
            "xray", 5);

    private static final Map<SeverityCode, Integer> TREATMENT_TICKS = Map.of(
            SeverityCode.RED, 20,
            SeverityCode.YELLOW, 40,
            SeverityCode.GREEN, 25,
            SeverityCode.WHITE, 15);

    private final String id;
    private final List<String> specializations;
    private final BedPool beds;
    private final TriageQueue triage = new TriageQueue();
    private final Map<String, Equipment> equipment = new LinkedHashMap<>();
    private final List<ExamJob> examJobs = new ArrayList<>();
    private final List<TreatmentJob> treatmentJobs = new ArrayList<>();
    private final Set<String> presentPatients = new LinkedHashSet<>();
    private final Map<String, SeverityCode> triageResults = new LinkedHashMap<>();
    private final Set<String> lostReservations = new LinkedHashSet<>();
    private final Map<String, String> leaseExpired = new LinkedHashMap<>();
    private final Map<String, Long> walkInDischargeTick = new LinkedHashMap<>();

    public HospitalModel(final ScenarioConfig.HospitalSpec spec) {
        this.id = spec.id();
        this.specializations = List.copyOf(spec.specializations());
        this.beds = new BedPool(spec.beds());
        for (final String eq : spec.equipment()) {
            equipment.put(eq, new Equipment(eq));
        }
    }

    public String id() {
        return id;
    }

    public List<String> specializations() {
        return specializations;
    }

    public TriageQueue triage() {
        return triage;
    }

    public synchronized int bedsCapacity() {
        return beds.capacity();
    }

    public synchronized int bedsFree() {
        return beds.free();
    }

    public synchronized boolean reserveBed(final String patient) {
        final boolean reserved = beds.reserve(patient);
        if (reserved) {
            lostReservations.remove(patient);
        }
        return reserved;
    }

    public synchronized boolean releaseBed(final String patient) {
        return beds.releaseReservation(patient);
    }

    public synchronized boolean patientArrived(final String patient) {
        if (!beds.occupyArrived(patient)) {
            return false;
        }
        presentPatients.add(patient);
        return true;
    }

    public synchronized Optional<String> walkIn(final long nowTick) {
        final Optional<String> stolen = beds.walkIn();
        stolen.ifPresent(lostReservations::add);
        for (final String walkIn : beds.walkInPatients()) {
            walkInDischargeTick.putIfAbsent(walkIn, nowTick + WALK_IN_STAY_TICKS);
        }
        return stolen;
    }

    public synchronized void clearLostReservation(final String patient) {
        lostReservations.remove(patient);
    }

    public synchronized Set<String> lostReservations() {
        return Set.copyOf(lostReservations);
    }

    public synchronized boolean isPresent(final String patient) {
        return presentPatients.contains(patient);
    }

    public synchronized Set<String> presentPatients() {
        return Set.copyOf(presentPatients);
    }

    public synchronized void recordTriageResult(final String patient, final SeverityCode code) {
        triageResults.put(patient, code);
    }

    public synchronized Optional<SeverityCode> triageResult(final String patient) {
        return Optional.ofNullable(triageResults.get(patient));
    }

    public synchronized Map<String, SeverityCode> triageResults() {
        return Map.copyOf(triageResults);
    }

    public synchronized Optional<Equipment> equipment(final String name) {
        return Optional.ofNullable(equipment.get(name));
    }

    public synchronized List<Equipment> equipmentUnits() {
        return List.copyOf(equipment.values());
    }

    public synchronized Map<String, String> leaseExpirations() {
        return Map.copyOf(leaseExpired);
    }

    public synchronized void clearLease(final String equipmentName) {
        leaseExpired.remove(equipmentName);
    }

    public synchronized boolean startExam(final String patient, final String exam, final String equipmentName,
            final String doctor) {
        final Integer duration = EXAM_TICKS.get(exam);
        if (duration == null || !presentPatients.contains(patient)) {
            return false;
        }
        examJobs.add(new ExamJob(patient, exam, equipmentName, doctor, duration));
        return true;
    }

    public synchronized boolean startTreatment(final String patient, final String doctor) {
        final SeverityCode code = triageResults.get(patient);
        if (code == null || !presentPatients.contains(patient)) {
            return false;
        }
        treatmentJobs.add(new TreatmentJob(patient, doctor, TREATMENT_TICKS.get(code)));
        return true;
    }

    public synchronized void abortJobsFor(final String patient) {
        examJobs.removeIf(job -> job.patient().equals(patient));
        treatmentJobs.removeIf(job -> job.patient().equals(patient));
    }

    public synchronized List<ExamJob> examJobsOf(final String doctor) {
        return examJobs.stream().filter(j -> j.doctor().equals(doctor)).toList();
    }

    public synchronized List<TreatmentJob> treatmentJobsOf(final String doctor) {
        return treatmentJobs.stream().filter(j -> j.doctor().equals(doctor)).toList();
    }

    public synchronized boolean dischargePatient(final String patient) {
        if (!presentPatients.remove(patient)) {
            return false;
        }
        beds.discharge(patient);
        triageResults.remove(patient);
        triage.remove(patient);
        abortJobsFor(patient);
        return true;
    }

    public synchronized void onTick(final long tick) {
        examJobs.forEach(ExamJob::tick);
        treatmentJobs.forEach(TreatmentJob::tick);
        walkInDischargeTick.entrySet().removeIf(entry -> {
            if (tick >= entry.getValue()) {
                beds.discharge(entry.getKey());
                return true;
            }
            return false;
        });
        for (final Equipment eq : equipment.values()) {
            final EquipmentLockState state = eq.state();
            if (!state.isFree() && tick - state.sinceTick() > LEASE_TICKS) {
                leaseExpired.putIfAbsent(state.equipment(), state.owner());
            }
        }
    }
}
