# 001. 회원 인증 Work Log

## 2026-08-12

### 작업 내용
`001-auth.md` Plan의 선행 작업으로, 로컬 개발 환경에 MySQL/Redis를 Docker Compose로 연동했다.

### 변경된 파일
- `docker-compose.yml` (신규) — MySQL 8, Redis 7 컨테이너 정의
- `.env` (신규, gitignore) — MySQL/Redis 계정·비밀번호
- `.gitignore` (수정) — `.env`, `application-local.properties` 추가
- `src/main/resources/application-local.properties` (신규, gitignore) — 로컬 DB/Redis 접속 정보
- `src/main/resources/application.properties` (수정) — `spring.profiles.active=local`, 공통 JPA 설정 추가
- `docs/plans/001-auth.md` (수정) — 사전 준비 섹션 추가 및 실제 결과 반영

### 주요 변경사항
- MySQL: `board` DB, `board` 계정 신규 생성. 호스트 포트는 `127.0.0.1:3307`로 바인딩(외부 미노출).
- Redis: `requirepass`로 비밀번호 인증 적용, `127.0.0.1:6379`로 바인딩.
- 전용 Docker 네트워크 분리는 현재 범위에서는 보류(Compose 기본 네트워크로 충분, 앱 컨테이너화 시점에 재검토).
- 민감 정보(`.env`, `application-local.properties`)는 git에 커밋되지 않도록 `.gitignore`에 추가.

### 테스트 결과
- `docker exec board-mysql mysqladmin ping` → 성공
- `docker exec board-mysql mysql -e "SHOW DATABASES;"` → `board` 스키마 존재 확인
- `docker exec board-redis redis-cli -a <password> ping` → `PONG`
- `docker exec board-redis redis-cli ping` (비밀번호 없이) → `NOAUTH Authentication required` (인증 강제 확인)

### 발생한 문제 및 해결 방법
- 로컬에 기존 `MySQL80` Windows 서비스가 3306/33060 포트를 이미 점유하고 있어 Docker MySQL 기동 시 포트 바인딩 실패.
  → 기존 서비스는 유지한 채, Docker MySQL의 호스트 포트를 3307로 변경하여 해결. `application-local.properties`도 함께 수정.

### Plan과 실제 구현의 차이점
- Plan 수립 시점에는 포트 번호를 구체적으로 명시하지 않았으나(3306 가정), 기존 로컬 MySQL과의 충돌로 실제로는 3307을 사용하게 됨. `001-auth.md`에 반영 완료.
- User 엔티티/Security/JWT/OAuth2 등 인증 기능 자체는 아직 착수하지 않음 (인프라 연동만 완료).

## 2026-08-13

### 작업 내용
`001-auth.md` Plan의 회원 인증 기능(자체 회원가입/로그인, Google/Naver OAuth2, JWT Access/Refresh, Role 기반 권한) 구현이 이미 되어 있는 상태에서, 누락되어 있던 테스트 코드를 작성하고 전체 테스트를 통과시켰다. Plan 상태를 `Completed`로 갱신했다.

### 변경된 파일
- `src/test/java/com/example/board/security/jwt/JwtTokenProviderTest.java` (신규)
- `src/test/java/com/example/board/service/AuthServiceTest.java` (신규)
- `src/test/java/com/example/board/controller/AuthControllerTest.java` (신규)
- `src/test/java/com/example/board/controller/UserControllerTest.java` (신규)
- `docs/plans/001-auth.md` (수정) — 실제 결과 반영, 작업 상태를 `Completed`로 변경

### 주요 변경사항
- `JwtTokenProviderTest`: Access/Refresh Token 생성·파싱, 만료 토큰, 서명 변조 토큰, 형식 오류 토큰 검증 실패 케이스.
- `AuthServiceTest`(Mockito 단위 테스트): 회원가입 성공/이메일 중복, 로그인 성공, Refresh Token 재발급 성공/유효하지 않은 토큰/Redis 미존재/불일치, 로그아웃.
- `AuthControllerTest`: `@WebMvcTest` + `MockMvc`로 `/api/auth/signup`, `/api/auth/login`, `/api/auth/reissue` 상태 코드·응답 검증. `/api/auth/logout`은 컨트롤러 직접 호출 방식으로 대체(아래 문제 참고).
- `UserControllerTest`: `/api/users/me`도 동일한 이유로 컨트롤러 직접 호출 방식의 단위 테스트로 작성.

### 테스트 결과
- `./mvnw test` (JDK 17) 전체 통과: `BoardApplicationTests` 1건, `AuthControllerTest` 6건, `UserControllerTest` 1건, `JwtTokenProviderTest` 5건, `AuthServiceTest` 8건 — 총 21건 성공.

### 발생한 문제 및 해결 방법
- 로컬 `JAVA_HOME`이 JDK 11로 설정되어 있어 테스트 컴파일 시 `release version 17 not supported` 오류 발생 → 빌드 시 `JAVA_HOME`을 JDK 17 경로로 명시적으로 지정해 해결. (JDK 11/17이 모두 설치되어 있고 기본값이 11로 잡혀 있음)
- 이 프로젝트가 사용하는 Spring Boot 4.0(스냅샷) 기준으로 테스트 관련 클래스 위치가 기존 문서와 다름: `@WebMvcTest`/`@AutoConfigureMockMvc`는 `org.springframework.boot.webmvc.test.autoconfigure` 패키지로, `@MockBean`은 폐기되고 `org.springframework.test.context.bean.override.mockito.MockitoBean`으로 대체됨. `ObjectMapper`도 Jackson 2(`com.fasterxml.jackson.databind`)가 아닌 신규 Jackson 3(`tools.jackson.databind`) 빈이 등록되어 있어 테스트 코드에서 임포트를 맞춤.
- `@WebMvcTest(AuthController.class)`/`UserController.class` 슬라이스에서 `spring-boot-starter-oauth2-client`의 `OAuth2ClientWebSecurityAutoConfiguration`이 자동 적용되어 `HttpSecurity` 빈을 찾지 못해 컨텍스트 로딩이 실패 → `excludeAutoConfiguration`으로 OAuth2 클라이언트 관련 자동설정 2종을 제외해 해결.
- 제외 후에도 `@AuthenticationPrincipal` 인자가 컨트롤러에서 null로 들어와 `/api/auth/logout`, `/api/users/me`가 500을 반환 → 원인은 `SecurityConfig`(`@EnableWebSecurity`)가 슬라이스 컨텍스트에 없어 인증 principal 리졸버가 등록되지 않기 때문. `SecurityConfig`를 그대로 `@Import`하면 이번에는 OAuth2 관련 빈 부재로 다시 컨텍스트 로딩이 실패해, 두 엔드포인트는 MockMvc 대신 컨트롤러 메서드를 직접 호출하는 방식으로 테스트를 작성해 우회했다.

### Plan과 실제 구현의 차이점
- 테스트 계획에는 `/api/auth/logout`, `/api/users/me`를 "요청/응답 상태 코드 검증"하는 Controller 통합 테스트로 명시했으나, 위 문제로 인해 MockMvc 기반 HTTP 테스트 대신 컨트롤러 메서드 직접 호출 단위 테스트로 대체했다. `001-auth.md`에 사유를 반영 완료.
- OAuth2(Google/Naver) 실제 client-id/secret 등록 및 수동 로그인 테스트는 사용자의 IdP 앱 등록이 필요해 이번 작업 범위에서는 진행하지 않음.
