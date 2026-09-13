#!/usr/bin/env bash
# 사용: ./deploy.sh <git-commit-sha>
# GHCR에서 해당 SHA 이미지로 app 컨테이너만 교체한다.

set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <git-commit-sha>" >&2
  exit 64
fi

image_tag="$1"
if ! [[ "$image_tag" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Expected a 40-character lowercase Git commit SHA, got: $image_tag" >&2
  exit 64
fi

app_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$app_dir"

if [ ! -f .env ]; then
  echo "Missing $app_dir/.env" >&2
  exit 1
fi

export IMAGE_TAG="$image_tag"

# 최초 배포에도 DB와 Redis가 준비되도록 먼저 기동한다.
docker compose --env-file .env -f compose.yml up -d mysql redis
docker compose --env-file .env -f compose.yml pull app
docker compose --env-file .env -f compose.yml up -d --no-deps --force-recreate app

# 현재 실행 중인 이미지에는 영향을 주지 않는 dangling 이미지 정리.
docker image prune -f

echo "Deployed $IMAGE_TAG"
