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


    @GetMapping("/client/{clientId}/tracking")
    public ResponseEntity<MechanicClientDTO> getTrackingForClient(
            @PathVariable Long clientId) {

        return ResponseEntity.ok(
                mechanicService.getTrackingForClient(clientId)
        );
    }

    @PostMapping("/{mechanicId}/clients/{clientId}/status")
    public ResponseEntity<Void> updateClientStatus(
            @PathVariable Long mechanicId,
            @PathVariable Long clientId,
            @RequestBody StatusUpdateRequest request) {
        mechanicService.updateClientStatus(mechanicId, clientId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{mechanicId}/clients/{clientId}/tracking-update")
    public ResponseEntity<Void> updateTrackingMessage(
            @PathVariable Long mechanicId,
            @PathVariable Long clientId,
            @RequestBody TrackingUpdateRequest request) {
        mechanicService.updateLatestTrackingMessage(mechanicId, clientId, request.getMessage());
        return ResponseEntity.ok().build();
    }

    @lombok.Data
    public static class StatusUpdateRequest {
        private String status;
    }

    @lombok.Data
    public static class TrackingUpdateRequest {
        private String message;
    }
}
