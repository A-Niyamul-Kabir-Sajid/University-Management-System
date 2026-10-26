package com.example.ums;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RbacIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static String userJson(String name, String email, String password) {
        return """
                {"name":"%s","email":"%s","password":"%s","department":"CSE","studentId":"123"}
                """.formatted(name, email, password);
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    // ---------- not logged in ----------

    @Test
    void anonymousUserGets401() throws Exception {
        mvc.perform(get("/api/teachers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admins")).andExpect(status().isUnauthorized());
    }

    // ---------- role rules ----------

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherCanOnlyManageTeachers() throws Exception {
        mvc.perform(get("/api/teachers")).andExpect(status().isOk());
        mvc.perform(get("/api/students")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admins")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCanOnlyManageStudents() throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isOk());
        mvc.perform(get("/api/teachers")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admins")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReachAllThreeResources() throws Exception {
        mvc.perform(get("/api/admins")).andExpect(status().isOk());
        mvc.perform(get("/api/teachers")).andExpect(status().isOk());
        mvc.perform(get("/api/students")).andExpect(status().isOk());
    }

    // ---------- full CRUD ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminFullCrudOnTeachers() throws Exception {
        String email = uniqueEmail();

        // CREATE -> 201 + Location, password hash never returned
        MvcResult created = mvc.perform(post("/api/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Dr. Rahman", email, "secret123")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        // duplicate email -> 409
        mvc.perform(post("/api/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Other", email, "secret123")))
                .andExpect(status().isConflict());

        // READ -> 200
        mvc.perform(get("/api/teachers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dr. Rahman"));

        // UPDATE -> 200 (no password = keep old one)
        mvc.perform(put("/api/teachers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dr. Rahman Updated\",\"email\":\"" + email + "\",\"department\":\"EEE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dr. Rahman Updated"))
                .andExpect(jsonPath("$.department").value("EEE"));

        // a teacher id is not visible through the students endpoint
        mvc.perform(get("/api/students/" + id)).andExpect(status().isNotFound());

        // DELETE -> 204, then 404
        mvc.perform(delete("/api/teachers/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/teachers/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validationErrorsReturn400() throws Exception {
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"abcdef\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("Short Pass", uniqueEmail(), "123")))
                .andExpect(status().isBadRequest());
    }

    // ---------- real login / logout ----------

    @Test
    void loginMeAndLogoutFlow() throws Exception {
        // wrong password -> 401
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@ums.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        // correct login -> 200 + session
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@ums.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@ums.com"));

        mvc.perform(get("/api/admins").session(session)).andExpect(status().isOk());

        // logout -> session no longer works
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admins").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void teacherLoginIsBlockedFromStudentsAndAdmins() throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"teacher@ums.com\",\"password\":\"teacher123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/teachers").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/students").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admins").session(session)).andExpect(status().isForbidden());
    }
}
