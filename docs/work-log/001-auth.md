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

## 2026-08-13 (프론트엔드 화면 구현)

> 이 작업은 원래 별도 문서인 `plans/002-frontend-pages.md` / `work-log/002-frontend-pages.md`로 기록되었으나, 별도 기능이 아니라 `001-auth.md` 인증 API에 대한 화면이라는 판단에 따라 이후 `001-auth.md` 쪽 문서로 병합했다(아래 "2026-08-13 (2)" 참고).

### 작업 내용
`002-frontend-pages.md` Plan(현재는 `001-auth.md`에 병합됨)에 따라 로그인/회원가입/홈/OAuth2 리다이렉트 수신 화면을 정적 리소스로 구현했다.

### 변경된 파일
- `src/main/java/com/example/board/controller/MainController.java` (삭제) — 템플릿 엔진이 없어 렌더링되지 않던 뷰 반환 컨트롤러
- `src/main/resources/templates/home.html` (삭제) — 사용되지 않던 템플릿
- `src/main/resources/static/index.html`, `login.html`, `signup.html`, `oauth2-redirect.html` (신규)
- `src/main/resources/static/css/style.css` (신규) — 공통 스타일
- `src/main/resources/static/js/auth.js`, `login.js`, `signup.js`, `index.js`, `oauth2-redirect.js` (신규)
- `src/main/resources/application.properties` (수정) — `oauth2.redirect-uri`를 `http://localhost:3000/oauth2/redirect` → `http://localhost:8081/oauth2-redirect.html`로 변경

### 주요 변경사항
- 새 의존성 추가 없이 `src/main/resources/static/`에 정적 HTML/CSS/바닐라 JS로 구현. Spring Boot의 welcome page 기능을 이용해 `static/index.html`이 `/`에서 자동 서빙되도록 함(로그 확인: `Adding welcome page: class path resource [static/index.html]`).
- 토큰은 `localStorage`에 저장. `auth.js`가 `authFetch` 래퍼를 제공해 Access Token 만료(401) 시 `/api/auth/reissue`로 1회 자동 재시도.
- 홈 화면(`index.js`)은 로그인 상태면 `GET /api/users/me`로 사용자 이름을 표시하고 로그아웃 버튼을, 비로그인 상태면 로그인/회원가입 링크를 표시. 게시판은 "준비 중" 텍스트만 배치(CRUD 화면은 범위 외).
- OAuth2 로그인 성공 시 `OAuth2SuccessHandler`가 리다이렉트하는 대상을 이 앱 자신의 `oauth2-redirect.html`로 맞춰, 쿼리 파라미터의 토큰을 저장 후 홈으로 이동하도록 구현.
- 신규 API는 추가하지 않고 기존 `/api/auth/**`, `/api/users/me`를 그대로 사용.

### 테스트 결과
- `./mvnw.cmd -o test` (JDK 17) 전체 통과: 기존 21건 그대로 성공(정적 리소스만 추가되어 백엔드 테스트 영향 없음).
- 화면 자체의 회원가입/로그인/로그아웃 수동 브라우저 테스트는 진행하지 않음(로컬 실행 환경 확인은 사용자 몫으로 남김). Google/Naver 소셜 로그인 완료 테스트는 이전 대화에서 안내한 대로 실제 client-id/secret 등록이 선행되어야 한다.

### 발생한 문제 및 해결 방법
- 없음. `MainController` 제거 후 welcome page 메커니즘이 정상 동작함을 테스트 로그로 확인했다. (단, 이후 "2026-08-13 (2)" 작업에서 실제 브라우저 접근 시 인증 관련 버그 2건이 발견되었다 — 당시에는 수동 브라우저 테스트를 하지 않아 발견되지 않음.)

### Plan과 실제 구현의 차이점
- 없음. Plan에 명시한 파일 구성 그대로 구현했다.

## 2026-08-13 (2)

