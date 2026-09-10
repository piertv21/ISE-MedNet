package mednet.rbac;

import java.util.Optional;

public final class AgentNames {

    private AgentNames() {
    }

    // The hospital id embedded in an agent name: doctor_h2_neurology gives h2.
    public static Optional<String> hospitalIdOf(final String agentName) {
        return Role.fromAgentName(agentName).flatMap(role -> switch (role) {
            case HOSPITAL, TRIAGE_NURSE, DOCTOR, EQUIPMENT_MANAGER ->
                    suffixParts(agentName, role).map(parts -> parts[0]);
            default -> Optional.empty();
        });
    }

    // The specialization embedded in a doctor name: doctor_h3_trauma_surgery gives
    // trauma_surgery.
    public static Optional<String> specializationOf(final String agentName) {
        if (Role.fromAgentName(agentName).orElse(null) != Role.DOCTOR) {
            return Optional.empty();
        }
        return suffixParts(agentName, Role.DOCTOR)
                .filter(parts -> parts.length > 1)
                .map(parts -> parts[1]);
    }

    private static Optional<String[]> suffixParts(final String agentName, final Role role) {
        final String rest = agentName.substring(role.prefix().length());
        return rest.startsWith("_")
                ? Optional.of(rest.substring(1).split("_", 2))
                : Optional.empty();
    }
}
