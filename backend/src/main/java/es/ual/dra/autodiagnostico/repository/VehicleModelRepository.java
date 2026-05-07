package es.ual.dra.autodiagnostico.repository;

import es.ual.dra.autodiagnostico.model.entitity.core.Vehicle;
import es.ual.dra.autodiagnostico.model.entitity.core.VehicleModel;
import es.ual.dra.autodiagnostico.model.entitity.core.Product; // Ensure this path is correct
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {

    Optional<VehicleModel> findByModelNameAndVehicle(String modelName, Vehicle vehicle);

    @Modifying
    @Query("UPDATE VehicleModel vm SET vm.products = :products WHERE vm = :vehicleModel")
    void updateProducts(@Param("vehicleModel") VehicleModel vehicleModel, @Param("products") List<Product> products);
}