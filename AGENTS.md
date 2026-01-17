# Repository Guidelines

## Project Structure & Module Organization
- Root `pom.xml` is the parent Maven project; modules live in `protocol/`, `hermes/`, and `argus/`.
- `protocol/src/main/java/dev/cheng/dov/protocol/` holds shared frame/codec and file chunking logic.
- `hermes/src/main/java/dev/cheng/dov/sender/` is the Hermes JavaFX application.
- `argus/src/main/java/dev/cheng/dov/receiver/` is the Argus application.
- Design references live in `DESIGN.md`, `LOGIC.md`, and `Draft.md`.
- Build outputs are generated into `*/target/` and should not be edited manually.

## Build, Test, and Development Commands
- `mvn clean compile` builds all modules.
- `mvn clean package` produces module artifacts (Hermes includes a shaded executable JAR).
- `mvn clean install` installs artifacts to the local Maven repository.
- `mvn clean compile -pl hermes` (or `-pl argus`) builds a single module.
- `mvn test` runs the test suite (currently no committed tests).
- `mvn test -pl argus` runs module-scoped tests.

## Coding Style & Naming Conventions
- Java 21 is the target runtime; keep code compatible with `maven.compiler.source/target` in `pom.xml`.
- Follow the existing formatting: 4-space indentation, braces on the same line, one class per file.
- Package names use `dev.cheng.dov.<module>`.
- Classes use `PascalCase`, methods/fields use `lowerCamelCase`, constants use `UPPER_SNAKE_CASE`.

## Testing Guidelines
- There are no committed tests yet; add new tests under `*/src/test/java`.
- Use Maven Surefire defaults: name tests `*Test` and keep them module-scoped.
- Prefer small unit tests for protocol logic before adding integration-style tests.

## Commit & Pull Request Guidelines
- Recent history follows Conventional Commits with optional scopes, e.g. `feat(hermes): add screen selector`.
- Keep commit subjects imperative and scoped to the module when relevant.
- PRs should include a short summary, testing notes, and screenshots or clips for Hermes UI changes.
- Link relevant issues or design notes when applicable.

## Architecture Notes
- This is a Maven multi-module layout: `protocol` is shared by Hermes (`hermes`) and Argus (`argus`).
- For protocol changes, update the design docs if the frame layout or encoding rules change.
