# Spring Security Guide – Login, Logout, Password Hashing and RBAC

This guide explains how authentication and authorization work in this project, using the real code.

Files involved:
- [SecurityConfig.java](../src/main/java/com/example/ums/security/SecurityConfig.java): rules, logout, error responses, password encoder
- [CustomUserDetailsService.java](../src/main/java/com/example/ums/security/CustomUserDetailsService.java): loads a user from the database
- [AuthController.java](../src/main/java/com/example/ums/controller/AuthController.java): the login endpoint
- [DataInitializer.java](../src/main/java/com/example/ums/security/DataInitializer.java): creates the first admin
- [UserService.java](../src/main/java/com/example/ums/service/UserService.java): hashes passwords on create and update

---

# 1. Key terms

| Term | Meaning |
|------|---------|
| **Authentication** | Proving who you are (email + password) |
| **Authorization** | Deciding what you may do (based on your role) |
| **Session** | Server-side memory of "this user is logged in" |
| **JSESSIONID** | Cookie holding the session ID; the client sends it back with each request |
| **Hashing** | One-way scrambling of a password so the original cannot be recovered |
| **BCrypt** | The hashing algorithm used here |
| **RBAC** | Role-Based Access Control: permissions are given to roles, and users get roles |
| **Filter chain** | A line of security checks every request passes through before reaching a controller |

Authentication answers "who are you?" (a failure gives `401`). Authorization answers "are you allowed?" (a failure gives `403`).

---

# 2. Big picture

```text
                        ┌────────────────────────────────────────────┐
                        │            Spring Security filter chain    │
 HTTP request           │                                            │
 ─────────────────────► │  1. Is there a session (JSESSIONID)?       │
                        │  2. Who is the user and what is the role?  │
                        │  3. Does the URL rule allow that role?     │
                        └───────────────┬──────────────┬─────────────┘
                                        │ allowed      │ not allowed
                                        ▼              ▼
                                  Controller     401 (no login) or
                                  → Service      403 (wrong role)
                                  → Database
```

Nothing reaches a controller until the filter chain allows it.

---

# 3. Password hashing (BCrypt)

## The rule

The database never stores `admin123`. It stores something like:

```text
$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BfjkVEpgVEQnr1uzZ6Sq0Z1p9uNq
```

## The bean

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

## Why BCrypt?

| Property | Benefit |
|----------|---------|
| One-way | A stolen database does not reveal passwords |
| **Salted** (random value added per hash) | Two users with the same password get different hashes |
| **Slow on purpose** | Guessing millions of passwords becomes expensive |
| Self-contained | The salt and cost are inside the hash string |

## Anatomy of a hash

```text
$2a$10$ 7EqJtq98hPqEX7fNZaFWoO hi5BfjkVEpgVEQnr1uzZ6Sq0Z1p9uNq
 │   │         │                        │
 │   │         └ salt (22 chars)        └ hash (31 chars)
 │   └ cost factor 10 (2^10 rounds)
 └ algorithm version
```

## Where it happens in the code

| Where | What |
|-------|------|
| `DataInitializer.createUser` | `passwordEncoder.encode(password)` for the default admin and demo users |
| `UserService.create` | `passwordEncoder.encode(request.password())` when a user is created |
| `UserService.update` | Re-hashes only if a new password is sent |
| Login | Spring Security's `DaoAuthenticationProvider` calls `passwordEncoder.matches(typed, storedHash)` |

You never decrypt a hash. To check a login, the typed password is hashed again with the same salt and compared with the stored hash.

---

# 4. Loading the user: `CustomUserDetailsService`

Spring Security does not know our `users` table. This class connects the two.

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    public UserDetails loadUserByUsername(String email) {
        AppUser user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return User.withUsername(user.getEmail())
                .password(user.getPassword())          // the BCrypt hash
                .roles(user.getRole().name())          // ADMIN -> ROLE_ADMIN
                .build();
    }
}
```

- The "username" in Spring Security is our **email**.
- `.roles("ADMIN")` stores the authority `ROLE_ADMIN`. The `ROLE_` prefix is added automatically, and `hasRole("ADMIN")` checks for it.
- The returned object holds the hash. Spring compares it with the typed password.

---

# 5. Login flow: `POST /api/auth/login`

The code is in [AuthController.java](../src/main/java/com/example/ums/controller/AuthController.java).

```java
@PostMapping("/login")
public UserResponse login(@Valid @RequestBody LoginRequest request,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

    Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                    request.email().trim().toLowerCase(), request.password()));

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);

    return currentUser(authentication);
}
```

## Step by step

```text
 Client                         AuthController               AuthenticationManager
   │  POST /api/auth/login           │                               │
   │  {email, password}              │                               │
   ├────────────────────────────────►│ authenticate(email, password) │
   │                                 ├──────────────────────────────►│
   │                                 │                               │ 1. CustomUserDetailsService
   │                                 │                               │    loads user + hash from DB
   │                                 │                               │ 2. BCrypt matches(typed, hash)?
   │                                 │                               │ 3. builds Authentication
   │                                 │◄──────────────────────────────┤    (email + ROLE_xxx)
   │                                 │ save into the HTTP session    │
   │  200 + UserResponse JSON        │                               │
   │  Set-Cookie: JSESSIONID=...     │                               │
   │◄────────────────────────────────┤                               │
