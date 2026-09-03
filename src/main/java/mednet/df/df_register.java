package mednet.df;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Term;

public class df_register extends DefaultInternalAction {

    @Override
    public Object execute(final TransitionSystem ts, final Unifier un, final Term[] args)
            throws Exception {
        final jade.core.Agent jadeAgent = JadeDf.jadeAgentOf(ts);
        if (jadeAgent == null) {
            return new jason.stdlib.df_register().execute(ts, un, args);
        }
        JadeDf.register(jadeAgent, stringOf(args[0]));
        return true;
    }

    static String stringOf(final Term term) {
        if (term instanceof jason.asSyntax.StringTerm string) {
            return string.getString();
        }
        return term.toString();
    }
}
