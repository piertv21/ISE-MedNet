package mednet.env;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import jason.asSyntax.NumberTerm;
import jason.asSyntax.Structure;
import mednet.env.probe.ProbeRegistry;
import mednet.model.clock.SimulationClock;
import mednet.model.hospital.HospitalModel;
import mednet.model.patient.PatientRecord;
import mednet.model.patient.PatientRegistry;
import mednet.model.patient.SeverityCode;
import mednet.model.territorial.AmbulanceState;
import mednet.model.territorial.HospitalSite;
import mednet.model.territorial.TerritorialModel;
import mednet.rbac.AgentNames;

final class ActionExecutor {

    private static final Logger LOGGER = Logger.getLogger(ActionExecutor.class.getName());

    private final TerritorialModel territorial;
    private final Map<String, HospitalModel> hospitals;
    private final PatientRegistry patients;
    private final SimulationClock clock;

    ActionExecutor(final TerritorialModel territorial, final Map<String, HospitalModel> hospitals,
            final PatientRegistry patients, final SimulationClock clock) {
        this.territorial = territorial;
        this.hospitals = hospitals;
        this.patients = patients;
        this.clock = clock;
    }

    boolean execute(final String agent, final Structure action) {
        try {
            return switch (action.getFunctor()) {
                case "move_to" -> moveTo(agent, intArg(action, 0), intArg(action, 1));
                case "preliminary_triage" -> preliminaryTriage(agent, atomArg(action, 0));
                case "load_patient" -> loadPatient(agent, atomArg(action, 0));
                case "unload_patient" -> unloadPatient(agent, atomArg(action, 0));
                case "reserve_bed" -> reserveBed(agent, atomArg(action, 0));
                case "release_bed" -> releaseBed(agent, atomArg(action, 0));
                case "secondary_triage" -> secondaryTriage(agent, atomArg(action, 0));
                case "enqueue_patient" -> enqueuePatient(agent, atomArg(action, 0), atomArg(action, 1));
                case "dequeue_patient" -> dequeuePatient(agent, atomArg(action, 0));
                case "requeue_front" -> requeueFront(agent, atomArg(action, 0), atomArg(action, 1));
                case "lock_equipment" -> lockEquipment(agent, atomArg(action, 0));
                case "unlock_equipment" -> unlockEquipment(agent, atomArg(action, 0));
                case "run_exam" -> runExam(agent, atomArg(action, 0), atomArg(action, 1), atomArg(action, 2));
                case "start_treatment" -> startTreatment(agent, atomArg(action, 0));
                case "abort_treatment" -> abortTreatment(agent, atomArg(action, 0));
                case "discharge_patient" -> dischargePatient(agent, atomArg(action, 0));
                case "force_release" -> forceRelease(agent, atomArg(action, 0));
                default -> {
                    LOGGER.warning(() -> "Unknown action " + action + " by " + agent);
                    yield false;
                }
            };
        } catch (final IllegalArgumentException e) {
            LOGGER.warning(() -> "Malformed action " + action + " by " + agent + ": " + e.getMessage());
            return false;
        }
    }

    private boolean moveTo(final String agent, final int x, final int y) {
        final Optional<AmbulanceState> ambulance = territorial.ambulance(agent);
        ambulance.ifPresent(a -> a.setTarget(x, y));
        return ambulance.isPresent();
    }

    private boolean preliminaryTriage(final String agent, final String patient) {
        final Optional<AmbulanceState> ambulance = territorial.ambulance(agent);
        final Optional<PatientRecord> record = patients.find(patient);
        if (ambulance.isEmpty() || record.isEmpty() || !record.get().isOnMap()) {
            return false;
        }
        final AmbulanceState a = ambulance.get();
        final PatientRecord p = record.get();
        if (a.x() != p.x() || a.y() != p.y()) {
            return false;
        }
        a.setAssessment(patient, p.pathology(), p.guessCode());
        ProbeRegistry.current().onEvent("preliminary_triage", agent, patient);
        return true;
    }

