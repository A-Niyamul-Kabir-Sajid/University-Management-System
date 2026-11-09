# Integration Tests Explained (UserApiIntegrationTest)

File: `src/test/java/com/example/ums/UserApiIntegrationTest.java`

---

## 1. What an integration test is

A unit test checks one class with fakes. An **integration test checks that the real parts work together**:

```
HTTP request -> Security filter chain -> Controller -> Service -> Repository -> H2 database
                                                      <- JSON response <-
```

Nothing is mocked here. The app starts for real, but in memory and without a network port.

| | Unit test (`UserServiceTest`) | Integration test (`UserApiIntegrationTest`) |
|---|---|---|
| Spring started? | No | Yes, the full application |
| Database | Fake repository | Real H2 in-memory database |
| Security rules | Not involved | Real `SecurityConfig` |
| Speed | ~0.6 s for 6 tests | ~4 s for 3 tests (mostly the Spring start-up) |
| Finds | Logic bugs in one class | Wiring, security, JSON and database bugs |

---

## 2. How the test class is built (the setup)

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserApiIntegrationTest {

    @Autowired private MockMvc mvc;
    ...
}
```

### What happens when you run it

1. Maven finds the class (in `src/test/java`, name ends with `Test`).
2. `@SpringBootTest` starts the **whole application context**: all controllers, the service, the repository, security, JPA, and an in-memory H2 database (the default in `application.properties`, because no `DB_URL` is set).
3. `DataInitializer` runs at start-up and creates the demo accounts (`admin@ums.com`, `teacher@ums.com`, `student@ums.com`).
4. `@AutoConfigureMockMvc` creates a `MockMvc` object. It sends requests straight into Spring's web layer **without opening a real port**, which is fast and needs no network.
5. `@Autowired` injects that `MockMvc` into the field `mvc`.
6. Each `@Test` builds a request with `mvc.perform(...)` and checks the response with `.andExpect(...)`.
7. The Spring context is **created once and reused** by all tests in the class, so the database is **shared**.

### Annotations and tools

| Item | Meaning |
|---|---|
| `@SpringBootTest` | Start the full application for the test |
| `@AutoConfigureMockMvc` | Provide `MockMvc` for sending fake HTTP requests |
| `@Autowired` | Let Spring give the test the `MockMvc` bean |
| `@WithMockUser(roles = "ADMIN")` | Runs the test as an already logged-in user with that role. No login request is needed. |
| `user("t@test.com").roles("TEACHER")` | Same idea, but for a single request. Attached with `.with(...)` |
| `mvc.perform(get/post(...))` | Sends the request |
| `.andExpect(status().isOk())` | Checks the HTTP status code |
| `.andExpect(jsonPath("$.role").value("TEACHER"))` | Checks a field in the JSON response |
| `jsonPath("$.password").doesNotExist()` | Checks a field is absent from the JSON |
| `uniqueEmail()` | Random email for each test run |

### Why `uniqueEmail()` exists

All tests share one database, and emails must be unique. A random email means that tests never collide with each other or with a previous run's data.

### Security note

The real security rules from `SecurityConfig` apply:

| URL | Allowed roles |
|---|---|
| `/api/admins/**` | ADMIN |
| `/api/teachers/**` | ADMIN, TEACHER |
| `/api/students/**` | ADMIN, STUDENT |
| `/api/auth/login`, `/error` | anyone |
| everything else | any logged-in user |

Unauthenticated requests get 401. Logged-in users without the right role get 403.

---

## 3. The flow of each test

### I1: Anonymous gets 401, teacher gets 403 on students
`anonymousGets401_andTeacherIsForbiddenFromStudents`

| Step | Request | What the app does | Expected |
|---|---|---|---|
| 1 | `GET /api/students` with no user | Security filter finds no authentication, so the authentication entry point writes a JSON error | **401 Unauthorized** |
| 2 | `GET /api/students` as `user("t@test.com").roles("TEACHER")` | Security sees a logged-in user with role TEACHER. The rule for `/api/students/**` allows only ADMIN and STUDENT, so the access denied handler writes a JSON error | **403 Forbidden** |

What it proves: "not logged in" and "logged in but not allowed" are handled as two different cases.
The request never reaches the controller in either step.
Red state (by breaking): change `/api/students/**` to `permitAll()` and step 2 returns 200.

### I2: Admin creates a teacher, then the teacher can log in
`adminCreatesTeacher_thenTeacherCanLogIn` (runs as `@WithMockUser(roles = "ADMIN")`)

| Step | Request | What the app does | Expected |
|---|---|---|---|
| 1 | `POST /api/teachers` with a JSON body (name, random email, password `secret123`, department) | Security allows ADMIN. The controller validates the body (`@Valid`). `UserService.create` checks the rules, hashes the password with BCrypt, saves a row in H2. The controller returns 201 with a `Location` header | **201 Created**, `role = TEACHER`, **no `password` field** in the JSON |
| 2 | `POST /api/auth/login` with the same email and `secret123` (no mock user needed, the login URL is public) | `AuthController` calls `AuthenticationManager`. It loads the user from H2, and BCrypt compares `secret123` with the stored hash. The login succeeds and a session is created | **200 OK**, `role = TEACHER` |

What it proves: the whole chain controller -> service -> BCrypt -> database -> authentication works, and the password is stored in a form that still verifies.
Red state (by breaking): store the plain password instead of the encoded one, and step 2 returns 401.

### I3: Count endpoint and JSON 404
`countEndpoint_returnsNumber_andMissingIdReturns404` (runs as `@WithMockUser(roles = "ADMIN")`)

| Step | Request | What the app does | Expected |
|---|---|---|---|
| 1 | `GET /api/teachers/count` | The controller's `count()` method is chosen (the literal `/count` wins over `/{id}`). It calls `userService.countByRole(TEACHER)`, which runs a count query on H2, and returns `{"count": n}` | **200 OK**, `$.count` is a number |
| 2 | `GET /api/teachers/99999` | `getOne` calls `findByIdAndRole`, finds nothing, and throws `ResourceNotFoundException`. `GlobalExceptionHandler` turns it into JSON | **404 Not Found**, `$.status = 404` |

What it proves: the new endpoint works end to end, and errors come back as clean JSON with the right status, not as an HTML error page.

---

## 4. Why these 3 tests

| Test | Area covered |
|---|---|
| I1 | Security and role-based access |
| I2 | Create flow, password hashing and authentication |
| I3 | New endpoint, database query and error handling |

## 5. Common problems

| Problem | Cause and fix |
|---|---|
| `Email already in use` | A fixed email was reused. Use `uniqueEmail()` |
| A test passes alone but fails after another | Shared database. Do not depend on exact row counts, check types and status codes instead |
| 401 where you expect 200 | Missing `@WithMockUser` (or `.with(user(...))`) on a protected URL |
| 403 where you expect 200 | The mock user's role is not allowed for that URL |
| Slow first test | `@SpringBootTest` starts the whole app once (about 3-4 s) |

## 6. Running only these tests

```powershell
mvn -B test -Dtest=UserApiIntegrationTest
```

To run every test (what GitHub Actions runs): `mvn -B verify`
