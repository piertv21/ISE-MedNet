package mednet.model;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import mednet.model.hospital.HospitalModel;
import mednet.model.patient.PatientRegistry;
import mednet.model.scenario.ScenarioConfig;
import mednet.model.scenario.ScenarioGenerator;

class ScenarioGeneratorTest {

    @Test
    void sameSeedYieldsIdenticalTimeline() {
        final var a = ScenarioConfig.byName("default", 42).events().toString();
        final var b = ScenarioConfig.byName("default", 42).events().toString();
        final var c = ScenarioConfig.byName("default", 43).events().toString();
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void eventsAreSortedByTick() {
        final var events = ScenarioConfig.byName("default", 7).events();
        for (int i = 1; i < events.size(); i++) {
            assertThat(events.get(i).tick()).isGreaterThanOrEqualTo(events.get(i - 1).tick());
        }
    }

    @Test
    void patientsActivateAtTheirScheduledTick() {
        final ScenarioConfig config = ScenarioConfig.byName("preemption", 1);
        final PatientRegistry registry = new PatientRegistry();
        final Map<String, HospitalModel> hospitals = hospitalsOf(config);
        final ScenarioGenerator generator = new ScenarioGenerator(config, registry, hospitals);

        generator.onTick(4);
        assertThat(registry.find("patient1")).isEmpty();
        generator.onTick(5);
        assertThat(registry.find("patient1")).hasValueSatisfying(p -> {
            assertThat(p.isActive()).isTrue();
            assertThat(p.pathology()).isEqualTo("fracture");
        });
        generator.onTick(25);
        assertThat(registry.find("patient2")).isPresent();
    }

    @Test
    void walkInEventsReachTheHospitalModel() {
        final ScenarioConfig config = ScenarioConfig.byName("default", 42);
        final PatientRegistry registry = new PatientRegistry();
        final Map<String, HospitalModel> hospitals = hospitalsOf(config);
        final ScenarioGenerator generator = new ScenarioGenerator(config, registry, hospitals);

        final int freeBefore = hospitals.get("h1").bedsFree();
        generator.onTick(40);
        assertThat(hospitals.get("h1").bedsFree()).isEqualTo(freeBefore - 1);
    }

    private static Map<String, HospitalModel> hospitalsOf(final ScenarioConfig config) {
        final Map<String, HospitalModel> hospitals = new LinkedHashMap<>();
        config.hospitals().forEach(spec -> hospitals.put(spec.id(), new HospitalModel(spec)));
        return hospitals;
    }
}
