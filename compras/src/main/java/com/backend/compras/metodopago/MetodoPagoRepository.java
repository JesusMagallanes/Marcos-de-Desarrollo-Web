package com.backend.compras.metodopago;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Long> {

    boolean existsByName(String name);

    List<MetodoPago> findAllByOrderByIdAsc();
}
