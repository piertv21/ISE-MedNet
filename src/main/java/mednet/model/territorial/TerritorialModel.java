package mednet.model.territorial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mednet.model.scenario.ScenarioConfig;

public final class TerritorialModel {

    public static final int WIDTH = 30;
    public static final int HEIGHT = 30;

    private final Map<String, AmbulanceState> ambulances = new LinkedHashMap<>();
    private final List<HospitalSite> sites;

    public TerritorialModel(final ScenarioConfig config) {
        for (final ScenarioConfig.AmbulanceSpec spec : config.ambulances()) {
            ambulances.put(spec.name(), new AmbulanceState(spec.name(), spec.x(), spec.y()));
        }
        this.sites = config.hospitals().stream()
                .map(h -> new HospitalSite(h.id(), h.x(), h.y()))
                .toList();
    }

    public synchronized void onTick(final long tick) {
        for (final AmbulanceState ambulance : ambulances.values()) {
            MovementEngine.step(ambulance);
        }
    }

    public synchronized Optional<AmbulanceState> ambulance(final String name) {
        return Optional.ofNullable(ambulances.get(name));
    }

    public synchronized List<AmbulanceState> ambulances() {
        return List.copyOf(ambulances.values());
    }

    public List<HospitalSite> sites() {
        return sites;
    }

    public Optional<HospitalSite> siteAt(final int x, final int y) {
        return sites.stream().filter(s -> s.x() == x && s.y() == y).findFirst();
    }

    public Optional<HospitalSite> siteOfHospitalAgent(final String hospitalAgent) {
        return sites.stream().filter(s -> s.hospitalAgent().equals(hospitalAgent)).findFirst();
    }

    public synchronized int eta(final String ambulanceName, final int x, final int y) {
        final AmbulanceState a = ambulances.get(ambulanceName);
        if (a == null) {
            return Integer.MAX_VALUE;
        }
        return Math.abs(a.x() - x) + Math.abs(a.y() - y);
    }
}
