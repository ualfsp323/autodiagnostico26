package es.ual.dra.autodiagnostico.service.core;

import es.ual.dra.autodiagnostico.model.entitity.core.Engine;
import es.ual.dra.autodiagnostico.model.entitity.core.Vehicle;
import es.ual.dra.autodiagnostico.model.entitity.core.VehicleModel;

import es.ual.dra.autodiagnostico.repository.EngineRepository;
import es.ual.dra.autodiagnostico.repository.VehicleModelRepository;
import es.ual.dra.autodiagnostico.repository.VehicleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Commit
class DataPopulationServiceIntegrationTest {

    @Autowired
    private CarDataPopulationService dataPopulationService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleModelRepository vehicleModelRepository;

    @Autowired
    private EngineRepository engineRepository;

    /**
     * IMPORTANT:
     *
     * Your rewritten architecture expects:
     *
     * scraper-output/
     * └── vag/
     * ├── ultimatespecs-seat.json
     * └── carparts-vag.json
     *
     * So create this structure under:
     *
     * src/test/resources/test-scraper-output/
     */
    private static final String TEST_ROOT = "src/test/resources/test-scraper-output";

    @BeforeEach
    void cleanDatabase() {

        vehicleModelRepository.deleteAll();
        engineRepository.deleteAll();
        vehicleRepository.deleteAll();
    }

    @Test
    void shouldPopulateDatabaseFromJsonFiles()
            throws Exception {

        dataPopulationService.scanAndPopulate(TEST_ROOT);

        List<Vehicle> vehicles = vehicleRepository.findAll();

        assertFalse(
                vehicles.isEmpty(),
                "Vehicles should be populated");

        // ------------------------------------------------
        // COMPROBAR SEAT MII
        // ------------------------------------------------

        Vehicle mii = vehicles.stream()
                .filter(v -> v.getName()
                        .equalsIgnoreCase(
                                "Seat Mii Ficha Tecnica"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Seat Mii should exist"));

        assertEquals(
                "seat",
                mii.getBrand());

        assertEquals(
                "242.1 cm / 95.31 pulgadas",
                mii.getWheelbase());

        // ------------------------------------------------
        // COMPROBAR VARIANTES IBIZA
        // ------------------------------------------------

        List<Vehicle> ibizas = vehicles.stream()
                .filter(v -> v.getName()
                        .toLowerCase()
                        .contains("ibiza"))
                .toList();

        assertEquals(
                6,
                ibizas.size(),
                "Should contain 6 Ibiza variants");

        // ------------------------------------------------
        // COMPROBAR MODELOS VEHÍCULOS
        // ------------------------------------------------

        List<VehicleModel> models = vehicleModelRepository.findAll();

        assertFalse(
                models.isEmpty(),
                "Vehicle models should exist");

        assertTrue(
                models.size() >= 2,
                "Should have at least 2 models");

        // ------------------------------------------------
        // COMPROBAR MOTORES
        // ------------------------------------------------

        List<Engine> engines = engineRepository.findAll();

        assertFalse(
                engines.isEmpty(),
                "Engines should exist");

        // ------------------------------------------------
        // OUTPUT PARA DEBUG (OPCIONAL)
        // ------------------------------------------------

        System.out.println("\n=== VEHICLES ===");
        vehicles.forEach(System.out::println);

        System.out.println("\n=== MODELS ===");
        models.forEach(System.out::println);

        System.out.println("\n=== ENGINES ===");
        engines.forEach(System.out::println);
    }
}