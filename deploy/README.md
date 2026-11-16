# EC2 배포

`Mymyseoyoung` 브랜치에 push하면 GitHub Actions가 테스트를 실행하고 Docker 이미지를 GHCR에 게시한 뒤, EC2에서 이미지를 내려받아 앱과 MySQL을 Compose로 실행한다. PR은 테스트만 실행한다.

## 최초 준비

1. EC2에 Docker Engine과 Compose를 설치하고 `~/spring-cgv-24th` 디렉터리를 만든다. 이 디렉터리의 `.env`는 서버에만 보관한다. 워크플로우가 Compose 파일만 교체하므로 `.env`와 MySQL 데이터 볼륨은 유지된다.
2. `.env`에 `DB_NAME`, `DB_USERNAME`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`, `TOSS_SECRET_KEY`를 설정한다. `JWT_SECRET`은 `openssl rand -base64 48`처럼 32바이트 이상을 Base64로 인코딩한 값이어야 한다. `.env`는 Git에 올리지 않는다.
3. EC2 디스크와 메모리를 확인한다. 현재 약 1GB RAM과 6.7GB 루트 디스크에서는 이미지 빌드가 OOM으로 종료됐다. GitHub Actions로 빌드를 옮겨도 이미지, MySQL 데이터, 두 컨테이너의 실행 메모리는 EC2에 필요하다. 배포 전에 EBS 여유 공간을 늘리고, 실행 중 OOM이 나면 인스턴스 메모리도 늘린다.
4. GitHub 저장소의 Settings → Environments에서 `production`을 만들고 다음 Environment secrets를 등록한다.

   | 이름 | 값 |
   | --- | --- |
   | `EC2_HOST` | EC2 퍼블릭 DNS 또는 IP |
   | `EC2_USER` | Ubuntu AMI의 경우 `ubuntu` |
   | `EC2_SSH_KEY` | EC2 접속에 사용한 `.pem` 파일의 전체 내용 |

5. GitHub Actions 러너가 EC2의 SSH 22번 포트에 접속할 수 있어야 한다. GitHub 호스팅 러너의 IP는 고정되지 않으므로 보안 그룹 설정을 별도로 검토한다. 공개 SSH를 쓰는 경우 키 인증만 사용하고, 장기 운영에서는 AWS Systems Manager 같은 접근 경로를 권장한다.

## 실행과 확인

- Push 후 Actions의 `test` → `publish` → `deploy` 작업을 확인한다. 테스트가 실패하면 이미지를 게시하거나 배포하지 않는다.
- 배포 작업은 `compose.yaml`과 `compose.deploy.yaml`을 EC2에 복사하고, 커밋 SHA로 태그된 이미지를 받아 `docker compose up -d --no-build`로 실행한다.
- EC2에서 상태를 확인한다:

  ```bash
  cd ~/spring-cgv-24th
  APP_IMAGE=ghcr.io/mymyseoyoung/spring-cgv-24th:<배포된-커밋-SHA>
  sudo env APP_IMAGE="$APP_IMAGE" docker compose -f compose.yaml -f compose.deploy.yaml ps
  sudo env APP_IMAGE="$APP_IMAGE" docker compose -f compose.yaml -f compose.deploy.yaml logs --tail=100 app
  ```

  Actions는 `APP_IMAGE` 값을 자동으로 전달한다.

- 앱은 `http://localhost:8080/api/movies` 응답으로 확인한다. 외부에서 접속할 경우 EC2 보안 그룹의 8080 포트를 필요한 IP에만 연다. MySQL 3306 포트는 외부에 공개하지 않는다.
