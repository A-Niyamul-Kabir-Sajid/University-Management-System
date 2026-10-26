# compose.yaml Explained

# Part 1: The format of a Compose file

## What is Docker Compose?

A Dockerfile describes **one image**. Real applications have several parts, here the app and a database. Docker Compose lets you describe **all the containers together** in one file and start them with one command:

```powershell
docker compose up --build
```

Without Compose you would run several long `docker run ...` commands by hand and wire up the network yourself.

## The file format: YAML

`compose.yaml` is written in **YAML**, a format built from indentation.

| Rule | Example |
|------|---------|
| Indentation shows structure (use **spaces**, never tabs) | child lines are indented under their parent |
| `key: value` is a setting | `image: postgres:16-alpine` |
| `- item` is one entry in a list | `- "8080:8080"` |
| `#` starts a comment | `# wait until PostgreSQL is ready` |
| Quotes protect values that could be misread | `"true"` stays text, not a boolean |

## Top-level structure

```yaml
services:     # the containers to run
  ...
volumes:      # named storage areas that outlive containers
  ...
```

Each entry under `services:` becomes one container. The entry's name (for example `db`) is also its **hostname** on the network Compose creates, so containers can reach each other by that name.

```text
 Your PC (localhost)
    │  port 8080
    ▼
 ┌──────────── Compose network ────────────┐
 │  ┌─────────┐   db:5432   ┌───────────┐  │
 │  │ app     │────────────►│ db        │  │
 │  │ (Spring)│             │ (Postgres)│  │
 │  └─────────┘             └─────┬─────┘  │
 └────────────────────────────────┼────────┘
                                  ▼
                          volume "pgdata"
                       (database files kept)
```

Only `app` is reachable from your PC. `db` has no `ports:`, so it is reachable only from inside the Compose network.

## Keys used in this file

| Key | Meaning |
|-----|---------|
| `services` | The list of containers |
| `image` | Use a ready-made image from Docker Hub |
| `build` | Build an image from a Dockerfile instead |
| `container_name` | A fixed, readable name for the container |
| `environment` | Environment variables passed into the container |
| `volumes` | Mount storage into the container |
| `healthcheck` | A test that tells Docker whether the container is really working |
| `depends_on` | Start order and conditions between services |
| `ports` | Map a port of your PC to a port in the container |
| `restart` | What to do if the container stops |

---

# Part 2: Line-by-line explanation

The full file is [compose.yaml](../compose.yaml).

## Top

### Line 1

```yaml
services:
```

Starts the list of containers. Everything indented below it is a service.

## Service 1: the database

### Line 2

```yaml
  db:
```

Names the first service `db`. This name is important: the app uses `db` as the **host name** of the database.

### Line 3

```yaml
    image: postgres:16-alpine
```

Run the official PostgreSQL image, version 16. `alpine` means it is built on the tiny Alpine Linux, so the image is small. Docker downloads it on first use. There is no `build:` here because we do not need to build anything ourselves.

### Line 4

```yaml
    container_name: ums-db
```

Names the container `ums-db`. Without this, Compose would generate a name like `foldername-db-1`. A fixed name is easier to use in commands such as `docker exec -it ums-db psql ...`.

### Lines 5–8

```yaml
    environment:
      POSTGRES_DB: ums
      POSTGRES_USER: ums_user
      POSTGRES_PASSWORD: ums_pass
```

Environment variables the Postgres image reads **the first time it starts with an empty data folder**:

| Variable | Effect |
|----------|--------|
| `POSTGRES_DB` | Creates a database named `ums` |
| `POSTGRES_USER` | Creates a user named `ums_user` |
| `POSTGRES_PASSWORD` | Sets that user's password to `ums_pass` |

If the data volume already exists, changing these values does nothing, because the database is already created. To reset, run `docker compose down -v`.

### Lines 9–10

```yaml
    volumes:
      - pgdata:/var/lib/postgresql/data
```

Mounts the named volume `pgdata` at the folder where PostgreSQL keeps its files (`/var/lib/postgresql/data`).

**Why?** A container's own file system is deleted with the container. A volume lives outside the container, so your users and records survive `docker compose down` and restarts. The volume is declared at the bottom of the file.

### Lines 11–15

```yaml
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ums_user -d ums"]
      interval: 5s
      timeout: 5s
      retries: 10
```

A health check is a command Docker runs repeatedly to ask "is this container ready?".

| Key | Meaning |
|-----|---------|
| `test` | The command to run. `CMD-SHELL` runs it in a shell. `pg_isready` is a PostgreSQL tool that returns success when the server accepts connections |
| `interval: 5s` | Run it every 5 seconds |
| `timeout: 5s` | Treat the check as failed if it takes longer than 5 seconds |
| `retries: 10` | After 10 failed checks in a row, mark the container `unhealthy` |

**Why?** A container being *started* is not the same as the database being *ready*. Postgres takes a few seconds to initialize. The health check lets Docker know the real moment it is ready. Possible states are `starting`, `healthy` and `unhealthy`.

### Line 16

```yaml
    restart: unless-stopped
```

If the container crashes, or Docker restarts, start it again automatically. It stays stopped only if you stopped it yourself.

## Service 2: the application

### Line 18

```yaml
  app:
```

Names the second service `app`.

### Line 19

```yaml
    build: .
```

Build the image from the `Dockerfile` in the current folder (`.`), instead of downloading one. This runs the multi-stage build described in [DOCKERFILE_EXPLAINED.md](DOCKERFILE_EXPLAINED.md). The `--build` flag in `docker compose up --build` forces a rebuild every time.

