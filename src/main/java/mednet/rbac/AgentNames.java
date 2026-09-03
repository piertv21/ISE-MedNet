package mednet.rbac;

import java.util.Optional;

public final class AgentNames {

    private AgentNames() {
    }

    public static Optional<String> hospitalIdOf(final String agentName) {
        return Role.fromAgentName(agentName).flatMap(role -> switch (role) {
            case HOSPITAL, TRIAGE_NURSE, DOCTOR, EQUIPMENT_MANAGER -> {
                final String rest = agentName.substring(role.prefix().length());
                if (rest.startsWith("_")) {
                    final String[] parts = rest.substring(1).split("_", 2);
                    yield Optional.of(parts[0]);
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        });
    }

    public static Optional<String> specializationOf(final String agentName) {
        if (Role.fromAgentName(agentName).orElse(null) != Role.DOCTOR) {
            return Optional.empty();
        }
        final String rest = agentName.substring(Role.DOCTOR.prefix().length());
        if (!rest.startsWith("_")) {
            return Optional.empty();
        }
        final String[] parts = rest.substring(1).split("_", 2);
        return parts.length > 1 ? Optional.of(parts[1]) : Optional.empty();
    }
}
