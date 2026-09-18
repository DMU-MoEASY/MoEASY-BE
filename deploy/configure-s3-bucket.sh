#!/usr/bin/env bash
# Apply the security baseline to an existing private S3 bucket.
# Usage: AWS_REGION=... AWS_S3_BUCKET=... ./configure-s3-bucket.sh

set -euo pipefail

bucket_name="${AWS_S3_BUCKET:-}"
region="${AWS_REGION:-}"

if [[ -z "$bucket_name" || -z "$region" ]]; then
  echo "AWS_REGION and AWS_S3_BUCKET must be set" >&2
  exit 64
fi

aws s3api put-public-access-block \
  --bucket "$bucket_name" \
  --region "$region" \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

aws s3api put-bucket-encryption \
  --bucket "$bucket_name" \
  --region "$region" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-bucket-versioning \
  --bucket "$bucket_name" \
  --region "$region" \
  --versioning-configuration Status=Enabled

echo "Applied S3 security baseline to $bucket_name in $region"
