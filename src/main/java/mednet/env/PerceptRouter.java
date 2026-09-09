package mednet.env;

import mednet.model.hospital.EquipmentLockState;
import mednet.model.hospital.Equipment;
import mednet.model.hospital.ExamJob;
import mednet.model.hospital.HospitalModel;
import mednet.model.hospital.TreatmentJob;
import mednet.model.hospital.TriageEntry;
import mednet.model.clock.SimulationClock;
import mednet.model.patient.PatientRegistry;
import mednet.model.territorial.AmbulanceState;
import mednet.model.territorial.HospitalSite;
import mednet.model.territorial.TerritorialModel;
import mednet.rbac.AgentNames;
import mednet.rbac.Role;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.parser.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

final class PerceptRouter {

    private final TerritorialModel territorial;
    private final Map<String, HospitalModel> hospitals;
    private final PatientRegistry patients;
    private final SimulationClock clock;

    PerceptRouter(final TerritorialModel territorial, final Map<String, HospitalModel> hospitals,
            final PatientRegistry patients, final SimulationClock clock) {
        this.territorial = territorial;
        this.hospitals = hospitals;
        this.patients = patients;
        this.clock = clock;
    }

    List<Literal> perceptsFor(final String agentName) {
        final Optional<Role> role = Role.fromAgentName(agentName);
        if (role.isEmpty()) {
            return List.of();
        }
        final List<Literal> percepts = new ArrayList<>();
        switch (role.get()) {
            case PATIENT -> patientPercepts(agentName, percepts);
            case CONTROL_CENTER -> controlCenterPercepts(percepts);
            case AMBULANCE -> ambulancePercepts(agentName, percepts);
            case HOSPITAL -> hospitalPercepts(agentName, percepts);
            case TRIAGE_NURSE -> nursePercepts(agentName, percepts);
            case DOCTOR -> doctorPercepts(agentName, percepts);
            case EQUIPMENT_MANAGER -> equipmentManagerPercepts(agentName, percepts);
        }
        return percepts;
    }

    private void patientPercepts(final String name, final List<Literal> out) {
        patients.find(name).ifPresent(record -> {
            if (record.isActive() && !record.isDischarged()) {
                out.add(lit("active"));
                out.add(lit("my_condition(%s, %s)", record.pathology(), record.guessCode().atom()));
                if (record.isOnMap()) {
                    out.add(lit("at(pos(%d, %d))", record.x(), record.y()));
                }
            }
        });
    }

    private void controlCenterPercepts(final List<Literal> out) {
        for (final AmbulanceState ambulance : territorial.ambulances()) {
            out.add(lit("ambulance_pos(%s, pos(%d, %d))", ambulance.name(), ambulance.x(), ambulance.y()));
        }
        addHospitalInfo(out);
    }

    private void addHospitalInfo(final List<Literal> out) {
        for (final HospitalSite site : territorial.sites()) {
            out.add(lit("hospital_info(%s, %s, %d, %d)",
                    site.hospitalAgent(), site.nurseAgent(), site.x(), site.y()));
        }
    }

    private void ambulancePercepts(final String name, final List<Literal> out) {
        territorial.ambulance(name).ifPresent(ambulance -> {
            if (ambulance.hasTarget() && ambulance.isArrived()) {
                out.add(lit("at_target(%d, %d)", ambulance.targetX(), ambulance.targetY()));
            }
            if (ambulance.carrying() != null) {
                out.add(lit("carrying(%s)", ambulance.carrying()));
            }
            if (ambulance.assessedPatient() != null) {
                out.add(lit("triage_data(%s, %s, %s)", ambulance.assessedPatient(),
                        ambulance.assessedPathology(), ambulance.assessedCode().atom()));
            }
        });
        addHospitalInfo(out);
    }

