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

<!-- BEGIN AWS Agent Toolkit rules -->
# AWS Guidance for the new AWS experience

This user has signed up for the new AWS experience. This experience lets you sign into AWS using a social provider and requires the following additional context.

Where this guidance conflicts with the project's own instructions, the project's instructions take precedence.

## Context
### Terminology:
- Say "project" instead of "account" — a project contains an AWS account and settings for sharing with other collaborators
- Say "team member" instead of "IAM user" — users are invited by email, not created or federated in IAM
- Say "AWS Settings" when referring to management tasks at [settings.aws.com](https://settings.aws.com/) (project management, billing, team members, spend limits). Users view their actual AWS resources in the AWS Management Console.
- Say "selected Region" when referring to the user's Region — not "home Region"
- The user has a managed IAM experience. This includes a managed service control policies (SCP) and resource control policies (RCP) that govern the use of AWS. They will still need to use IAM to create policies to let services work with each other. If there are questions about the SCPs or RCPs, go to the documentation at https://docs.aws.amazon.com/accounts/latest/reference/scps-and-rcps-for-projects.html

### Constraints:
- All projects share a single AWS Region determined by the user's contact address. Resources cannot be created in other Regions
- When developing:
  - MUST create all Regional resources in the project's assigned Region
  - You CAN create S3, Kinesis, Cloudwatch Logs, Cloudwatch metrics resources in us-east-1 when there are global resources (like a global WAF instance) that require a connection to dependencies in us-east-1. You should not use these for any other reason, because resources in the selected Region will provide lower cost (due to no cross-Region traffic), increased availability (due to no cross-Region traffic), and easier manageability (due to not needing to look in another Region).
  - When you need to do an inventory of resources, you need to look in both the selected Region and us-east-1 for S3, Kinesis, Cloudwatch Logs, Cloudwatch metrics resources.
- MUST NOT attempt to create Lambda, API Gateway, or other Regional resources in any other Region
- MUST direct users to confirm their Region in AWS Settings > View all projects > Overview > Additional Info > Region. If the user cannot confirm their Region, check in ~/.aws/config
- MUST NOT use Lambda@Edge — excluded from both Lambda and CloudFront
- MUST NOT use CloudFormation StackSets — no multi-account or multi-Region deployments
- MUST NOT attempt cross-Region actions — no cross-Region replication for DynamoDB/S3/RDS, no multi-Region KMS keys
- CloudFront is a global service and its actions ARE allowed in us-east-1. A user can create a CloudFront distribution pointing to their project-region Lambda function URL or API Gateway. However, Lambda and API Gateway themselves MUST NOT be created in us-east-1 — they must be in the project Region.
- Reduced availability in eu-north-1 specifically: Amazon Rekognition, Amazon Textract, Amazon Personalize, AWS App Runner are not available in that Region.
- IAM permissions for human access are managed by AWS. Don't assign roles to team members unless absolutely necessary
- The user may have a spend limit if they are on the paid plan. The limit that pauses their project if it's exceeded. If resources suddenly become inaccessible, ask if they have a spend limit configured. Only project owners can modify a spend limit.
- When developing:
  - MUST ask about spend limit status if the user reports sudden "Access Denied" errors on operations that previously worked
  - MUST direct users to check spend status in AWS Settings > Billing
  - MUST check if a user has upgraded their account to the paid plan
  - MUST ask the user if they want to clean up the successfully created resources or keep them to reduce cost
- The user sets up billing, creates spend limits, and retrieves and pays invoices in AWS Settings. The user creates budgets and optimizes their costs in the AWS Billing and Cost Management console
- Not all AWS services are available. If a service isn't working, do the following:
  1. Run the command `aws freetier get-account-plan-state`
  2. If accountPlanType": "FREE", check the [Free Tier supported services list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-free-tier) next,
  3. If accountPlanType": "PAID", check the [Paid Tier supported services list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-paid-plan).
  4. If neither list shows the service, check the [Not supported for this experience list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#unsupported-services). The user will need to activate advanced features to access this service.
- Users can activate advanced AWS services and capabilities for their account.
- Before starting a task, check whether a relevant AWS skill is available. Load the skill with retrieve_skill and prefer its guidance over general knowledge.

### Help level
- help_level (required): LOW, MEDIUM, or HIGH. While a user is building, you MUST ask the user: "How much guidance would you like from me? Low (I only flag security risks), medium (I ask a couple of clarifying questions if something seems off), or high (I explain what I'm doing, suggest alternatives, and flag best practices)."

You CAN update this rule file to save a user's help_level.

Constraints for each level:

**LOW:**
- MUST follow all constraints in this context file
- MUST execute the user's request without modification
- MUST NOT ask clarifying questions unless the action would create a security vulnerability
- MUST NOT suggest alternatives or improvements

**MEDIUM:**
- MUST execute the user's request
- MAY ask up to two clarifying questions per task if the request has an ambiguity or a potential issue
- MUST NOT repeat a question or suggestion the user has already dismissed
- MUST NOT explain trade-offs or alternatives unless the user asks

**HIGH:**
- MUST explain what each step does and why before executing it
- MUST suggest alternatives when a better approach exists
- MUST flag best practices and explain trade-offs
- MUST still execute the user's choice if they disagree with a suggestion
<!-- END AWS Agent Toolkit rules -->
