# Deployment Environment

This document lists configuration names only. Store values in the Raspberry Pi development server environment file; do not commit real credentials.

## Raspberry Pi development runtime configuration

Set `SPRING_PROFILES_ACTIVE=prod` and provide the application connection values:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for the `moeasy` MySQL database.
- `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` for the Redis service.
- `JWT_SECRET` for token signing.
- `AWS_REGION` and `AWS_S3_BUCKET` for the private S3 bucket.
- `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` for the Raspberry Pi development IAM user. Keep these values only in `deploy/.env` on the server.
- `OPENAI_API_KEY` for AI review and `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, and `PORTONE_CHANNEL_KEY` for payments when those features are enabled.
- OAuth and optional feature values such as `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `FCM_ENABLED` as required by the deployment.

The Spring Boot container receives the Raspberry Pi credentials through `deploy/.env` and the AWS SDK default credential provider chain. Do not place them in the image, application YAML, or repository.

## S3 bucket baseline

The repository does not use Terraform, CloudFormation, or CDK. Apply the baseline to an existing private bucket with the AWS CLI script below; it does not create or delete buckets:

```bash
AWS_REGION=ap-northeast-2 AWS_S3_BUCKET=example-private-bucket ./deploy/configure-s3-bucket.sh
```

The script enables all four S3 Public Access Block settings, configures SSE-S3 (`AES256`) as the default encryption, and enables Versioning. On a versioned bucket, the service's existing `DeleteObject` request creates a delete marker rather than permanently deleting prior versions; version purge is intentionally out of scope.

The Raspberry Pi runtime policy is provided as `deploy/raspberry-pi-s3-policy.json.template`. Replace only `<bucket-name>` before attaching it to the development credential. It grants `s3:GetObject`, `s3:PutObject`, and `s3:DeleteObject` on that bucket's objects; it does not grant `ListBucket`, Secrets Manager, or bucket administration permissions.

AWS Secrets Manager is intentionally not used. Runtime credentials remain environment variables in the server-local `deploy/.env` and are read by the AWS SDK `DefaultCredentialsProvider`.

## GitHub Actions settings

The current image workflow uses the built-in `GITHUB_TOKEN` for GHCR. The development deployment runs on the Raspberry Pi and reads its AWS credentials from the server-local `deploy/.env`; GitHub Actions does not receive or transfer them.

- `EC2_HOST`, `EC2_USER`, and `EC2_SSH_PRIVATE_KEY` are not used by the current Raspberry Pi self-hosted deployment.
- `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`, and payment/OAuth secrets if the workflow transfers the runtime environment file.
- `DISCORD_WEBHOOK` is optional and is used only for test-failure notifications.

Non-sensitive values such as `AWS_REGION`, `AWS_S3_BUCKET`, `EC2_APP_DIR`, and the image name may be deployment configuration. The bucket baseline script configures an existing bucket but does not create or delete AWS resources, and the repository does not automatically apply IAM policies.
