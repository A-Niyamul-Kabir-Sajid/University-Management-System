# CURL API Testing Guide

## What is curl?

`curl` is a command-line tool used to send HTTP requests to a web server and receive responses.

It is commonly used for:

- Testing REST APIs
- Debugging backend services
- Calling endpoints without Postman
- Automating API requests in scripts

In this example, we are testing:

- Authentication (Login)
- Session Cookies
- Student CRUD Operations

---

# Understanding the Request Flow

```text
Login
   ↓
Receive Cookie (JSESSIONID)
   ↓
Save Cookie into cookies.txt
   ↓
Send Cookie with next requests
   ↓
Server recognizes logged-in user
```

---

# 1. Login Request

```powershell
curl.exe -c cookies.txt -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"admin@ums.com\",\"password\":\"admin123\"}"
```

## Breakdown

### curl.exe

Runs the curl program.

```powershell
curl.exe
```

---

### -c cookies.txt

Cookie jar.

```powershell
-c cookies.txt
```

Means:

Save cookies returned by the server into a file.

Example server response:

```http
Set-Cookie: JSESSIONID=ABC123
```

Stored in:

```text
cookies.txt
```

---

### -X POST

Specifies the HTTP method.

```powershell
-X POST
```

Equivalent to:

```http
POST /api/auth/login
```

---

### URL

```powershell
http://localhost:8080/api/auth/login
```

Meaning:

| Part | Meaning |
|--------|--------|
| localhost | Your computer |
| 8080 | Server Port |
| /api/auth/login | Login Endpoint |

---

### -H

Adds an HTTP Header.

```powershell
-H "Content-Type: application/json"
```

Sent to server:

```http
Content-Type: application/json
```

This tells Spring Boot that JSON is being sent.

---

### -d

Request body (Data).

```powershell
-d "{\"email\":\"admin@ums.com\",\"password\":\"admin123\"}"
```

Actual JSON:

```json
{
  "email": "admin@ums.com",
  "password": "admin123"
}
```

---

## Actual HTTP Request

```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "email":"admin@ums.com",
  "password":"admin123"
}
```

---

# Browser Equivalent

Can you use browser URL?

❌ No

Because:

- Browser URL only sends GET requests.
- Login requires POST.
- Login requires a JSON body.

Cannot do:

```text
http://localhost:8080/api/auth/login
```

for login.

---

# Postman Equivalent

### Method

```text
POST
```

### URL

```text
http://localhost:8080/api/auth/login
```

### Headers

```text
Content-Type: application/json
```

### Body → Raw → JSON

```json
{
  "email": "admin@ums.com",
  "password": "admin123"
}
```

Click:

```text
Send
```

---

# 2. Who Am I Request

```powershell
curl.exe -b cookies.txt http://localhost:8080/api/auth/me
```

---

## Breakdown

### -b cookies.txt

Reads cookies from file.

```powershell
-b cookies.txt
```

Suppose file contains:

```text
JSESSIONID=ABC123
```

Curl sends:

```http
Cookie: JSESSIONID=ABC123
```

Server now knows who you are.

---

## Actual HTTP Request

```http
GET /api/auth/me
Cookie: JSESSIONID=ABC123
```

---

# Browser Equivalent

If the browser already logged in:

```text
http://localhost:8080/api/auth/me
```

will work because the browser automatically sends cookies.

---

# Postman Equivalent

Method:

```text
GET
```

URL:

```text
http://localhost:8080/api/auth/me
```

Cookies:

Postman automatically stores and sends cookies.

Click:

```text
Send
```

---

# 3. Get All Students

```powershell
curl.exe -b cookies.txt http://localhost:8080/api/students
```

---

## Meaning

- GET request
- Sends session cookie
- Returns all students

---

## Actual Request

```http
GET /api/students

Cookie: JSESSIONID=ABC123
```

---

# Browser Equivalent

Works only if endpoint allows GET:

```text
http://localhost:8080/api/students
```

Browser automatically sends cookies.

---

# Postman Equivalent

Method:

```text
GET
```

URL:

```text
http://localhost:8080/api/students
```

