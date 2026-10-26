package com.example.ums.controller;

import com.example.ums.model.Role;
import com.example.ums.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only ADMIN can reach this (see SecurityConfig). */
@RestController
@RequestMapping("/api/admins")
public class AdminController extends BaseUserController {

    public AdminController(UserService userService) {
        super(userService);
    }

    @Override
    protected Role role() {
        return Role.ADMIN;
    }
}
