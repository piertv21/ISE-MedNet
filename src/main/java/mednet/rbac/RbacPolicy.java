package mednet.rbac;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import mednet.prolog.PrologKb;

public final class RbacPolicy {

    private static final Map<String, Boolean> DECISIONS = new ConcurrentHashMap<>();

    private RbacPolicy() {
    }

    public static Set<String> knownActions() {
        return new LinkedHashSet<>(PrologKb.allAtoms("Action", "known_action(Action)"));
    }

    public static boolean check(final String agentName, final String action) {
        if (agentName == null || action == null) {
            return false;
        }
        return DECISIONS.computeIfAbsent(agentName + '/' + action,
                key -> PrologKb.proves("can(" + PrologKb.quote(agentName) + ", " + PrologKb.quote(action) + ")"));
    }
}
