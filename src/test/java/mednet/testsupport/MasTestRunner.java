package mednet.testsupport;

import jason.infra.local.RunLocalMAS;

// Boots a Jason MAS on a daemon thread for integration tests. The test logging config
// replaces the Swing console with a plain handler and --no-net disables RMI and the mind
// inspector. Each test class runs in its own JVM via forkEvery = 1, so the global Jason
// runtime state does not leak between classes.
public final class MasTestRunner {

    private MasTestRunner() {
    }

    public static void boot(final String mas2jPath) {
        final Thread masThread = new Thread(() -> {
            try {
                // the project file must come first, RunLocalMAS reads args[0]
                RunLocalMAS.main(new String[] {
                        mas2jPath,
                        "--log-conf", "src/test/resources/logging-test.properties",
                        "--no-net",
                });
            } catch (final Exception e) {
                throw new IllegalStateException("MAS boot failed for " + mas2jPath, e);
            }
        }, "mas-under-test");
        masThread.setDaemon(true);
        masThread.start();
    }
}
