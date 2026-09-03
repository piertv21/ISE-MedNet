package mednet.model.patient;

public final class PatientRecord {

    private final String name;
    private final String pathology;
    private final SeverityCode guessCode;
    private final SeverityCode trueCode;
    private int x;
    private int y;

    private boolean active;
    private String onboardOf;
    private String hospitalId;
    private SeverityCode finalCode;
    private boolean discharged;

    public PatientRecord(final String name, final String pathology, final SeverityCode guessCode,
            final SeverityCode trueCode, final int x, final int y) {
        this.name = name;
        this.pathology = pathology;
        this.guessCode = guessCode;
        this.trueCode = trueCode;
        this.x = x;
        this.y = y;
    }

    public String name() {
        return name;
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

    public synchronized void activate() {
        this.active = true;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized boolean isOnMap() {
        return active && onboardOf == null && hospitalId == null && !discharged;
    }

    public synchronized String onboardOf() {
        return onboardOf;
    }

    public synchronized void loadOn(final String ambulance) {
        this.onboardOf = ambulance;
    }

    public synchronized String hospitalId() {
        return hospitalId;
    }

    public synchronized void admitTo(final String hospital) {
        this.onboardOf = null;
        this.hospitalId = hospital;
    }

    public synchronized SeverityCode finalCode() {
        return finalCode;
    }

    public synchronized void setFinalCode(final SeverityCode code) {
        this.finalCode = code;
    }

    public synchronized boolean isDischarged() {
        return discharged;
    }

    public synchronized void discharge() {
        this.discharged = true;
        this.hospitalId = null;
    }

    @Override
    public String toString() {
        return name + "(" + pathology + "," + trueCode.atom() + ")";
    }
}