### Line 20

```yaml
    container_name: ums-app
```

Names the container `ums-app`.

### Lines 21–23

```yaml
    depends_on:
      db:
        condition: service_healthy   # wait until PostgreSQL is ready
```

Controls start order.

| Part | Meaning |
|------|---------|
| `depends_on` | `app` needs `db` |
| `condition: service_healthy` | Start `app` only after `db`'s health check passes |

A plain `depends_on: db` would only wait for the database container to *start*, not to be *ready*. The app would then try to connect too early, fail, and crash. This condition uses the health check above to prevent that.

### Lines 24–25

```yaml
    ports:
      - "8080:8080"
```

Port mapping in the form `HOST:CONTAINER`.

```text
 your browser / curl ──► localhost:8080 (your PC) ──► port 8080 (inside ums-app)
```

To use another port on your PC, change the left number, for example `"9090:8080"`, then call `http://localhost:9090`. The quotes keep YAML from misreading the colon.

### Lines 26–33

```yaml
    environment:
      # Spring Boot reads these through application.properties
      DB_URL: jdbc:postgresql://db:5432/ums
      DB_USERNAME: ums_user
      DB_PASSWORD: ums_pass
      ADMIN_EMAIL: admin@ums.com
      ADMIN_PASSWORD: admin123
      SEED_DEMO_DATA: "true"
```

Environment variables passed to the app. `application.properties` reads them with the `${NAME:default}` syntax.

| Variable | Used in `application.properties` | Meaning |
|----------|----------------------------------|---------|
| `DB_URL` | `spring.datasource.url=${DB_URL:jdbc:h2:mem:...}` | Database address. `jdbc:postgresql://db:5432/ums` means PostgreSQL, host `db`, port 5432, database `ums` |
| `DB_USERNAME` | `spring.datasource.username` | Must match `POSTGRES_USER` |
| `DB_PASSWORD` | `spring.datasource.password` | Must match `POSTGRES_PASSWORD` |
| `ADMIN_EMAIL` | `app.admin.email` | Login email of the default admin |
| `ADMIN_PASSWORD` | `app.admin.password` | Password of the default admin (stored hashed) |
| `SEED_DEMO_DATA` | `app.seed-demo-data` | `"true"` creates a demo teacher and student at startup |

Key points:
- The host in `DB_URL` is **`db`**, the service name, not `localhost`. Inside the app container, `localhost` means the app container itself.
- If you run the app without Compose, these variables are missing, so the defaults after `:` apply and the app uses an in-memory H2 database. Compose is what switches it to PostgreSQL.
- `SEED_DEMO_DATA` is in quotes so YAML treats it as the text `"true"` rather than a boolean.
- The values `DB_USERNAME` and `DB_PASSWORD` must be identical to the `POSTGRES_*` values in the `db` service.

### Line 34

```yaml
    restart: unless-stopped
```

Same as for `db`: restart the app automatically unless you stopped it.

## Bottom

### Lines 36–37

```yaml
volumes:
  pgdata:
```

Declares the named volume `pgdata` used by `db`. The empty value means "create it with default settings". Docker stores it on your machine and manages the location for you (it shows up in the Docker Desktop **Volumes** tab).

---

# Part 3: What happens on `docker compose up --build`

```text
 1. Compose reads compose.yaml
 2. Creates a private network and the volume "pgdata"
 3. Pulls postgres:16-alpine, builds the app image from the Dockerfile
 4. Starts db
 5. Every 5s: pg_isready ... → "starting" ... → "healthy"
 6. Only now starts app  (depends_on: service_healthy)
 7. Spring Boot starts, connects to db:5432, creates tables (ddl-auto=update)
 8. DataInitializer creates the admin (and demo users)
 9. App listens on 8080 → you open http://localhost:8080
```

---

# Part 4: Commands

| Command | What it does |
|---------|--------------|
| `docker compose up --build` | Build and start everything, showing logs |
| `docker compose up -d --build` | Same, in the background |
| `docker compose ps` | Show the status of the services (including `healthy`) |
| `docker compose logs -f app` | Follow the app logs |
| `docker compose logs db` | See the database logs |
| `docker compose stop` | Stop containers, keep them |
| `docker compose down` | Stop and remove containers; **data is kept** in `pgdata` |
| `docker compose down -v` | Also delete the volume; **all data is lost** |
| `docker compose exec db psql -U ums_user -d ums` | Open a SQL shell in the database |

---

# Part 5: Common problems

| Problem | Cause / fix |
|---------|-------------|
| App cannot connect to the database | `DB_URL` must use host `db`, and the username and password must match the `POSTGRES_*` values |
| Changed `POSTGRES_PASSWORD` but the old one still works | The database already exists in `pgdata`. Run `docker compose down -v` to recreate it |
| `port is already allocated` | Port 8080 is used by another program. Change the left number in `"8080:8080"` |
| Data disappeared | You ran `docker compose down -v`, which deletes the volume |
| App code changed but nothing changed | Rebuild with `docker compose up --build` |
| YAML error on startup | Indentation problem. Use spaces only and keep children aligned under their parent |

## Security note

The passwords in this file (`ums_pass`, `admin123`) are for learning and local testing only. They are in plain text and would be committed with the project. For real deployments, move secrets into an `.env` file or Docker secrets, use strong passwords, and set `SEED_DEMO_DATA` to `"false"`.
