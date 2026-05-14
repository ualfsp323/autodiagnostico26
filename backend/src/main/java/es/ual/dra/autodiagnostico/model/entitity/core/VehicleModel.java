package es.ual.dra.autodiagnostico.model.entitity.core;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicle_model")
@Getter
@Setter
@ToString(exclude = { "vehicle", "personalVehicles", "engine", "products" })
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

    private String modelName;

    private int yearFirstProduction;

    private TransmissionType transmission;

    @OneToMany(mappedBy = "vehicleModel", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PersonalVehicle> personalVehicles = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "idEngine")
    private Engine engine;

    @ManyToMany
    // VM_Has_Product
    @JoinTable(name = "vm_has_product", joinColumns = @JoinColumn(name = "idVehicleModel"), inverseJoinColumns = @JoinColumn(name = "idProduct"))
    @Builder.Default
    private List<Product> products = new ArrayList<>();

}
