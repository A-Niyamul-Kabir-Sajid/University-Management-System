package com.example.ums.dto;

import com.example.ums.model.AppUser;
import com.example.ums.model.Role;

/** What the API sends back. The password hash is intentionally not included. */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        String department,
        String studentId) {

    public static UserResponse from(AppUser u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                u.getDepartment(), u.getStudentId());
    }
}
