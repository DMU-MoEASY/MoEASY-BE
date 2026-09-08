# Deployment Environment

This document lists configuration names only. Store values in the EC2 environment file or GitHub Settings; do not commit real credentials.

## EC2 runtime configuration

Set `SPRING_PROFILES_ACTIVE=prod` and provide the application connection values:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for the `moeasy` MySQL database.
- `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` for the Redis service.
- `JWT_SECRET` for token signing.
- `AWS_REGION` and `AWS_S3_BUCKET` for S3. `AWS_S3_PUBLIC_BASE_URL` is optional.
- `OPENAI_API_KEY` for AI review and `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, and `PORTONE_CHANNEL_KEY` for payments when those features are enabled.
- OAuth and optional feature values such as `KAKAO_APP_ID`, `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_ALLOWED_ISSUERS`, and `FCM_ENABLED` as required by the deployment.

The EC2 instance should use an IAM instance role for S3 access. Do not place `AWS_ACCESS_KEY_ID` or `AWS_SECRET_ACCESS_KEY` in the image or repository unless the deployment design explicitly requires them.

## GitHub Actions settings

The current image workflow uses the built-in `GITHUB_TOKEN` for GHCR. A future EC2 deployment workflow will typically need these GitHub Secrets:

- `EC2_HOST`, `EC2_USER`, and `EC2_SSH_PRIVATE_KEY` for SSH deployment.
- `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`, and payment/OAuth secrets if the workflow transfers the runtime environment file.
- `DISCORD_WEBHOOK` is optional and is used only for test-failure notifications.

Non-sensitive values such as `AWS_REGION`, `AWS_S3_BUCKET`, `EC2_APP_DIR`, and the image name should preferably be GitHub Actions Variables. No AWS resource creation or production deployment is performed by this repository setup.
