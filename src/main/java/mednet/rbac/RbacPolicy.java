package mednet.rbac;

import mednet.prolog.PrologKb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
