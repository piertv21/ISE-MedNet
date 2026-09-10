package mednet.prolog;

import java.util.List;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTerm;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Term;

// mednet.prolog.consult_kb(Facts): unifies Facts with the knowledge an agent needs to
// reason, taken from the shared mednet_kb.pl theory. The agent adds them to its belief
// base at start-up and its plan contexts then read them like any other belief, so the
// knowledge is written in one place.
public class consult_kb extends DefaultInternalAction {

    // Queries whose solutions become beliefs: a template paired with the goal grounding it.
    private static final List<String[]> KNOWLEDGE = List.of(
            new String[] {"code_priority(C, P)", "code_priority(C, P)"},
            new String[] {"requires_specialization(Path, S)", "requires_specialization(Path, S)"},
            new String[] {"admission_weights(C, DistW, SpecPenalty)",
                    "admission_weights(C, DistW, SpecPenalty)"},
            new String[] {"protocol_max_parallel_patients(R, N)", "protocol_max_parallel_patients(R, N)"},
            // derived by the theory, not stated in it
            new String[] {"protocol_preemptable(C)", "protocol_preemptable(C)"});

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public int getMaxArgs() {
        return 1;
    }

    @Override
    public Object execute(final TransitionSystem ts, final Unifier un, final Term[] args) throws Exception {
        final ListTerm facts = new ListTermImpl();
        for (final String[] entry : KNOWLEDGE) {
            for (final alice.tuprolog.Term solution : PrologKb.all(entry[0], entry[1])) {
                facts.add(ASSyntax.parseLiteral(solution.toString()));
            }
        }
        return un.unifies(args[0], facts);
    }
}
