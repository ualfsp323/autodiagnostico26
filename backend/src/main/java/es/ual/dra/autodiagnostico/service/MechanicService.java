package es.ual.dra.autodiagnostico.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import es.ual.dra.autodiagnostico.dto.MechanicClientDTO;
import es.ual.dra.autodiagnostico.model.entitity.chat.TallerAssignment;
import es.ual.dra.autodiagnostico.model.entitity.user.AppUser;
import es.ual.dra.autodiagnostico.repository.TallerAssignmentRepository;
import es.ual.dra.autodiagnostico.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MechanicService {

    private final TallerAssignmentRepository tallerAssignmentRepository;
    private final UserRepository userRepository;

    public List<MechanicClientDTO> getClientsForMechanic(Long mechanicId) {
        // Get all active assignments for this mechanic
        List<TallerAssignment> assignments = tallerAssignmentRepository.findByTallerIdAndActiveTrue(mechanicId);

        return assignments.stream().map(assignment -> {
            AppUser client = userRepository.findById(assignment.getClientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

            return MechanicClientDTO.builder()
                    .clientId(client.getId())
                    .clientName(client.getFullName())
                    .clientEmail(client.getEmail())
                    .clientAvatar(client.getAvatarUrl())
                    .carInfo("Toyota Corolla 2018") // TODO: Get from actual vehicle data
                    .problemDescription(assignment.getDescription())
                    .status("amarillo") // TODO: Get from assignment status
                    .tallerAssignmentId(assignment.getId())
                    .build();
        }).collect(Collectors.toList());
    }

    public void updateClientStatus(Long mechanicId, Long clientId, String newStatus) {
        TallerAssignment assignment = tallerAssignmentRepository
                .findByTallerIdAndClientIdAndActiveTrue(mechanicId, clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignación no encontrada"));

        // TODO: Store status in assignment or create a separate status entity
        // For now, just validate the status
        validateStatus(newStatus);
    }

    private void validateStatus(String status) {
        List<String> validStatuses = List.of("verde", "amarillo", "naranja", "rojo");
        if (!validStatuses.contains(status.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido: " + status);
        }
    }
}