Click:

```text
Send
```

---

# 4. Create Student

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Rahim\",\"email\":\"rahim@ums.com\",\"password\":\"pass123\",\"department\":\"CSE\",\"studentId\":\"2107001\"}"
```

---

## JSON Body

```json
{
  "name": "Rahim",
  "email": "rahim@ums.com",
  "password": "pass123",
  "department": "CSE",
  "studentId": "2107001"
}
```

---

## Spring Boot Controller Example

```java
@PostMapping("/students")
public Student create(@RequestBody StudentDTO dto)
{
    return studentService.create(dto);
}
```

---

# Browser Equivalent

❌ Not possible directly.

Reason:

Browser URL can only perform GET requests.

Creating data requires POST.

---

# Postman Equivalent

Method:

```text
POST
```

URL:

```text
http://localhost:8080/api/students
```

Headers:

```text
Content-Type: application/json
```

Body:

```json
{
  "name": "Rahim",
  "email": "rahim@ums.com",
  "password": "pass123",
  "department": "CSE",
  "studentId": "2107001"
}
```

---

# 5. Update Student

```powershell
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/students/1 `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Rahim Uddin\",\"email\":\"rahim@ums.com\",\"department\":\"EEE\",\"studentId\":\"2107001\"}"
```

---

## URL

```text
/api/students/1
```

Meaning:

Update student whose ID is 1.

---

## PUT Method

```powershell
-X PUT
```

PUT updates an existing resource.

---

## Actual HTTP Request

```http
PUT /api/students/1

Cookie: JSESSIONID=ABC123

Content-Type: application/json

{
  "name":"Rahim Uddin",
  "email":"rahim@ums.com",
  "department":"EEE",
  "studentId":"2107001"
}
```

---

# Browser Equivalent

❌ Not possible directly.

Browser URL bar only supports GET.

---

# Postman Equivalent

Method:

```text
PUT
```

URL:

```text
http://localhost:8080/api/students/1
```

Headers:

```text
Content-Type: application/json
```

Body:

```json
{
  "name": "Rahim Uddin",
  "email": "rahim@ums.com",
  "department": "EEE",
  "studentId": "2107001"
}
```

---

# 6. Delete Student

```powershell
curl.exe -b cookies.txt -X DELETE http://localhost:8080/api/students/1
```

---

## Meaning

Delete student whose ID is 1.

---

## Actual HTTP Request

```http
DELETE /api/students/1

Cookie: JSESSIONID=ABC123
```

---

# Browser Equivalent

❌ Not possible directly.

Browser URL cannot perform DELETE requests.

---

# Postman Equivalent

Method:

```text
DELETE
```

URL:

```text
http://localhost:8080/api/students/1
```

Click:

```text
Send
```

---

# 7. Get One Student By ID

```powershell
curl.exe -b cookies.txt http://localhost:8080/api/students/1
```

- GET request, returns `200` with the student, or `404` if the ID does not exist.
- Browser: works if you are already logged in, `http://localhost:8080/api/students/1`.
- Postman: `GET` + the URL, then Send.

---

# 8. Logout

```powershell
curl.exe -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/auth/logout
```

- Handled by Spring Security (`/api/auth/logout`), not by `AuthController`.
- It must be `POST`, so the browser URL bar cannot do it.
- After logout, the old `JSESSIONID` is invalid. Calling `/api/auth/me` with it returns `401`.

---

# 9. Teacher Endpoints (`/api/teachers`)

Same five operations as students. Allowed roles: `ADMIN` and `TEACHER`.

```powershell
# List all teachers
curl.exe -b cookies.txt http://localhost:8080/api/teachers

# Get one teacher
curl.exe -b cookies.txt http://localhost:8080/api/teachers/1

# Create a teacher
curl.exe -b cookies.txt -X POST http://localhost:8080/api/teachers `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Dr. Karim\",\"email\":\"karim@ums.com\",\"password\":\"pass123\",\"department\":\"CSE\"}"

