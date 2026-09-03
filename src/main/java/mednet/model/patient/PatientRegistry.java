package mednet.model.patient;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PatientRegistry {

    private final Map<String, PatientRecord> patients = new ConcurrentHashMap<>();

    public void register(final PatientRecord record) {
        patients.put(record.name(), record);
    }

    public Optional<PatientRecord> find(final String name) {
        return Optional.ofNullable(patients.get(name));
    }

    public Collection<PatientRecord> all() {
        return patients.values();
    }
}
