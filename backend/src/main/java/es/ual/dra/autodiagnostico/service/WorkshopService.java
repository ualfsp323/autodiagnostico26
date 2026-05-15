package es.ual.dra.autodiagnostico.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import es.ual.dra.autodiagnostico.dto.MechanicClientDTO;
import es.ual.dra.autodiagnostico.dto.RepairVehicleMockDTO;
import es.ual.dra.autodiagnostico.dto.WorkshopDTO;
import es.ual.dra.autodiagnostico.dto.WorkshopSelectionResponseDTO;
import es.ual.dra.autodiagnostico.model.entitity.chat.TallerAssignment;
import es.ual.dra.autodiagnostico.model.entitity.core.Workshop;
import es.ual.dra.autodiagnostico.model.entitity.user.AppUser;
import es.ual.dra.autodiagnostico.model.entitity.user.UserRole;
import es.ual.dra.autodiagnostico.repository.TallerAssignmentRepository;
import es.ual.dra.autodiagnostico.repository.UserRepository;
import es.ual.dra.autodiagnostico.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final TallerAssignmentRepository tallerAssignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkshopDTO> listWorkshops(Long clientId) {
        return workshopRepository.findAll().stream()
                .map(workshop -> toDto(workshop, clientId))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkshopDTO getWorkshop(Long workshopId, Long clientId) {
        Workshop workshop = getWorkshopOrThrow(workshopId);
        return toDto(workshop, clientId);
    }

    @Transactional
    public WorkshopSelectionResponseDTO selectWorkshop(Long workshopId, Long clientId) {
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId es obligatorio");
        }

        Workshop workshop = getWorkshopOrThrow(workshopId);
        AppUser client = userRepository.findById(clientId)
                .filter(user -> user.getRole() == UserRole.USER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        AppUser mechanic = getMechanic(workshop);
        List<TallerAssignment> currentClientAssignments = tallerAssignmentRepository.findByClientIdAndActiveTrue(clientId);
        TallerAssignment existingForWorkshop = currentClientAssignments.stream()
                .filter(assignment -> assignment.getTallerId().equals(mechanic.getId()))
                .findFirst()
                .orElse(null);

        TallerAssignment assignment;
        if (existingForWorkshop != null) {
            assignment = existingForWorkshop;
        } else {
            long activeVehicles = tallerAssignmentRepository.countByTallerIdAndActiveTrue(mechanic.getId());
            if (activeVehicles >= workshop.getVehicleLimit()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El taller ha alcanzado su capacidad maxima");
            }

            LocalDateTime now = LocalDateTime.now();
            for (TallerAssignment current : currentClientAssignments) {
                current.setActive(false);
                current.setUpdatedAt(now);
            }
            if (!currentClientAssignments.isEmpty()) {
                tallerAssignmentRepository.saveAll(currentClientAssignments);
            }

            assignment = TallerAssignment.builder()
                    .tallerId(mechanic.getId())
                    .clientId(client.getId())
                    .sessionUuid(UUID.randomUUID().toString())
                    .active(true)
                    .description("Seleccion de taller por el cliente")
                    .status("amarillo")
                    .latestUpdate("Taller seleccionado. Pendiente de primera revision del mecanico.")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            assignment = tallerAssignmentRepository.save(assignment);
        }

        return WorkshopSelectionResponseDTO.builder()
                .workshop(toDto(workshop, clientId))
                .tracking(toTrackingDto(assignment, client))
                .build();
    }

    private Workshop getWorkshopOrThrow(Long workshopId) {
        return workshopRepository.findById(workshopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Taller no encontrado"));
    }

    private AppUser getMechanic(Workshop workshop) {
        return userRepository.findById(workshop.getMechanicId())
                .filter(user -> user.getRole() == UserRole.TALLER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mecanico del taller no encontrado"));
    }

    private WorkshopDTO toDto(Workshop workshop, Long clientId) {
        AppUser mechanic = getMechanic(workshop);
        TallerAssignment activeClientAssignment = findClientAssignmentForWorkshop(workshop, clientId);
        long activeVehicles = tallerAssignmentRepository.countByTallerIdAndActiveTrue(mechanic.getId());

        return WorkshopDTO.builder()
                .id(workshop.getId())
                .name(workshop.getName())
                .address(workshop.getAddress())
                .phone(workshop.getPhone())
                .email(workshop.getEmail())
                .schedule(workshop.getSchedule())
                .photoUrl(workshop.getPhotoUrl())
                .vehicleLimit(workshop.getVehicleLimit())
                .activeVehicles(activeVehicles)
                .mechanicId(mechanic.getId())
                .mechanicName(mechanic.getFullName())
                .mechanicAvatar(mechanic.getAvatarUrl())
                .latitude(workshop.getLatitude())
                .longitude(workshop.getLongitude())
                .selectedByClient(activeClientAssignment != null)
                .sessionUuid(activeClientAssignment == null ? null : activeClientAssignment.getSessionUuid())
                .vehiclesInRepair(buildVehicleMocks(activeClientAssignment))
                .build();
    }

    private TallerAssignment findClientAssignmentForWorkshop(Workshop workshop, Long clientId) {
        if (clientId == null) {
            return null;
        }

        return tallerAssignmentRepository.findByTallerIdAndClientIdAndActiveTrue(workshop.getMechanicId(), clientId)
                .orElse(null);
    }

    private List<RepairVehicleMockDTO> buildVehicleMocks(TallerAssignment assignment) {
        if (assignment == null) {
            return List.of();
        }

        return List.of(
                RepairVehicleMockDTO.builder()
                        .id(assignment.getId())
                        .name("Toyota Corolla 2018")
                        .plate("0000-MCK")
                        .status(assignment.getStatus())
                        .build());
    }

    private MechanicClientDTO toTrackingDto(TallerAssignment assignment, AppUser client) {
        return MechanicClientDTO.builder()
                .clientId(client.getId())
                .clientName(client.getFullName())
                .clientEmail(client.getEmail())
                .clientAvatar(client.getAvatarUrl())
                .carInfo("Toyota Corolla 2018")
                .problemDescription(assignment.getDescription())
                .status(assignment.getStatus())
                .latestUpdate(assignment.getLatestUpdate())
                .sessionUuid(assignment.getSessionUuid())
                .tallerAssignmentId(assignment.getId())
                .build();
    }
}
