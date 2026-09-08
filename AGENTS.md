# Repository Guidelines

## Project Structure

- `src/main/java/com/moeasy/moeasybe/` contains the Spring Boot application code.
- `src/main/resources/` contains shared configuration, Flyway migrations in `db/migration/`, and local seed data in `db/dummy/`.
- `src/test/java/` and `src/test/resources/` contain JUnit/Spring Boot tests and test configuration.
- `docker-compose.local.yml` defines MySQL and Redis services. CI workflows live in `.github/workflows/`.

## Build, Test, and Development Commands

Use Java 21, Docker, and Go Task (`task`). MySQL uses database `moeasy`, user `moeasy`, and credentials defined by the local Compose file. Run `task setup` once after cloning to install the pre-commit hook.

- `task docker-up` starts local MySQL and Redis with the `moeasy` database; `task docker-down` stops them while preserving data. To start only Redis, run `docker compose -f docker-compose.local.yml up -d redis`.
- `task run` starts the Spring Boot application.
- `task compile` performs a fast production-source compilation.
- `task test` runs the JUnit platform tests; `task build` runs the full Gradle build and tests.
- `task check` runs all pre-commit checks followed by the full build.
- `task db-new -- add_description` creates a timestamped migration; use `task db-info` to inspect Flyway state.

## Required Verification

After changes, run `git diff --check`, `task precommit`, `task compile`, `task test`, and `task build`. Start the local services with `task docker-up` before tests that require MySQL or Redis.

## Coding Style and Naming

Follow standard Java conventions: four-space indentation, `PascalCase` classes, `camelCase` methods and variables, and descriptive packages under the existing base package. Keep Gradle and YAML formatting consistent with neighboring files. Pre-commit checks YAML, whitespace, merge markers, line endings, large files, and Java compilation.

## Testing Guidelines

Tests use JUnit Platform and Spring Boot test support. Name test classes with a `Test` or `Tests` suffix and keep resources in `src/test/resources/`. Integration tests should use the local MySQL/Redis containers or equivalent CI services. No coverage threshold is configured.

## Commits and Pull Requests

Use short, imperative, lowercase Conventional Commit-style prefixes such as `chore:` and `ci:`; keep commits focused. PRs should describe the change, select the applicable type, list implementation details, document test commands/results, link related issues, and call out review considerations. Do not create commits or push branches unless the user explicitly requests it.

## Configuration and Security

Configuration supports environment-variable overrides and an optional local `.env`. Keep passwords, JWT secrets, OAuth credentials, AWS keys, and bucket-specific production values out of Git; provide them through environment variables or the deployment secret manager.
