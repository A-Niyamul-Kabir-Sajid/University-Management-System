# REST API Guide – CRUD for Admin, Teacher and Student

This guide explains how the REST API in this project works, from the HTTP request to the database row and back.

---

# 1. What is REST?

REST is a set of conventions for building web APIs.

| Idea | Meaning | In this project |
|------|---------|-----------------|
| **Resource** | A "thing" the API manages, named with a noun in the URL | `/api/students`, `/api/teachers`, `/api/admins` |
| **HTTP method** | The verb, saying what to do with the resource | `GET`, `POST`, `PUT`, `DELETE` |
| **Representation** | The data format sent back and forth | JSON |
| **Status code** | A number saying how the request went | `200`, `201`, `204`, `400`, `404`, `409` |
| **Stateless request** | Each request carries what the server needs to handle it | The session cookie `JSESSIONID` identifies the user |

Rule of thumb: the **URL names the thing**, the **method names the action**. We never write URLs like `/api/createStudent` or `/api/deleteStudent`.

---

# 2. The CRUD ↔ HTTP mapping

CRUD stands for Create, Read, Update, Delete.

| CRUD | HTTP method | URL | Success code | Meaning |
|------|-------------|-----|--------------|---------|
| Read (all) | `GET` | `/api/students` | `200 OK` | List every student |
| Read (one) | `GET` | `/api/students/{id}` | `200 OK` | One student |
| Create | `POST` | `/api/students` | `201 Created` | Add a new student |
| Update | `PUT` | `/api/students/{id}` | `200 OK` | Change an existing student |
| Delete | `DELETE` | `/api/students/{id}` | `204 No Content` | Remove a student |

The same five operations exist for `/api/teachers` and `/api/admins`.

---

# 3. Project structure (the layers)

```text
src/main/java/com/example/ums/
├── controller/   ← receives HTTP requests, returns HTTP responses
│   ├── BaseUserController.java   (the 5 CRUD endpoints, written once)
│   ├── AdminController.java      (/api/admins)
│   ├── TeacherController.java    (/api/teachers)
│   ├── StudentController.java    (/api/students)
│   └── AuthController.java       (/api/auth/login, /me)
├── service/
│   └── UserService.java          ← business logic (rules, checks, hashing)
├── repository/
│   └── UserRepository.java       ← talks to the database
├── model/
│   ├── AppUser.java              ← the database entity (table "users")
│   └── Role.java                 ← ADMIN / TEACHER / STUDENT
├── dto/
│   ├── UserRequest.java          ← JSON coming IN
│   ├── UserResponse.java         ← JSON going OUT
│   └── ErrorResponse.java        ← JSON for errors
└── exception/
    ├── GlobalExceptionHandler.java   ← turns exceptions into JSON + status codes
    └── (NotFound, Duplicate, BadRequest exceptions)
```

## Why layers?

| Layer | Job | Does NOT do |
|-------|-----|-------------|
| Controller | Read the request, call the service, build the response | Business rules, SQL |
| Service | Rules: unique email, password length, hashing, "cannot delete yourself" | HTTP details |
| Repository | Save and find rows | Rules |

---

# 4. One table, three resources

Admins, teachers and students are all rows in **one table**, `users`. The `role` column tells them apart.

```text
users
┌────┬──────────────┬─────────────────┬──────────────┬─────────┬────────────┬───────────┐
│ id │ name         │ email           │ password     │ role    │ department │ studentId │
├────┼──────────────┼─────────────────┼──────────────┼─────────┼────────────┼───────────┤
│ 1  │ Admin        │ admin@ums.com   │ $2a$10$...   │ ADMIN   │ null       │ null      │
│ 2  │ Demo Teacher │ teacher@ums.com │ $2a$10$...   │ TEACHER │ CSE        │ null      │
│ 3  │ Demo Student │ student@ums.com │ $2a$10$...   │ STUDENT │ CSE        │ 2107001   │
└────┴──────────────┴─────────────────┴──────────────┴─────────┴────────────┴───────────┘
```

- `email` is unique and is used as the login name.
- `password` is a BCrypt hash, never the plain password.
- `studentId` is only kept for students. The service sets it to `null` for admins and teachers.

---

# 5. Writing the CRUD code once: `BaseUserController`

The three resources behave the same way, so the five endpoints are written once in an abstract class ([BaseUserController.java](../src/main/java/com/example/ums/controller/BaseUserController.java)). Each subclass only says which role it handles.

```java
@RestController
@RequestMapping("/api/students")
public class StudentController extends BaseUserController {

    public StudentController(UserService userService) { super(userService); }

    @Override
    protected Role role() { return Role.STUDENT; }
}
```

