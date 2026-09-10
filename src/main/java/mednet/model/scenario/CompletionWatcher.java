package mednet.model.scenario;

import java.util.Map;

import mednet.env.probe.ProbeRegistry;
import mednet.model.clock.SimulationClock;
import mednet.model.clock.TickListener;
import mednet.model.hospital.HospitalModel;
import mednet.model.patient.PatientRecord;
import mednet.model.patient.PatientRegistry;

// Detects the end of a run (timeline exhausted, every patient discharged, every hospital
// back to full free capacity) and then freezes the clock. Must be the last listener
// registered on the clock, see MedNetEnv.init.
public final class CompletionWatcher implements TickListener {

    private final ScenarioGenerator generator;
    private final PatientRegistry patients;
    private final Map<String, HospitalModel> hospitals;
    private final SimulationClock clock;
    private final Runnable onFinished;
    private boolean finished;

    public CompletionWatcher(final ScenarioGenerator generator, final PatientRegistry patients,
            final Map<String, HospitalModel> hospitals, final SimulationClock clock,
            final Runnable onFinished) {
        this.generator = generator;
        this.patients = patients;
        this.hospitals = hospitals;
        this.clock = clock;
        this.onFinished = onFinished;
    }

    @Override
    public synchronized void onTick(final long tick) {
        if (finished || !isComplete()) {
            return;
        }
        finished = true;
        clock.finish();
        ProbeRegistry.current().onEvent("simulation_finished", tick, patients.all().size());
        onFinished.run();
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    private boolean isComplete() {
        return generator.isTimelineExhausted()
                && patients.all().stream().allMatch(PatientRecord::isDischarged)
                && hospitals.values().stream().allMatch(h -> h.bedsFree() == h.bedsCapacity());
    }
}
