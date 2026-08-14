package com.garage.garageapi.address.repository;

import com.garage.garageapi.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findAllByUserIdOrderByPrimaryDescCreatedAtAsc(Long userId);
    Optional<Address> findByIdAndUserId(Long id, Long userId);
    List<Address> findAllByUserIdAndPrimaryTrue(Long userId);
    Optional<Address> findFirstByUserIdOrderByCreatedAtAsc(Long userId);
    long countByUserId(Long userId);
}
