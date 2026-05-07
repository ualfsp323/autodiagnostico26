package es.ual.dra.autodiagnostico.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.ual.dra.autodiagnostico.model.entitity.core.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

}
