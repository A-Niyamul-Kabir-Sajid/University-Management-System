package com.example.ums.service;

import com.example.ums.dto.UserRequest;
import com.example.ums.dto.UserResponse;
import com.example.ums.exception.BadRequestException;
import com.example.ums.exception.DuplicateResourceException;
import com.example.ums.exception.ResourceNotFoundException;
import com.example.ums.model.AppUser;
import com.example.ums.model.Role;
import com.example.ums.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * All CRUD logic lives here. Every method takes a {@link Role}, so the same code
 * serves /api/admins, /api/teachers and /api/students.
 */
@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(Role role) {
        return repository.findByRole(role).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Role role, Long id) {
        return UserResponse.from(getOrThrow(role, id));
    }

    @Transactional
    public UserResponse create(Role role, UserRequest request) {
        String email = normalize(request.email());
        requireValidPassword(request.password());
        if (repository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already in use: " + email);
        }

        AppUser user = new AppUser();
        user.setRole(role);
        apply(user, request, email);
        user.setPassword(passwordEncoder.encode(request.password()));
        return UserResponse.from(repository.save(user));
    }

    @Transactional
    public UserResponse update(Role role, Long id, UserRequest request) {
        AppUser user = getOrThrow(role, id);
        String email = normalize(request.email());

        repository.findByEmail(email)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Email already in use: " + email);
                });

        apply(user, request, email);
        if (request.password() != null && !request.password().isBlank()) {
            requireValidPassword(request.password());
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(repository.save(user));
    }

    @Transactional
    public void delete(Role role, Long id) {
        AppUser user = getOrThrow(role, id);

        // Stops someone from locking themselves out by deleting their own account.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && user.getEmail().equalsIgnoreCase(auth.getName())) {
            throw new BadRequestException("You cannot delete your own account");
        }
        repository.delete(user);
    }

    private AppUser getOrThrow(Role role, Long id) {
        return repository.findByIdAndRole(id, role)
                .orElseThrow(() -> new ResourceNotFoundException(
                        capitalize(role) + " not found with id " + id));
    }

    private void apply(AppUser user, UserRequest request, String email) {
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setDepartment(request.department());
        // studentId only makes sense for students
        user.setStudentId(user.getRole() == Role.STUDENT ? request.studentId() : null);
    }

    private void requireValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException(
                    "Password is required and must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private String capitalize(Role role) {
        String s = role.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
