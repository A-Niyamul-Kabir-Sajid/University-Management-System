package com.example.ums.security;

import com.example.ums.model.AppUser;
import com.example.ums.model.Role;
import com.example.ums.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Without at least one admin nobody could log in and create other users,
 * so we create a default admin on first start (plus demo users for easy testing).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.name}")
    private String adminName;
    @Value("${app.admin.email}")
    private String adminEmail;
    @Value("${app.admin.password}")
    private String adminPassword;
    @Value("${app.seed-demo-data}")
    private boolean seedDemoData;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByRole(Role.ADMIN)) {
            createUser(adminName, adminEmail, adminPassword, Role.ADMIN, null, null);
        }

        if (seedDemoData) {
            createIfMissing("Demo Teacher", "teacher@ums.com", "teacher123", Role.TEACHER, "CSE", null);
            createIfMissing("Demo Student", "student@ums.com", "student123", Role.STUDENT, "CSE", "2107001");
        }
    }

    private void createIfMissing(String name, String email, String password, Role role,
                                 String department, String studentId) {
        if (!userRepository.existsByEmail(email)) {
            createUser(name, email, password, role, department, studentId);
        }
    }

    private void createUser(String name, String email, String password, Role role,
                            String department, String studentId) {
        AppUser user = new AppUser();
        user.setName(name);
        user.setEmail(email.trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setDepartment(department);
        user.setStudentId(studentId);
        userRepository.save(user);
    }
}
