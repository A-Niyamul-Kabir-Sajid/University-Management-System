package com.example.ums.controller;

import com.example.ums.model.Role;
import com.example.ums.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN and TEACHER can reach this (see SecurityConfig). */
@RestController
@RequestMapping("/api/teachers")
public class TeacherController extends BaseUserController {

    public TeacherController(UserService userService) {
        super(userService);
    }

    @Override
    protected Role role() {
        return Role.TEACHER;
    }
}
