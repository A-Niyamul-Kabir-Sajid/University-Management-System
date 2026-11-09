# GitHub Actions CI Workflow Explained Line by Line

This is a **GitHub Actions workflow file** written in YAML. It defines a CI (Continuous Integration) pipeline that automatically runs when code is pushed or a pull request is created.

---

## Complete Workflow

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

---

## Line-by-Line Explanation

### Workflow Name

```yaml
name: CI
```

- `name` gives a name to the workflow.
- Here the workflow will appear in GitHub Actions as **"CI"**.

---

### Trigger Events

```yaml
on: [push, pull_request]
```

- `on` specifies the events that trigger the workflow.
- `push` → run the workflow whenever code is pushed to the repository.
- `pull_request` → run the workflow whenever a pull request is opened, updated, or synchronized.

Equivalent longer form:

```yaml
on:
  - push
  - pull_request
```

---

### Jobs Section

```yaml
jobs:
```

- A workflow consists of one or more **jobs**.
- `jobs` is the section where those jobs are defined.

---

### Job Name

```yaml
  test:
```

- Defines a job named `test`.
- The name is an identifier used internally by GitHub Actions.
- You could name it anything:

```yaml
jobs:
  build:
```

or

```yaml
jobs:
  unit-tests:
```

---

### Runner Operating System

```yaml
    runs-on: ubuntu-latest
```

- Specifies the operating system for the GitHub runner.
- `ubuntu-latest` means GitHub will create a fresh Ubuntu virtual machine.

Examples:

```yaml
runs-on: windows-latest
```

```yaml
runs-on: macos-latest
```

Every workflow run gets a clean machine.

---

### Steps Section

```yaml
    steps:
```

- A job is made up of multiple steps.
- Steps execute one after another.

Conceptually:

```text
Job
 ├─ Step 1
 ├─ Step 2
 └─ Step 3
```

---

### Checkout Repository

```yaml
      - uses: actions/checkout@v4
```

#### What is `uses`?

- `uses` means "use an existing GitHub Action".
- Here we're using the official action:

```text
actions/checkout
```

Version:

```text
@v4
```

#### What does checkout do?

It downloads your repository code onto the runner machine.

Without this step:

```bash
mvn verify
```

would fail because the source code wouldn't exist on the runner.

Conceptually, it is similar to:

```bash
git clone <repository-url>
```

---

### Install Java

```yaml
      - uses: actions/setup-java@v4
```

- Uses another official GitHub Action.
- This action installs and configures Java.

Without it, Java might not be available in the required version.

---

### Action Parameters

```yaml
        with:
```

- `with` passes configuration options to the action.

Think of it as function parameters:

```yaml
action(parameter=value)
```

---

### Java Distribution

```yaml
          distribution: temurin
```

- Chooses which Java distribution to install.
- `temurin` is the Eclipse Temurin OpenJDK distribution.

Other possibilities include:

```yaml
distribution: zulu
distribution: microsoft
distribution: corretto
```

---

### Java Version

```yaml
          java-version: 17
```

- Installs Java 17.

After this step:

```bash
java -version
```

would output something similar to:

```text
openjdk version "17"
```

---

### Maven Cache

```yaml
          cache: maven
```

- Enables dependency caching for Maven.

Normally Maven downloads dependencies from Maven Central:

```xml
<dependency>
    ...
</dependency>
```

Without caching:

1. Workflow starts.
2. Maven downloads all dependencies.
3. Build takes longer.

With caching:

1. First run downloads dependencies.
2. GitHub stores them.
3. Later runs reuse them.

Result: **much faster builds**.

---

### Run Maven Build

```yaml
      - run: mvn -B verify
```

#### What is `run`?

- Executes a shell command on the runner.

Equivalent to opening a terminal and typing:

```bash
mvn -B verify
```

---

### What Does `-B` Mean?

```bash
-B
```

means **Batch Mode**.

Benefits:

- No interactive prompts
- Better for CI/CD environments
- Cleaner logs

---

### What Does `verify` Mean?

`verify` is a Maven lifecycle phase.

Typical Maven lifecycle:

```text
validate
compile
test
package
verify
install
deploy
```

When you run:

```bash
mvn verify
```

Maven executes all earlier phases automatically:

```text
validate
↓
compile
↓
test
↓
package
↓
verify
```

This generally:

1. Validates the project
2. Compiles the source code
3. Runs unit tests
4. Creates the build artifact (JAR/WAR)
5. Runs verification checks

If any step fails, the job fails.

---

## Complete Execution Flow

```text
Push / Pull Request
        │
        ▼
Start Ubuntu Runner
        │
        ▼
Checkout Repository
        │
        ▼
Install Java 17
        │
        ▼
Restore Maven Cache
        │
        ▼
Run mvn verify
        │
        ├─ Success → ✅ Workflow Passes
        │
        └─ Failure → ❌ Workflow Fails
```

---

## Summary

This workflow says:

> Whenever someone pushes code or creates/updates a pull request, GitHub should create an Ubuntu machine, download the repository, install Java 17, cache Maven dependencies, and run `mvn verify` to ensure the project builds successfully and all tests pass.