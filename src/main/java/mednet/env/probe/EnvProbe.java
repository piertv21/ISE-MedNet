package mednet.env.probe;

// Observation hook for environment events. Production uses the no-op default; tests
// install a recording probe to assert on the simulation timeline.
public interface EnvProbe {

    default void onEvent(String type, Object... data) {
    }
}
