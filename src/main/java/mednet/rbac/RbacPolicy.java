package mednet.rbac;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import mednet.prolog.PrologKb;

// Role-based access control over environment actions. The policy lives in the rbac.pl
// theory; this class only asks it can(Agent, Action).
public final class RbacPolicy {

    private static final Map<String, Boolean> DECISIONS = new ConcurrentHashMap<>();

    private RbacPolicy() {
    }

    public static boolean check(final String agentName, final String action) {
        if (agentName == null || action == null) {
            return false;
        }
        return DECISIONS.computeIfAbsent(agentName + '/' + action,
                key -> PrologKb.proves("can(" + PrologKb.quote(agentName) + ", " + PrologKb.quote(action) + ")"));
    }
}
