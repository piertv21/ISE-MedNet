package mednet.model.scenario;

import mednet.model.patient.SeverityCode;

public final class ScenarioEvent {

    public enum Kind { ACTIVATE_PATIENT, WALK_IN }

    private final Kind kind;
    private final long tick;
    private final String patientName;
    private final String pathology;
    private final SeverityCode guessCode;
    private final SeverityCode trueCode;
    private final int x;
    private final int y;
    private final String hospitalId;

    private ScenarioEvent(final Kind kind, final long tick, final String patientName, final String pathology,
            final SeverityCode guessCode, final SeverityCode trueCode, final int x, final int y,
            final String hospitalId) {
        this.kind = kind;
        this.tick = tick;
        this.patientName = patientName;
        this.pathology = pathology;
        this.guessCode = guessCode;
        this.trueCode = trueCode;
        this.x = x;
        this.y = y;
        this.hospitalId = hospitalId;
    }

    public static ScenarioEvent activatePatient(final long tick, final String name, final String pathology,
            final SeverityCode guessCode, final SeverityCode trueCode, final int x, final int y) {
        return new ScenarioEvent(Kind.ACTIVATE_PATIENT, tick, name, pathology, guessCode, trueCode, x, y, null);
    }

    public static ScenarioEvent walkIn(final long tick, final String hospitalId) {
        return new ScenarioEvent(Kind.WALK_IN, tick, null, null, null, null, 0, 0, hospitalId);
    }

    public Kind kind() {
        return kind;
    }

    public long tick() {
        return tick;
    }

    public String patientName() {
        return patientName;
    }

    public String pathology() {
        return pathology;
    }

    public SeverityCode guessCode() {
        return guessCode;
    }

    public SeverityCode trueCode() {
        return trueCode;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public String hospitalId() {
        return hospitalId;
    }

    @Override
    public String toString() {
        return kind == Kind.ACTIVATE_PATIENT
                ? "activate(" + tick + "," + patientName + "," + pathology + "," + trueCode.atom() + ")"
                : "walk_in(" + tick + "," + hospitalId + ")";
    }
}
