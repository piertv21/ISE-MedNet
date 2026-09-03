package mednet.model.hospital;

public final class TreatmentJob {

    private final String patient;
    private final String doctor;
    private int remainingTicks;
    private boolean done;

    public TreatmentJob(final String patient, final String doctor, final int durationTicks) {
        this.patient = patient;
        this.doctor = doctor;
        this.remainingTicks = durationTicks;
    }

    public String patient() {
        return patient;
    }

    public String doctor() {
        return doctor;
    }

    public boolean isDone() {
        return done;
    }

    void tick() {
        if (!done && --remainingTicks <= 0) {
            done = true;
        }
    }
}