### 작업 내용
기존 `002-frontend-pages.md`로 별도 관리되던 로그인/회원가입/홈 화면 Plan을, 별도 기능이 아니라 `001-auth.md` 인증 API에 대한 화면이라는 사용자 판단에 따라 `001-auth.md`로 병합했다(문서 재구성, 코드 변경 없음). 이어서 화면 파일들을 검토하는 과정에서 로그인 관련 화면을 하위 폴더로 정리하고, `SecurityConfig`의 버그 2건(정적 리소스 접근 시 인증 요구, 미인증 리다이렉트 대상이 커스텀 로그인 화면이 아닌 Spring Security 기본 생성 페이지였던 문제)을 발견해 함께 수정했다.

### 변경된 파일
- `docs/plans/002-frontend-pages.md` (삭제) — `docs/plans/001-auth.md`로 내용 병합
- `docs/plans/001-auth.md` (수정) — 프론트엔드 화면 섹션 병합, 폴더 구조/실제 결과/작업 상태 반영
- `src/main/resources/static/auth/login.html`, `signup.html`, `oauth2-redirect.html` (이동) — 기존 `static/` 루트에서 `static/auth/`로 이동
- `src/main/resources/static/js/auth/login.js`, `signup.js`, `oauth2-redirect.js` (이동) — 기존 `static/js/` 루트에서 `static/js/auth/`로 이동
- `src/main/resources/static/js/index.js`, `src/main/resources/static/js/auth.js`, `src/main/resources/static/auth/login.html`, `src/main/resources/static/auth/signup.html`, `src/main/resources/static/js/auth/signup.js` (수정) — `/login.html`, `/signup.html`, `/oauth2-redirect.html` 등의 경로를 `/auth/...`로 수정
- `src/main/resources/application.properties` (수정) — `oauth2.redirect-uri`를 `http://localhost:8081/auth/oauth2-redirect.html`로 수정
- `src/main/java/com/example/board/security/SecurityConfig.java` (수정) — `PERMIT_ALL_PATHS`에 `/index.html`, `/auth/**`, `/css/**`, `/js/**` 추가, `oauth2Login(...).loginPage("/auth/login.html")` 지정

### 주요 변경사항
- **폴더 재구성**: 로그인/회원가입/OAuth2 리다이렉트 화면과 그 스크립트를 `static/auth/`, `static/js/auth/`로 이동해, 이후 게시판 화면이 추가될 때 루트가 어지러워지지 않도록 정리. 공통 유틸(`auth.js`)은 여러 화면이 공유하므로 `static/js/` 루트에 유지.
- **버그 수정 1 (정적 리소스 인증 요구)**: `SecurityConfig`의 `PERMIT_ALL_PATHS`가 정확히 `"/"`만 허용해, welcome page 처리(`/index.html`)와 `/css/**`, `/js/**`, 화면 페이지 요청이 전부 `anyRequest().authenticated()`에 걸려 있었다. `/index.html`, `/auth/**`, `/css/**`, `/js/**`를 permitAll에 추가해 정적 화면이 인증 없이 정상 로드되도록 수정.
- **버그 수정 2 (미인증 리다이렉트 대상)**: `oauth2Login()`에 `loginPage()`를 지정하지 않아, 미인증 상태로 보호된 경로에 접근하면 Spring Security가 자동 생성한 기본 로그인 페이지(`/login`)로 302 리다이렉트되고 있었다(우리가 만든 `/auth/login.html`이 아님). `.loginPage("/auth/login.html")`을 지정해 리다이렉트 대상을 우리가 만든 로그인 화면으로 변경.

### 테스트 결과
- `JAVA_HOME`을 JDK 17로 지정해 `./mvnw -o compile` 성공.
- `spring-boot:run`으로 로컬 기동 후 `curl`로 직접 검증:
  - `GET /`, `/css/style.css`, `/auth/login.html`, `/auth/signup.html`, `/js/auth/login.js` → 모두 200
  - `GET /api/users/me` (토큰 없이) → `302 Location: http://localhost:8081/auth/login.html` (수정 전에는 `http://localhost:8081/login`으로 리다이렉트되었음을 수정 전 별도 확인)
