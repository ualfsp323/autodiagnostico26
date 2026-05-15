package es.ual.dra.autodiagnostico.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.ual.dra.autodiagnostico.model.entitity.core.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    public Optional<Product> findByName(String name);
}
