# 005. 관리자(ADMIN) 기능 — 회원 관리 - Work Log

## 2026-09-22

### 작업 내용
`docs/plans/005-admin.md`에 따라 관리자 전용 회원 관리 기능(API+화면)을 구현했다. 회원 목록 조회/검색과 권한(USER↔ADMIN) 변경을 지원한다.

### 변경된 파일

**신규**
```
src/main/java/com/example/board/controller/AdminController.java
src/main/java/com/example/board/service/AdminService.java
src/main/java/com/example/board/dto/response/AdminUserResponse.java
src/main/java/com/example/board/dto/request/RoleUpdateRequest.java
src/main/java/com/example/board/exception/UserNotFoundException.java

src/main/resources/static/admin/users.html
src/main/resources/static/js/admin/users.js

src/test/java/com/example/board/service/AdminServiceTest.java
src/test/java/com/example/board/controller/AdminControllerTest.java
```

**수정**
```
src/main/java/com/example/board/entity/User.java                  # changeRole(Role) 추가
src/main/java/com/example/board/repository/UserRepository.java    # findByEmailContainingOrNameContaining 추가
src/main/java/com/example/board/exception/GlobalExceptionHandler.java  # UserNotFoundException 404 처리 추가
src/main/java/com/example/board/security/SecurityConfig.java      # /api/admin/**를 hasRole("ADMIN")으로 제한
src/main/resources/static/js/nav.js                                 # ADMIN이면 "관리자" 링크 노출
src/main/resources/static/js/index.js                               # 홈 화면에도 동일하게 "관리자" 링크 노출
```

### 주요 변경사항
- 회원 검색은 단일 테이블 조회라 MyBatis 없이 JPA 파생 쿼리(`findByEmailContainingOrNameContaining`)로 처리했다.
- `AdminService.changeRole`에서 관리자가 자기 자신의 권한을 바꾸려 하면 `ForbiddenOperationException`을 던진다(스스로 잠기는 것 방지).
- `AdminController`는 Role 검사를 하지 않는다 — `/api/admin/**` 전체를 `SecurityConfig`에서 `hasRole("ADMIN")`으로 막기 때문에 컨트롤러는 HTTP 처리만 담당한다(기존 아키텍처 규칙 그대로).

### 테스트 결과
- `./mvnw test` 최종 77건 전체 통과(BUILD SUCCESS) — 기존 69건 + 신규 8건.
- 로컬 앱을 별도 포트(8087)로 띄워 curl + Playwright로 검증:
  - 토큰 없이 `/api/admin/users`, `/admin/users.html` 호출 → 401
  - 일반 USER 토큰으로 `/api/admin/users` 호출 → 403(JSON 바디 확인, 별도 AccessDeniedHandler 설정 불필요했음)
  - ADMIN으로 직접 DB에서 승격한 계정으로 호출 → 200, 목록/검색 정상
  - 브라우저로 일반 사용자는 관리자 네비 링크 안 보이고 `/admin/users.html` 직접 접근 시 "관리자만 접근할 수 있습니다" 안내 후 홈으로 이동하는지, ADMIN은 관리자 링크가 보이고 회원 목록에서 다른 회원 권한을 바꾸면 즉시 반영되는지, 본인 계정 행에는 변경 버튼이 없는지(`-`)까지 확인. 스크린샷으로 최종 확인.
- 검증에 쓴 테스트 계정은 이번엔 정리하지 않았다(회원 목록 조회만 하는 기능이라 leftover 계정이 있어도 어떤 테스트도 깨지지 않음 — `boards`/`comments`와 달리 `users` 테이블 카운트를 검증하는 테스트가 없음).

