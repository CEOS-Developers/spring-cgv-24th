# 배포 (GitHub Actions → EC2)

## 흐름
- PR: `./gradlew build`(테스트 포함)만 실행한다.
- `chjpooh2002` 브랜치 push: 테스트 통과 → jar를 EC2로 전송 → `deploy.sh`가 systemd 서비스 `cgv`를 재시작 → `/v3/api-docs` 응답 확인.
- 서버의 환경변수 파일(`/opt/cgv/cgv.env`)은 배포마다 GitHub Secrets/Variables로 새로 만든다. 서버에서 직접 고치지 않는다.

## 최초 1회 수동 작업
1. **GitHub 환경 생성**: 저장소 Settings → Environments → `production` 생성.
2. **Secrets 등록** (`production` 환경의 Environment secrets)

   | 이름 | 값 |
   |---|---|
   | `EC2_HOST` | EC2 퍼블릭 IP 또는 DNS |
   | `EC2_USER` | Ubuntu AMI면 `ubuntu`, Amazon Linux면 `ec2-user` |
   | `EC2_SSH_KEY` | EC2 키 페어 `.pem` 파일 내용 전체 |
   | `DB_HOST` | RDS 엔드포인트 |
   | `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | RDS 접속 정보 |
   | `JWT_SECRET` | `openssl rand -base64 32` 결과 (로컬과 다른 값) |
   | `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | 결제위젯 테스트 키 |

3. **Variables 등록** (`production` 환경의 Environment variables, 비밀이 아닌 값)

   | 이름 | 예시 |
   |---|---|
   | `JWT_ACCESS_TOKEN_VALIDITY` | `30m` |
   | `REFRESH_TOKEN_VALIDITY` | `14d` |
   | `CORS_ALLOWED_ORIGINS` | 프런트엔드 출처, 없으면 `http://localhost:3000` |

4. **보안 그룹**
   - EC2: 22번 포트를 GitHub Actions 러너가 접근할 수 있어야 한다. 러너 IP는 고정이 아니어서 `0.0.0.0/0`으로 열게 되므로 비밀번호 로그인은 꺼 두고 키 인증만 쓴다.
   - RDS: 3306 인바운드를 EC2 보안 그룹에서만 허용한다.
5. **기존 프로세스 정리**: `nohup java -jar ...`로 띄운 앱이 있으면 종료한다(`pkill -f 'java -jar'`). 8080 포트를 systemd 서비스가 써야 한다.
6. EC2에 Java 21이 설치돼 있어야 한다(`java -version`).

## 운영 중 확인
```bash
sudo systemctl status cgv
sudo journalctl -u cgv -f
```

## 주의
- `DB_PASSWORD` 등에 `"`, `\`, `$` 가 들어가면 systemd 환경변수 파일에서 다르게 해석될 수 있다. 영문·숫자 위주로 정한다.
- 운영 프로필(`prod`)은 `ddl-auto: update`다. 테이블·컬럼 추가만 반영되고, 컬럼 삭제·타입 변경은 반영되지 않는다.
