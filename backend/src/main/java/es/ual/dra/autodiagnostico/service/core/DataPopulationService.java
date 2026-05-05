package es.ual.dra.autodiagnostico.service.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.ual.dra.autodiagnostico.model.entitity.core.Engine;
import es.ual.dra.autodiagnostico.model.entitity.core.EngineType;
import es.ual.dra.autodiagnostico.model.entitity.core.TransmissionType;
import es.ual.dra.autodiagnostico.model.entitity.core.Vehicle;
import es.ual.dra.autodiagnostico.model.entitity.core.VehicleModel;
import es.ual.dra.autodiagnostico.repository.EngineRepository;
import es.ual.dra.autodiagnostico.repository.VehicleModelRepository;
import es.ual.dra.autodiagnostico.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DataPopulationService {

    private final VehicleRepository vehicleRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final EngineRepository engineRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String currentBrand;

    /**
     * Scans a directory for JSON files and populates the database.
     * 
     * @param rootPath Path to the root folder (e.g. scraper-output/Groups)
     */
    public void scanAndPopulate(String rootPath) {
        try {
            Files.walk(Paths.get(rootPath))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().startsWith("carparts")) // Skip carparts files
                    .forEach(this::processFile);
        } catch (IOException e) {
            log.error("Error scanning directory {}: {}", rootPath, e.getMessage());
        }
    }

    private void processFile(Path path) {
        // Extract brand from filename (e.g. ultimatespecs-Seat.json -> Seat)
        String fileName = path.getFileName().toString();
        if (fileName.contains("-")) {
            currentBrand = fileName.substring(fileName.lastIndexOf("-") + 1, fileName.lastIndexOf("."));
        } else {
            // Or use the parent folder name as brand if filename is generic
            currentBrand = path.getParent().getFileName().toString();
        }

        log.info("Processing file: {}", fileName);
        populateFromFile(path.toString());
    }

    public void populateFromFile(String filePath) {
        try {
            JsonNode root = objectMapper.readTree(new File(filePath));
            JsonNode models = root.get("models");
            if (models != null && models.isArray()) {
                for (JsonNode modelNode : models) {
                    processModel(modelNode);
                }
            }
        } catch (IOException e) {
            log.error("Error reading file {}: {}", filePath, e.getMessage());
        }
    }

    private void processModel(JsonNode modelNode) {
        JsonNode versions = modelNode.get("versions");
        if (versions != null && versions.isArray()) {
            for (JsonNode versionNode : versions) {
                Vehicle vehicle = mapToVehicle(versionNode);
                Vehicle finalVehicle = vehicleRepository.findByNameAndBrand(vehicle.getName(), vehicle.getBrand())
                        .orElseGet(() -> vehicleRepository.save(vehicle));

                processTableVersions(versionNode.get("table_versions"), finalVehicle);
            }
        }
    }

    private Vehicle mapToVehicle(JsonNode versionNode) {
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand(currentBrand);
        vehicle.setName(versionNode.get("actualFinalModelName").asText());

        JsonNode specs = versionNode.get("specifications");
        if (specs != null) {
            vehicle.setWheelbase(getSpec(specs, "Batalla:"));
            vehicle.setAverageConsumptionPer100km(getSpec(specs, "Consumos Medio:"));
            vehicle.setHeight(getSpec(specs, "Alto:"));
            vehicle.setLength(getSpec(specs, "Largo:"));
            vehicle.setWidth(getSpec(specs, "Ancho:"));
            vehicle.setWeight(getSpec(specs, "Peso:"));
            vehicle.setPeriodOfProduction(getSpec(specs, "Período de producción:"));
            vehicle.setEngineDisplacement(getSpec(specs, "Cilindrada:"));
        }
        return vehicle;
    }

    private String getSpec(JsonNode specs, String key) {
        JsonNode node = specs.get(key);
        return node != null ? node.asText() : null;
    }

    private void processTableVersions(JsonNode tableVersions, Vehicle vehicle) {
        if (tableVersions != null && tableVersions.isArray()) {
            for (JsonNode entry : tableVersions) {
                String modelName = extractModelName(entry);
                EngineType type = detectEngineType(entry);

                if (modelName != null) {
                    Engine engine = engineRepository.findByNameAndEngineType(modelName, type)
                            .orElseGet(() -> engineRepository.save(Engine.builder()
                                    .name(modelName)
                                    .engineType(type)
                                    .build()));

                    VehicleModel vehicleModel = vehicleModelRepository.findByModelNameAndVehicle(modelName, vehicle)
                            .orElseGet(() -> {
                                VehicleModel vm = VehicleModel.builder()
                                        .modelName(modelName)
                                        .vehicle(vehicle)
                                        .engine(engine)
                                        .build();

                                vm.setTransmission(inferTransmissionType(vm, score));
                                return vehicleModelRepository.save(vm);
                            });
                }
            }
        }
    }

    /**
     * Computes the logit-scale probability of the given VehicleModel having an AT
     * (Automatic
     * Transmission)
     * 
     * @param vehicleModel The vehicle model to compute the probability for
     * @return The logit-scale probability of the given VehicleModel having an AT
     */
    private double computeATScore(VehicleModel vehicleModel) {
        double score = 0;

        int year = vehicleModel.getYearFirstProduction();
        if (year < 2005) {
            score -= 3.0;

        }

        if (year >= 2005 && year < 2012) {
            score -= 1.5;
        }

        if (year >= 2012 && year < 2016) {
            score += 0.5;
        }

        if (year >= 2016 && year < 2020) {
            score += 1.5;
        }

        if (year >= 2020) {
            score += 3.0;
        }

        // Dependiendo de su motorización
        EngineType engineType = vehicleModel.getEngine().getEngineType();

        if (engineType == EngineType.BEV) {
            score += 2.0;
        }

        if (engineType == EngineType.PHEV || engineType == EngineType.HEV) {
            score += 1.0;
        }

        // Con gasolina/diésel no hay cambio

        // Heurística por marca
        switch(currentBrand) {
            case 
        }

        return score;

    }

    /**
     * 
     * @param currentBrand La marca actual en formato JSON que se está leyendo,
     *                     sin ningún otro texto adicional aparte del nombre de la
     *                     marca
     * @param vehicleModel Se asume que vehicleModel tiene un motor ya poblado,
     *                     pero no una transmisión
     * @param score        Entrada lógica para el modelo probabilístico
     *                     que infiere el tipo de transmisión
     * @return El tipo de transmisión inferido
     */

    private TransmissionType inferTransmissionType(
            String currentBrand,
            VehicleModel vehicleModel,
            double score // renamed: this is NOT probability, it's the logit input
    ) {

        double atProb = 1.0 / (1.0 + Math.exp(-score));
        Engine engine = vehicleModel.getEngine();
        String processedNormalBrand = currentBrand.toLowerCase().trim();

        // Sobreescrituras de máxima confianza
        if (engine != null) {
            EngineType engineType = engine.getEngineType();

            if (engineType != null) {

                // Eléctricos -> AT
                if (engineType == EngineType.BEV) {
                    return TransmissionType.AT;
                }

                // Lógica coches híbridos
                if (engineType == EngineType.PHEV || engineType == EngineType.HEV) {

                    if (processedNormalBrand != null) {
                        switch (processedNormalBrand) {
                            case "toyota":
                            case "lexus":
                                return TransmissionType.eCVT;
                            case "honda":
                                return TransmissionType.eCVT;
                            default:
                                return TransmissionType.CVT;
                        }
                    }
                    return TransmissionType.CVT;
                }
            }
        }

        // Si es probablemente manual, devolver manual
        if (atProb < 0.4) {
            return TransmissionType.MT;
        }

        // Refinar tipo de AT
        String brand = vehicleModel.getModelName();

        if (brand != null) {
            switch (brand) {

                // Grupo VAG → DCT
                case "volkswagen":
                case "audi":
                case "seat":
                case "skoda":
                case "cupra":
                    return TransmissionType.DCT;

                // Convertidores de par premium
                case "bmw":
                case "mercedes-benz":
                case "jaguar":
                case "land rover":
                case "volvo":
                    return TransmissionType.AT;

                // CVT predominante
                case "nissan":
                case "subaru":
                    return TransmissionType.CVT;

                // AT predominante
                case "mazda":
                case "toyota":
                    return TransmissionType.AT;

                // DCT coreano
                case "hyundai":
                case "kia":
                    return TransmissionType.DCT;

                default:
                    if (atProb > 0.75) {
                        return TransmissionType.AT;
                    } else {
                        return TransmissionType.CVT;
                    }
            }
        }

        return atProb > 0.5 ? TransmissionType.AT : TransmissionType.MT;
    }

    private String extractModelName(JsonNode entry) {
        // The model name is usually the value of the engine type key
        Iterator<Map.Entry<String, JsonNode>> fields = entry.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if (isEngineCategoryKey(key)) {
                return field.getValue().asText();
            }
        }
        // Fallback to "Otros" if no category key found
        if (entry.has("Otros")) {
            return entry.get("Otros").asText();
        }
        return null;
    }

    private boolean isEngineCategoryKey(String key) {
        return key.contains("Gasolina") || key.contains("Diesel") || key.contains("Diésel")
                || key.contains("Eléctrico") || key.contains("Híbrido") || key.contains("HEV")
                || key.contains("PHEV") || key.contains("REEV");
    }

    private EngineType detectEngineType(JsonNode entry) {
        Iterator<String> fieldNames = entry.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            if (key.contains("Gasolina"))
                return EngineType.PETROL;
            if (key.contains("Diesel") || key.contains("Diésel"))
                return EngineType.DIESEL;
            if (key.contains("Eléctrico"))
                return EngineType.BEV;
            if (key.contains("HEV"))
                return EngineType.HEV;
            if (key.contains("PHEV"))
                return EngineType.PHEV;
            if (key.contains("REEV"))
                return EngineType.REEV;
        }
        return EngineType.PETROL; // Default
    }
}