    private boolean loadPatient(final String agent, final String patient) {
        final Optional<AmbulanceState> ambulance = territorial.ambulance(agent);
        final Optional<PatientRecord> record = patients.find(patient);
        if (ambulance.isEmpty() || record.isEmpty() || !record.get().isOnMap()) {
            return false;
        }
        final AmbulanceState a = ambulance.get();
        final PatientRecord p = record.get();
        if (a.x() != p.x() || a.y() != p.y() || a.carrying() != null) {
            return false;
        }
        p.loadOn(agent);
        a.setCarrying(patient);
        ProbeRegistry.current().onEvent("patient_loaded", agent, patient);
        return true;
    }

    private boolean unloadPatient(final String agent, final String patient) {
        final Optional<AmbulanceState> ambulance = territorial.ambulance(agent);
        if (ambulance.isEmpty() || !patient.equals(ambulance.get().carrying())) {
            return false;
        }
        final AmbulanceState a = ambulance.get();
        final Optional<HospitalSite> site = territorial.siteAt(a.x(), a.y());
        if (site.isEmpty()) {
            return false;
        }
        final HospitalModel hospital = hospitals.get(site.get().id());
        if (hospital == null || !hospital.patientArrived(patient)) {
            return false;
        }
        patients.find(patient).ifPresent(p -> p.admitTo(site.get().id()));
        a.setCarrying(null);
        a.clearAssessment();
        ProbeRegistry.current().onEvent("patient_arrived", site.get().id(), patient);
        return true;
    }

    private boolean reserveBed(final String agent, final String patient) {
        return withHospital(agent, hospital -> {
            final boolean ok = hospital.reserveBed(patient);
            if (ok) {
                ProbeRegistry.current().onEvent("bed_reserved", hospital.id(), patient);
            }
            return ok;
        });
    }

    private boolean releaseBed(final String agent, final String patient) {
        return withHospital(agent, hospital -> {
            final boolean ok = hospital.releaseBed(patient);
            if (ok) {
                ProbeRegistry.current().onEvent("bed_released", hospital.id(), patient);
            }
            return ok;
        });
    }

    private boolean secondaryTriage(final String agent, final String patient) {
        return withHospital(agent, hospital -> {
            final Optional<PatientRecord> record = patients.find(patient);
            if (record.isEmpty() || !hospital.isPresent(patient)) {
                return false;
            }
            hospital.recordTriageResult(patient, record.get().trueCode());
            record.get().setFinalCode(record.get().trueCode());
            ProbeRegistry.current().onEvent("secondary_triage", hospital.id(), patient,
                    record.get().trueCode().atom());
            return true;
        });
    }

    private boolean enqueuePatient(final String agent, final String patient, final String code) {
        return withHospital(agent, hospital -> {
            if (!hospital.isPresent(patient)) {
                return false;
            }
            hospital.triage().enqueue(patient, SeverityCode.fromAtom(code), clock.currentTick());
            ProbeRegistry.current().onEvent("patient_enqueued", hospital.id(), patient, code);
            return true;
        });
    }

    private boolean dequeuePatient(final String agent, final String patient) {
        return withHospital(agent, hospital -> hospital.triage().remove(patient).isPresent());
    }

    private boolean requeueFront(final String agent, final String patient, final String code) {
        return withHospital(agent, hospital -> {
            if (!hospital.isPresent(patient)) {
                return false;
            }
            hospital.triage().requeueFront(patient, SeverityCode.fromAtom(code), clock.currentTick());
            ProbeRegistry.current().onEvent("patient_requeued", hospital.id(), patient, code);
            return true;
        });
    }

    private boolean lockEquipment(final String agent, final String equipmentName) {
        return withHospital(agent, hospital -> hospital.equipment(equipmentName).map(equipment -> {
            final boolean ok = equipment.lock(agent, clock.currentTick());
            if (ok) {
                ProbeRegistry.current().onEvent("equipment_locked", hospital.id(), equipmentName, agent);
            }
            return ok;
        }).orElse(false));
    }

