package es.ual.dra.autodiagnostico.service.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.ual.dra.autodiagnostico.model.entitity.core.Engine;
import es.ual.dra.autodiagnostico.model.entitity.core.EngineType;
import es.ual.dra.autodiagnostico.model.entitity.core.Product;
import es.ual.dra.autodiagnostico.model.entitity.core.TransmissionType;
import es.ual.dra.autodiagnostico.model.entitity.core.Vehicle;
import es.ual.dra.autodiagnostico.model.entitity.core.VehicleModel;
import es.ual.dra.autodiagnostico.repository.EngineRepository;
import es.ual.dra.autodiagnostico.repository.ProductRepository;
import es.ual.dra.autodiagnostico.repository.VehicleModelRepository;
import es.ual.dra.autodiagnostico.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DataPopulationService {

    private final VehicleRepository vehicleRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final EngineRepository engineRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Contiene la marca actual siendo procesada SIN el prefijo "ultimatespecs-" ni
    // otras palabras. Además, está en minúsculas.
    private static String currentBrand;
    private static String currentCarGroup;
    private static String generalPartsJSONPath = "src/main/resources/general-car-parts.json";

    // Comentado ya que ahora mismo previene la ejecución y no es un método usado
    // ahora mismo en el test
    // /**
    // * Scans a directory for JSON files and populates the database.
    // *
    // * @param rootPath Path to the root folder (e.g. scraper-output/Groups)
    // */
    // public void scanAndPopulate(String rootPath) {
    // try {
    // Files.walk(Paths.get(rootPath))
    // .filter(Files::isRegularFile)
    // .filter(p -> p.toString().endsWith(".json"))
    // .filter(p -> !p.getFileName().toString().startsWith("carparts")) // Skip
    // carparts files
    // .forEach(path -> {
    // this.processFilePath(path);
    // this.populateFromFile(path.toString());
    // });
    // } catch (IOException e) {
    // log.error("Error scanning directory {}: {}", rootPath, e.getMessage());
    // }
    // }

    private void processFilePath(Path filePath, Path carPartsGroupPath) {
        String carPartsFileName = carPartsGroupPath.getFileName().toString();
        currentCarGroup = carPartsFileName.substring(0, carPartsFileName.lastIndexOf(".")).toLowerCase()
                .trim();
        if (currentCarGroup.contains("-")) {
            currentCarGroup = currentCarGroup.substring(0, currentCarGroup.lastIndexOf("-")).trim();
        }
        String fileName = filePath.getFileName().toString();
        if (fileName.contains("-")) {
            currentBrand = fileName.substring(fileName.lastIndexOf("-") + 1, fileName.lastIndexOf(".")).toLowerCase()
                    .trim();
        } else {
            currentBrand = fileName;
        }

        log.info("Processing file: {}", fileName);
    }

    public void populateFromFile(String filePathStr, String carPartsGroupPathStr) {
        List<Map<Vehicle, List<VehicleModel>>> allVehiclesModels = new ArrayList<>();
        try {
            Path carPartsGroupPath = Paths.get(carPartsGroupPathStr);
            processFilePath(Paths.get(filePathStr), carPartsGroupPath);
            JsonNode root = objectMapper.readTree(new File(filePathStr));
            JsonNode models = root.get("models");
            if (models != null && models.isArray()) {
                for (JsonNode modelNode : models) {
                    Map<Vehicle, List<VehicleModel>> mapOfVehicleAndModels = processModel(modelNode);
                    for (List<VehicleModel> listOfVM : mapOfVehicleAndModels.values()) {
                        for (VehicleModel vm : listOfVM) {
                            processCarPartsForVehicleModel(carPartsGroupPath, vm);
                        }
                    }
                    allVehiclesModels.add(mapOfVehicleAndModels);
                }
            }
        } catch (IOException e) {
            log.error("Error reading file {}: {}", filePathStr, e.getMessage());
        }
    }

    private Product findPartByName(String partName) {
        if (partName == null || partName.isEmpty())
            return null;

        try {
            JsonNode root = objectMapper.readTree(new File(generalPartsJSONPath));
            if (!root.isArray())
                return null;

            String searchName = partName.toLowerCase().trim();
            LevenshteinDistance distance = LevenshteinDistance.getDefaultInstance();

            for (JsonNode node : root) {

                String entryName = node.get("name").asText().toLowerCase().trim();
                boolean exactMatch = entryName.equals(searchName);
                boolean containsMatch = !exactMatch ? (entryName.contains(searchName) ||
                        searchName.contains(entryName)) : false;
                boolean hasDecentError = false;
                int res = distance.apply(entryName, searchName);
                int errorLimit = Math.max(3, entryName.length() / 4); // 3 errores máximo si la longitud es
                // 12 o menos, o 1 error por cada 4 caracteres.

                if (!exactMatch && !containsMatch) {
                    hasDecentError = res <= errorLimit;
                }

                boolean found = exactMatch || containsMatch || hasDecentError;

                String priceRangeStr = node.get("priceRange").asText().replace("€", "").replace(" ", "");
                String[] priceRangeArray = priceRangeStr.split("-");
                double lowRangePrice = Double.parseDouble(priceRangeArray[0].trim());
                double highRangePrice = Double.parseDouble(priceRangeArray[1].trim());

                if (found) {
                    return Product.builder()
                            .name(node.get("name").asText())
                            .description(node.get("description").asText())
                            .lowRangePrice(lowRangePrice)
                            .highRangePrice(highRangePrice)
                            .image(node.get("image").asText())
                            .build();
                } else {
                    return null;
                }
            }

        } catch (IOException e) {
            log.error("Error reading file {}: {}", generalPartsJSONPath, e.getMessage());
        }
        return null;
    }

    private EngineType mapStringToEngineType(String fuelType, EngineType engineType) {
        if (fuelType == null)
            return null;

        return switch (fuelType.toUpperCase()) {
            case "DIESEL" -> (engineType == EngineType.DIESEL) ? EngineType.DIESEL : null;
            case "GASOLINA" -> (engineType == EngineType.PETROL
                    || engineType == EngineType.PHEV) ? EngineType.PETROL : null;
            case "ELECTRIC" -> (engineType == EngineType.BEV) ? EngineType.BEV : null;
            case "PHEV" -> (engineType == EngineType.PHEV) ? EngineType.PHEV : null;
            case "HYBRID", "HEV" -> (engineType == EngineType.HEV) ? EngineType.HEV : null;
            default -> null;
        };
    }

    private void processCarPartsForVehicleModel(Path carPartsGroupPath, VehicleModel vehicleModel) {
        try {
            JsonNode carPartRoot = objectMapper.readTree(new File(carPartsGroupPath.toAbsolutePath().toString()));
            if (carPartRoot.isArray()) {
                for (JsonNode node : carPartRoot) {
                    String platformGen = node.get("plataforma_generacion").asText();
                    String fuelType = node.get("tipo_combustible").asText();

                    JsonNode parts = node.get("piezas");
                    if (parts.isArray()) {
                        for (JsonNode part : parts) {
                            String partName = part.asText().trim().toLowerCase();
                            System.out.println("Buscando pieza: " + partName + " para " + vehicleModel.getModelName());
                            Product product = findPartByName(partName);

                            if (product == null) {
                                System.out.println("No se encontró la pieza, se procede a crear una dummy.");
                                product = Product.builder()
                                        .name(partName)
                                        .build();
                                System.out.println("Pieza creada: " + product);
                                productRepository.save(product);
                            }

                            Engine eng = vehicleModel.getEngine();
                            EngineType engineType = mapStringToEngineType(fuelType, eng.getEngineType());

                            System.out
                                    .println("Para engine: " + eng.getName() + " y mapeo a EngineType: " + engineType);

                            if (engineType != null) {
                                List<Product> productsToAdd = new ArrayList<>();
                                productsToAdd.add(product);
                                System.out.println(
                                        "Asociando a " + vehicleModel.getModelName() + "->" + product.getName());
                                // Asociar VehicleModel a Producto
                                vehicleModelRepository.updateProducts(vehicleModel, productsToAdd);
                            }

                        }
                    }

                }
            } else {
                System.out.println("carPartRoot is not array");
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

    }

    private Map<Vehicle, List<VehicleModel>> processModel(JsonNode modelNode) {
        Map<Vehicle, List<VehicleModel>> vehicleModels = new HashMap<>();
        JsonNode versions = modelNode.get("versions");
        if (versions != null && versions.isArray()) {
            for (JsonNode versionNode : versions) {
                Vehicle vehicle = mapToVehicle(versionNode);
                Vehicle finalVehicle = vehicleRepository.findByNameAndBrand(vehicle.getName(), vehicle.getBrand())
                        .orElseGet(
                                () -> vehicleRepository.save(vehicle));

                vehicleModels.put(finalVehicle, processTableVersions(versionNode.get("table_versions"), finalVehicle));
            }
        }

        return vehicleModels;
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

    private List<VehicleModel> processTableVersions(JsonNode tableVersions, Vehicle vehicle) {
        List<VehicleModel> vehicleModels = new ArrayList<>();
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

                    Set<VehicleModel> vehicleModelsSet = vehicleModelRepository
                            .findByModelNameAndVehicle(modelName, vehicle)
                            .stream().collect(Collectors.toSet());

                    if (vehicleModelsSet.isEmpty()) {

                        VehicleModel dummyVm = VehicleModel.builder()
                                .modelName(modelName)
                                .vehicle(vehicle)
                                .yearFirstProduction(Integer.valueOf(entry.get("Año").asText()))
                                .engine(engine)
                                .build();
                        vehicleModels.add(dummyVm);

                        Set<TransmissionType> inferredTransmissionTypes = inferTransmissionType(dummyVm,
                                computeATScore(dummyVm));

                        for (TransmissionType trans : inferredTransmissionTypes) {
                            dummyVm.setTransmission(trans);
                            VehicleModel vm = vehicleModelRepository.save(dummyVm);
                            vehicleModelsSet.add(vm);
                        }
                    }

                    vehicleModels.addAll(vehicleModelsSet);
                }
            }
        }

        return vehicleModels;
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

        System.out.println("Before switch, current brand is: " + currentBrand);

        // Heurística por marca
        switch (currentBrand) {
            case "bmw":
            case "mercedes-benz":
            case "audi":
                score += 1.5;
                break;
            case "dacia":
            case "fiat":
            case "suzuki":
                score -= 1.5;
                break;
            case "ferrari":
            case "lamborghini":
                score += 4.0;
                break;
            default:
                score += 0;
                break;
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
     * @return Las transmisiones probables
     */

    private Set<TransmissionType> inferTransmissionType(
            VehicleModel vehicleModel,
            double score // renamed: this is NOT probability, it's the logit input
    ) {

        Set<TransmissionType> probableTransmissions = new HashSet<>();

        double atProb = 1.0 / (1.0 + Math.exp(-score));
        Engine engine = vehicleModel.getEngine();

        // Sobreescrituras de máxima confianza
        if (engine != null) {
            EngineType engineType = engine.getEngineType();

            if (engineType != null) {

                // Eléctricos -> AT
                if (engineType == EngineType.BEV) {
                    probableTransmissions.add(TransmissionType.AT);
                    return probableTransmissions;
                }

                // Lógica coches híbridos
                if (engineType == EngineType.PHEV || engineType == EngineType.HEV) {

                    if (currentBrand != null) {
                        switch (currentBrand) {
                            case "toyota":
                            case "lexus":
                                probableTransmissions.add(TransmissionType.eCVT);
                            case "honda":
                                probableTransmissions.add(TransmissionType.eCVT);
                            default:
                                probableTransmissions.add(TransmissionType.CVT);
                        }
                    }
                    probableTransmissions.add(TransmissionType.CVT);
                    return probableTransmissions;
                }
            }
        }

        // Si es probablemente manual, añadir manual
        if (atProb <= 0.4) {
            probableTransmissions.add(TransmissionType.MT);
        }

        if (atProb >= 0.4) {
            probableTransmissions.add(TransmissionType.AT);
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
                    probableTransmissions.add(TransmissionType.DCT);
                    break;

                // Convertidores de par premium
                case "bmw":
                case "mercedes-benz":
                case "jaguar":
                case "land rover":
                case "volvo":
                    probableTransmissions.add(TransmissionType.AT);
                    break;

                // CVT predominante
                case "nissan":
                case "subaru":
                    probableTransmissions.add(TransmissionType.CVT);
                    break;

                // AT predominante
                case "mazda":
                case "toyota":
                    probableTransmissions.add(TransmissionType.AT);
                    break;

                // DCT coreano
                case "hyundai":
                case "kia":
                    probableTransmissions.add(TransmissionType.DCT);
                    break;

                default:
                    if (atProb > 0.75) {
                        probableTransmissions.add(TransmissionType.AT);
                    } else {
                        probableTransmissions.add(TransmissionType.CVT);
                    }
                    break;
            }
        }

        if (atProb > 0.5) {
            probableTransmissions.add(TransmissionType.AT);
        } else {
            probableTransmissions.add(TransmissionType.MT);
        }

        return probableTransmissions;
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
