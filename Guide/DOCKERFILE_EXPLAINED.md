# Dockerfile Explained

# Part 1: The format of a Dockerfile

## What is a Dockerfile?

A Dockerfile is a plain text file of instructions that tells Docker how to build an **image**. An image is a ready-to-run package holding an operating system layer, your program and everything it needs. When you start an image, it becomes a **container**.

```text
Dockerfile  ──(docker build)──►  Image  ──(docker run)──►  Container
 recipe                          packaged meal               the meal being eaten
```

The file is named exactly `Dockerfile`, with no extension. It lives in the project root, next to `pom.xml`.

## The format

Each line is an **instruction** followed by arguments:

```text
INSTRUCTION arguments
```

- Instructions are written in UPPERCASE by convention.
- Lines starting with `#` are comments.
- Docker runs the instructions from top to bottom.
- Each instruction creates a **layer**. Docker caches layers, so unchanged steps are skipped on the next build.

## Instructions used in this project

| Instruction | What it does |
|-------------|--------------|
| `FROM` | Chooses the starting image. Every Dockerfile begins with it |
| `AS name` | Gives a build stage a name so a later stage can copy from it |
| `WORKDIR` | Sets the working folder inside the image (and creates it) |
| `COPY` | Copies files from your PC (or from another stage) into the image |
| `RUN` | Runs a command **while building** the image |
| `USER` | Sets which user the following commands, and the container, run as |
| `EXPOSE` | Documents which port the app listens on |
| `ENTRYPOINT` | The command that runs when the container **starts** |

## RUN vs ENTRYPOINT

| | RUN | ENTRYPOINT |
|---|---|---|
| When | At build time | At container start |
| Result | Changes the image (installs, compiles) | Starts the program |
| Count | Many allowed | The last one counts |

## What is a multi-stage build?

A Dockerfile can contain several `FROM` lines. Each starts a new **stage**. You can build in a big stage and copy only the final result into a small stage.

```text
Stage 1 "build"  (Maven + JDK, ~500 MB)      Stage 2 (JRE only, ~250 MB)
 ┌──────────────────────────┐                 ┌─────────────────────────┐
 │ source code, Maven,      │   copy app.jar  │ Java runtime + app.jar  │
 │ compiler, downloaded     │ ──────────────► │                         │
 │ libraries                │                 │ nothing else            │
 └──────────────────────────┘                 └─────────────────────────┘
   thrown away after the build                   this is the final image
```

Why use it?
- The final image has no compiler, Maven or source code, so it is smaller.
- Fewer tools means a smaller attack surface.
- You do not need Java or Maven installed on your PC; Docker builds the jar for you.

---

# Part 2: Line-by-line explanation

The full file is [Dockerfile](../Dockerfile).

## Stage 1: build the jar

### Line 1

```dockerfile
# ---------- Stage 1: build the jar ----------
```

A comment. It only labels the section.

