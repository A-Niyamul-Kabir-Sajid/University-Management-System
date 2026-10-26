package com.example.ums.controller;

import com.example.ums.dto.UserRequest;
import com.example.ums.dto.UserResponse;
import com.example.ums.model.Role;
import com.example.ums.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * The five REST endpoints shared by Admin, Teacher and Student controllers.
 * Each subclass only says which {@link Role} it is responsible for.
 *
 * GET    /{base}        -> 200 list
 * GET    /{base}/{id}   -> 200 / 404
 * POST   /{base}        -> 201 + Location header
 * PUT    /{base}/{id}   -> 200 / 404
 * DELETE /{base}/{id}   -> 204 / 404
 */
public abstract class BaseUserController {

    protected final UserService userService;

    protected BaseUserController(UserService userService) {
        this.userService = userService;
    }

    protected abstract Role role();

    @GetMapping
    public List<UserResponse> getAll() {
        return userService.findAll(role());
    }

    @GetMapping("/{id}")
    public UserResponse getOne(@PathVariable Long id) {
        return userService.findById(role(), id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.create(role(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userService.update(role(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(role(), id);
    }
}
