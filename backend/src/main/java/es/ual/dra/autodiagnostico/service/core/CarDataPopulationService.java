package es.ual.dra.autodiagnostico.service.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.ual.dra.autodiagnostico.model.entitity.core.*;
import es.ual.dra.autodiagnostico.repository.*;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.text.similarity.LevenshteinDistance;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;

import java.nio.file.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarDataPopulationService {

    private final VehicleRepository vehicleRepository;
    private final VehicleModelRepository vehicleModelRepository;
    private final EngineRepository engineRepository;
    private final ProductRepository productRepository;

    private final ObjectMapper objectMapper;

    private static final String GENERAL_PARTS_JSON = "src/main/resources/general-car-parts.json";

    /**
     * Cache de productos generales.
     */
    private final Map<String, ProductTemplate> productCache = new ConcurrentHashMap<>();

    /**
     * Limita presión sobre DB.
     */
    private final Semaphore dbSemaphore = new Semaphore(16);

    /**
     * Distancia fuzzy reutilizable.
     */
    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    /**
     * DTO interno inmutable.
     */
    private record ProductTemplate(
            String name,
            String description,
            double lowRangePrice,
            double highRangePrice,
            String image) {
    }

    /**
     * Contexto inmutable por archivo.
     */
    private record ProcessingContext(
            String brand,
            String group,
            Path modelFile,
            Path carPartsFile) {
    }

    @PostConstruct
    void preloadParts() throws IOException {

        JsonNode root = objectMapper.readTree(
                new File(GENERAL_PARTS_JSON));

        for (JsonNode node : root) {

            String normalized = normalize(node.get("name").asText());

            String priceRangeStr = node.get("priceRange")
                    .asText()
                    .replace("€", "")
                    .replace(" ", "");

            String[] ranges = priceRangeStr.split("-");

            JsonNode nameNode = node.get("name");
            JsonNode descNode = node.get("description");
            JsonNode imageNode = node.get("image");

            String productName = nameNode != null ? nameNode.asText() : null;
            String desc = descNode != null ? descNode.asText() : null;
            String image = imageNode != null ? imageNode.asText() : null;

            System.out.println(productName + " | " + desc + " | " + ranges[0] + " | " + ranges[1] + " | " + image);

            ProductTemplate template = new ProductTemplate(
                    productName,
                    desc,
                    Double.parseDouble(ranges[0]),
                    Double.parseDouble(ranges[1]),
                    image);

            productCache.put(normalized, template);
        }

        log.info("Loaded {} products into cache",
                productCache.size());
    }

    public void scanAndPopulate(String rootPath)
            throws Exception {

        List<ProcessingContext> jobs = buildJobs(rootPath);

        log.info("Discovered {} jobs", jobs.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<Object>> futures = jobs.stream()
                    .map(job -> executor.submit(() -> {
                        processJob(job);
                        return null;
                    }))
                    .toList();

            for (Future<?> future : futures) {
                future.get();
            }
        }

        log.info("Population completed");
    }

    private List<ProcessingContext> buildJobs(String rootPath)
            throws IOException {

        List<ProcessingContext> jobs = new ArrayList<>();

        List<Path> allJsonFiles;

        try (Stream<Path> stream = Files.walk(Paths.get(rootPath))) {

            allJsonFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();
        }

        System.out.println("All JSON files: " + allJsonFiles);

        List<Path> modelFiles = allJsonFiles.stream()
                .filter(p -> p.getFileName()
                        .toString()
                        .startsWith("ultimatespecs-"))
                .toList();

        System.out.println("Model files: " + modelFiles);

        for (Path modelFile : modelFiles) {

            String brand = extractBrand(modelFile);

            String group = modelFile.getParent()
                    .getFileName()
                    .toString()
                    .toLowerCase();

            Path carPartsFile = allJsonFiles.stream()
                    .filter(p -> p.getParent()
                            .getFileName()
                            .toString()
                            .equalsIgnoreCase(group))
                    .findFirst()
                    .orElse(null);

            if (carPartsFile == null) {
                continue;
            }

            jobs.add(
                    new ProcessingContext(
                            brand,
                            group,
                            modelFile,
                            carPartsFile));
        }

        return jobs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processJob(ProcessingContext ctx) {

        try {

            log.info("Processing brand {}", ctx.brand());

            JsonNode modelRoot = objectMapper.readTree(
                    ctx.modelFile().toFile());

            JsonNode carPartsRoot = objectMapper.readTree(
                    ctx.carPartsFile().toFile());

            JsonNode models = modelRoot.get("models");

            if (models == null || !models.isArray()) {
                return;
            }

            for (JsonNode modelNode : models) {

                processModel(
                        ctx,
                        modelNode,
                        carPartsRoot);
            }

        } catch (Exception e) {
            log.error("Error processing {}", ctx.brand(), e);
        }
    }

    private void processModel(
            ProcessingContext ctx,
            JsonNode modelNode,
            JsonNode carPartsRoot) {

        JsonNode versions = modelNode.get("versions");

        if (versions == null || !versions.isArray()) {
            return;
        }

        for (JsonNode versionNode : versions) {

            Vehicle vehicle = mapToVehicle(ctx, versionNode);

            vehicle = upsertVehicle(vehicle);

            processTableVersions(
                    ctx,
                    versionNode.get("table_versions"),
                    vehicle,
                    carPartsRoot);
        }
    }

    private Vehicle upsertVehicle(Vehicle vehicle) {

        return vehicleRepository
                .findByNameAndBrand(
                        vehicle.getName(),
                        vehicle.getBrand())
                .orElseGet(() -> vehicleRepository.save(vehicle));
    }

    private void processTableVersions(
            ProcessingContext ctx,
            JsonNode tableVersions,
            Vehicle vehicle,
            JsonNode carPartsRoot) {

        if (tableVersions == null
                || !tableVersions.isArray()) {
            return;
        }

        for (JsonNode entry : tableVersions) {

            String modelName = extractModelName(entry);

            if (modelName == null) {
                continue;
            }

            EngineType type = detectEngineType(entry);

            Engine engine = upsertEngine(modelName, type);

            VehicleModel vm = buildVehicleModel(
                    ctx,
                    entry,
                    vehicle,
                    engine);

            Set<TransmissionType> transmissions = inferTransmissionType(
                    vm,
                    ctx.brand(),
                    computeATScore(
                            vm,
                            ctx.brand()));

            for (TransmissionType transmission : transmissions) {

                vm.setTransmission(transmission);

                VehicleModel saved = vehicleModelRepository
                        .save(vm);

                processCarPartsForVehicleModel(
                        saved,
                        carPartsRoot);
            }
        }
    }

    private Vehicle mapToVehicle(
            ProcessingContext ctx,
            JsonNode versionNode) {

        Vehicle vehicle = new Vehicle();

        vehicle.setBrand(ctx.brand());

        vehicle.setName(
                versionNode
                        .get("actualFinalModelName")
                        .asText());

        JsonNode specs = versionNode.get("specifications");

        if (specs != null) {

            vehicle.setWheelbase(
                    getSpec(specs, "Batalla:"));

            vehicle.setAverageConsumptionPer100km(
                    getSpec(specs, "Consumos Medio:"));

            vehicle.setHeight(
                    getSpec(specs, "Alto:"));

            vehicle.setLength(
                    getSpec(specs, "Largo:"));

            vehicle.setWidth(
                    getSpec(specs, "Ancho:"));

            vehicle.setWeight(
                    getSpec(specs, "Peso:"));

            vehicle.setPeriodOfProduction(
                    getSpec(specs, "Período de producción:"));

            vehicle.setEngineDisplacement(
                    getSpec(specs, "Cilindrada:"));
        }

        return vehicle;
    }

    private String extractModelName(JsonNode entry) {
        // El nombre del modelo suele ser el valor de la clave del tipo de motor
        Iterator<Map.Entry<String, JsonNode>> fields = entry.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if (isEngineCategoryKey(key)) {
                return field.getValue().asText();
            }
        }
        // Recurrir a "Otros" si no se encuentra ninguna clave de categoría
        if (entry.has("Otros")) {
            return entry.get("Otros").asText();
        }
        return null;
    }

    private VehicleModel buildVehicleModel(
            ProcessingContext ctx,
            JsonNode entry,
            Vehicle vehicle,
            Engine engine) {

        return VehicleModel.builder()
                .modelName(engine.getName())
                .vehicle(vehicle)
                .engine(engine)
                .yearFirstProduction(
                        Integer.parseInt(
                                entry.get("Año").asText()))
                .build();
    }

    private Engine upsertEngine(
            String modelName,
            EngineType type) {

        return engineRepository
                .findByNameAndEngineType(
                        modelName,
                        type)
                .orElseGet(() -> engineRepository.save(
                        Engine.builder()
                                .name(modelName)
                                .engineType(type)
                                .build()));
    }

    private void processCarPartsForVehicleModel(
            VehicleModel vm,
            JsonNode carPartRoot) {

        if (!carPartRoot.isArray()) {
            return;
        }

        List<Product> productsToAssociate = new ArrayList<>();

        for (JsonNode node : carPartRoot) {

            String fuelType = node.get("tipo_combustible")
                    .asText();

            JsonNode parts = node.get("piezas");

            if (!parts.isArray()) {
                continue;
            }

            for (JsonNode part : parts) {

                String partName = normalize(part.asText());

                Product product = resolveProduct(partName);

                if (product == null) {
                    continue;
                }

                EngineType mapped = mapStringToEngineType(
                        fuelType,
                        vm.getEngine()
                                .getEngineType());

                if (mapped != null) {
                    productsToAssociate.add(product);
                }
            }
        }

        if (!productsToAssociate.isEmpty()) {

            try {

                dbSemaphore.acquire();

                vehicleModelRepository
                        .updateProducts(
                                vm,
                                productsToAssociate);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

            } finally {

                dbSemaphore.release();
            }
        }
    }

    private Product resolveProduct(String partName) {
        // 1. Intentar encontrar el producto en la base de datos primero para evitar
        // duplicados
        return productRepository.findByName(partName)
                .orElseGet(() -> {
                    // 2. No está en la BD, buscar coincidencia exacta en la caché
                    ProductTemplate template = productCache.get(partName);
                    if (template != null) {
                        return productRepository.save(toProduct(template));
                    }

                    // 3. Intento de búsqueda difusa en la caché
                    for (Map.Entry<String, ProductTemplate> entry : productCache.entrySet()) {
                        int dist = levenshtein.apply(entry.getKey(), partName);
                        int limit = Math.max(3, entry.getKey().length() / 4);
                        if (dist <= limit) {
                            // Comprobar si este nombre de plantilla ya está en la BD antes de guardar
                            String templateName = entry.getValue().name();
                            return productRepository.findByName(templateName)
                                    .orElseGet(() -> productRepository.save(toProduct(entry.getValue())));
                        }
                    }

                    // 4. Caso de reserva: Guardar un producto mínimo si no se encuentra ninguna
                    // coincidencia
                    return productRepository.save(
                            Product.builder()
                                    .name(partName)
                                    .build());
                });
    }

    private Product toProduct(ProductTemplate t) {

        return Product.builder()
                .name(t.name())
                .description(t.description())
                .lowRangePrice(t.lowRangePrice())
                .highRangePrice(t.highRangePrice())
                .image(t.image())
                .build();
    }

    private String normalize(String s) {

        return s == null
                ? null
                : s.toLowerCase().trim();
    }

    private String extractBrand(Path path) {

        String file = path.getFileName().toString();

        return file.substring(
                file.lastIndexOf("-") + 1,
                file.lastIndexOf(".")).toLowerCase();
    }

    private String getSpec(
            JsonNode specs,
            String key) {

        JsonNode node = specs.get(key);

        return node != null
                ? node.asText()
                : null;
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
        return EngineType.PETROL; // Por defecto
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
            String currentBrand,
            double score // renombrado: esto NO es probabilidad, es la entrada logit
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

    /**
     * Calcula la probabilidad en escala logit de que el VehicleModel dado tenga una
     * TA
     * (Transmisión Automática)
     * 
     * @param vehicleModel El modelo de vehículo para el cual calcular la
     *                     probabilidad
     * @return La probabilidad en escala logit de que el VehicleModel dado tenga una
     *         TA
     */
    private double computeATScore(VehicleModel vehicleModel, String brand) {
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

        System.out.println("Before switch, current brand is: " + brand);

        // Heurística por marca
        switch (brand) {
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

    private boolean isEngineCategoryKey(String key) {
        return key.contains("Gasolina") || key.contains("Diesel") || key.contains("Diésel")
                || key.contains("Eléctrico") || key.contains("Híbrido") || key.contains("HEV")
                || key.contains("PHEV") || key.contains("REEV");
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

}