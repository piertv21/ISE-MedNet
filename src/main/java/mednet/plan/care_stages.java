package mednet.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTerm;
import jason.asSyntax.Literal;
import jason.asSyntax.Term;
import mednet.env.probe.ProbeRegistry;
import mednet.prolog.PrologKb;

// mednet.plan.care_stages(Patient, Pathology, Code, DoneExams, Stages): asks the STRIPS
// planner for the care plan of one patient and hands it to the doctor.
public class care_stages extends DefaultInternalAction {

    @Override
    public int getMinArgs() {
        return 5;
    }

    @Override
    public int getMaxArgs() {
        return 5;
    }

    @Override
    public Object execute(final TransitionSystem ts, final Unifier un, final Term[] args) throws Exception {
        final String patient = atomOf(args[0]);
        final String pathology = atomOf(args[1]);
        final String code = atomOf(args[2]);
        final String done = prologListOf(args[3]);

        final String goal = "care_stages(" + pathology + ", " + code + ", " + done + ", Stages), "
                + "plan_length(Stages, Steps), Result = result(Stages, Steps)";
        final alice.tuprolog.Term result = PrologKb.first(goal, "Result").orElse(null);
        if (!(result instanceof alice.tuprolog.Struct plan) || plan.getArity() != 2) {
            return false;
        }

        final Term stages = ASSyntax.parseTerm(plan.getArg(0).getTerm().toString());
        final String steps = plan.getArg(1).getTerm().toString();
        ProbeRegistry.current().onEvent("care_plan", patient, pathology, code, steps);
        return un.unifies(args[4], stages);
    }

    private static String atomOf(final Term term) {
        if (term instanceof Literal literal && literal.getArity() == 0) {
            return literal.getFunctor();
        }
        return term.toString();
    }

    private static String prologListOf(final Term term) {
        final List<String> elements = new ArrayList<>();
        if (term instanceof ListTerm list) {
            list.forEach(element -> elements.add(atomOf(element)));
        }
        final StringJoiner joiner = new StringJoiner(", ", "[", "]");
        elements.forEach(joiner::add);
        return joiner.toString();
    }
}