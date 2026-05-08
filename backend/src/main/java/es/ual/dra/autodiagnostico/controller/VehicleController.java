package es.ual.dra.autodiagnostico.controller;

import es.ual.dra.autodiagnostico.dto.VehicleModelSummaryDTO;
import es.ual.dra.autodiagnostico.dto.VehicleVariantDTO;
import es.ual.dra.autodiagnostico.service.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping("/brands")
    public ResponseEntity<List<String>> getBrands() {
        return ResponseEntity.ok(vehicleService.getBrands());
    }

    @GetMapping("/brands/{brand}/models")
    public ResponseEntity<List<VehicleModelSummaryDTO>> getModelsByBrand(@PathVariable String brand) {
        return ResponseEntity.ok(vehicleService.getModelsByBrand(brand));
    }

    @GetMapping("/{vehicleId}/variants")
    public ResponseEntity<List<VehicleVariantDTO>> getVariants(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(vehicleService.getVariantsByVehicleId(vehicleId));
    }
}
