package mednet.model.territorial;

import mednet.model.patient.SeverityCode;

public final class AmbulanceState {

    private final String name;
    private int x;
    private int y;
    private Integer targetX;
    private Integer targetY;
    private boolean arrived;
    private String carrying;
    private String assessedPatient;
    private String assessedPathology;
    private SeverityCode assessedCode;

    public AmbulanceState(final String name, final int x, final int y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public String name() {
        return name;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    void setPosition(final int x, final int y) {
        this.x = x;
        this.y = y;
    }

    public Integer targetX() {
        return targetX;
    }

    public Integer targetY() {
        return targetY;
    }

    public boolean hasTarget() {
        return targetX != null;
    }

    public boolean isArrived() {
        return arrived;
    }

    void setArrived(final boolean arrived) {
        this.arrived = arrived;
    }

    public void setTarget(final int tx, final int ty) {
        this.targetX = tx;
        this.targetY = ty;
        this.arrived = x == tx && y == ty;
    }

    public String carrying() {
        return carrying;
    }

    public void setCarrying(final String patient) {
        this.carrying = patient;
    }

    public String assessedPatient() {
        return assessedPatient;
    }

    public String assessedPathology() {
        return assessedPathology;
    }

    public SeverityCode assessedCode() {
        return assessedCode;
    }

    public void setAssessment(final String patient, final String pathology, final SeverityCode code) {
        this.assessedPatient = patient;
        this.assessedPathology = pathology;
        this.assessedCode = code;
    }

    public void clearAssessment() {
        this.assessedPatient = null;
        this.assessedPathology = null;
        this.assessedCode = null;
    }
}
