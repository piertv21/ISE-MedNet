package mednet.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mednet.model.scenario.ScenarioConfig;
import mednet.model.territorial.AmbulanceState;
import mednet.model.territorial.TerritorialModel;

class TerritorialModelTest {

    private TerritorialModel model;

    @BeforeEach
    void setUp() {
        model = new TerritorialModel(ScenarioConfig.byName("empty", 1));
    }

    @Test
    void ambulanceMovesOneCellPerTickAndArrives() {
        final AmbulanceState a1 = model.ambulance("ambulance_a1").orElseThrow();
        a1.setTarget(17, 16);
        assertThat(a1.isArrived()).isFalse();
        model.onTick(1);
        model.onTick(2);
        model.onTick(3);
        assertThat(a1.x()).isEqualTo(17);
        assertThat(a1.y()).isEqualTo(16);
        assertThat(a1.isArrived()).isTrue();
        model.onTick(4);
        assertThat(a1.x()).isEqualTo(17);
    }

    @Test
    void settingTheCurrentCellAsTargetArrivesImmediately() {
        final AmbulanceState a1 = model.ambulance("ambulance_a1").orElseThrow();
        a1.setTarget(15, 15);
        assertThat(a1.isArrived()).isTrue();
    }

    @Test
    void hospitalSitesAreDiscoverableByPositionAndAgentName() {
        assertThat(model.siteAt(5, 5)).hasValueSatisfying(site -> {
            assertThat(site.id()).isEqualTo("h1");
            assertThat(site.hospitalAgent()).isEqualTo("hospital_h1");
            assertThat(site.nurseAgent()).isEqualTo("triage_nurse_h1");
        });
        assertThat(model.siteAt(0, 0)).isEmpty();
        assertThat(model.siteOfHospitalAgent("hospital_h3")).isPresent();
        assertThat(model.sites()).hasSize(3);
    }
}
