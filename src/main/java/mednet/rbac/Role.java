package mednet.rbac;

import java.util.List;
import java.util.Optional;

// Organizational roles, derived from the agent naming convention: control_center,
// ambulance_a1, hospital_h2, triage_nurse_h1, doctor_h1_cardiology,
// equipment_manager_h3, patient4.
public enum Role {
    CONTROL_CENTER("control_center"),
    EQUIPMENT_MANAGER("equipment_manager"),
    TRIAGE_NURSE("triage_nurse"),
    AMBULANCE("ambulance"),
    HOSPITAL("hospital"),
    DOCTOR("doctor"),
    PATIENT("patient");

    private static final List<Role> BY_PREFIX_LENGTH = List.of(
            EQUIPMENT_MANAGER, CONTROL_CENTER, TRIAGE_NURSE, AMBULANCE, HOSPITAL, PATIENT, DOCTOR);

    private final String prefix;

    Role(final String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static Optional<Role> fromAgentName(final String agentName) {
        if (agentName == null) {
            return Optional.empty();
        }
        return BY_PREFIX_LENGTH.stream()
                .filter(role -> agentName.equals(role.prefix) || agentName.startsWith(role.prefix + "_")
                        || (role == PATIENT && agentName.matches("patient\\d+")))
                .findFirst();
    }
}
