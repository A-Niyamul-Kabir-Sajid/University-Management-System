# Unit Tests Explained (UserServiceTest)

File: `src/test/java/com/example/ums/service/UserServiceTest.java`
Class under test: `src/main/java/com/example/ums/service/UserService.java`

---

## 1. What a unit test is

A unit test checks **one class on its own**. Everything that class depends on is replaced by a fake (a *mock*), so:

- no Spring Boot start-up, no web server, no database
- the whole class runs in milliseconds (about 0.6 s for all 6 tests)
- when a test fails, the bug is in `UserService`, not somewhere else

`UserService` depends on two things, and both are mocked:

| Real dependency | In the test |
|---|---|
| `UserRepository` (talks to the database) | `@Mock repository` |
| `PasswordEncoder` (BCrypt) | `@Mock passwordEncoder` |

---

## 2. How the test class is built (the setup)

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService service;

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }
}
```

### What happens when you run it, step by step

1. Maven (Surefire) finds the class because it is in `src/test/java` and its name ends with `Test`.
2. JUnit 5 starts it. `@ExtendWith(MockitoExtension.class)` tells JUnit to let Mockito prepare the object **before every test method**.
3. Mockito creates a fake `UserRepository` and a fake `PasswordEncoder` (`@Mock`).
4. Mockito calls `new UserService(fakeRepository, fakeEncoder)` using the constructor and stores it in `service` (`@InjectMocks`).
5. The `@Test` method runs.
6. `@AfterEach` runs, clearing the Spring Security context so a fake login in one test cannot leak into the next.
7. For the next test, steps 3-6 repeat with **brand new** objects. Tests never share state.

### The annotations

| Annotation | Meaning |
|---|---|
| `@ExtendWith(MockitoExtension.class)` | Turns on Mockito for this class. Without it `@Mock` fields stay `null`. |
| `@Mock` | Creates a fake. Its methods do nothing and return defaults (`null`, `0`, `false`, empty `Optional`). |
| `@InjectMocks` | Creates the **real** object under test and passes the mocks into its constructor. |
| `@Test` | Marks a method as a test. Only these methods are run. |
| `@DisplayName("...")` | Readable name shown in IDE and reports. |
| `@AfterEach` | Runs after every test (pass or fail). Used for cleanup. |

### The Mockito tools used

| Tool | What it does | Example in this file |
|---|---|---|
| `when(x).thenReturn(y)` | Programs the fake: "when `x` is called, return `y`" | `when(repository.countByRole(Role.TEACHER)).thenReturn(7L)` |
| `when(x).thenAnswer(...)` | Computes the return value from the call's arguments | U6 `save`, which returns the entity it receives, with an id set |
| `verify(mock).method()` | Asserts that the method **was** called | `verify(repository).save(...)` |
| `verify(mock, never()).method()` | Asserts that the method was **not** called | `verify(repository, never()).save(any())` |
| `any()` | Matches any argument | `save(any())` |
| `ArgumentCaptor` | Grabs the object that was passed to a mock so you can inspect it | U6 captures the `AppUser` sent to `save` |

### The JUnit tools used

| Tool | What it does |
|---|---|
| `assertThrows(X.class, () -> ...)` | Passes only if the code throws exception `X` |
| `assertEquals(expected, actual)` | Passes only if the values are equal |
| `assertNull(value)` | Passes only if the value is `null` |

### The test pattern: Arrange, Act, Assert

Every test follows three parts:

1. **Arrange**: prepare the input and program the mocks.
2. **Act**: call the one `UserService` method being tested.
3. **Assert**: check the result and/or what the mocks received.

### Strict stubbing (important)

Mockito fails a test with `UnnecessaryStubbingException` if a `when(...)` is never used. That is why each test only programs the calls that its code path really makes.

---

## 3. The order of checks inside `UserService.create(...)`

Knowing this order explains the flow of U1, U2, U4 and U6:

```
create(role, request)
 1. requireValidName(name)                -> blank name?      -> BadRequestException
 2. requireStudentIdForStudent(role, id)  -> student w/o id?  -> BadRequestException
 3. normalize(email)                      -> trim + lowercase
 4. requireValidPassword(password)        -> < 6 chars?       -> BadRequestException
 5. repository.existsByEmail(email)       -> true?            -> DuplicateResourceException
 6. new AppUser, apply(...) (trim name, set studentId only for STUDENT)
 7. passwordEncoder.encode(password)
 8. repository.save(user)
 9. return UserResponse.from(saved)
