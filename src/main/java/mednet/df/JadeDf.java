package mednet.df;

import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jason.asSemantics.TransitionSystem;
import jason.architecture.AgArch;
import jason.infra.jade.JasonBridgeArch;

import java.util.LinkedHashSet;
import java.util.Set;

final class JadeDf {

    static final String SERVICE_TYPE = "mednet";

    private JadeDf() {
    }

    static jade.core.Agent jadeAgentOf(final TransitionSystem ts) {
        AgArch arch = ts.getAgArch().getFirstAgArch();
        while (arch != null) {
            if (arch instanceof JasonBridgeArch bridge) {
                return bridge.getJadeAg();
            }
            arch = arch.getNextAgArch();
        }
        return null;
    }

    static void register(final jade.core.Agent agent, final String service) throws Exception {
        final DFAgentDescription query = new DFAgentDescription();
        query.setName(agent.getAID());
        final DFAgentDescription[] found = DFService.search(agent, query);

        final DFAgentDescription dfd = found.length > 0 ? found[0] : new DFAgentDescription();
        dfd.setName(agent.getAID());
        final ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_TYPE);
        sd.setName(service);
        dfd.addServices(sd);

        if (found.length > 0) {
            DFService.modify(agent, dfd);
        } else {
            DFService.register(agent, dfd);
        }
    }

    static Set<String> search(final jade.core.Agent agent, final String service) throws Exception {
        final DFAgentDescription template = new DFAgentDescription();
        final ServiceDescription sd = new ServiceDescription();
        sd.setType(SERVICE_TYPE);
        sd.setName(service);
        template.addServices(sd);
        final Set<String> names = new LinkedHashSet<>();
        for (final DFAgentDescription found : DFService.search(agent, template)) {
            names.add(found.getName().getLocalName());
        }
        return names;
    }
}
