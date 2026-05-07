package es.ual.dra.autodiagnostico.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.ual.dra.autodiagnostico.dto.MechanicClientDTO;
import es.ual.dra.autodiagnostico.service.MechanicService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/mechanic")
@RequiredArgsConstructor
public class MechanicController {

    private final MechanicService mechanicService;

    @GetMapping("/{mechanicId}/clients")
    public ResponseEntity<List<MechanicClientDTO>> getClientsForMechanic(@PathVariable Long mechanicId) {
        List<MechanicClientDTO> clients = mechanicService.getClientsForMechanic(mechanicId);
        return ResponseEntity.ok(clients);
    }

    @PostMapping("/{mechanicId}/clients/{clientId}/status")
    public ResponseEntity<Void> updateClientStatus(
            @PathVariable Long mechanicId,
            @PathVariable Long clientId,
            @RequestBody StatusUpdateRequest request) {
        mechanicService.updateClientStatus(mechanicId, clientId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @lombok.Data
    public static class StatusUpdateRequest {
        private String status;
    }
}
