package com.bookfair.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bookfair.backend.model.User;
import com.bookfair.backend.model.enums.SystemRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

        Optional<User> findByEmailAndActiveTrue(String email);

        Optional<User> findByUsernameAndActiveTrue(String username);

        Optional<User> findByIdAndActiveTrue(UUID id);

        boolean existsByEmailAndActiveTrue(String email);

        boolean existsByUsernameAndActiveTrue(String username);

        Optional<User> findByUsername(String username);

        Page<User> findAllByActiveTrue(Pageable pageable);

        long countByActiveTrue();

        long countBySystemRoleAndActiveTrue(SystemRole systemRole);

        List<User> findBySystemRole(SystemRole systemRole);

}
