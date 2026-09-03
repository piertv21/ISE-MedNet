package mednet.testsupport;

import jason.infra.local.RunLocalMAS;

public final class MasTestRunner {

    private MasTestRunner() {
    }

    public static void boot(final String mas2jPath) {
        final Thread masThread = new Thread(() -> {
            try {
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