```

1. The JSON is validated (`email` and `password` must not be blank).
2. The email is trimmed and lowercased.
3. `authenticationManager.authenticate(...)` loads the user, checks the password with BCrypt, and returns an `Authentication` holding the email and roles.
4. If email or password is wrong, it throws `BadCredentialsException`. `GlobalExceptionHandler` turns it into `401 Invalid email or password`.
5. On success, the `Authentication` is stored in an empty `SecurityContext`, and that context is saved into the HTTP session (`HttpSessionSecurityContextRepository`).
6. The server creates the session and replies with `Set-Cookie: JSESSIONID=...`.
7. The response body is the user (no password), so the client knows its role.

The wrong-email and wrong-password cases return the same message, so an attacker cannot tell which emails exist.

## Later requests

```text
GET /api/students
Cookie: JSESSIONID=ABC123
          │
          ▼
Security filter reads the cookie → finds the session → restores the
Authentication (email + roles) → checks the URL rule → controller.
```

This is why curl needs `-c cookies.txt` on login and `-b cookies.txt` afterwards.

## Who am I: `GET /api/auth/me`

Returns the logged-in user's profile. Any authenticated user can call it. Without a valid session the response is `401`.

---

# 6. Logout flow: `POST /api/auth/logout`

Logout is configured in `SecurityConfig`; there is no controller method for it.

```java
.logout(logout -> logout
        .logoutUrl("/api/auth/logout")
        .invalidateHttpSession(true)
        .deleteCookies("JSESSIONID")
        .logoutSuccessHandler((request, response, authentication) ->
                writeJson(response, 200, "{\"message\":\"Logged out successfully\"}")))
```

What happens:
1. Spring Security's logout filter sees `POST /api/auth/logout`.
2. `invalidateHttpSession(true)` destroys the server-side session.
3. `deleteCookies("JSESSIONID")` tells the client to drop the cookie.
4. The success handler returns `200` with `{"message":"Logged out successfully"}`.

After this, using the old cookie gives `401`, because the session no longer exists.

Logout must be **POST**. Opening the URL in a browser (a GET) will not log you out.

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/auth/logout
curl.exe -i -b cookies.txt http://localhost:8080/api/auth/me      # now 401
```

---

# 7. Role-based access control (RBAC)

## The roles

[Role.java](../src/main/java/com/example/ums/model/Role.java) has three values: `ADMIN`, `TEACHER`, `STUDENT`. Each user has exactly one, stored in the `role` column.

## The rules (from `SecurityConfig`)

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login", "/error").permitAll()
        .requestMatchers("/api/admins/**").hasRole("ADMIN")
        .requestMatchers("/api/teachers/**").hasAnyRole("ADMIN", "TEACHER")
        .requestMatchers("/api/students/**").hasAnyRole("ADMIN", "STUDENT")
        .anyRequest().authenticated())
```

Rules are checked **top to bottom**, and the first match wins. `anyRequest().authenticated()` is the catch-all, so anything not listed still needs a login. That is the safe default.

## Access matrix

| Endpoint | Not logged in | STUDENT | TEACHER | ADMIN |
|----------|:---:|:---:|:---:|:---:|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/auth/me` | ❌ 401 | ✅ | ✅ | ✅ |
| `POST /api/auth/logout` | ✅ | ✅ | ✅ | ✅ |
| `/api/students/**` | ❌ 401 | ✅ | ❌ 403 | ✅ |
| `/api/teachers/**` | ❌ 401 | ❌ 403 | ✅ | ✅ |
| `/api/admins/**` | ❌ 401 | ❌ 403 | ❌ 403 | ✅ |

The admin can reach everything. Admin is the only role that can manage admins.

## 401 vs 403

| | 401 Unauthorized | 403 Forbidden |
|---|---|---|
| Meaning | "I don't know who you are" | "I know who you are, and you may not do this" |
| Cause | No cookie, expired session, wrong password | Valid session, wrong role |
| Fix | Log in | Use an account with the right role |

Both return JSON through custom handlers in `SecurityConfig`:

```json
{"status":401,"error":"Unauthorized","message":"Authentication required. Please log in."}
{"status":403,"error":"Forbidden","message":"You do not have permission to access this resource."}
```

## How the check runs

```text
 Request: GET /api/teachers   (cookie of a STUDENT)
     │
     ▼
 Session found → Authentication: student@ums.com, [ROLE_STUDENT]
     │
     ▼
 Rule matched: /api/teachers/** → hasAnyRole(ADMIN, TEACHER)
     │
     ▼
 ROLE_STUDENT is not in {ROLE_ADMIN, ROLE_TEACHER}
     │
     ▼
 accessDeniedHandler → 403 JSON   (the controller is never called)
```

