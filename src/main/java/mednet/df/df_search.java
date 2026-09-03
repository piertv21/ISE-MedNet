package mednet.df;

import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.ListTerm;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Term;

public class df_search extends DefaultInternalAction {

    @Override
    public Object execute(final TransitionSystem ts, final Unifier un, final Term[] args)
            throws Exception {
        final jade.core.Agent jadeAgent = JadeDf.jadeAgentOf(ts);
        if (jadeAgent == null) {
            return new jason.stdlib.df_search().execute(ts, un, args);
        }
        final ListTerm result = new ListTermImpl();
        for (final String name : JadeDf.search(jadeAgent, df_register.stringOf(args[0]))) {
            result.add(ASSyntax.createAtom(name));
        }
        return un.unifies(args[1], result);
    }
}
