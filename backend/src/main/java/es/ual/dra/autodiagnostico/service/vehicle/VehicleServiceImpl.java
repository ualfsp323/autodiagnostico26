package es.ual.dra.autodiagnostico.service.vehicle;

import es.ual.dra.autodiagnostico.dto.VehicleModelSummaryDTO;
import es.ual.dra.autodiagnostico.dto.VehicleVariantDTO;
import es.ual.dra.autodiagnostico.model.entitity.core.EngineType;
import es.ual.dra.autodiagnostico.model.entitity.core.Vehicle;
import es.ual.dra.autodiagnostico.model.entitity.core.VehicleModel;
import es.ual.dra.autodiagnostico.repository.VehicleModelRepository;
import es.ual.dra.autodiagnostico.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleModelRepository vehicleModelRepository;

    @Override
    public List<String> getBrands() {
        return vehicleRepository.findAll().stream()
                .map(Vehicle::getBrand)
                .filter(b -> b != null && !b.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<VehicleModelSummaryDTO> getModelsByBrand(String brand) {
        return vehicleRepository.findAll().stream()
                .filter(v -> brand.equalsIgnoreCase(v.getBrand()))
                .map(v -> new VehicleModelSummaryDTO(v.getIdVehicle(), v.getName()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    @Override
    public List<VehicleVariantDTO> getVariantsByVehicleId(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> vehicleModelRepository.findAll().stream()
                        .filter(vm -> vm.getVehicle() != null
                                && vm.getVehicle().getIdVehicle().equals(vehicleId))
                        .map(this::toVariantDTO)
                        .sorted((a, b) -> {
                            String na = a.modelName() != null ? a.modelName() : "";
                            String nb = b.modelName() != null ? b.modelName() : "";
                            return na.compareToIgnoreCase(nb);
                        })
                        .toList())
                .orElse(List.of());
    }

    private VehicleVariantDTO toVariantDTO(VehicleModel vm) {
        String engineName = null;
        EngineType engineType = null;
        if (vm.getEngine() != null) {
            engineName = vm.getEngine().getName();
            engineType = vm.getEngine().getEngineType();
        }
        return new VehicleVariantDTO(
                vm.getIdVehicleModel(),
                vm.getModelName(),
                vm.getTransmission(),
                engineName,
                engineType);
    }
}