## Demo accounts

| Role | Email | Password |
|------|-------|----------|
| ADMIN | `admin@ums.com` | `admin123` |
| TEACHER | `teacher@ums.com` | `teacher123` |
| STUDENT | `student@ums.com` | `student123` |

The admin comes from the `ADMIN_EMAIL` / `ADMIN_PASSWORD` environment values in `compose.yaml`. The teacher and student are created because `SEED_DEMO_DATA` is `"true"`.

---

# 8. The first admin: `DataInitializer`

If nobody can log in, nobody can create users. So on startup, `DataInitializer` (a `CommandLineRunner`) does this:

```text
App starts
   │
   ├─ no ADMIN in the database?  → create the default admin (password hashed)
   │
   └─ app.seed-demo-data is true? → create demo teacher and student if missing
```

It is safe to restart: it checks `existsByRole` and `existsByEmail` first, so it never creates duplicates.

Change the admin credentials through environment variables, not code. In production, set `SEED_DEMO_DATA` to `false` and use a strong admin password.

---

# 9. Other security settings

| Setting | Code | Why |
|---------|------|-----|
| CSRF disabled | `.csrf(AbstractHttpConfigurer::disable)` | CSRF attacks target browser forms that rely on cookies. This is a JSON API used from curl/Postman. If a browser front end is added later, enable CSRF or switch to tokens (JWT) |
| Session based | `JSESSIONID` cookie | Simple; the server remembers who is logged in |
| Passwords never returned | `UserResponse` has no password | The hash is not exposed |
| Role set by server | Controller supplies `role()` | A client cannot choose its own role |
| Self-delete blocked | `UserService.delete` | An admin cannot lock themselves out |

---

# 10. Test the whole flow with curl

Use separate cookie files for each role.

## 10.1 Log in as each role

```powershell
curl.exe -c admin.txt -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@ums.com\",\"password\":\"admin123\"}"
curl.exe -c teacher.txt -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"teacher@ums.com\",\"password\":\"teacher123\"}"
curl.exe -c student.txt -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"student@ums.com\",\"password\":\"student123\"}"
```

## 10.2 Check authentication

```powershell
# 401: no cookie
curl.exe -i http://localhost:8080/api/students

# 401: wrong password
curl.exe -i -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@ums.com\",\"password\":\"wrong\"}"

# 200: who am I
curl.exe -b student.txt http://localhost:8080/api/auth/me
```

## 10.3 Check authorization (RBAC)

```powershell
# STUDENT
curl.exe -i -b student.txt http://localhost:8080/api/students     # 200
curl.exe -i -b student.txt http://localhost:8080/api/teachers     # 403
curl.exe -i -b student.txt http://localhost:8080/api/admins       # 403

# TEACHER
curl.exe -i -b teacher.txt http://localhost:8080/api/teachers     # 200
curl.exe -i -b teacher.txt http://localhost:8080/api/students     # 403
curl.exe -i -b teacher.txt http://localhost:8080/api/admins       # 403

# ADMIN
curl.exe -i -b admin.txt http://localhost:8080/api/students       # 200
curl.exe -i -b admin.txt http://localhost:8080/api/teachers       # 200
curl.exe -i -b admin.txt http://localhost:8080/api/admins         # 200
```

## 10.4 Check password hashing

Open a shell in the database container and look at the stored passwords:

```powershell
docker exec -it ums-db psql -U ums_user -d ums -c "SELECT email, role, password FROM users;"
```

Every `password` value should start with `$2a$` (BCrypt), never the plain text.

## 10.5 Check logout

```powershell
curl.exe -b admin.txt -X POST http://localhost:8080/api/auth/logout   # 200 message
curl.exe -i -b admin.txt http://localhost:8080/api/auth/me            # 401
```

(`curl -b` only reads the cookie file here. The server has already destroyed that session, so the old cookie is useless.)

---

# 11. Quick summary

| Requirement | How it is done |
|-------------|----------------|
| Login | `POST /api/auth/login` → `AuthenticationManager` → session + `JSESSIONID` |
| Logout | `POST /api/auth/logout` → session destroyed, cookie deleted |
| Password hashing | `BCryptPasswordEncoder` (salted, slow, one-way) |
| User lookup | `CustomUserDetailsService` loads by email from the `users` table |
| RBAC | `hasRole` / `hasAnyRole` rules in `SecurityConfig`, one role per user |
| Error handling | JSON `401` and `403` via custom entry point and access-denied handler |
| First admin | `DataInitializer` on startup |

## Limitations (useful to mention in a report)

- Rules only check the **role**, not **ownership**. A student can see and edit other students through `/api/students`. A stricter design would let students read only their own record.
- A teacher can create, update and delete other teachers.
- No account lockout or rate limiting on repeated wrong passwords.
- Sessions are stored in server memory by default, so they are lost when the app restarts.
- CSRF is off, which is fine for curl/Postman but needs revisiting for a browser front end.
- Demo accounts and the default admin password should be changed before any real deployment.