```

---

## 4. The flow of each test

### U1: Blank name is rejected
`create_blankName_throwsBadRequest`

| Part | What happens |
|---|---|
| Arrange | Request with name `"   "` (spaces only), email `a@test.com`, password `secret123` |
| Act | `service.create(Role.TEACHER, req)` |
| Flow in the service | Step 1 sees the name is blank and throws `BadRequestException("Name is required")`. Steps 2-8 never run. |
| Assert | `assertThrows(BadRequestException.class, ...)` and `verify(repository, never()).save(any())` (nothing was stored) |
| No mocks programmed | The service stops before touching the repository, so no `when(...)` is needed |
| Why it was red first | The name check did not exist, so the request went through to `save` and no exception was thrown |

### U2: Student without studentId is rejected
`create_studentWithoutStudentId_throwsBadRequest`

| Part | What happens |
|---|---|
| Arrange | Request with a valid name but `studentId = " "` (blank) |
| Act | `service.create(Role.STUDENT, req)` |
| Flow in the service | Step 1 passes. Step 2 sees role is STUDENT and `studentId` is blank, so it throws `BadRequestException`. |
| Assert | Exception thrown, and `save` was never called |
| Why it was red first | The rule did not exist yet |

### U3: Count by role returns the repository count
`countByRole_returnsRepositoryCount`

| Part | What happens |
|---|---|
| Arrange | `when(repository.countByRole(Role.TEACHER)).thenReturn(7L)`: the fake repository says there are 7 teachers |
| Act | `service.countByRole(Role.TEACHER)` |
| Flow in the service | It simply returns `repository.countByRole(role)` |
| Assert | `assertEquals(7L, result)` |
| What it proves | The service passes the role through and returns what the repository gave |
| Why it was red first | `countByRole` did not exist, so the test would not even compile |

### U4: Duplicate email is rejected
`create_duplicateEmail_throwsDuplicateResource`

| Part | What happens |
|---|---|
| Arrange | `when(repository.existsByEmail("dup@test.com")).thenReturn(true)`: the fake says the email is taken |
| Act | `service.create(Role.TEACHER, req)` with email `dup@test.com` |
| Flow in the service | Steps 1-4 pass. Step 5 gets `true` and throws `DuplicateResourceException`. Steps 6-9 never run. |
| Assert | Exception thrown, and `save` was never called |
| What it proves | A used email can never create a second account |
| Red state (by breaking) | Delete the `existsByEmail` check, and no exception is thrown |

### U5: Admin cannot delete their own account
`delete_ownAccount_throwsBadRequest`

| Part | What happens |
|---|---|
| Arrange | 1. Build an `AppUser` with id 1, email `admin@ums.com`, role ADMIN. 2. `when(repository.findByIdAndRole(1L, Role.ADMIN)).thenReturn(Optional.of(me))`. 3. Put a fake login for `admin@ums.com` into `SecurityContextHolder` |
| Act | `service.delete(Role.ADMIN, 1L)` |
| Flow in the service | `getOrThrow` finds the user via the fake repository. The service reads the logged-in name from `SecurityContextHolder`. It equals the user's email, so it throws `BadRequestException("You cannot delete your own account")`. `repository.delete` is never reached. |
| Assert | Exception thrown, and `verify(repository, never()).delete(any())` |
| Cleanup | `@AfterEach` clears the security context |
| Red state (by breaking) | Remove the `equalsIgnoreCase` check, and the user gets deleted |

### U6: Password is hashed and email/name are normalized
`create_encodesPasswordAndNormalizesEmail`

| Part | What happens |
|---|---|
| Arrange | `existsByEmail("mixed@test.com")` returns `false`. `encode("secret123")` returns `"HASHED"`. `save(any)` returns the same entity with id 10 set (via `thenAnswer`). Input: name `" Dr. Rahman "`, email `"  Mixed@Test.com "`, studentId `"999"`, role TEACHER |
| Act | `service.create(Role.TEACHER, request)` |
| Flow in the service | Steps 1-2 pass. The email becomes `mixed@test.com`. Password length is fine. `existsByEmail` is false. The name is trimmed. Because the role is TEACHER, `studentId` is forced to `null`. The password becomes `"HASHED"`. `save` is called. A `UserResponse` with id 10 is returned. |
| Assert | `ArgumentCaptor` grabs the `AppUser` that was passed to `save` and checks: password `"HASHED"`, email `"mixed@test.com"`, name `"Dr. Rahman"`, role TEACHER, `studentId` null. Also checks the response id is 10 |
| What it proves | The plain password is never stored, the email is cleaned, and teachers never keep a `studentId` |
| Red state (by breaking) | Remove `.toLowerCase()` from `normalize`, and the email assertion fails |

---

## 5. Why these 6 tests

Each one covers a different branch of `UserService`, so together they cover validation (U1, U2), a read path (U3), a conflict rule (U4), a safety rule (U5), and the main success path with data transformation (U6).

## 6. Running only these tests

```powershell
mvn -B test -Dtest=UserServiceTest
```
