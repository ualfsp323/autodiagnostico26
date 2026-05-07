package es.ual.dra.autodiagnostico.dto;

import es.ual.dra.autodiagnostico.model.entitity.core.EngineType;
import es.ual.dra.autodiagnostico.model.entitity.core.TransmissionType;

public record VehicleVariantDTO(
        Long id,
        String modelName,
        TransmissionType transmission,
        String engineName,
        EngineType engineType) {}
