package mednet.env.probe;

public final class ProbeRegistry {

    private static final EnvProbe NOOP = new EnvProbe() {
    };

    private static volatile EnvProbe current = NOOP;

    private ProbeRegistry() {
    }

    public static void install(final EnvProbe probe) {
        current = probe == null ? NOOP : probe;
    }

    public static void reset() {
        current = NOOP;
    }

    public static EnvProbe current() {
        return current;
    }
}
