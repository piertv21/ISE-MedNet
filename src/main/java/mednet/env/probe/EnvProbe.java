package mednet.env.probe;

public interface EnvProbe {

    default void onEvent(String type, Object... data) {
    }
}
