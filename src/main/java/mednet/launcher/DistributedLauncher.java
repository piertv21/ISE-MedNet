package mednet.launcher;

import mednet.env.MedNetEnv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public final class DistributedLauncher {

    private static final String PROJECT = "mednet_jade.mas2j";
    private static final int JADE_PORT = 1099;
    private static final List<String> HOSPITALS = List.of("h1", "h2", "h3");
    private static final long STARTUP_TIMEOUT_MS = 60_000;

    private DistributedLauncher() {
    }

    public static void main(final String[] args) throws Exception {
        final String javaBin = System.getProperty("java.home") + "/bin/java";
        final String classpath = System.getProperty("java.class.path");
        final List<Process> children = new ArrayList<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> children.forEach(Process::destroy)));

        try (ServerSocket rendezvous = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            rendezvous.setSoTimeout((int) STARTUP_TIMEOUT_MS);

            final Process main = start(javaBin, classpath, "main",
                    List.of(PROJECT, "-container-name", "main"),
                    List.of("-D" + MedNetEnv.READY_PORT_PROPERTY + "=" + rendezvous.getLocalPort()));
            children.add(main);
            awaitMainContainer(main, rendezvous);

            for (final String hospital : HOSPITALS) {
                children.add(start(javaBin, classpath, hospital, List.of(
                        PROJECT,
                        "-container",
                        "-host", "localhost",
                        "-port", String.valueOf(JADE_PORT),
                        "-container-name", "container_" + hospital), List.of()));
            }
        }

        for (final Process child : children) {
            child.waitFor();
        }
    }

    private static Process start(final String javaBin, final String classpath, final String label,
            final List<String> jasonArgs, final List<String> extraJvmArgs) throws IOException {
        final List<String> command = new ArrayList<>(List.of(javaBin, "-cp", classpath));
        command.addAll(extraJvmArgs);
        for (final String property : List.of("java.awt.headless", "java.util.logging.config.file")) {
            final String value = System.getProperty(property);
            if (value != null) {
                command.add("-D" + property + "=" + value);
            }
        }
        command.add("jason.infra.jade.RunJadeMAS");
        command.addAll(jasonArgs);

        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + label + "] " + line);
                }
            } catch (final IOException ignored) {
            }
        }, "pump-" + label);
        pump.setDaemon(true);
        pump.start();
        return process;
    }

    private static void awaitMainContainer(final Process main, final ServerSocket rendezvous)
            throws InterruptedException {
        final Thread watchdog = new Thread(() -> {
            try {
                main.waitFor();
                rendezvous.close();
            } catch (final InterruptedException | IOException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "main-container-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        try {
            rendezvous.accept().close();
            System.out.println("[launcher] main container ready: environment up on port " + JADE_PORT);
        } catch (final SocketTimeoutException e) {
            throw new IllegalStateException("The JADE main container did not come up within "
                    + STARTUP_TIMEOUT_MS / 1000 + "s; see its output above");
        } catch (final SocketException e) {
            throw new IllegalStateException("The JADE main container exited during startup (code "
                    + (main.isAlive() ? "unknown" : main.exitValue()) + "); see its output above");
        } catch (final IOException e) {
            throw new IllegalStateException("Waiting for the JADE main container failed", e);
        }
    }
}
