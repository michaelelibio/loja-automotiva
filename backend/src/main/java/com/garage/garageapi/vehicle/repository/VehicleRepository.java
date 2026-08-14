package com.garage.garageapi.vehicle.repository;

import com.garage.garageapi.vehicle.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findAllByUserIdOrderByPrimaryDescCreatedAtAsc(Long userId);
    Optional<Vehicle> findByIdAndUserId(Long id, Long userId);
    Optional<Vehicle> findFirstByUserIdOrderByCreatedAtAsc(Long userId);
    List<Vehicle> findAllByUserIdAndPrimaryTrue(Long userId);
    long countByUserId(Long userId);
}