# Update a teacher
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/teachers/1 `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Dr. Karim Hossain\",\"email\":\"karim@ums.com\",\"department\":\"EEE\"}"

# Delete a teacher
curl.exe -b cookies.txt -X DELETE http://localhost:8080/api/teachers/1
```

---

# 10. Admin Endpoints (`/api/admins`)

Same five operations. Allowed role: `ADMIN` only.

```powershell
# List all admins
curl.exe -b cookies.txt http://localhost:8080/api/admins

# Get one admin
curl.exe -b cookies.txt http://localhost:8080/api/admins/1

# Create an admin
curl.exe -b cookies.txt -X POST http://localhost:8080/api/admins `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Second Admin\",\"email\":\"admin2@ums.com\",\"password\":\"admin456\"}"

# Update an admin
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/admins/2 `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Second Admin Updated\",\"email\":\"admin2@ums.com\"}"

# Delete an admin
curl.exe -b cookies.txt -X DELETE http://localhost:8080/api/admins/2
```

---

# 11. Log In As Another User (Separate Cookie File)

Use a different cookie file for each user so sessions do not overwrite each other.

```powershell
# Log in as the teacher created above
curl.exe -c teacher-cookies.txt -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"karim@ums.com\",\"password\":\"pass123\"}"

# Use the teacher session
curl.exe -b teacher-cookies.txt http://localhost:8080/api/auth/me
curl.exe -b teacher-cookies.txt http://localhost:8080/api/teachers
```

---

# 12. Why "Create Student" Fails in PowerShell (Quoting)

This command looks right but fails:

```powershell
curl.exe -b cookies.txt -X POST "http://localhost:8080/api/students" `
  -H "Content-Type: application/json" `
  -d '{"name":"Sajid Hasan","email":"sajid@ums.com","password":"pass123","department":"CSE","studentId":"2107001"}'
```

**Cause:** Windows PowerShell 5.1 removes the plain `"` characters inside a single-quoted string before passing it to `curl.exe`. The server receives `{name:Sajid Hasan,email:...}`, which is not valid JSON, and answers `400 Malformed or missing JSON request body`.

To see the real reason, add `-i` (status line and headers) to any command:

```powershell
curl.exe -i -b cookies.txt ...
```

## Three ways that work

**A. Escape every inner quote with a backslash** (used throughout this guide):

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Sajid Hasan\",\"email\":\"sajid@ums.com\",\"password\":\"pass123\",\"department\":\"CSE\",\"studentId\":\"2107001\"}"
```

**B. Put the JSON in a file** (easiest to read, no escaping at all). Create `student.json`:

```json
{
  "name": "Sajid Hasan",
  "email": "sajid@ums.com",
  "password": "pass123",
  "department": "CSE",
  "studentId": "2107001"
}
```

then send it with `@`:

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" `
  -d "@student.json"
```

**C. Use a PowerShell here-string** piped to curl:

```powershell
@'
{"name":"Sajid Hasan","email":"sajid@ums.com","password":"pass123","department":"CSE","studentId":"2107001"}
'@ | curl.exe -b cookies.txt -X POST http://localhost:8080/api/students -H "Content-Type: application/json" --data-binary "@-"
```

## Other reasons a create can fail

| Response | Reason | Fix |
|----------|--------|-----|
| `401` | Not logged in, or the cookie file is empty or stale | Run the login command again |
| `403` | Logged in with a role that cannot use this URL (for example a teacher on `/api/students`) | Log in as admin |
| `400 Malformed or missing JSON` | Quoting problem (above) | Use method A, B or C |
| `400 Validation failed` | `name` blank, or `email` not valid | Fix the field named in `fieldErrors` |
| `400 Password is required...` | Password missing or shorter than 6 characters | Send at least 6 characters |
| `409 Email already in use` | That email exists already (emails are unique) | Use another email, or run `DELETE` on the old one |
| Connection refused | The containers are not running | `docker compose up -d` |

Note: `studentId` has no unique rule, so `2107001` (the demo student's number) is accepted again.

---

# 13. Complete Command List (using JSON files — recommended)

All POST and PUT commands use `-d "@file.json"`. This avoids every PowerShell quoting problem.
Run every command from the project root folder where the `.json` files live.

## JSON files (already created in your project root)

| File | Used for |
|------|----------|
| `login.json` | Admin login |
| `login-teacher.json` | Teacher login |
| `login-student.json` | Student login |
| `student.json` | Create student |
| `student-update.json` | Update student (no password change) |
| `teacher.json` | Create teacher |
| `teacher-update.json` | Update teacher |
| `admin.json` | Create second admin |
| `admin-update.json` | Update admin |

Edit these files freely to change names, emails, passwords.

---

## 13.1 Authentication

```powershell
# Login as admin → saves cookie to cookies.txt
curl.exe -c cookies.txt -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "@login.json"

