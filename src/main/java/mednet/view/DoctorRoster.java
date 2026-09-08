package mednet.view;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import jason.environment.EnvironmentInfraTier;
import jason.runtime.RuntimeServices;
import mednet.rbac.AgentNames;
import mednet.rbac.Role;

public final class DoctorRoster {

    private static final Logger LOGGER = Logger.getLogger(DoctorRoster.class.getName());

    private final Supplier<EnvironmentInfraTier> infraTier;
    private volatile Map<String, List<String>> byHospital = Map.of();

    public DoctorRoster(final Supplier<EnvironmentInfraTier> infraTier) {
        this.infraTier = infraTier;
    }

    public List<String> of(final String hospitalId) {
        return resolve().getOrDefault(hospitalId, List.of());
    }

    private Map<String, List<String>> resolve() {
        if (!byHospital.isEmpty()) {
            return byHospital;
        }
        final Map<String, List<String>> roster = new LinkedHashMap<>();
        agentNames().stream()
                .filter(name -> Role.fromAgentName(name).orElse(null) == Role.DOCTOR)
                .sorted()
                .forEach(name -> AgentNames.hospitalIdOf(name).ifPresent(
                        hospital -> roster.computeIfAbsent(hospital, h -> new ArrayList<>()).add(name)));
        if (!roster.isEmpty()) {
            byHospital = Map.copyOf(roster);
        }
        return roster;
    }

    private Collection<String> agentNames() {
        try {
            final EnvironmentInfraTier tier = infraTier.get();
            if (tier == null) {
                return List.of();
            }
            final RuntimeServices services = tier.getRuntimeServices();
            return services == null ? List.of() : services.getAgentsNames();
        } catch (final RemoteException | RuntimeException e) {
            LOGGER.log(Level.FINE, "agent roster unavailable; the view shows no doctors", e);
            return List.of();
        }
    }
}
