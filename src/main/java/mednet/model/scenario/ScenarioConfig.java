package mednet.model.scenario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import mednet.model.patient.SeverityCode;
import mednet.prolog.MedicalKb;

public final class ScenarioConfig {

    public record HospitalSpec(String id, int x, int y, int beds, List<String> specializations,
            List<String> equipment) {
    }

    public record AmbulanceSpec(String name, int x, int y) {
    }

    public static final List<String> STANDARD_EQUIPMENT =
            List.of("ct_scanner", "xray_room", "ecg_station", "lab", "operating_room");

    private static final List<String> PATHOLOGIES = List.copyOf(MedicalKb.pathologies());

    private static SeverityCode defaultCodeOf(final String pathology) {
        return MedicalKb.defaultCode(pathology)
                .map(SeverityCode::fromAtom)
                .orElseThrow(() -> new IllegalStateException("No default_code/2 for " + pathology));
    }

    private final String name;
    private final List<HospitalSpec> hospitals;
    private final List<AmbulanceSpec> ambulances;
    private final List<ScenarioEvent> events;

    private ScenarioConfig(final String name, final List<HospitalSpec> hospitals,
            final List<AmbulanceSpec> ambulances, final List<ScenarioEvent> events) {
        this.name = name;
        this.hospitals = List.copyOf(hospitals);
        this.ambulances = List.copyOf(ambulances);
        final List<ScenarioEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong(ScenarioEvent::tick));
        this.events = List.copyOf(sorted);
    }

    public String name() {
        return name;
    }

    public List<HospitalSpec> hospitals() {
        return hospitals;
    }

    public List<AmbulanceSpec> ambulances() {
        return ambulances;
    }

    public List<ScenarioEvent> events() {
        return events;
    }

    private static List<HospitalSpec> standardHospitals() {
        return List.of(
                new HospitalSpec("h1", 5, 5, 3, List.of("cardiology", "general"), STANDARD_EQUIPMENT),
                new HospitalSpec("h2", 24, 24, 2, List.of("neurology", "general"), STANDARD_EQUIPMENT),
                new HospitalSpec("h3", 5, 24, 2, List.of("trauma_surgery", "general"), STANDARD_EQUIPMENT));
    }

    private static List<AmbulanceSpec> standardAmbulances() {
        return List.of(
                new AmbulanceSpec("ambulance_a1", 15, 15),
                new AmbulanceSpec("ambulance_a2", 14, 15),
                new AmbulanceSpec("ambulance_a3", 16, 15));
    }

    public static ScenarioConfig byName(final String name, final long seed) {
        return switch (name) {
            case "default" -> defaultScenario(seed);
            case "e2e" -> e2eScenario();
            case "preemption" -> preemptionScenario();
            case "divert" -> divertScenario();
            case "severity" -> severityScenario();
            case "empty" -> new ScenarioConfig("empty", standardHospitals(), standardAmbulances(), List.of());
            default -> throw new IllegalArgumentException("Unknown scenario: " + name);
        };
    }

    private static ScenarioConfig defaultScenario(final long seed) {
        final Random random = new Random(seed);
        final List<ScenarioEvent> events = new ArrayList<>();
        long tick = 5;
        for (int i = 1; i <= 5; i++) {
            final String pathology = PATHOLOGIES.get(random.nextInt(PATHOLOGIES.size()));
            final SeverityCode trueCode = defaultCodeOf(pathology);
            final SeverityCode guess = random.nextInt(5) == 0 && trueCode.priority() < 3
                    ? SeverityCode.values()[trueCode.priority() + 1]
                    : trueCode;
            final int x = 2 + random.nextInt(26);
            final int y = 2 + random.nextInt(26);
            events.add(new ScenarioEvent.ActivatePatient(tick, "patient" + i, pathology, guess,
                    trueCode, x, y));
            tick += 10 + random.nextInt(25);
        }
        events.add(new ScenarioEvent.WalkIn(40, "h1"));
        events.add(new ScenarioEvent.WalkIn(42, "h1"));
        events.add(new ScenarioEvent.WalkIn(44, "h1"));
        return new ScenarioConfig("default", standardHospitals(), standardAmbulances(), events);
    }

    private static ScenarioConfig e2eScenario() {
        final List<ScenarioEvent> events = List.of(
                new ScenarioEvent.ActivatePatient(5, "patient1", "stroke",
                        SeverityCode.RED, SeverityCode.RED, 20, 20),
                new ScenarioEvent.ActivatePatient(12, "patient2", "fracture",
                        SeverityCode.GREEN, SeverityCode.GREEN, 8, 10),
                new ScenarioEvent.ActivatePatient(20, "patient3", "abdominal_pain",
                        SeverityCode.GREEN, SeverityCode.GREEN, 10, 22),
                new ScenarioEvent.ActivatePatient(28, "patient4", "major_trauma",
                        SeverityCode.YELLOW, SeverityCode.YELLOW, 7, 25));
        return new ScenarioConfig("e2e", standardHospitals(), standardAmbulances(), events);
    }

    private static ScenarioConfig divertScenario() {
        final List<ScenarioEvent> events = List.of(
                new ScenarioEvent.ActivatePatient(5, "patient1", "cardiac_arrest",
                        SeverityCode.RED, SeverityCode.RED, 20, 20),
                new ScenarioEvent.WalkIn(45, "h1"),
                new ScenarioEvent.WalkIn(46, "h1"),
                new ScenarioEvent.WalkIn(47, "h1"));
        return new ScenarioConfig("divert", standardHospitals(), standardAmbulances(), events);
    }

    private static ScenarioConfig severityScenario() {
        final List<ScenarioEvent> events = List.of(
                new ScenarioEvent.WalkIn(2, "h1"),
                new ScenarioEvent.WalkIn(3, "h1"),
                new ScenarioEvent.ActivatePatient(5, "patient1", "fracture",
                        SeverityCode.GREEN, SeverityCode.GREEN, 7, 14),
                new ScenarioEvent.ActivatePatient(20, "patient2", "abdominal_pain",
                        SeverityCode.RED, SeverityCode.RED, 3, 14));
        return new ScenarioConfig("severity", standardHospitals(), standardAmbulances(), events);
    }

    private static ScenarioConfig preemptionScenario() {
        final List<ScenarioEvent> events = List.of(
                new ScenarioEvent.ActivatePatient(5, "patient1", "fracture",
                        SeverityCode.GREEN, SeverityCode.YELLOW, 8, 8),
                new ScenarioEvent.ActivatePatient(20, "patient2", "cardiac_arrest",
                        SeverityCode.RED, SeverityCode.RED, 9, 9));
        return new ScenarioConfig("preemption", standardHospitals(), standardAmbulances(), events);
    }
}