### 발생한 문제 및 해결 방법
1. **`/admin/**` 화면을 `hasRole("ADMIN")`으로 서버에서 막으니, 정작 관리자가 "관리자" 링크를 눌러도 로그인 페이지로 튕기는 버그가 실제로 재현됐다.** 이 앱은 JWT를 `localStorage`에 저장하고 `fetch` 호출 시점에만 JS가 Authorization 헤더를 붙이는 구조인데, 일반 브라우저 페이지 내비게이션(링크 클릭, 주소창 입력)은 `fetch`가 아니라서 Authorization 헤더가 전혀 안 붙는다. 그래서 서버 입장에서는 관리자든 아니든 정적 페이지 요청이 전부 "비로그인"으로 보여, 인증 실패 시 기본 동작(로그인 페이지로 리다이렉트)이 걸렸다. curl로 `-H "Authorization: ..."`를 직접 붙여서 테스트했을 때는 이 문제가 안 보였는데, 실제 브라우저 내비게이션으로 재현하고 나서야 발견했다. **이 앱의 인증 구조상 정적 화면을 서버에서 Role로 막는 건 원천적으로 불가능**하다고 판단해, Plan을 수정: `/admin/**`도 다른 화면들처럼 `PERMIT_ALL_PATHS`에 넣고, 실제 데이터가 오가는 `/api/admin/**`만 `hasRole("ADMIN")`으로 막는 방식으로 바꿨다. 화면 자체는 누구나 열 수 있지만 API가 403을 반환하므로 데이터 노출은 없다.
2. **`index.js`(홈 화면)에 "관리자" 링크가 안 뜨는 버그.** `nav.js`에만 조건부 링크를 추가했는데, 홈 화면(`index.html`)은 `nav.js`가 아니라 `index.js`의 자체 네비게이션 렌더링 로직을 쓴다는 걸 깜빡했다(`003-my-page.md`에서 "마이페이지" 링크 추가할 때도 같은 실수를 했었는데 이번에 또 반복). `index.js`에도 동일한 조건을 추가해 해결했다. 두 파일에 네비게이션 렌더링 로직이 중복돼 있는 게 반복적인 실수의 근본 원인이라, 추후 리팩터링 여지가 있다(이번 Plan 범위 밖이라 지금은 손대지 않음).

### Plan과 실제 구현의 차이점
`docs/plans/005-admin.md`의 "구현 결과와의 차이점" 절 참고 — `/admin/**` 접근 제어 방식을 서버 차단에서 화면 공개+API 차단으로 변경한 것이 가장 큰 차이점이고, 나머지(회원 목록/검색, 권한 변경, 자기 자신 변경 방지)는 계획대로다.

## 2026-09-22 (추가) — 최초 관리자 계정 부트스트랩

### 작업 내용
사용자가 "회원가입으로 관리자 계정을 만들 방법이 없는데 어떻게 관리자로 들어가냐"고 질문했다. 맞는 지적이었다 — 관리자 화면/API는 다 있지만 최초의 관리자를 만들 방법이 DB 직접 수정뿐이었다. `application.properties`에 관리자 이메일을 지정하면 그 이메일로 가입 시 자동으로 ADMIN이 되는 방식으로 해결했다(사용자가 제시한 선택지 중 선택).

### 변경된 파일

**신규**
```
src/main/java/com/example/board/security/AdminBootstrapPolicy.java
src/test/java/com/example/board/security/AdminBootstrapPolicyTest.java
```

**수정**
```
src/main/java/com/example/board/service/AuthService.java                          # signup 시 AdminBootstrapPolicy로 role 결정
src/main/java/com/example/board/security/oauth2/CustomOAuth2UserService.java      # OAuth2 최초 가입 시에도 동일하게 적용
src/main/resources/application.properties                                          # admin.bootstrap-email 키 선언(빈 값)
src/main/resources/application-local.properties                                    # 실제 값을 넣을 자리 추가(커밋 안 됨, 사용자가 본인 이메일로 채워야 함)
src/test/java/com/example/board/service/AuthServiceTest.java                       # AdminBootstrapPolicy 목 추가, 부트스트랩 이메일 가입 테스트 추가
```

### 주요 변경사항
- `AdminBootstrapPolicy`를 `AuthService`(자체 가입)와 `CustomOAuth2UserService`(OAuth2 최초 가입) 양쪽에서 공통으로 쓰도록 분리해서, 회원가입 경로가 두 개인데도 로직 중복 없이 같은 규칙이 적용되게 했다.
- `admin.bootstrap-email`은 빈 값이 기본값이며, 빈 값일 때는 항상 `USER`를 반환한다(설정 안 하면 기존 동작과 완전히 동일 — 하위 호환).
- 이메일 비교는 대소문자를 구분하지 않는다(`equalsIgnoreCase`).

### 테스트 결과
- `./mvnw test` 최종 82건 전체 통과 — 기존 77건 + 신규 5건(`AdminBootstrapPolicyTest` 4건, `AuthServiceTest` 1건).
- 로컬 앱을 별도 포트(8088)로, `--admin.bootstrap-email=bootstrap-admin@example.com`을 커맨드라인 인자로 넘겨 띄워서 확인: 그 이메일로 가입하면 응답에 `"role":"ADMIN"`, 다른 이메일로 가입하면 `"role":"USER"`로 정확히 나뉘는 것을 curl로 확인했다. 검증에 쓴 계정은 확인 후 DB에서 정리했다.

### 안내
사용자가 실제로 관리자가 되려면 `src/main/resources/application-local.properties`의 `admin.bootstrap-email=` 뒤에 본인이 가입할 이메일을 직접 채워 넣어야 한다(이 파일은 `.gitignore`에 있어 커밋되지 않으므로 Claude가 대신 채워주지 않음). 그 상태로 앱을 재시작한 뒤 그 이메일로 회원가입하면 ADMIN 권한으로 생성된다.
