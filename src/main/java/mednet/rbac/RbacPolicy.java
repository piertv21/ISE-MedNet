package mednet.rbac;

import java.util.Map;
import java.util.Set;

public final class RbacPolicy {

    private static final Map<String, Set<Role>> ALLOWED = Map.ofEntries(
            Map.entry("move_to", Set.of(Role.AMBULANCE)),
            Map.entry("preliminary_triage", Set.of(Role.AMBULANCE)),
            Map.entry("load_patient", Set.of(Role.AMBULANCE)),
            Map.entry("unload_patient", Set.of(Role.AMBULANCE)),
            Map.entry("reserve_bed", Set.of(Role.HOSPITAL)),
            Map.entry("release_bed", Set.of(Role.HOSPITAL)),
            Map.entry("secondary_triage", Set.of(Role.TRIAGE_NURSE)),
            Map.entry("enqueue_patient", Set.of(Role.TRIAGE_NURSE)),
            Map.entry("dequeue_patient", Set.of(Role.TRIAGE_NURSE)),
            Map.entry("requeue_front", Set.of(Role.TRIAGE_NURSE)),
            Map.entry("lock_equipment", Set.of(Role.DOCTOR)),
            Map.entry("unlock_equipment", Set.of(Role.DOCTOR)),
            Map.entry("run_exam", Set.of(Role.DOCTOR)),
            Map.entry("start_treatment", Set.of(Role.DOCTOR)),
            Map.entry("abort_treatment", Set.of(Role.DOCTOR)),
            Map.entry("discharge_patient", Set.of(Role.DOCTOR)),
            Map.entry("force_release", Set.of(Role.EQUIPMENT_MANAGER)));

    private RbacPolicy() {
    }

    public static Set<String> knownActions() {
        return ALLOWED.keySet();
    }

    public static boolean check(final String agentName, final String action) {
        final Set<Role> allowedRoles = ALLOWED.get(action);
        if (allowedRoles == null) {
            return false;
        }
        return Role.fromAgentName(agentName).map(allowedRoles::contains).orElse(false);
    }
}
