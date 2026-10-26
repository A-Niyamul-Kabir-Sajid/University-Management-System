package com.example.ums.controller;

import com.example.ums.model.Role;
import com.example.ums.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN and STUDENT can reach this (see SecurityConfig). */
@RestController
@RequestMapping("/api/students")
public class StudentController extends BaseUserController {

    public StudentController(UserService userService) {
        super(userService);
    }

    @Override
    protected Role role() {
        return Role.STUDENT;
    }
}
