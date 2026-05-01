package es.ual.dra.autodiagnostico.model.entitity.core;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicle_model")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVehicleModel;

    @ManyToOne
    @JoinColumn(name = "idVehicle")
    private Vehicle vehicle;

    private TransmissionType transmission;

    @ManyToOne
    @JoinColumn(name = "idEngine")
    private Engine engine;
}
