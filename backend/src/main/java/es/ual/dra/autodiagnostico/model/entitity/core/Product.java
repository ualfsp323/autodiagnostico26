package es.ual.dra.autodiagnostico.model.entitity.core;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.ManyToMany;

/**
 * Entidad que representa un producto asociado a un vehículo.
 */
@Entity
@Table(name = "product")
@Getter
@Setter
@ToString(exclude = "vehicleModels")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idProduct;

    // Nombre del producto
    private String name;

    // Descripción del producto
    private String description;

    // Precio del producto
    private Double lowRangePrice; // Puede ser nulo

    private Double highRangePrice; // Puede ser nulo

    private String image; // Puede ser nulo

    @ManyToMany(mappedBy = "products")
    @Builder.Default
    private List<VehicleModel> vehicleModels = new ArrayList<>();
}
