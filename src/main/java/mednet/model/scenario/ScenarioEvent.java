package mednet.model.scenario;

import mednet.model.patient.SeverityCode;

public sealed interface ScenarioEvent {

    long tick();

    record ActivatePatient(long tick, String patientName, String pathology, SeverityCode guessCode,
            SeverityCode trueCode, int x, int y) implements ScenarioEvent {
    }

    record WalkIn(long tick, String hospitalId) implements ScenarioEvent {
    }
}