`TeacherController` returns `Role.TEACHER` and is mapped to `/api/teachers`. `AdminController` returns `Role.ADMIN` and is mapped to `/api/admins`.

The base class has:

```java
@GetMapping            public List<UserResponse> getAll()
@GetMapping("/{id}")   public UserResponse getOne(@PathVariable Long id)
@PostMapping           public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request)
@PutMapping("/{id}")   public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request)
@DeleteMapping("/{id}") @ResponseStatus(NO_CONTENT) public void delete(@PathVariable Long id)
```

## Important annotations

| Annotation | What it does |
|------------|--------------|
| `@RestController` | Every method returns data (JSON), not an HTML page |
| `@RequestMapping("/api/students")` | Base URL for all methods in the class |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` | Maps an HTTP method to a Java method |
| `@PathVariable Long id` | Takes `{id}` from the URL |
| `@RequestBody` | Converts the JSON body into a Java object |
| `@Valid` | Runs the validation rules on that object |
| `@ResponseStatus(NO_CONTENT)` | Makes the response `204` |

---

# 6. DTOs: what goes in and what comes out

A **DTO** (Data Transfer Object) is a small class that defines the JSON shape. We never send the database entity directly, because it contains the password hash.

## Input: `UserRequest`

```java
public record UserRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    String password,
    String department,
    String studentId) {}
```

```json
{
  "name": "Rahim",
  "email": "rahim@ums.com",
  "password": "pass123",
  "department": "CSE",
  "studentId": "2107001"
}
```

| Field | Required? | Rule |
|-------|-----------|------|
| `name` | Yes | Not blank |
| `email` | Yes | Not blank, valid email format |
| `password` | On create: yes. On update: optional | Minimum 6 characters (checked in the service) |
| `department` | No | Free text |
| `studentId` | No | Kept only for students |

## Output: `UserResponse`

```json
{
  "id": 4,
  "name": "Rahim",
  "email": "rahim@ums.com",
  "role": "STUDENT",
  "department": "CSE",
  "studentId": "2107001"
}
```

The `password` field is not in `UserResponse`, so it can never leak.

---

# 7. Flow of each operation

## 7.1 Create: `POST /api/students`

```text
Client                Security            Controller         Service              Repository / DB
  │  POST + JSON         │                    │                 │                       │
  ├─────────────────────►│ logged in?         │                 │                       │
  │                      │ role allowed?      │                 │                       │
  │                      ├───────────────────►│ @Valid body     │                       │
  │                      │                    ├────────────────►│ normalize email       │
  │                      │                    │                 │ password >= 6 chars?  │
  │                      │                    │                 │ email already used? ─►│ existsByEmail
  │                      │                    │                 │ hash password (BCrypt)│
  │                      │                    │                 │ save ────────────────►│ INSERT
  │  201 Created         │                    │◄────────────────┤◄──────────────────────┤
  │◄─────────────────────┴────────────────────┤ + Location header                       │
```

Step by step:
1. Spring Security checks the session cookie and the role.
2. `@Valid` checks `name` and `email`. If this fails, the response is `400`.
3. `UserService.create` lowercases and trims the email.
4. It checks the password is at least 6 characters, or answers `400`.
5. It checks the email is not taken, or answers `409 Conflict`.
6. It sets the role from the controller (`STUDENT`), so a client cannot choose its own role.
7. It hashes the password with BCrypt and saves the row.
8. The controller returns `201 Created`, the new user as JSON, and a `Location` header such as `http://localhost:8080/api/students/4`.

## 7.2 Read all: `GET /api/students`

- `repository.findByRole(STUDENT)` runs `SELECT ... WHERE role = 'STUDENT'`.
- Each row is converted to `UserResponse`.
- The response is `200` with a JSON array. The array is `[]` if no students exist.

## 7.3 Read one: `GET /api/students/4`

- `repository.findByIdAndRole(4, STUDENT)` looks for a row with both the ID and the role.
- If none is found, the response is `404`.
- Because the role is part of the query, `GET /api/teachers/4` returns `404` when user 4 is a student. IDs do not leak across resources.

## 7.4 Update: `PUT /api/students/4`

1. Find the student by ID and role, or answer `404`.
2. Check that no other user already has the new email, or answer `409`. Keeping your own email is fine.
3. Copy `name`, `email`, `department` and `studentId` onto the entity.
4. If `password` is present and not blank, validate it and hash it. If you leave it out, the old password stays.
5. Save and return `200` with the updated user.

`PUT` here replaces the editable fields with the ones you send. Send the full set of fields each time.

## 7.5 Delete: `DELETE /api/students/4`