# Login as teacher → saves cookie to teacher-cookies.txt
curl.exe -c teacher-cookies.txt -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "@login-teacher.json"

# Login as student → saves cookie to student-cookies.txt
curl.exe -c student-cookies.txt -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "@login-student.json"

# Who am I (any role)
curl.exe -b cookies.txt http://localhost:8080/api/auth/me

# Logout
curl.exe -b cookies.txt -X POST http://localhost:8080/api/auth/logout
```

---

## 13.2 Students  (allowed: ADMIN, STUDENT)

```powershell
# List all students
curl.exe -b cookies.txt http://localhost:8080/api/students

# Get one student by id
curl.exe -b cookies.txt http://localhost:8080/api/students/4

# Create  → 201 Created  (edit student.json to change the data)
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/students -H "Content-Type: application/json" -d "@student.json"

# Update  → 200  (password omitted in student-update.json, old one is kept)
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/students/4 -H "Content-Type: application/json" -d "@student-update.json"

# Delete  → 204 No Content
curl.exe -i -b cookies.txt -X DELETE http://localhost:8080/api/students/4
```

---

## 13.3 Teachers  (allowed: ADMIN, TEACHER)

```powershell
# List all teachers
curl.exe -b cookies.txt http://localhost:8080/api/teachers

# Get one teacher by id
curl.exe -b cookies.txt http://localhost:8080/api/teachers/2

# Create  → 201 Created
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/teachers -H "Content-Type: application/json" -d "@teacher.json"

# Update  → 200
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/teachers/5 -H "Content-Type: application/json" -d "@teacher-update.json"

# Delete  → 204
curl.exe -i -b cookies.txt -X DELETE http://localhost:8080/api/teachers/5
```

---

## 13.4 Admins  (allowed: ADMIN only)

```powershell
# List all admins
curl.exe -b cookies.txt http://localhost:8080/api/admins

# Get one admin by id
curl.exe -b cookies.txt http://localhost:8080/api/admins/1

# Create  → 201 Created
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/admins -H "Content-Type: application/json" -d "@admin.json"

# Update  → 200
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/admins/6 -H "Content-Type: application/json" -d "@admin-update.json"

# Delete  → 204
curl.exe -i -b cookies.txt -X DELETE http://localhost:8080/api/admins/6
```

> **IDs** are examples. Use the `id` from the create response or the list response.

---

## 13.5 See headers and status code (add `-i`)

```powershell
curl.exe -i -b cookies.txt http://localhost:8080/api/students
```

`-i` prints the HTTP status line and headers above the JSON body.

---

## 13.6 Error cases

```powershell
# 401 — no cookie at all
curl.exe -i http://localhost:8080/api/students

# 401 — wrong password (bad-login.json: { "email":"admin@ums.com", "password":"wrong" })
curl.exe -i -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "@bad-login.json"

# 403 — student calls the teachers endpoint
curl.exe -i -b student-cookies.txt http://localhost:8080/api/teachers

# 403 — teacher calls the admins endpoint
curl.exe -i -b teacher-cookies.txt http://localhost:8080/api/admins

# 404 — id does not exist
curl.exe -i -b cookies.txt http://localhost:8080/api/students/9999

# 404 — id exists but belongs to a different role (admin id 1 looked up as a student)
curl.exe -i -b cookies.txt http://localhost:8080/api/students/1

# 400 — id is not a number
curl.exe -i -b cookies.txt http://localhost:8080/api/students/abc

