#!/usr/bin/env bash
# GitHub Actions가 jar·환경변수 파일·유닛 파일을 ~/cgv-deploy에 올린 뒤 EC2에서 실행한다.
set -euo pipefail

STAGING_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR=/opt/cgv
SERVICE=cgv
HEALTH_URL=http://localhost:8080/v3/api-docs
APP_USER="$(id -un)"

JAVA_BIN="$(command -v java || true)"
if [ -z "$JAVA_BIN" ]; then
  echo "java를 찾을 수 없습니다. EC2에 Java 21을 먼저 설치하세요." >&2
  exit 1
fi
# systemd는 PATH를 거의 비워 두고 실행하므로 심볼릭 링크를 풀어 절대 경로로 넣는다.
JAVA_BIN="$(readlink -f "$JAVA_BIN")"

sudo mkdir -p "$APP_DIR"
sudo install -m 644 -o "$APP_USER" "$STAGING_DIR/cgv.jar" "$APP_DIR/cgv.jar"
sudo install -m 600 -o "$APP_USER" "$STAGING_DIR/cgv.env" "$APP_DIR/cgv.env"
sed -e "s|__USER__|$APP_USER|" -e "s|__JAVA__|$JAVA_BIN|" "$STAGING_DIR/cgv.service" \
  | sudo tee /etc/systemd/system/$SERVICE.service > /dev/null

sudo systemctl daemon-reload
sudo systemctl enable "$SERVICE" > /dev/null
sudo systemctl restart "$SERVICE"

# restart는 프로세스를 띄우기만 하고 기다리지 않는다. 실제로 요청을 받을 때까지 확인해야 실패한 배포가 성공으로 보이지 않는다.
for _ in $(seq 1 40); do
  if curl -fs -o /dev/null "$HEALTH_URL"; then
    rm -rf "$STAGING_DIR"
    echo "배포 완료"
    exit 0
  fi
  sleep 3
done

echo "앱이 120초 안에 응답하지 않았습니다. 최근 로그:" >&2
sudo journalctl -u "$SERVICE" -n 100 --no-pager >&2
exit 1