### Line 2

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
```

| Part | Meaning |
|------|---------|
| `FROM` | Start from an existing image |
| `maven:3.9-eclipse-temurin-17` | An image that has **Maven 3.9** and **JDK 17** (Eclipse Temurin is a free build of Java). The part before `:` is the name, the part after is the **tag** (version) |
| `AS build` | Names this stage `build`, so stage 2 can refer to it |

The first time, Docker downloads this image from Docker Hub.

### Line 3

```dockerfile
WORKDIR /app
```

Sets the working folder inside the image to `/app`, creating it if needed. Later `COPY` and `RUN` lines work relative to it, like running `cd /app`.

### Line 5

```dockerfile
# Copy only pom.xml first so the dependency download is cached between builds
```

A comment explaining the trick used in the next two lines.

### Line 6

```dockerfile
COPY pom.xml .
```

Copies `pom.xml` from your project folder into the image. The `.` means "the current folder inside the image", which is `/app`.

### Line 7

```dockerfile
RUN mvn -B -q dependency:go-offline
```

Runs Maven to download every library listed in `pom.xml` (Spring Boot, PostgreSQL driver, etc.).

| Part | Meaning |
|------|---------|
| `mvn` | The Maven command |
| `-B` | Batch mode: no colors or progress bars, cleaner logs |
| `-q` | Quiet: print only errors |
| `dependency:go-offline` | Download all dependencies now, so later steps need no internet |

**Why only `pom.xml` first?** Docker caches each layer. `pom.xml` rarely changes, but your source code changes often. With this order:

```text
Change a .java file  → lines 6-7 are cached (skipped)  → only lines 9-10 run   (fast)
Change pom.xml       → lines 6-7 run again, downloads libraries again           (slow, but rare)
```

If you copied everything first, every code change would re-download all libraries.

### Line 9

```dockerfile
COPY src ./src
```

Copies your `src` folder (the Java code and `application.properties`) into `/app/src`.

### Line 10

```dockerfile
RUN mvn -B -q clean package -DskipTests
```

Builds the application.

| Part | Meaning |
|------|---------|
| `clean` | Delete old build output |
| `package` | Compile the code and bundle it into a runnable `.jar` in `/app/target/` |
| `-DskipTests` | Do not run unit tests during the image build, which makes it faster |

Because `-q` is on, you see nothing unless it fails. Skipping tests means a broken test will not stop the image from building. Run tests separately with `mvn test`.

## Stage 2: the small runtime image

### Line 12

```dockerfile
# ---------- Stage 2: small runtime image ----------
```

A comment marking the start of the final image.

### Line 13

```dockerfile
FROM eclipse-temurin:17-jre
```

Starts a **new, clean** image that has only a **JRE** (Java Runtime Environment, enough to run a jar but not compile). Nothing from stage 1 is here yet; the compiler, Maven and source code are left behind.

### Line 14

```dockerfile
WORKDIR /app
```

Sets the working folder in this new image to `/app`.

### Line 16

```dockerfile
# Do not run the app as root
```

A comment about the security step below.

### Line 17

```dockerfile
RUN addgroup --system spring && adduser --system --ingroup spring spring
```

Creates a low-privilege user so the app does not run as `root`.

| Part | Meaning |
|------|---------|
| `addgroup --system spring` | Create a system group named `spring` |
| `&&` | Run the next command only if the first one succeeded |
| `adduser --system --ingroup spring spring` | Create a system user named `spring` in that group (no password, no login shell) |

**Why?** If someone exploits the app, they would only have the rights of `spring`, not full control of the container.

### Line 18

```dockerfile
COPY --from=build /app/target/*.jar app.jar
```

Copies the finished jar **from stage 1** into this image.

| Part | Meaning |
|------|---------|
| `--from=build` | Take the file from the stage named `build`, not from your PC |
| `/app/target/*.jar` | The jar that Maven produced; `*` matches any version number |
| `app.jar` | Save it as `/app/app.jar` here |

This is the only thing carried over from the big stage.

### Line 19

```dockerfile
USER spring
```

From now on, and when the container runs, everything is done as the `spring` user instead of `root`.

### Line 21

```dockerfile
EXPOSE 8080
```

Documents that the app listens on port 8080 (matching `server.port=8080` in `application.properties`). It does **not** open the port on your PC. That is done by `ports:` in `compose.yaml` or `-p` in `docker run`.

### Line 22

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The command that runs when the container starts: `java -jar app.jar`.

The square-bracket form (called "exec form") runs Java directly as the main process. This lets it receive stop signals properly, so `docker compose down` shuts the app down cleanly.

---

# Part 3: Whole build in order

```text
docker build
 │
 ├─ Stage "build"
 │   1. Start from Maven + JDK 17
 │   2. cd /app
 │   3. Copy pom.xml, download dependencies          (cached unless pom.xml changes)
 │   4. Copy src/
 │   5. mvn package  →  /app/target/<name>.jar
 │
 └─ Final stage
     6. Start from the JRE 17 image
     7. cd /app
     8. Create user "spring"
     9. Copy the jar from the build stage as app.jar
    10. Switch to user "spring"
    11. Document port 8080
    12. On start: java -jar app.jar
```

---

# Part 4: Related file: `.dockerignore`

[.dockerignore](../.dockerignore) lists files Docker should **not** send to the build.

```text
target/
.git/
.idea/
.vscode/
*.iml
*.log
cookies.txt
```

| Entry | Why it is ignored |
|-------|-------------------|
| `target/` | Old build output; Docker rebuilds it |
| `.git/` | Version history, not needed in the image |
| `.idea/`, `.vscode/`, `*.iml` | Editor settings |
| `*.log` | Log files |
| `cookies.txt` | Your saved session cookie from curl; it should never go into an image |

This makes builds faster and keeps secrets and clutter out of the image.

---

# Part 5: Build and run this image alone

`docker compose up --build` normally does this for you. To build and run just the app by hand:

```powershell
docker build -t ums-app .
docker run -p 8080:8080 ums-app
```

| Part | Meaning |
|------|---------|
| `docker build` | Build an image from the Dockerfile |
| `-t ums-app` | Tag (name) the image `ums-app` |
| `.` | Use the current folder as the build context |
| `docker run` | Start a container from the image |
| `-p 8080:8080` | Map port 8080 of your PC to port 8080 of the container |

Run that way, the app has no PostgreSQL. Your `application.properties` falls back to an in-memory H2 database, so data is lost when the container stops. With `compose.yaml`, the app is pointed to PostgreSQL instead. See [COMPOSE_EXPLAINED.md](COMPOSE_EXPLAINED.md).

Useful commands:

```powershell
docker images              # list images
docker ps                  # list running containers
docker image rm ums-app    # delete the image
```

---

# Part 6: Common problems

| Problem | Cause / fix |
|---------|-------------|
| First build is very slow | It downloads the base images and all Maven dependencies. Later builds use the cache |
| `COPY failed: file not found` | You ran the build in the wrong folder; run it where `Dockerfile` and `pom.xml` are |
| Code changes do not show up | The image was not rebuilt. Use `docker compose up --build` |
| Build fails at `mvn package` | A compile error in the code. Remove `-q` temporarily to see the full output |
| Port already in use | Another program uses 8080. Change the left side of the port mapping |
