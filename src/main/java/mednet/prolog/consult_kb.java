package mednet.prolog;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTerm;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Term;

import java.util.List;

public class consult_kb extends DefaultInternalAction {

    private static final List<String[]> KNOWLEDGE = List.of(
            new String[] {"code_priority(C, P)", "code_priority(C, P)"},
            new String[] {"requires_specialization(Path, S)", "requires_specialization(Path, S)"},
            new String[] {"admission_weights(C, DistW, SpecPenalty)",
                    "admission_weights(C, DistW, SpecPenalty)"},
            new String[] {"protocol_max_parallel_patients(R, N)", "protocol_max_parallel_patients(R, N)"},
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
