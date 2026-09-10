package mednet.testsupport;

import mednet.env.probe.ProbeRegistry;
import mednet.rbac.AgentNames;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.Structure;
import jason.environment.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Minimal environment for the mocked CNP tests: no world model, no RBAC. It serves only
// the percepts the real agents need (my_hospital, triage_result, waiting), emulates the
// triage queue actions and records every action into the installed TestProbe. Mock agents
// use the report pseudo-action to surface negotiation outcomes.
public class TestSupportEnv extends Environment {

    private static final Map<String, String> PROGRAMMED_TRIAGE = new ConcurrentHashMap<>();

    private final Map<String, QueueEntry> queue = new ConcurrentHashMap<>();
    private final Map<String, String> triageResults = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    private record QueueEntry(String code, long seq) {
    }

    // Programs the outcome of secondary_triage(patient); call before booting the MAS.
    public static void programTriage(final String patient, final String codeAtom) {
        PROGRAMMED_TRIAGE.put(patient, codeAtom);
    }

    public static void resetProgram() {
        PROGRAMMED_TRIAGE.clear();
    }

    @Override
    public List<Literal> getPercepts(final String agName) {
        final List<Literal> percepts = new ArrayList<>();
        AgentNames.hospitalIdOf(agName).ifPresent(h -> percepts.add(lit("my_hospital(%s)", h)));
        triageResults.forEach((patient, code) -> percepts.add(lit("triage_result(%s, %s)", patient, code)));
        queue.forEach((patient, entry) ->
                percepts.add(lit("waiting(%s, %s, %d)", patient, entry.code(), entry.seq())));
        return percepts;
    }

    @Override
    public boolean executeAction(final String agName, final Structure action) {
        final Object[] data = new Object[action.getArity() + 2];
        data[0] = agName;
        data[1] = action.getFunctor();
        for (int i = 0; i < action.getArity(); i++) {
            data[i + 2] = action.getTerm(i).toString();
        }
        ProbeRegistry.current().onEvent("action", data);

        switch (action.getFunctor()) {
            case "secondary_triage" -> {
                final String patient = action.getTerm(0).toString();
                triageResults.put(patient, PROGRAMMED_TRIAGE.getOrDefault(patient, "yellow"));
            }
            case "enqueue_patient", "requeue_front" -> {
                final String patient = action.getTerm(0).toString();
                final String code = action.getTerm(1).toString();
                // Same rule as the real TriageQueue: a known patient keeps its arrival
                // sequence, so a requeue does not cost it its place in line.
                final long entrySeq = queue.containsKey(patient)
                        ? queue.get(patient).seq()
                        : seq.getAndIncrement();
                queue.put(patient, new QueueEntry(code, entrySeq));
            }
            case "dequeue_patient" -> queue.remove(action.getTerm(0).toString());
            default -> {
                // report(...) and any other action: recorded above, always succeeds
            }
        }
        informAgsEnvironmentChanged();
        return true;
    }

    private static Literal lit(final String format, final Object... args) {
        try {
            return ASSyntax.parseLiteral(String.format(format, args));
        } catch (final jason.asSyntax.parser.ParseException e) {
            throw new IllegalStateException(e);
        }
    }
}
