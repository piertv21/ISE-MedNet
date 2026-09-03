package mednet.model.patient;

public enum SeverityCode {
    RED(0),
    YELLOW(1),
    GREEN(2),
    WHITE(3);

    private final int priority;

    SeverityCode(final int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    public String atom() {
        return name().toLowerCase();
    }

    public static SeverityCode fromAtom(final String atom) {
        return valueOf(atom.toUpperCase());
    }
}
