# Repository Guidelines

## Project Overview

This repository contains the Spring Boot backend for MoEASY.

The project uses:

- Java 21
- Spring Boot
- Gradle
- MySQL
- Redis
- Flyway
- Docker
- Go Task (`task`)
- GitHub Actions
- AWS S3

The application follows a domain-oriented package structure.

This file contains project-wide guidance. Layer-specific rules are maintained in `docs/project_code_convention/`.

---

## Project Structure

- `src/main/java/com/moeasy/moeasybe/` contains the Spring Boot application code.
- `src/main/resources/` contains shared configuration, Flyway migrations in `db/migration/`, and local seed data in `db/dummy/`.
- `src/test/java/` and `src/test/resources/` contain JUnit/Spring Boot tests and test configuration.
- `docker-compose.local.yml` defines local MySQL and Redis services.
- `.github/workflows/` contains CI workflows.
- `deploy/` contains deployment-related configuration and scripts.
- `docs/project_code_convention/` contains detailed code convention documents.

Prefer domain-oriented organization.

Domain-specific code should remain inside the corresponding domain package unless the code is genuinely shared across multiple domains.

Shared configuration, infrastructure, common exception handling, and reusable utilities should be placed under the appropriate `global` package.

---

## Architecture and Conventions

API request handling generally follows:

```text
Controller → Service → Converter
```

Detailed implementation rules are defined in:

```text
docs/project_code_convention/
├── controller_convention.md
├── service_convention.md
├── converter_convention.md
├── dto_convention.md
├── exception_convention.md
└── testing_convention.md
```

Review and follow the relevant convention document before creating, modifying, or reviewing code in that area.

The convention documents define the source of truth for layer-specific implementation details.

### Convention Responsibilities

- `controller_convention.md`
  - Controller responsibilities
  - Request validation
  - Service invocation
  - Common API response handling
  - OpenAPI/Swagger documentation interfaces

- `service_convention.md`
  - Application use cases
  - Query/Command service separation
  - Transaction boundaries
  - Repository interaction
  - Business exception handling

- `converter_convention.md`
  - Entity/DTO transformation
  - Converter structure and naming
  - Object construction and response composition

- `dto_convention.md`
  - Request/response DTO package structure
  - DTO naming
  - Record usage
  - Validation rules

- `exception_convention.md`
  - Domain exceptions
  - Error codes
  - Success codes
  - Application response-code conventions

- `testing_convention.md`
  - Unit tests
  - Controller slice tests
  - Repository tests
  - Integration tests
  - Mockito and assertion conventions

Do not duplicate the full contents of these documents in this file.

If a convention conflicts with existing code, inspect neighboring implementations before changing the architecture. Do not introduce a structural migration unless the current task explicitly requires it.

---

## Layer Responsibilities

The detailed rules are defined in the corresponding convention documents, but the following responsibilities apply project-wide.

### Layer boundaries

Keep HTTP handling, use-case coordination, persistence, and DTO/entity conversion in their appropriate layers. Avoid bypassing a layer or duplicating its responsibilities. Follow the corresponding convention document listed above for detailed rules.

### Repository

Repositories are responsible only for persistence and database access.

Do not place application, HTTP, response-construction, or unrelated business logic inside repositories.

---

## Package Conventions

Use domain-oriented packages and follow the applicable convention document for package structure. Create only packages needed by the domain; do not add empty layers solely to match an example. Before adding an abstraction, inspect existing implementations and the relevant convention.

---

## DTO Guidelines

Follow [`dto_convention.md`](docs/project_code_convention/dto_convention.md).

Project-wide principle: do not expose JPA entities directly through API request or response contracts.

---

## Exception and Response Guidelines

Domain-specific exceptions, error codes, and success codes follow [`exception_convention.md`](docs/project_code_convention/exception_convention.md).

Use centralized exception handling for API errors and never expose stack traces, credentials, or internal details in API responses. Follow the linked convention for exception types and response codes.

---

## Validation Guidelines

Follow [`controller_convention.md`](docs/project_code_convention/controller_convention.md) and [`dto_convention.md`](docs/project_code_convention/dto_convention.md) for validation responsibilities.

---

## Transaction Guidelines

Follow [`service_convention.md`](docs/project_code_convention/service_convention.md).

Place transaction boundaries in the application/service layer and avoid holding them across long-running external calls.

---

## Database and Flyway Guidelines

Database schema changes must be managed through Flyway.

- Keep JPA entity definitions consistent with the schema produced by Flyway.
- Do not rely on Hibernate automatic schema generation to apply production schema changes.
- Do not modify an already-applied migration to change the schema.
- Create a new migration when the schema changes.
- Add a migration when persisted entity fields, constraints, relationships, or indexes change.
- Review existing migration naming patterns before introducing new migrations.

Create a timestamped migration with:

```bash
task db-new -- add_description
```

Inspect Flyway state with:

```bash
task db-info
```

---

## Build, Test, and Development Commands

Use Java 21, Docker, and Go Task (`task`).

MySQL uses the `moeasy` database and `moeasy` user, with local credentials defined by the Compose configuration.

Run once after cloning:

```bash
task setup
```

This installs the project's pre-commit hook.

### Local infrastructure

Start MySQL and Redis:

```bash
task docker-up
```

Stop them while preserving data:

```bash
task docker-down
```

Start only Redis:

```bash
docker compose -f docker-compose.local.yml up -d redis
```

### Application

Run the Spring Boot application:

```bash
task run
```

Compile production sources:

```bash
task compile
```

Run tests:

```bash
task test
```

Run the full Gradle build:

```bash
task build
```

Run all project checks:

```bash
task check
```

---

## Required Verification

During implementation, run the closest relevant test or check to get fast feedback. Before reporting the work complete, run the full final verification sequence:

```bash
git diff --check
task check
```

`task check` runs `task precommit` and `task build`; the build compiles production code and runs tests. Run `task compile` or `task test` separately when you need faster or more focused feedback while diagnosing a failure.

Start local services with:

```bash
task docker-up
```

before running tests that require MySQL or Redis.

Do not weaken, remove, or bypass failing tests merely to make the build pass.

If a required test cannot be executed, report:

- Which test or verification was not run.
- Why it could not be run.
- What risk remains unverified.

---

## Testing Guidelines

Follow [`testing_convention.md`](docs/project_code_convention/testing_convention.md) for detailed test rules.

Use tests to verify observable behavior. Integration tests requiring MySQL or Redis must use the documented local Docker services or the equivalent CI environment.

No project-wide coverage threshold is currently configured.

---

## Coding Style and Naming

Follow standard Java conventions.

- Use four-space indentation.
- Use `PascalCase` for classes.
- Use `camelCase` for methods and variables.
- Use `UPPER_SNAKE_CASE` for constants.
- Use descriptive names.
- Avoid unnecessary abbreviations.
- Keep Gradle and YAML formatting consistent with neighboring files.

Prefer consistency with existing project code over introducing a new formatting or naming pattern.

Pre-commit checks include:

- YAML validation
- whitespace
- merge markers
- line endings
- large files
- Java compilation

---

## Dependency Guidelines

Before adding a new dependency:

1. Check whether the required functionality already exists in the project.
2. Check whether the JDK, Spring, or an existing dependency already provides the functionality.
3. Add a dependency only when it provides a clear project benefit.

Do not introduce libraries solely to avoid small amounts of straightforward code.

When adding or upgrading dependencies, update relevant tests and configuration if required.

---

## Commits and Pull Requests

Use short, imperative, lowercase Conventional Commit-style prefixes where appropriate, such as:

```text
feat:
fix:
chore:
ci:
test:
docs:
refactor:
```

Keep commits focused on one logical change.

Pull requests should:

- Describe the change.
- Select the applicable change type.
- List important implementation details.
- Document verification commands and results.
- Link related issues.
- Highlight important review considerations.

Do not create commits, push branches, merge pull requests, or otherwise modify remote Git state unless the user explicitly requests it.

Do not include unrelated refactoring in feature or bug-fix work unless it is necessary to complete the requested task.

---

## Configuration and Security

Application configuration supports environment-variable overrides and may use a local `.env` file for development.

AWS Secrets Manager is not used by this project.

Follow these rules:

- Never commit passwords.
- Never commit JWT secrets.
- Never commit OAuth credentials.
- Never commit AWS access keys or secret access keys.
- Never commit authentication tokens or other credentials.
- Never commit credential-containing `.env` files.
- Supply secrets and credentials through environment variables.
- Local development credentials may be stored in a Git-ignored `.env` file when required.
- Deployment credentials must be supplied by the deployment environment and must not be stored in Git.
- AWS SDK integrations should use the configured credential provider chain rather than hard-coded credentials.
- Do not hard-code environment-specific credentials.
- Do not expose credentials or secrets through application logs, CI output, scripts, or exception messages.
- Follow the principle of least privilege for AWS IAM permissions.
- Keep S3 buckets private unless public access is explicitly required.
- Do not introduce AWS Secrets Manager unless a future architectural decision explicitly changes the project's credential-management strategy.

---

## AWS Project Constraints

These AWS-specific constraints apply whenever AWS resources or project settings are involved:

