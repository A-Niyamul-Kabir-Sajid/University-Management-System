# Testing and GitHub Actions Guide

This guide explains the 6 unit tests, the 3 integration tests, the GitHub Actions workflow, and how to show the **red (failing) first, then green (passing)** process.

---

## 1. What was added

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | GitHub Actions workflow: runs `mvn -B verify` on every push and pull request |
| `src/test/java/com/example/ums/service/UserServiceTest.java` | 6 unit tests (U1-U6) |
| `src/test/java/com/example/ums/UserApiIntegrationTest.java` | 3 integration tests (I1-I3) |

The old `RbacIntegrationTest` was deleted, so these 9 tests are the whole test suite.

Three small features were added to the application code so that some tests have something new to fail on:

| Change | File |
|---|---|
| Blank name is rejected (`BadRequestException`) | `UserService.create` |
| A student must have a `studentId` (`BadRequestException`) | `UserService.create` |
| `countByRole(Role)` | `UserService`, `UserRepository` |
| `GET /api/{teachers,students,admins}/count` returns `{"count": n}` | `BaseUserController` |

---

## 2. Unit tests vs. integration tests

**Unit test**: tests one class alone. Everything it depends on is replaced by a *mock* (a fake object). No Spring, no database, runs in milliseconds. Here, `UserService` is tested with a mocked `UserRepository` and a mocked `PasswordEncoder` (Mockito).

**Integration test**: starts the real application (`@SpringBootTest`), with the real security rules, controllers, service and an in-memory H2 database. Requests are sent with `MockMvc` (no real network port needed).

---

## 3. The 6 unit tests (`UserServiceTest`)

Setup: `@ExtendWith(MockitoExtension.class)`, `@Mock UserRepository`, `@Mock PasswordEncoder`, `@InjectMocks UserService`.

| ID | Method | What it proves | Type of red phase |
|---|---|---|---|
| U1 | `create_blankName_throwsBadRequest` | A blank name is rejected and nothing is saved | New feature (test-first) |
| U2 | `create_studentWithoutStudentId_throwsBadRequest` | A student needs a `studentId` | New feature (test-first) |
| U3 | `countByRole_returnsRepositoryCount` | The service returns the repository's count | New feature (does not compile until `countByRole` exists) |
| U4 | `create_duplicateEmail_throwsDuplicateResource` | A used email gives `DuplicateResourceException`, no save | Break the code, then fix |
| U5 | `delete_ownAccount_throwsBadRequest` | A logged-in user cannot delete their own account | Break the code, then fix |
| U6 | `create_encodesPasswordAndNormalizesEmail` | Password is hashed, email is trimmed and lowercased, name is trimmed, a teacher's `studentId` is dropped (uses `ArgumentCaptor`) | Break the code, then fix |

Notes:
- `@AfterEach` clears the `SecurityContextHolder` so U5 does not leak into other tests.
- Mockito is strict: a `when(...)` that is never used fails the test, so only the calls that the code path really makes are stubbed.

---

## 4. The 3 integration tests (`UserApiIntegrationTest`)

Setup: `@SpringBootTest` + `@AutoConfigureMockMvc`, `MockMvc` injected. Emails are random (`uniqueEmail()`), because all tests share one H2 database.

| ID | Method | What it proves |
|---|---|---|
| I1 | `anonymousGets401_andTeacherIsForbiddenFromStudents` | No login gives 401. A TEACHER on `/api/students` gives 403 |
| I2 | `adminCreatesTeacher_thenTeacherCanLogIn` | ADMIN creates a teacher (201, no password in the JSON), then that teacher can log in with the same password (200, role TEACHER) |
| I3 | `countEndpoint_returnsNumber_andMissingIdReturns404` | `GET /api/teachers/count` gives 200 and a numeric `count`. A missing id gives a JSON 404 |

---

## 5. The GitHub Actions workflow

`.github/workflows/ci.yml`:

```yaml
name: CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: maven
      - run: mvn -B verify
```

- `on: [push, pull_request]`: runs on every push and PR.
- `setup-java` installs JDK 17 (the project uses Java 17) and caches the Maven dependencies.
- `mvn -B verify` compiles, runs all tests, and packages. If any test fails, the job (and the workflow) turns red.

---

## 6. Running tests locally

```powershell
mvn -B test                          # all 9 tests
mvn -B test -Dtest=UserServiceTest   # only the unit tests
mvn -B test -Dtest=UserApiIntegrationTest
```

Expected result: `Tests run: 9, Failures: 0, Errors: 0`.

---

## 7. Showing "first fail, then pass" on GitHub

The final code in this repository is the **green** state. To show the red-then-green history in the Actions tab, redo the work as small commits. Do this on a branch:

```powershell
git checkout -b add-tests
```

### Step A: baseline (green)
Commit the workflow with no tests: Actions is green.

### Step B: test-first for U1, U2, U3, I3 (real red, then green)
For each feature:
1. Commit **only the test**. `git stash` or temporarily revert the `src/main` change for that feature. U3 will not even compile. Push and Actions turns red ❌.
2. Commit the `src/main` change (`UserService` / `UserRepository` / `BaseUserController`). Push and Actions turns green ✅.

### Step C: break-then-fix for U4, U5, U6, I1, I2
These test behavior that already exists, so create the red state yourself:

| Test | Temporarily break this | Why it fails |
|---|---|---|
| U4 | In `UserService.create`, remove the `if (repository.existsByEmail(email)) { throw ... }` block | No exception is thrown |
| U5 | In `UserService.delete`, remove the `equalsIgnoreCase` check | No exception is thrown |
| U6 | In `UserService.normalize`, remove `.toLowerCase()` | Email is not lowercased |
| I1 | In `SecurityConfig`, change `/api/students/**` to `.permitAll()` | Teacher gets 200, not 403 |
| I2 | In `UserService.create`, store `request.password()` instead of the encoded one | Login fails with 401 |

Push the broken version (red ❌), then revert it (`git revert` or restore the line) and push (green ✅).

### Suggested commit order

1. `Add CI workflow`
2. `Add test: blank name rejected` (red) → `Reject blank name` (green)
3. `Add test: student needs studentId` (red) → `Require studentId for students` (green)
4. `Add test: countByRole` (red) → `Add countByRole` (green)
5. `Add test: count endpoint` (red) → `Add /count endpoint` (green)
6. `Add duplicate-email test` + broken code (red) → `Restore duplicate check` (green)
7. Same pattern for U5, U6, I1, I2

Take a screenshot of the Actions list showing the red and green runs for your report.

---

## 8. Common problems

| Problem | Fix |
|---|---|
| `UnnecessaryStubbingException` | Remove the `when(...)` the code path never calls |
| `NullPointerException` in `UserResponse.from` | Stub `repository.save(...)` to return the entity (see U6) |
| Integration test fails with duplicate email | Use `uniqueEmail()` for every created user |
| `JAVA_HOME` or version errors locally | Use JDK 17 or newer (CI uses 17) |
| Actions does not start | Check the file is exactly `.github/workflows/ci.yml` and was pushed |
