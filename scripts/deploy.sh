#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 1 || ! $1 =~ ^spring-cgv-24th:[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]]; then
  printf 'Usage: %s spring-cgv-24th:<tag>\n' "$0" >&2
  exit 2
fi

image=$1
deploy_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$deploy_dir"
[[ -f .env && -f compose-rds.yml ]] || { printf 'Deployment configuration is missing.\n' >&2; exit 1; }

exec 9> .deploy.lock
flock -n 9 || { printf 'Another deployment is running.\n' >&2; exit 1; }

compose=(docker compose --project-directory "$deploy_dir" --env-file "$deploy_dir/.env" -f "$deploy_dir/compose-rds.yml")
docker image inspect "$image" > /dev/null
container_id=$("${compose[@]}" ps --all --quiet app)
previous_image=
if [[ -n "$container_id" ]]; then
  previous_image=$(docker inspect --format '{{.Config.Image}}' "$container_id")
fi

env_tmp=$(mktemp "$deploy_dir/.env.XXXXXX")
deployed=false
cleanup() {
  status=$?
  trap - EXIT INT TERM
  rm -f "$env_tmp"
  if [[ "$deployed" != true ]]; then
    if [[ -n "$previous_image" ]]; then
      printf 'Deployment failed; restoring the previous image.\n' >&2
      if CGV_IMAGE="$previous_image" "${compose[@]}" up --detach --no-build --pull never --wait --wait-timeout 240 app; then
        printf 'Previous image is healthy again.\n' >&2
      else
        printf 'Rollback failed; manual recovery is required.\n' >&2
      fi
    else
      printf 'No previous container was available for rollback.\n' >&2
    fi
    [[ "$status" -ne 0 ]] || status=1
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Prepare the replacement without evaluating or displaying credentials.
awk '!/^[[:space:]]*(export[[:space:]]+)?CGV_IMAGE[[:space:]]*=/' .env > "$env_tmp"
printf 'CGV_IMAGE=%s\n' "$image" >> "$env_tmp"
chmod 600 "$env_tmp"

CGV_IMAGE="$image" "${compose[@]}" up --detach --no-build --pull never --wait --wait-timeout 240 app
mv -f "$env_tmp" .env
deployed=true
printf 'Deployment is healthy: %s\n' "$image"
