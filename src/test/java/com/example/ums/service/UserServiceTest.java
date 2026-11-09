package com.example.ums.service;

import com.example.ums.dto.UserRequest;
import com.example.ums.dto.UserResponse;
import com.example.ums.exception.BadRequestException;
import com.example.ums.exception.DuplicateResourceException;
import com.example.ums.model.AppUser;
import com.example.ums.model.Role;
import com.example.ums.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests: UserService is tested alone, the repository and encoder are mocks. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static UserRequest request(String name, String email, String password, String studentId) {
        return new UserRequest(name, email, password, "CSE", studentId);
    }

    // U1
    @Test
    void create_blankName_throwsBadRequest() {
        UserRequest req = request("   ", "a@test.com", "secret123", null);

        assertThrows(BadRequestException.class, () -> service.create(Role.TEACHER, req));
        verify(repository, never()).save(any());
    }

    // U2
    @Test
    void create_studentWithoutStudentId_throwsBadRequest() {
        UserRequest req = request("Karim", "karim@test.com", "secret123", " ");

        assertThrows(BadRequestException.class, () -> service.create(Role.STUDENT, req));
        verify(repository, never()).save(any());
    }

    // U3
    @Test
    void countByRole_returnsRepositoryCount() {
        when(repository.countByRole(Role.TEACHER)).thenReturn(7L);

        assertEquals(7L, service.countByRole(Role.TEACHER));
    }

    // U4
    @Test
    void create_duplicateEmail_throwsDuplicateResource() {
        when(repository.existsByEmail("dup@test.com")).thenReturn(true);
        UserRequest req = request("Dup", "dup@test.com", "secret123", null);

        assertThrows(DuplicateResourceException.class, () -> service.create(Role.TEACHER, req));
        verify(repository, never()).save(any());
    }

    // U5
    @Test
    void delete_ownAccount_throwsBadRequest() {
        AppUser me = new AppUser();
        me.setId(1L);
        me.setEmail("admin@ums.com");
        me.setRole(Role.ADMIN);
        when(repository.findByIdAndRole(1L, Role.ADMIN)).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@ums.com", null));

        assertThrows(BadRequestException.class, () -> service.delete(Role.ADMIN, 1L));
        verify(repository, never()).delete(any());
    }

    // U6
    @Test
    void create_encodesPasswordAndNormalizesEmail() {
        when(repository.existsByEmail("mixed@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        when(repository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponse res = service.create(Role.TEACHER,
                request(" Dr. Rahman ", "  Mixed@Test.com ", "secret123", "999"));

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertEquals("HASHED", saved.getPassword());
        assertEquals("mixed@test.com", saved.getEmail());
        assertEquals("Dr. Rahman", saved.getName());
        assertEquals(Role.TEACHER, saved.getRole());
        assertNull(saved.getStudentId());   // teachers never keep a studentId
        assertEquals(10L, res.id());
    }
}