- Use “project” for the AWS project, “team member” for a human collaborator, and “selected Region” for the project's Region. Refer to `AWS Settings` for project management and billing; refer to the AWS Management Console for AWS resources.
- Create Regional resources only in the project's assigned Region. Direct the user to AWS Settings → View all projects → Overview → Additional Info → Region to confirm it; if unavailable, check `~/.aws/config`.
- Use `us-east-1` for S3, Kinesis, CloudWatch Logs, or CloudWatch metrics only when a global resource requires a dependency there. For inventories of those services, check both the selected Region and `us-east-1`.
- Do not create Regional resources in another Region, use Lambda@Edge, use CloudFormation StackSets, or perform cross-Region replication or other cross-Region resource actions.
- CloudFront is global and may be configured in `us-east-1`; its Regional origin, such as Lambda or API Gateway, must remain in the selected Region.
- In `eu-north-1`, Rekognition, Textract, Personalize, and App Runner are unavailable for this experience.
- Human access is managed by AWS; do not assign roles to team members unless necessary. IAM roles and policies may still be needed for service-to-service access. SCPs and RCPs may also limit operations.
- For questions about SCPs or RCPs, use [AWS project policy documentation](https://docs.aws.amazon.com/accounts/latest/reference/scps-and-rcps-for-projects.html).
- If an operation that previously worked suddenly returns Access Denied, ask whether a spend limit is configured and direct the user to AWS Settings → Billing. Only project owners can change a spend limit. Check whether the project is on the paid plan when relevant.
- Before relying on an AWS service, run `aws freetier get-account-plan-state`. For `FREE`, check the [Free Tier supported-services list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-free-tier); for `PAID`, check the [Paid Tier supported-services list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-paid-plan). If the service is not listed, check the [unsupported-services list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#unsupported-services). The user may need to activate advanced capabilities.
- Before creating AWS resources, consider the project's spend limit and ask whether successfully created resources should be kept or cleaned up.
- Before AWS development or operations, read the relevant AWS skill and follow its service-specific guidance.

When the user is building, ask: “How much guidance would you like from me? Low (I only flag security risks), medium (I ask a couple of clarifying questions if something seems off), or high (I explain what I'm doing, suggest alternatives, and flag best practices).” Follow the selected level for the task.

- Low: execute the request as given; do not ask clarifying questions unless needed to prevent a security vulnerability, and do not suggest alternatives.
- Medium: execute the request; ask at most two clarifying questions when ambiguity or a potential issue warrants it; do not repeat dismissed questions or suggestions.
- High: explain steps and trade-offs, suggest alternatives, and flag best practices while still following the user's choice.

---

## External Infrastructure

External infrastructure such as AWS S3 should remain separated from domain logic where practical.

- Prefer dedicated storage or infrastructure components.
- Avoid spreading AWS SDK-specific implementation details throughout domain services.
- Reuse existing infrastructure components before creating new ones.
- Keep infrastructure-specific configuration under the appropriate shared configuration package.
- Introduce abstractions only when they provide a clear architectural benefit.
- Do not create unnecessary abstraction layers around trivial infrastructure usage.

---

## Documentation Guidelines

Update documentation when a change affects:

- Environment variables
- Local development setup
- Deployment procedures
- Public API contracts
- Database migration workflows
- Code conventions
- Architecture decisions

Do not leave documentation describing behavior that no longer exists.

When changing a convention, update the corresponding document under `docs/project_code_convention/`.

---

## Agent Implementation Guidelines

When working in this repository:

1. Inspect the relevant existing implementation before creating new code.
2. Read the relevant document under `docs/project_code_convention/`.
3. Follow neighboring project patterns before introducing new abstractions.
4. Reuse existing converters, DTO patterns, exception patterns, response structures, utilities, repositories, and configuration where appropriate.
5. Keep changes scoped to the requested issue or task.
6. Do not perform unrelated refactoring.
7. Do not modify unrelated files merely for formatting or structural consistency.
8. Do not introduce new dependencies unless they are necessary.
9. Add or update tests when observable behavior changes.
10. Preserve existing public API contracts unless the task explicitly requires changing them.
11. Update documentation when configuration, deployment, architecture, or developer workflows change.
12. Prefer the smallest change that correctly satisfies the requested behavior.
13. Do not silently introduce a new architectural pattern.
14. Before creating a new architectural pattern, verify that an equivalent pattern does not already exist in the repository.
15. If this file, a convention document, and existing code appear inconsistent, inspect neighboring implementations and identify the inconsistency before making a structural change.

### Precedence

When instructions conflict, use the following precedence:

1. Explicit instructions for the current task.
2. Relevant files under `docs/project_code_convention/` for layer-specific rules.
3. Repository-level `AGENTS.md` for project-wide rules.
4. Existing implementation patterns.
5. General framework or language conventions.

Do not treat an outdated or isolated implementation as justification for ignoring the applicable convention document.

---

## Convention Maintenance

Convention documents define the expected implementation style for new and modified code.

Do not refactor unaffected legacy code solely to make it conform to a newly introduced convention.

When modifying existing code:

- Apply the relevant convention to the changed area when practical.
- Avoid unrelated migration of surrounding legacy code.
- If full compliance would require a broad refactor, keep the requested change scoped and document the remaining inconsistency instead.

Changes to project conventions should be made intentionally and reviewed as architectural changes rather than introduced incidentally during feature implementation.