    private void hospitalPercepts(final String name, final List<Literal> out) {
        withHospital(name, (id, hospital) -> {
            out.add(lit("my_hospital(%s)", id));
            territorial.siteOfHospitalAgent(name)
                    .ifPresent(site -> out.add(lit("my_pos(%d, %d)", site.x(), site.y())));
            for (final String spec : hospital.specializations()) {
                out.add(lit("my_specialization(%s)", spec));
            }
            out.add(lit("beds_free(%d)", hospital.bedsFree()));
            out.add(lit("beds_capacity(%d)", hospital.bedsCapacity()));
            for (final String patient : hospital.presentPatients()) {
                out.add(lit("arrived(%s)", patient));
            }
            for (final String patient : hospital.lostReservations()) {
                out.add(lit("reservation_lost(%s)", patient));
            }
            for (final String patient : hospital.expiredReservations()) {
                out.add(lit("reservation_expired(%s)", patient));
            }
        });
    }

    private void nursePercepts(final String name, final List<Literal> out) {
        withHospital(name, (id, hospital) -> {
            out.add(lit("my_hospital(%s)", id));
            for (final TriageEntry entry : hospital.triage().snapshot()) {
                out.add(lit("waiting(%s, %s, %d)", entry.patient(), entry.code().atom(), entry.sinceTick()));
            }
            hospital.triageResults().forEach((patient, code) ->
                    out.add(lit("triage_result(%s, %s)", patient, code.atom())));
            addPatientPathologies(hospital, out);
        });
    }

    private void doctorPercepts(final String name, final List<Literal> out) {
        withHospital(name, (id, hospital) -> {
            out.add(lit("my_hospital(%s)", id));
            AgentNames.specializationOf(name)
                    .ifPresent(spec -> out.add(lit("my_specialization(%s)", spec)));
            addPatientPathologies(hospital, out);
            for (final String patient : hospital.presentPatients()) {
                for (final String exam : hospital.completedExams(patient)) {
                    out.add(lit("exam_completed(%s, %s)", patient, exam));
                }
            }
            for (final ExamJob job : hospital.examJobsOf(name)) {
                if (job.isDone()) {
                    out.add(lit("exam_done(%s, %s)", job.patient(), job.exam()));
                }
            }
            for (final TreatmentJob job : hospital.treatmentJobsOf(name)) {
                if (job.isDone()) {
                    out.add(lit("treatment_done(%s)", job.patient()));
                }
            }
        });
    }

    private void equipmentManagerPercepts(final String name, final List<Literal> out) {
        withHospital(name, (id, hospital) -> {
            out.add(lit("my_hospital(%s)", id));
            out.add(lit("tick(%d)", clock.currentTick()));
            for (final Equipment equipment : hospital.equipmentUnits()) {
                final EquipmentLockState state = equipment.state();
                out.add(state.isFree()
                        ? lit("equipment_state(%s, free)", state.equipment())
                        : lit("equipment_state(%s, locked(%s))", state.equipment(), state.owner()));
            }
            hospital.leaseExpirations().forEach((equipment, owner) ->
                    out.add(lit("lease_expired(%s, %s)", equipment, owner)));
        });
    }

    private void addPatientPathologies(final HospitalModel hospital, final List<Literal> out) {
        for (final String patient : hospital.presentPatients()) {
            patients.find(patient).ifPresent(record ->
                    out.add(lit("patient_pathology(%s, %s)", patient, record.pathology())));
        }
    }

    private void withHospital(final String agentName, final BiConsumer<String, HospitalModel> consumer) {
        AgentNames.hospitalIdOf(agentName).ifPresent(id -> {
            final HospitalModel hospital = hospitals.get(id);
            if (hospital != null) {
                consumer.accept(id, hospital);
            }
        });
    }

    private static Literal lit(final String format, final Object... args) {
        try {
            return ASSyntax.parseLiteral(String.format(format, args));
        } catch (final ParseException e) {
            throw new IllegalStateException("Invalid percept: " + String.format(format, args), e);
        }
    }
}