    private boolean unlockEquipment(final String agent, final String equipmentName) {
        return withHospital(agent, hospital -> hospital.equipment(equipmentName).map(equipment -> {
            final boolean ok = equipment.unlock(agent);
            if (ok) {
                hospital.clearLease(equipmentName);
                ProbeRegistry.current().onEvent("equipment_unlocked", hospital.id(), equipmentName, agent);
            }
            return ok;
        }).orElse(false));
    }

    private boolean runExam(final String agent, final String patient, final String exam,
            final String equipmentName) {
        return withHospital(agent, hospital -> {
            final boolean ownsLock = hospital.equipment(equipmentName)
                    .map(eq -> agent.equals(eq.state().owner()))
                    .orElse(false);
            if (!ownsLock) {
                return false;
            }
            final boolean ok = hospital.startExam(patient, exam, equipmentName, agent);
            if (ok) {
                ProbeRegistry.current().onEvent("exam_started", hospital.id(), patient, exam);
            }
            return ok;
        });
    }

    private boolean startTreatment(final String agent, final String patient) {
        return withHospital(agent, hospital -> {
            final Optional<SeverityCode> code = hospital.triageResult(patient);
            if (code.isEmpty()) {
                return false;
            }
            if (code.get() == SeverityCode.RED) {
                final boolean ownsOr = hospital.equipment("operating_room")
                        .map(eq -> agent.equals(eq.state().owner()))
                        .orElse(false);
                if (!ownsOr) {
                    return false;
                }
            }
            final boolean ok = hospital.startTreatment(patient, agent);
            if (ok) {
                ProbeRegistry.current().onEvent("treatment_started", hospital.id(), patient, agent);
            }
            return ok;
        });
    }

    private boolean abortTreatment(final String agent, final String patient) {
        return withHospital(agent, hospital -> {
            hospital.abortJobsFor(patient);
            ProbeRegistry.current().onEvent("treatment_aborted", hospital.id(), patient, agent);
            return true;
        });
    }

    private boolean dischargePatient(final String agent, final String patient) {
        return withHospital(agent, hospital -> {
            if (!hospital.dischargePatient(patient)) {
                return false;
            }
            patients.find(patient).ifPresent(PatientRecord::discharge);
            ProbeRegistry.current().onEvent("patient_discharged", hospital.id(), patient, agent);
            return true;
        });
    }

    private boolean forceRelease(final String agent, final String equipmentName) {
        return withHospital(agent, hospital -> hospital.equipment(equipmentName).map(equipment -> {
            equipment.forceRelease();
            hospital.clearLease(equipmentName);
            ProbeRegistry.current().onEvent("equipment_force_released", hospital.id(), equipmentName);
            return true;
        }).orElse(false));
    }

    private boolean withHospital(final String agent, final HospitalAction operation) {
        return AgentNames.hospitalIdOf(agent)
                .map(hospitals::get)
                .map(operation::apply)
                .orElse(false);
    }

    @FunctionalInterface
    private interface HospitalAction {
        boolean apply(HospitalModel hospital);
    }

    private static int intArg(final Structure action, final int index) {
        if (action.getArity() <= index || !(action.getTerm(index) instanceof NumberTerm number)) {
            throw new IllegalArgumentException("argument " + index + " must be a number");
        }
        try {
            return (int) number.solve();
        } catch (final Exception e) {
            throw new IllegalArgumentException("argument " + index + " cannot be evaluated");
        }
    }

    private static String atomArg(final Structure action, final int index) {
        if (action.getArity() <= index) {
            throw new IllegalArgumentException("argument " + index + " is missing");
        }
        final String value = action.getTerm(index).toString();
        if (!value.matches("[a-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("argument " + index + " must be an atom");
        }
        return value;
    }
}
