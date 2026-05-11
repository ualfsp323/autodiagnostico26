package es.ual.dra.autodiagnostico.service.vehicle;

import es.ual.dra.autodiagnostico.dto.VehicleModelSummaryDTO;
import es.ual.dra.autodiagnostico.dto.VehicleVariantDTO;

import java.util.List;

public interface VehicleService {
    List<String> getBrands();
    List<VehicleModelSummaryDTO> getModelsByBrand(String brand);
    List<VehicleVariantDTO> getVariantsByVehicleId(Long vehicleId);
}