# 400 — admin tries to delete their own account
curl.exe -i -b cookies.txt -X DELETE http://localhost:8080/api/admins/1

# 409 — duplicate email: run the student create command twice
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/students -H "Content-Type: application/json" -d "@student.json"
curl.exe -i -b cookies.txt -X POST http://localhost:8080/api/students -H "Content-Type: application/json" -d "@student.json"
```

---

## 13.7 Check that passwords are stored as BCrypt hashes

```powershell
docker exec -it ums-db psql -U ums_user -d ums -c "SELECT id, email, role, password FROM users;"
```

Every `password` value must start with `$2a$10$`. If you see plain text, run `docker compose down -v` and restart.

---

## 13.8 Pretty-print any response

```powershell
curl.exe -s -b cookies.txt http://localhost:8080/api/students | ConvertFrom-Json | Format-Table
curl.exe -s -b cookies.txt http://localhost:8080/api/teachers | ConvertFrom-Json | Format-Table
curl.exe -s -b cookies.txt http://localhost:8080/api/admins   | ConvertFrom-Json | Format-Table
```

`-s` hides the progress bar.

---

# Access Rules (from `SecurityConfig`)

| Endpoint | Who can call it |
|----------|-----------------|
| `POST /api/auth/login` | Everyone (no login needed) |
| `/api/auth/me`, `/api/auth/logout` | Any logged-in user |
| `/api/admins/**` | `ADMIN` |
| `/api/teachers/**` | `ADMIN`, `TEACHER` |
| `/api/students/**` | `ADMIN`, `STUDENT` |

CSRF protection is disabled, so no CSRF token is needed in curl requests.

---

# Status Codes You Will See

| Code | Meaning |
|------|---------|
| 200 | OK (GET, PUT) |
| 201 | Created (POST), with a `Location` header for the new resource |
| 204 | Deleted (DELETE), no body |
| 400 | Validation failed, for example a missing `name` or an invalid `email` |
| 401 | Not logged in, or wrong email/password |
| 403 | Logged in, but your role is not allowed for that endpoint |
| 404 | The ID does not exist, or belongs to a different role |
| 409 | Conflict: the email is already in use |

Add `-i` to any curl command to see the status code and headers, for example `curl.exe -i -b cookies.txt http://localhost:8080/api/students/999`.

---

# Why Are Cookies Used?

Suppose login is successful.

Server creates:

```java
request.getSession();
```

and returns:

```http
Set-Cookie: JSESSIONID=ABC123
```

Next requests send:

```http
Cookie: JSESSIONID=ABC123
```

Server identifies the same user.

Without cookies:

```text
Every request appears to come from a new anonymous user.
```

---

# Difference Between Browser, Postman, and curl

| Feature | Browser | Postman | curl |
|----------|----------|----------|----------|
| GET | ✅ | ✅ | ✅ |
| POST | ❌ URL Bar | ✅ | ✅ |
| PUT | ❌ | ✅ | ✅ |
| DELETE | ❌ | ✅ | ✅ |
| JSON Body | ❌ | ✅ | ✅ |
| Save Cookies | ✅ Auto | ✅ Auto | ✅ Using -c |
| Send Cookies | ✅ Auto | ✅ Auto | ✅ Using -b |
| Automation | ❌ | Limited | ✅ Excellent |

---

# Summary of curl Options

| Option | Meaning |
|----------|----------|
| curl.exe | Run curl |
| -X POST | Use POST |
| -X PUT | Use PUT |
| -X DELETE | Use DELETE |
| -H | Add Header |
| -d | Send Body Data |
| -c cookies.txt | Save Cookies |
| -b cookies.txt | Load/Send Cookies |
| URL | API Endpoint |
| ` | PowerShell Line Continuation |

---

# Common Interview Question

## Difference between -c and -b ?

### Save Cookie

```powershell
-c cookies.txt
```

Stores cookies received from server.

---

### Send Cookie

```powershell
-b cookies.txt
```

Reads cookies from file and sends them to the server.

---

### Easy Memory Trick

```text
-c = Create cookie file
-b = Bring cookie back
```