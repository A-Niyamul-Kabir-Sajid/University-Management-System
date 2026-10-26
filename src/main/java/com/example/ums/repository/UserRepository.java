package com.example.ums.repository;

import com.example.ums.model.AppUser;
import com.example.ums.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<AppUser> findByRole(Role role);

    /** Makes sure /api/teachers/5 can never return a student, and so on. */
    Optional<AppUser> findByIdAndRole(Long id, Role role);
}
