package mednet.env;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import jason.asSyntax.Literal;
import jason.asSyntax.Structure;
import jason.environment.Environment;
import mednet.env.probe.ProbeRegistry;
import mednet.model.clock.SimulationClock;
import mednet.model.hospital.HospitalModel;
import mednet.model.patient.PatientRegistry;
import mednet.model.scenario.CompletionWatcher;
import mednet.model.scenario.ScenarioConfig;
import mednet.model.scenario.ScenarioGenerator;
import mednet.model.territorial.TerritorialModel;
import mednet.rbac.RbacPolicy;
import mednet.view.MedNetGui;

public class MedNetEnv extends Environment {

    private static final Logger LOGGER = Logger.getLogger(MedNetEnv.class.getName());
    private static volatile MedNetEnv instance;

    public static final String READY_PORT_PROPERTY = "mednet.ready.port";

    private ScenarioConfig config;
    private TerritorialModel territorial;
    private Map<String, HospitalModel> hospitals;
    private PatientRegistry patients;
    private SimulationClock clock;
    private ScenarioGenerator generator;
    private CompletionWatcher completion;
    private PerceptRouter router;
    private ActionExecutor actions;
    private volatile MedNetGui gui;
    private volatile boolean stopped;

    @Override
    public void init(final String[] args) {
        long seed = 42;
        String scenarioName = "default";
        long periodMs = 250;
        boolean guiRequested = false;
        boolean manualClock = false;
        boolean probeLog = false;
        for (final String arg : args == null ? new String[0] : args) {
            if (arg.startsWith("seed=")) {
                seed = Long.parseLong(arg.substring("seed=".length()));
            } else if (arg.startsWith("scenario=")) {
                scenarioName = arg.substring("scenario=".length());
            } else if (arg.startsWith("period=")) {
                periodMs = Long.parseLong(arg.substring("period=".length()));
            } else if (arg.equals("gui")) {
                guiRequested = true;
            } else if (arg.equals("manualClock")) {
                manualClock = true;
            } else if (arg.equals("probeLog")) {
                probeLog = true;
            }
        }
        if (probeLog) {
            ProbeRegistry.install(new mednet.env.probe.EnvProbe() {
                @Override
                public void onEvent(final String type, final Object... data) {
                    System.err.println("[probe] " + type + " " + java.util.Arrays.toString(data));
                }
            });
        }

        config = ScenarioConfig.byName(scenarioName, seed);
        patients = new PatientRegistry();
        territorial = new TerritorialModel(config);
        hospitals = new LinkedHashMap<>();
        config.hospitals().forEach(spec -> hospitals.put(spec.id(), new HospitalModel(spec)));
        clock = new SimulationClock();
        generator = new ScenarioGenerator(config, patients, hospitals);
        router = new PerceptRouter(territorial, hospitals, patients, clock);
        actions = new ActionExecutor(territorial, hospitals, patients, clock);

        clock.register(territorial::onTick);
        hospitals.values().forEach(h -> clock.register(h::onTick));
        clock.register(generator::onTick);
        clock.register(tick -> informAgsEnvironmentChanged());

        completion = new CompletionWatcher(generator, patients, clock, this::onSimulationFinished);
        clock.register(completion);

        if (guiRequested && !GraphicsEnvironment.isHeadless()) {
            openGui();
        }

        instance = this;
        if (!manualClock) {
            clock.start(periodMs);
        }
        final String summary = "MedNetEnv up: scenario=" + config.name() + " seed=" + seed
                + " hospitals=" + hospitals.keySet() + (manualClock ? " (manual clock)" : "");
        LOGGER.info(summary);
        signalReady();
    }

    private static void signalReady() {
        final String port = System.getProperty(READY_PORT_PROPERTY);
        if (port == null) {
            return;
        }
        try (Socket ignored = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(port))) {
            // the connection itself is the handshake; there is nothing to write
        } catch (final IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "could not signal readiness on port " + port, e);
        }
    }

    @Override
    public Collection<Literal> getPercepts(final String agName) {
        return router == null ? List.of() : router.perceptsFor(agName);
    }

    @Override
    public boolean executeAction(final String agName, final Structure action) {
        if (!RbacPolicy.check(agName, action.getFunctor())) {
            ProbeRegistry.current().onEvent("rbac_denied", agName, action.getFunctor());
            LOGGER.warning(() -> "RBAC: denied " + action + " to " + agName);
            return false;
        }
        final boolean ok = actions.execute(agName, action);
        if (ok) {
            informAgsEnvironmentChanged();
        }
        return ok;
    }

    private void openGui() {
        SwingUtilities.invokeLater(() -> {
            if (stopped) {
                return;
            }
            try {
                final MedNetGui view = new MedNetGui(territorial, hospitals, patients, clock);
                view.open();
                gui = view;
            } catch (final RuntimeException | Error e) {
                LOGGER.log(Level.WARNING, "dual view unavailable; the simulation runs without it", e);
            }
        });
    }

    private void onSimulationFinished() {
        final long lastTick = clock.currentTick();
        LOGGER.info(() -> "Simulation finished at tick " + lastTick
                + ": all " + patients.all().size() + " patients discharged, clock halted");
        if (gui != null) {
            gui.markFinished(lastTick);
        }
    }

    @Override
    public void stop() {
        stopped = true;
        if (clock != null) {
            clock.stop();
        }
        if (gui != null) {
            gui.close();
            gui = null;
        }
        instance = null;
        super.stop();
    }

    public static MedNetEnv getInstance() {
        return instance;
    }

    public SimulationClock clock() {
        return clock;
    }

    public boolean isSimulationFinished() {
        return completion != null && completion.isFinished();
    }

    public TerritorialModel territorial() {
        return territorial;
    }

    public HospitalModel hospital(final String id) {
        return hospitals.get(id);
    }

    public PatientRegistry patients() {
        return patients;
    }
}