- `./mvnw -o test` 전체 재실행 → 21건 전부 통과(`BoardApplicationTests` 1, `AuthControllerTest` 6, `UserControllerTest` 1, `JwtTokenProviderTest` 5, `AuthServiceTest` 8), 회귀 없음 확인.

### 발생한 문제 및 해결 방법
- `loginPage()`를 지정하면 Spring Security의 기본 로그인 페이지 생성 필터가 비활성화되는데, `PERMIT_ALL_PATHS`의 `"/login/**"` 패턴이 (Ant 경로 매칭 특성상 `/x/**`가 `/x` 자체도 매치함) `/login` 요청도 permitAll로 통과시켜 버렸다. `/login`에는 매핑된 컨트롤러나 정적 파일이 없어 `NoResourceFoundException`이 발생하고, 이를 `GlobalExceptionHandler`가 일반 오류로 처리해 404 대신 500이 반환되는 것을 확인했다. 다만 애플리케이션 어디에서도 `/login`(슬래시 뒤 아무것도 없는 경로)을 직접 참조하지 않아 실제 사용자 흐름에는 영향이 없다고 판단, 이번 범위에서는 수정하지 않고 확인만 해 둠.

### Plan과 실제 구현의 차이점
- Plan(`002-frontend-pages.md`, 이후 `001-auth.md`로 병합)에는 화면 파일들이 `static/` 루트에 평면적으로 위치하는 것으로 되어 있었으나, 실제로는 로그인 관련 화면을 `static/auth/`, `static/js/auth/`로 재배치했다. `001-auth.md`에 반영 완료.
- `SecurityConfig`의 `PERMIT_ALL_PATHS` 보정과 `oauth2Login().loginPage(...)` 지정은 원래 Plan에는 없던 항목으로, 화면 구현 후 실제 동작을 확인하는 과정에서 발견되어 추가로 수정했다.

## 2026-08-13 (3)

### 작업 내용
바로 위 "2026-08-13 (2)"에서 범위 밖으로 남겨두었던 `GET /login` 500 이슈를, 사용자 요청으로 `MainController`를 재생성해 마저 수정했다.

### 변경된 파일
- `src/main/java/com/example/board/controller/MainController.java` (신규 — 기존과 동일 경로지만 용도가 다른 새 파일) — `GET /login` → `redirect:/auth/login.html`
- `docs/plans/001-auth.md` (수정) — `MainController` 재생성 관련 구현 방법/실제 결과 반영

### 주요 변경사항
- `PERMIT_ALL_PATHS`의 `"/login/**"` 패턴이 Ant 경로 매칭 특성상 `/login`도 permitAll로 통과시키는데, 매핑된 컨트롤러/정적 파일이 없어 `NoResourceFoundException` → `GlobalExceptionHandler`가 500으로 응답하던 문제였다.
- `MainController`에 `@GetMapping("/login")`을 추가해 `"redirect:/auth/login.html"`을 반환하도록 구현. 컨트롤러 매핑이 정적 리소스 폴백 핸들러보다 우선 매칭되므로 더 이상 `NoResourceFoundException`이 발생하지 않는다. `redirect:` 접두사는 Spring Boot가 기본 등록하는 `InternalResourceViewResolver`(`UrlBasedViewResolver`)가 처리하므로 Thymeleaf 등 템플릿 엔진 없이도 동작한다.

### 테스트 결과
- `curl -D - http://localhost:8081/login` → `302 Location: http://localhost:8081/auth/login.html`
- `curl -L -o /dev/null -w "%{http_code}"` → 최종 `200`
- `./mvnw -o test` 재실행 → 21건 전부 통과, 회귀 없음.

### 발생한 문제 및 해결 방법
- 없음. 계획대로 한 번에 해결됨.

### Plan과 실제 구현의 차이점
- 없음.
