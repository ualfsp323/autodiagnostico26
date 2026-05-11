package es.ual.dra.autodiagnostico.service.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.ual.dra.autodiagnostico.model.entitity.core.Engine;
import es.ual.dra.autodiagnostico.model.entitity.core.EngineType;
import es.ual.dra.autodiagnostico.model.entitity.core.TransmissionType;
import es.ual.dra.autodiagnostico.model.entitity.core.Vehicle;
import es.ual.dra.autodiagnostico.model.entitity.core.VehicleModel;
import es.ual.dra.autodiagnostico.repository.EngineRepository;
import es.ual.dra.autodiagnostico.repository.ProductRepository;
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
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

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
    // Cache del JSON de piezas generales
    private JsonNode generalPartsRoot = null;

    // Contiene la marca actual siendo procesada SIN el prefijo "ultimatespecs-" ni
    // otras palabras. Además, está en minúsculas.
    private static String currentBrand;
    private static String currentCarGroup;

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

    private void processCarPartsForVehicleModel(Path carPartsGroupPath, VehicleModel vehicleModel) {
        try {
            JsonNode carPartRoot = objectMapper.readTree(new File(carPartsGroupPath.toAbsolutePath().toString()));
            // Cargar el JSON de piezas generales si no está en memoria
            if (generalPartsRoot == null) {
                try {
                    generalPartsRoot = objectMapper
                            .readTree(getClass().getClassLoader().getResourceAsStream("general-car-parts.json"));
                } catch (Exception e) {
                    log.warn("No se pudo leer general-car-parts.json: {}", e.getMessage());
                    generalPartsRoot = null;
                }
            }
            if (carPartRoot.isArray()) {
                for (JsonNode node : carPartRoot) {
                    String platformGen = node.get("plataforma_generacion").asText();
                    String fuelType = node.get("tipo_combustible").asText();
                    JsonNode parts = node.get("piezas");
                    if (parts.isArray()) {
                        for (JsonNode part : parts) {
                            String partName = null;
                            if (part.isTextual()) {
                                partName = part.asText();
                            } else if (part.has("name")) {
                                partName = part.get("name").asText();
                            } else if (part.has("pieza")) {
                                partName = part.get("pieza").asText();
                            }

                            if (partName == null || partName.isBlank())
                                continue;

                            // Buscar en el JSON general una pieza que coincida
                            JsonNode matched = null;
                            if (generalPartsRoot != null && generalPartsRoot.isArray()) {
                                String pn = partName.toLowerCase();
                                for (JsonNode g : generalPartsRoot) {
                                    if (g.has("name")) {
                                        String gname = g.get("name").asText().toLowerCase();
                                        if (gname.equals(pn) || gname.contains(pn)
                                                || pn.contains(gname.split("\\s")[0])) {
                                            matched = g;
                                            break;
                                        }
                                    }
                                }
                            }

                            String description = null;
                            Double price = null;
                            String image = null;

                            if (matched != null) {
                                if (matched.has("description"))
                                    description = matched.get("description").asText();
                                if (matched.has("image"))
                                    image = matched.get("image").asText();
                                if (matched.has("priceRange")) {
                                    String pr = matched.get("priceRange").asText();
                                    // Extraer números del rango, p. ej. "1500-10000€"
                                    try {
                                        String cleaned = pr.replaceAll("[^0-9\\-]", "");
                                        if (cleaned.contains("-")) {
                                            String[] partsRange = cleaned.split("-", 2);
                                            double min = Double.parseDouble(partsRange[0]);
                                            double max = Double.parseDouble(partsRange[1]);
                                            price = (min + max) / 2.0; // tomar punto medio
                                        } else {
                                            price = Double.parseDouble(cleaned);
                                        }
                                    } catch (Exception ex) {
                                        // ignore parse errors
                                        log.debug("No se pudo parsear priceRange '{}' para '{}'", pr, partName);
                                    }
                                }
                            }

                            // Guardar o actualizar producto
                            String canonicalName = partName.trim();
                            es.ual.dra.autodiagnostico.model.entitity.core.Product product = productRepository
                                    .findByName(canonicalName).orElse(null);
                            if (product == null) {
                                product = es.ual.dra.autodiagnostico.model.entitity.core.Product.builder()
                                        .name(canonicalName)
                                        .description(description)
                                        .price(price)
                                        .image(image)
                                        .build();
                            } else {
                                // actualizar descripción/price si no existen
                                if ((product.getDescription() == null || product.getDescription().isBlank())
                                        && description != null)
                                    product.setDescription(description);
                                if (product.getPrice() == null && price != null)
                                    product.setPrice(price);
                                if ((product.getImage() == null || product.getImage().isBlank()) && image != null)
                                    product.setImage(image);
                            }

                            // Asociar vehicleModel
                            try {
                                if (product.getVehicleModels() == null)
                                    product.setVehicleModels(new ArrayList<>());
                                // evitar duplicados
                                boolean exists = false;
                                for (VehicleModel vm : product.getVehicleModels()) {
                                    if (vm.getIdVehicleModel() != null
                                            && vm.getIdVehicleModel().equals(vehicleModel.getIdVehicleModel())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists)
                                    product.getVehicleModels().add(vehicleModel);
                            } catch (Exception ex) {
                                log.debug("No se pudo asociar product->vehicleModel: {}", ex.getMessage());
                            }

                            productRepository.save(product);
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
                        .orElseGet(() -> vehicleRepository.save(vehicle));

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

                    VehicleModel vehicleModel = vehicleModelRepository.findByModelNameAndVehicle(modelName, vehicle)
                            .orElseGet(() -> {

                                VehicleModel vm = VehicleModel.builder()
                                        .modelName(modelName)
                                        .vehicle(vehicle)
                                        .yearFirstProduction(Integer.valueOf(entry.get("Año").asText()))
                                        .engine(engine)
                                        .build();
                                vehicleModels.add(vm);

                                vm.setTransmission(inferTransmissionType(vm, computeATScore(vm)));
                                return vehicleModelRepository.save(vm);
                            });
                    vehicleModels.add(vehicleModel);
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
     * @return El tipo de transmisión inferido
     */

    private TransmissionType inferTransmissionType(
            VehicleModel vehicleModel,
            double score // renamed: this is NOT probability, it's the logit input
    ) {

        double atProb = 1.0 / (1.0 + Math.exp(-score));
        Engine engine = vehicleModel.getEngine();

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

                    if (currentBrand != null) {
                        switch (currentBrand) {
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
