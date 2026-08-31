package com.bookfair.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.Organization;


@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByNameAndActiveTrue(String name);

    boolean existsByNameAndActiveTrue(String name);

    Page<Organization> findAllByActiveTrue(Pageable pageable);
    
    Optional<Organization> findByIdAndActiveTrue(UUID id);

    boolean existsByRegistrationNumberAndActiveTrue(String registrationNumber);

}