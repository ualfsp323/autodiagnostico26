package es.ual.dra.autodiagnostico.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import es.ual.dra.autodiagnostico.model.entitity.user.AppUser;
import es.ual.dra.autodiagnostico.model.entitity.user.UserRole;
import es.ual.dra.autodiagnostico.model.entitity.chat.TallerAssignment;
import es.ual.dra.autodiagnostico.repository.UserRepository;
import es.ual.dra.autodiagnostico.repository.TallerAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MechanicsDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TallerAssignmentRepository tallerAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Initializing mechanics and clients data...");
        initializeMechanicsAndClients();
    }

    private void initializeMechanicsAndClients() {
        // Ensure 10 mechanics exist (idempotent by email).
        List<AppUser> mechanics = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String email = "mecanico" + i + "@taller.local";
            AppUser mechanic = upsertUser(
                    email,
                    "Mecánico " + i,
                    UserRole.TALLER,
                    "password123",
                    "https://api.dicebear.com/9.x/initials/svg?seed=M" + i + "&backgroundColor=1a6bbd");
            mechanics.add(mechanic);
        }

        // Ensure 15 default clients exist (idempotent by email).
        List<AppUser> clients = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            String email = "cliente" + i + "@user.local";
            AppUser client = upsertUser(
                    email,
                    "Cliente " + i,
                    UserRole.USER,
                    "password123",
                    "https://api.dicebear.com/9.x/initials/svg?seed=C" + i + "&backgroundColor=1a6bbd");
            clients.add(client);
        }

        // Include any manually created users too.
        List<AppUser> allMechanics = userRepository.findByRole(UserRole.TALLER);
        List<AppUser> allClients = userRepository.findByRole(UserRole.USER);

        if (allMechanics.isEmpty() || allClients.isEmpty()) {
            log.warn("No mechanics or clients available to assign.");
            return;
        }

        allMechanics.sort(Comparator.comparing(AppUser::getId));
        allClients.sort(Comparator.comparing(AppUser::getId));

        rebalanceAssignments(allMechanics, allClients);

        log.info("Mechanics and clients initialization completed. mechanics={}, clients={}", allMechanics.size(),
                allClients.size());
    }

    private AppUser upsertUser(String email, String fullName, UserRole role, String rawPassword, String avatarUrl) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(AppUser::new);
        boolean isNew = user.getId() == null;

        user.setEmail(normalizedEmail);
        user.setFullName(fullName);
        user.setRole(role);
        user.setAvatarUrl(avatarUrl);

        if (isNew) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setCreatedAt(LocalDateTime.now());
            AppUser saved = userRepository.save(user);
            log.info("Created {}: {}", role, saved.getEmail());
            return saved;
        }

        // Keep existing password for existing users.
        AppUser saved = userRepository.save(user);
        log.info("Updated {}: {}", role, saved.getEmail());
        return saved;
    }

    private void rebalanceAssignments(List<AppUser> mechanics, List<AppUser> clients) {
        LocalDateTime now = LocalDateTime.now();

        List<TallerAssignment> activeAssignments = tallerAssignmentRepository.findByActiveTrue();
        for (TallerAssignment assignment : activeAssignments) {
            assignment.setActive(false);
            assignment.setUpdatedAt(now);
        }
        if (!activeAssignments.isEmpty()) {
            tallerAssignmentRepository.saveAll(activeAssignments);
        }

        for (int i = 0; i < clients.size(); i++) {
            AppUser client = clients.get(i);
            AppUser mechanic = mechanics.get(i % mechanics.size());

            TallerAssignment assignment = TallerAssignment.builder()
                    .tallerId(mechanic.getId())
                    .clientId(client.getId())
                    .sessionUuid(UUID.randomUUID().toString())
                    .active(true)
                    .description("Asignacion inicial de cliente a taller")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            tallerAssignmentRepository.save(assignment);
            log.info("Assigned client {} to mechanic {}", client.getEmail(), mechanic.getEmail());
        }
    }
}
