package mednet.model.hospital;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import mednet.env.probe.ProbeRegistry;
import mednet.model.patient.SeverityCode;
import mednet.model.scenario.ScenarioConfig;
import mednet.prolog.MedicalKb;

// Internal layer of one hospital: beds, triage queue, equipment, running exam and
// treatment jobs, per-patient triage results. Every mutator is synchronized because
// agent threads and the clock thread both touch this state.
public final class HospitalModel {

    // After this many ticks a held equipment lock is considered leaked.
    public static final long LEASE_TICKS = 60;

    // After this many ticks a bed still reserved for a patient who never arrived is
    // considered leaked and offered back to the hospital agent.
    public static final long RESERVATION_LEASE_TICKS = 120;

    // How long an off-network walk-in occupies a bed. No MedNet agent handles walk-ins,
    // so their beds are released again after this many ticks.
    public static final int WALK_IN_STAY_TICKS = 50;

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
    private final Set<String> expiredReservations = new LinkedHashSet<>();
    private final Map<String, String> leaseExpired = new LinkedHashMap<>();
    private final Map<String, Long> walkInDischargeTick = new LinkedHashMap<>();
    private final Map<String, Set<String>> completedExams = new LinkedHashMap<>();

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
        expiredReservations.remove(patient);
        return beds.releaseReservation(patient);
    }

    public synchronized boolean patientArrived(final String patient) {
        if (!beds.occupyArrived(patient)) {
            return false;
        }
        presentPatients.add(patient);
        return true;
    }

    // An off-network emergency takes a bed. A stolen reservation is remembered so the
    // hospital agent perceives reservation_lost/1 and starts the renegotiation.
    public synchronized Optional<String> walkIn(final long nowTick) {
        final Optional<String> stolen = beds.walkIn();
        stolen.ifPresent(lostReservations::add);
        for (final String walkIn : beds.walkInPatients()) {
            walkInDischargeTick.putIfAbsent(walkIn, nowTick + WALK_IN_STAY_TICKS);
        }
        return stolen;
    }

    public synchronized Set<String> lostReservations() {
        return Set.copyOf(lostReservations);
    }

    public synchronized Set<String> expiredReservations() {
        return Set.copyOf(expiredReservations);
    }

    public synchronized int bedsReserved() {
        return beds.reservedCount();
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
        final Optional<Integer> duration = MedicalKb.examDuration(exam);
        if (duration.isEmpty() || !presentPatients.contains(patient)) {
            return false;
        }
        examJobs.add(new ExamJob(patient, exam, equipmentName, doctor, duration.get()));
        return true;
    }

    public synchronized boolean startTreatment(final String patient, final String doctor) {
        final SeverityCode code = triageResults.get(patient);
        if (code == null || !presentPatients.contains(patient)) {
            return false;
        }
        final Optional<Integer> duration = MedicalKb.treatmentDuration(code.atom());
        if (duration.isEmpty()) {
            return false;
        }
        treatmentJobs.add(new TreatmentJob(patient, doctor, duration.get()));
        return true;
    }

    public synchronized void abortJobsFor(final String patient) {
        examJobs.removeIf(job -> job.patient().equals(patient));
        treatmentJobs.removeIf(job -> job.patient().equals(patient));
    }

    public synchronized List<ExamJob> examJobsOf(final String doctor) {
        return examJobs.stream().filter(j -> j.doctor().equals(doctor)).toList();
    }

    public synchronized Set<String> completedExams(final String patient) {
        return Set.copyOf(completedExams.getOrDefault(patient, Set.of()));
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
        completedExams.remove(patient);
        abortJobsFor(patient);
        return true;
    }

    public synchronized void onTick(final long tick) {
        examJobs.forEach(ExamJob::tick);
        examJobs.stream()
                .filter(ExamJob::isDone)
                .forEach(job -> completedExams
                        .computeIfAbsent(job.patient(), p -> new LinkedHashSet<>())
                        .add(job.exam()));
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
        for (final String patient : beds.ageReservations(tick, RESERVATION_LEASE_TICKS)) {
            if (expiredReservations.add(patient)) {
                ProbeRegistry.current().onEvent("reservation_expired", id, patient);
            }
        }
    }
}
