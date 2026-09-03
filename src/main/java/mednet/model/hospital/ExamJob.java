package mednet.model.hospital;

public final class ExamJob {

    private final String patient;
    private final String exam;
    private final String equipment;
    private final String doctor;
    private int remainingTicks;
    private boolean done;

    public ExamJob(final String patient, final String exam, final String equipment, final String doctor,
            final int durationTicks) {
        this.patient = patient;
        this.exam = exam;
        this.equipment = equipment;
        this.doctor = doctor;
        this.remainingTicks = durationTicks;
    }

    public String patient() {
        return patient;
    }

    public String exam() {
        return exam;
    }

    public String equipment() {
        return equipment;
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