1. Find the student by ID and role, or answer `404`.
2. If the target is the currently logged-in user, answer `400 You cannot delete your own account`. This stops an admin locking themselves out.
3. Delete the row and return `204` with no body.

---

# 8. Validation and error handling

All errors become a clean JSON body through [GlobalExceptionHandler.java](../src/main/java/com/example/ums/exception/GlobalExceptionHandler.java), so the client never sees a Java stack trace.

## Error JSON shape

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "timestamp": "2026-10-26T10:15:30Z",
  "fieldErrors": {
    "email": "email must be valid"
  }
}
```

`fieldErrors` is only filled for validation errors.

## Exception → status code

| Situation | Exception | Status |
|-----------|-----------|--------|
| ID does not exist (or has a different role) | `ResourceNotFoundException` | `404 Not Found` |
| Email already used | `DuplicateResourceException` | `409 Conflict` |
| Password too short, deleting yourself | `BadRequestException` | `400 Bad Request` |
| `@Valid` fails (blank name, bad email) | `MethodArgumentNotValidException` | `400 Bad Request` |
| Broken or missing JSON body | `HttpMessageNotReadableException` | `400 Bad Request` |
| `/api/students/abc` (ID is not a number) | `MethodArgumentTypeMismatchException` | `400 Bad Request` |
| Wrong login | `BadCredentialsException` | `401 Unauthorized` |
| Not logged in | Spring Security | `401 Unauthorized` |
| Role not allowed | Spring Security | `403 Forbidden` |

## All status codes used

| Code | Name | When |
|------|------|------|
| 200 | OK | Successful GET and PUT |
| 201 | Created | Successful POST |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Invalid input |
| 401 | Unauthorized | Not logged in / wrong credentials |
| 403 | Forbidden | Logged in, wrong role |
| 404 | Not Found | ID does not exist |
| 409 | Conflict | Duplicate email |

---

# 9. Try it with curl

First log in and save the cookie (the Spring Security guide explains why):

```powershell
curl.exe -c cookies.txt -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"admin@ums.com\",\"password\":\"admin123\"}"
```

## Create, then read, update, delete

```powershell
# CREATE -> 201 (add -i to see the Location header)
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Rahim\",\"email\":\"rahim@ums.com\",\"password\":\"pass123\",\"department\":\"CSE\",\"studentId\":\"2107002\"}"

# READ ALL -> 200
curl.exe -b cookies.txt http://localhost:8080/api/students

# READ ONE -> 200 (use the id from the create response)
curl.exe -b cookies.txt http://localhost:8080/api/students/4

# UPDATE -> 200 (password omitted, so it stays the same)
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/students/4 `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Rahim Uddin\",\"email\":\"rahim@ums.com\",\"department\":\"EEE\",\"studentId\":\"2107002\"}"

# DELETE -> 204
curl.exe -i -b cookies.txt -X DELETE http://localhost:8080/api/students/4
```

## Trigger each error on purpose

```powershell
# 404: id that does not exist
curl.exe -i -b cookies.txt http://localhost:8080/api/students/9999

# 400: validation failed (blank name, bad email)
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"pass123\"}"

# 400: password too short
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"A\",\"email\":\"a@ums.com\",\"password\":\"123\"}"

# 409: same email twice (run the create command two times)

# 400: malformed JSON
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" -d "{ broken"

# 400: id is not a number
curl.exe -i -b cookies.txt http://localhost:8080/api/students/abc

# 400: delete your own account (admin id is usually 1)
curl.exe -i -b cookies.txt -X DELETE http://localhost:8080/api/admins/1
```

More commands for teachers and admins are in [Curl.md](Curl.md).

---

# 10. Design choices to know about

| Choice | Reason |
|--------|--------|
| One controller base class + one service for all roles | No copy-pasted code; a new rule applies to all three |
| Role is set by the URL, not by the JSON | A client cannot send `"role":"ADMIN"` to promote itself |
| `findByIdAndRole` | `/api/teachers/{id}` can never return a student |
| DTOs instead of the entity | The password hash is never sent out |
| Email lowercased and trimmed | `Rahim@UMS.com` and `rahim@ums.com` are the same user |
| `Location` header on `201` | REST convention: tells the client where the new resource lives |
| `PUT` keeps the password when omitted | Edit a profile without re-entering the password |

## Limitations (useful to mention in a report)

- `PUT` needs `name` and `email` every time; there is no partial update (`PATCH`).
- `GET` list has no pagination or search; it returns all rows of that role.
- A student can call all five operations on `/api/students`, including creating and deleting other students. The rules only check the role, not ownership of a record. Restricting this would need extra checks in the service.
- The same is true for teachers on `/api/teachers`.
