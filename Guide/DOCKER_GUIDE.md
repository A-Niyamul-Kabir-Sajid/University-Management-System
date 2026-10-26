# Docker Guide – University Management System

## Question
Dockerfile (multi-stage Maven build → slim JRE image) and compose.yaml (PostgreSQL with a health check + the app, wired with environment variables). Explain Docker, how to put this project in Docker, what Docker will do, and how to make GET/POST requests with API calls.

## 1. What Docker does here

Normally you'd need Java, Maven and PostgreSQL installed on your PC. Docker packages each of those into a **container**, a small isolated mini-computer that runs the same way on any machine.

**Dockerfile** is the recipe for the app's image. It has two stages:
1. **Build stage:** a Maven + JDK image compiles your code into a `.jar`.
2. **Run stage:** a slim JRE image (Java runtime only, no compiler) copies in just that `.jar` and runs it. This keeps the final image small.

**compose.yaml** runs several containers together. It defines two services:
- **db:** PostgreSQL 16, with database `ums`, user `ums_user` and password `ums_pass`.
  - The `pgdata` volume keeps your data even if the container is deleted.
  - The health check runs `pg_isready` every 5 seconds.
- **app:** your Spring Boot app, built from the Dockerfile.
  - `depends_on: service_healthy` makes it wait until Postgres is actually ready.
  - It connects to `jdbc:postgresql://db:5432/ums`. `db` is the service name, and Docker's internal network resolves it.
  - The `environment:` values (DB login, admin email and password, `SEED_DEMO_DATA`) are passed to Spring Boot.
  - `"8080:8080"` exposes the app on your PC at `localhost:8080`.

## 2. How to run it

1. Open **Docker Desktop** and wait until it says "Engine running".
2. In VS Code, open a terminal with `` Ctrl+` `` in the project folder, where `compose.yaml` is.
3. Run:
   ```powershell
   docker compose up --build
   ```
   The first run downloads images and builds, so it takes a few minutes. When you see something like `Started ... in X seconds`, the app is ready.
4. Other commands:
   ```powershell
   docker compose up -d --build   # run in background
   docker compose logs -f app     # watch app logs
   docker compose down            # stop (data kept)
   docker compose down -v         # stop and wipe the database
   ```

## 3. Making API requests

The app uses **session-cookie login**, so you must log in first and keep the cookie. The default admin is `admin@ums.com` / `admin123`.

Endpoints:
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `/api/students`, `/api/teachers` and `/api/admins`, each with:
  - `GET` (list)
  - `GET /{id}`
  - `POST`
  - `PUT /{id}`
  - `DELETE /{id}`

**In PowerShell** (use `curl.exe`, because plain `curl` is an alias for something else):

```powershell
# POST: login, saving the cookie to a file
curl.exe -c cookies.txt -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{\"email\":\"admin@ums.com\",\"password\":\"admin123\"}'

# GET: who am I (sends the cookie)
curl.exe -b cookies.txt http://localhost:8080/api/auth/me

# GET: list students
curl.exe -b cookies.txt http://localhost:8080/api/students

# POST: create a student
curl.exe -b cookies.txt -X POST http://localhost:8080/api/students `
  -H "Content-Type: application/json" `
  -d '{\"name\":\"Rahim\",\"email\":\"rahim@ums.com\",\"password\":\"pass123\",\"department\":\"CSE\",\"studentId\":\"2107001\"}'

# PUT: update student with id 1
curl.exe -b cookies.txt -X PUT http://localhost:8080/api/students/1 `
  -H "Content-Type: application/json" `
  -d '{\"name\":\"Rahim Uddin\",\"email\":\"rahim@ums.com\",\"department\":\"EEE\",\"studentId\":\"2107001\"}'

# DELETE
curl.exe -b cookies.txt -X DELETE http://localhost:8080/api/students/1
```

- `-c` saves the cookie and `-b` sends it.
- The body fields are `name` and `email` (both required), plus `password`, `department` and `studentId`.
- Without logging in first, you'll get a 401.

**Easier with a GUI:** install the **Thunder Client** or **REST Client** extension in VS Code, or use Postman.
1. Send the `POST /api/auth/login` request.
2. The tool stores the `JSESSIONID` cookie automatically.
3. Send your other requests normally. Choose method, URL, and for POST/PUT set the body to JSON.

Note: `SecurityConfig` was not checked, so some endpoints may be restricted by role. A 403 means the logged-in user's role isn't allowed there.

## 4. How to use the Docker Desktop app

Docker Desktop is the window that shows what Docker is running. You can do everything from it without typing commands, except the first `docker compose up --build`.

1. **Start it.** Open Docker Desktop and wait for the bottom-left to show a green "Engine running". If it asks you to enable WSL 2 or virtualization, accept and restart.
2. **Start the project.** Run `docker compose up -d --build` in the project folder (see section 2). Docker Desktop then shows the project.
3. **Containers tab.**
   - You will see a group named after your folder, with two containers inside: `ums-db` and `ums-app`.
   - Green means running. Grey means stopped.
   - Use the **Start / Stop / Restart / Delete** buttons on the right of each row.
   - The play/stop button on the group row starts or stops both together.
4. **Logs.** Click a container name, then the **Logs** tab. This is where you see `Started ... in X seconds`, or error messages if something fails.
5. **Open the app.** On the `ums-app` row, click the `8080:8080` link. It opens `http://localhost:8080` in your browser.
6. **Terminal inside a container.** In a container, open the **Exec** (or **Terminal**) tab. For the database, you can run:
   ```
   psql -U ums_user -d ums
   ```
   Then try `\dt` to list tables, `SELECT * FROM app_user;` (the table name may differ in your project) to see rows, and `\q` to quit.
7. **Images tab.** Shows the downloaded `postgres:16-alpine` and your built app image. Delete images here to free disk space.
8. **Volumes tab.** Shows `pgdata`, where the database files live. Deleting it wipes all data (same as `docker compose down -v`).
9. **Stop everything.** Press the stop button on the project group, or run `docker compose down`.

**Common problems**
- `error during connect` or "cannot connect to the Docker daemon": Docker Desktop is not running. Start it and wait for "Engine running".
- `port is already allocated` on 8080: another program uses port 8080. Stop it, or change the left side of `"8080:8080"` in `compose.yaml` (for example `"8081:8080"`) and use `localhost:8081`.
- The app container keeps restarting: open its **Logs** tab. The cause is usually a database connection or configuration error.
- You changed the code and nothing changed: rebuild with `docker compose up -d --build`.
