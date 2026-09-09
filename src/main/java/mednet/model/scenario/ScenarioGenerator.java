package mednet.model.scenario;

import mednet.env.probe.ProbeRegistry;
import mednet.model.hospital.HospitalModel;
import mednet.model.patient.PatientRecord;
import mednet.model.patient.PatientRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;

public final class ScenarioGenerator {

    private final Deque<ScenarioEvent> pending;
    private final PatientRegistry patients;
    private final Map<String, HospitalModel> hospitals;

    public ScenarioGenerator(final ScenarioConfig config, final PatientRegistry patients,
            final Map<String, HospitalModel> hospitals) {
        this.pending = new ArrayDeque<>(config.events());
        this.patients = patients;
        this.hospitals = hospitals;
    }

    public synchronized void onTick(final long tick) {
        while (!pending.isEmpty() && pending.peekFirst().tick() <= tick) {
            fire(pending.pollFirst());
        }
    }

    public synchronized boolean isTimelineExhausted() {
        return pending.isEmpty();
    }

    private void fire(final ScenarioEvent event) {
        switch (event) {
            case ScenarioEvent.ActivatePatient call -> {
                final PatientRecord record = new PatientRecord(call.patientName(), call.pathology(),
                        call.guessCode(), call.trueCode(), call.x(), call.y());
                record.activate();
                patients.register(record);
                ProbeRegistry.current().onEvent("patient_activated", call.patientName(), call.pathology());
            }
            case ScenarioEvent.WalkIn walkIn -> {
                final HospitalModel hospital = hospitals.get(walkIn.hospitalId());
                if (hospital != null) {
                    final Optional<String> stolen = hospital.walkIn(walkIn.tick());
                    ProbeRegistry.current().onEvent("walk_in", walkIn.hospitalId(),
                            stolen.orElse("no_reservation_stolen"));
                }
            }
        }
    }
}
