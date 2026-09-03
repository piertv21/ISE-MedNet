package mednet.model.scenario;

import mednet.env.probe.ProbeRegistry;
import mednet.model.clock.SimulationClock;
import mednet.model.clock.TickListener;
import mednet.model.patient.PatientRecord;
import mednet.model.patient.PatientRegistry;

public final class CompletionWatcher implements TickListener {

    private final ScenarioGenerator generator;
    private final PatientRegistry patients;
    private final SimulationClock clock;
    private final Runnable onFinished;
    private boolean finished;

    public CompletionWatcher(final ScenarioGenerator generator, final PatientRegistry patients,
            final SimulationClock clock, final Runnable onFinished) {
        this.generator = generator;
        this.patients = patients;
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
                && patients.all().stream().allMatch(PatientRecord::isDischarged);
    }
}
