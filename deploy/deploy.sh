#!/usr/bin/env bash
set -euo pipefail

image="${1:?사용법: bash deploy.sh <image>:<tag>}"
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "[deploy] $(pwd)/.env 파일이 없습니다. .env.deploy.example을 참고해 먼저 작성하세요." >&2
  exit 1
fi
if [ "$(stat -c %a .env)" != "600" ]; then
  echo "[deploy] 경고: .env 권한이 600이 아닙니다. chmod 600 .env 를 권장합니다." >&2
fi

previous="$(tail -n 1 deploy-history.log 2>/dev/null | awk '{print $2}' || true)"
export APP_IMAGE="$image"

echo "[deploy] 배포 이미지: $image (직전 성공 이미지: ${previous:-없음})"
docker compose config --quiet
docker compose pull --quiet app

echo "[deploy] MySQL 준비 확인"
docker compose up -d --wait --wait-timeout 180 db

echo "[deploy] app 컨테이너 교체 및 헬스체크 대기"
if ! docker compose up -d --wait --wait-timeout 180 --no-deps app; then
  echo "[deploy] 실패: app 헬스체크를 통과하지 못했습니다. 최근 로그를 출력합니다." >&2
  docker compose ps -a >&2 || true
  docker compose logs --no-color --tail 200 app >&2 || true
  if [ -z "$previous" ] || [ "$previous" = "$image" ]; then
    echo "[deploy] 복구할 직전 성공 이미지가 없어 자동 복구를 건너뜁니다." >&2
    exit 1
  fi
  echo "[deploy] 직전 성공 이미지로 자동 복구합니다: $previous" >&2
  if APP_IMAGE="$previous" docker compose up -d --wait --wait-timeout 180 --no-deps app; then
    echo "[deploy] 자동 복구 성공: $previous 실행 중 (이번 배포는 실패로 처리합니다)" >&2
  else
    echo "[deploy] 자동 복구 실패: 직접 확인이 필요합니다." >&2
    docker compose ps -a >&2 || true
    docker compose logs --no-color --tail 100 app >&2 || true
  fi
  exit 1
fi

if grep -q '^APP_IMAGE=' .env; then
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=$image|" .env
else
  echo "APP_IMAGE=$image" >> .env
fi
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $image" >> deploy-history.log

stale_images="$(docker image ls "${image%:*}" --format '{{.Repository}}:{{.Tag}}' \
  | grep -vxF -e "$image" -e "${previous:-$image}" || true)"
if [ -n "$stale_images" ]; then
  echo "$stale_images" | xargs docker image rm >/dev/null || echo "[deploy] 경고: 오래된 이미지 정리에 실패했습니다." >&2
fi

docker compose ps
echo "[deploy] 성공: $image"
