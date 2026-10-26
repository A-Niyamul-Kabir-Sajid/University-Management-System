package com.example.ums.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Body for POST (create) and PUT (update).
 * On update the password is optional: leave it out to keep the old one.
 */
public record UserRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        String password,
        String department,
        String studentId) {
}
