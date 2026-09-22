# 005. 관리자(ADMIN) 기능 — 회원 관리

## 작업 목적
`Role.ADMIN`은 001-auth 때부터 존재하지만 지금까지는 "게시글/댓글 삭제 시 작성자가 아니어도 허용"하는 용도로만 쓰였고, 관리자가 실제로 뭔가를 관리할 수 있는 화면/API는 전혀 없다. 이번 Plan에서 **회원 관리**(목록 조회, 권한 변경)를 관리자 전용 화면+API로 구현한다.

## 요구사항
- [x] ADMIN 권한을 가진 사용자만 관리자 API를 호출할 수 있다 (화면 자체는 다른 화면들과 동일하게 공개, 실제 데이터 접근은 API 레벨에서 차단 — 아래 "구현 결과와의 차이점" 참고, 애초 계획과 달라짐)
- [x] 회원 목록 조회 (페이징, 이메일/이름 검색)
- [x] 회원 권한 변경 (USER ↔ ADMIN)
- [x] 관리자는 자기 자신의 권한은 변경할 수 없다(실수로 스스로 권한을 낮춰 잠기는 것 방지)
- [x] 로그인 상태에서 ADMIN 권한을 가진 사용자에게만 네비게이션에 "관리자" 링크 노출
- [x] 최초 관리자 계정 부트스트랩: `application-local.properties`에 지정한 이메일로 가입(자체 가입 또는 OAuth2)하면 자동으로 ADMIN 권한 부여 (사용자 질문으로 이 Plan에 추가 — 회원가입으로 ADMIN을 만들 방법이 아예 없다는 "최초 관리자를 어떻게 만드나" 문제 해결)

### 확인된 사용자 결정 사항
- 이번 범위는 **회원 관리(조회/권한 변경)만** 다룬다.
- **회원 강제 탈퇴(삭제)는 이번 범위에서 제외한다.** 삭제 대상 회원이 작성한 게시글/댓글/좋아요를 어떻게 처리할지(연쇄 삭제 vs 차단)가 단순 CRUD보다 복잡한 별도 의사결정이 필요해서, 회원 삭제는 필요 시 별도 Plan에서 다룬다.
- 게시글/댓글 관리자 삭제 기능은 이미 있다(`BoardService.delete`/`CommentService.delete`가 ADMIN Role을 확인해 작성자가 아니어도 삭제 허용) — 별도 관리자용 게시글 관리 화면은 이번 범위에서 추가하지 않는다.
- 공지사항 고정, 회원 정지(계정 잠금), 신고 처리 등은 범위 밖(필요 시 별도 Plan).

## 현재 구조 및 관련 코드
- `User` 엔티티에 `role` 필드가 있고 `Role` enum은 `USER`/`ADMIN` 두 값뿐이다. `role`을 바꾸는 메서드가 없어 추가가 필요하다(`changePassword`처럼 `changeRole` 추가).
- `UserRepository`는 `findByEmail`, `existsByEmail`, `findByProviderAndProviderId`만 있고 목록 조회(페이징)나 검색 기능이 없다 — `JpaRepository`가 기본 제공하는 `findAll(Pageable)`은 쓸 수 있지만, 이메일/이름 검색은 파생 쿼리 메서드를 추가해야 한다.
- 지금까지의 화면 접근 제어 방식은 전부 "정적 리소스는 `PERMIT_ALL_PATHS`로 열어두고, 실제 쓰기 API 호출 시점에만 인증/인가를 확인"하는 패턴이었다(비로그인도 화면 자체는 볼 수 있음, 예: `write.html`). **관리자 화면은 이 패턴을 따르지 않는다** — 일반 로그인 사용자가 관리자 화면 자체를 열람하는 것도 막아야 하므로, `SecurityConfig`에 `/admin/**`, `/api/admin/**`를 `hasRole("ADMIN")`으로 명시적으로 제한하는 매처를 추가한다(화면 정적 리소스에도 Spring Security 필터가 그대로 적용되므로 가능).
- `CustomUserDetails.getAuthorities()`가 이미 `"ROLE_" + user.getRole().name()` 형태로 권한을 제공하므로, `hasRole("ADMIN")`이 `ROLE_ADMIN`과 정확히 매칭된다(추가 설정 불필요).
- `002-board-crud.md`에서 이미 겪은 문제(`/api/**` 인증 실패 시 302 대신 401 반환하도록 수정)가 있었다 — 이번엔 인가 실패(403, `AccessDeniedException`) 케이스이므로 별도 확인이 필요하다. Spring Security 기본 `AccessDeniedHandler`는 별도 에러 페이지를 지정하지 않으면 단순히 403 상태 코드를 반환하므로 이론상 문제없어야 하지만, 구현 후 실제로 JSON 403이 오는지 확인한다(그렇지 않으면 이때 가서 수정).
- 화면 패턴은 001~004와 동일(정적 HTML + vanilla JS + fetch, `auth.js`/`nav.js`/`board/common.js`의 `renderPagination` 재사용).

## 변경할 파일

### 신규 생성
```
src/main/java/com/example/board/controller/AdminController.java      # /api/admin/**
src/main/java/com/example/board/service/AdminService.java
src/main/java/com/example/board/dto/response/AdminUserResponse.java  # id, email, name, role, provider, createdAt
src/main/java/com/example/board/dto/request/RoleUpdateRequest.java   # role

src/main/resources/static/admin/users.html
src/main/resources/static/js/admin/users.js
```

### 수정
```
src/main/java/com/example/board/entity/User.java                # changeRole(Role) 메서드 추가
src/main/java/com/example/board/repository/UserRepository.java  # 검색용 파생 쿼리 메서드 추가
src/main/java/com/example/board/security/SecurityConfig.java    # /admin/**, /api/admin/**를 hasRole("ADMIN")으로 제한
src/main/resources/static/js/nav.js                              # ADMIN이면 "관리자" 링크 노출
src/main/resources/static/css/style.css                          # 관리자 화면 스타일(대부분 기존 board-table 스타일 재사용)

src/test/java/com/example/board/service/... (신규 AdminServiceTest)
src/test/java/com/example/board/controller/... (신규 AdminControllerTest)
```

## 구현 방법

### API

#### 1. `User.changeRole` / `UserRepository` 검색
- `User`에 `changeRole(Role role)` 메서드 추가(`changePassword`와 동일한 패턴).
- `UserRepository`에 검색용 메서드 추가: `Page<User> findByEmailContainingOrNameContaining(String emailKeyword, String nameKeyword, Pageable pageable)`. 단순 단일 테이블 조회/검색이라 CLAUDE.md 규칙대로 JPA로 처리한다(MyBatis 불필요).

#### 2. `AdminService`
```java
public PageResponse<AdminUserResponse> getUsers(String keyword, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<User> result = (keyword == null || keyword.isBlank())
            ? userRepository.findAll(pageable)
            : userRepository.findByEmailContainingOrNameContaining(keyword, keyword, pageable);
    List<AdminUserResponse> content = result.getContent().stream().map(AdminUserResponse::from).toList();
    return PageResponse.of(content, page, size, result.getTotalElements());
}

@Transactional
public void changeRole(Long adminId, Long targetUserId, Role role) {
    if (adminId.equals(targetUserId)) {
        throw new ForbiddenOperationException("자기 자신의 권한은 변경할 수 없습니다.");
    }
    User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException(targetUserId));
    user.changeRole(role);
}
```
- `UserNotFoundException`(404)을 새로 추가한다(`BoardNotFoundException`과 동일한 패턴). 기존 `ForbiddenOperationException`(403)은 자기 자신 권한 변경 시도에 재사용한다.

#### 3. `AdminController`
```java
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminService.getUsers(keyword, page, size));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Void> changeRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        adminService.changeRole(userDetails.getId(), id, request.role());
        return ResponseEntity.noContent().build();
    }
}
```
- 이 컨트롤러의 모든 엔드포인트는 `SecurityConfig`에서 `/api/admin/**`를 `hasRole("ADMIN")`으로 막으므로, 메서드 내부에서 별도로 Role을 확인하지 않는다(인증/인가는 Security 계층, 컨트롤러는 HTTP 처리만 — 기존 아키텍처 규칙과 동일).

#### 4. `GlobalExceptionHandler`
- `UserNotFoundException` → 404 핸들러 추가(기존 `handleNotFound`에 클래스만 추가).

#### 5. `SecurityConfig`
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(PERMIT_ALL_PATHS).permitAll()
        .requestMatchers(HttpMethod.GET, "/api/boards/**").permitAll()
        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())
```
- `PERMIT_ALL_PATHS`보다 뒤, `anyRequest().authenticated()`보다 앞에 위치해야 한다(더 구체적인 매처가 먼저 평가되어야 함).

### 화면

#### 6. 회원 관리 화면 (`admin/users.html` + `js/admin/users.js`)
- 이메일/이름 검색 입력 + 목록 테이블(이메일/이름/가입경로/권한/가입일) + 페이징(`board/common.js`의 `renderPagination` 재사용).
- 각 행에 권한 변경 버튼(현재 USER면 "관리자로 변경", ADMIN이면 "일반회원으로 변경") — 클릭 시 `authFetch(PUT)` 호출 후 목록 새로고침.
- 본인 계정 행에는 권한 변경 버튼을 숨긴다(백엔드에서도 막지만 화면에서도 혼란 방지 차원에서 숨김 — `/api/users/me`로 현재 사용자 id 조회 후 비교).
- 진입 시 `isLoggedIn()` 아니면 로그인 페이지로. ADMIN이 아닌 로그인 사용자가 API 호출 시 403을 받으면 에러 메시지를 보여주고 홈으로 이동(화면 자체는 서버가 막지만, 클라이언트 쪽 방어도 추가).

#### 7. 네비게이션
- `nav.js`가 `/api/users/me` 응답을 파싱하도록 변경(현재는 `response.ok`만 확인하고 body를 읽지 않음)해서, `role === "ADMIN"`이면 `nav-links`에 `<a href="/admin/users.html">관리자</a>`를 추가한다.

## 데이터베이스 변경 사항
없음 (기존 `users.role` 컬럼 재사용).

## API 변경 사항

| Method | URI | 설명 | 인증 필요 |
|---|---|---|---|
| GET | /api/admin/users?keyword=&page=&size= | 회원 목록 조회/검색 | O (ADMIN) |
| PUT | /api/admin/users/{id}/role | 회원 권한 변경 | O (ADMIN) |

## 테스트 계획
- **Service 단위 테스트**: 회원 목록 조회(키워드 있음/없음), 권한 변경 성공, 자기 자신 권한 변경 시 예외, 존재하지 않는 회원 권한 변경 시 예외.
- **Controller 테스트**: 기존 슬라이스 테스트 제약(001-auth에서 확인)과 동일한 사유로 필요 시 컨트롤러 직접 호출 방식 적용.
- **SecurityConfig 동작 확인**(수동/curl): ADMIN 아닌 로그인 사용자로 `/api/admin/users` 호출 시 403, 비로그인은 401, ADMIN은 200. `/admin/users.html`도 동일하게 ADMIN이 아니면 접근 거부되는지 확인.
- **화면**: 브라우저 골든 패스 — ADMIN 로그인 → 관리자 메뉴 노출 확인 → 회원 목록/검색 → 다른 회원 권한 변경 → 본인 계정에는 변경 버튼 없는지 확인 → 일반 사용자로 로그인 시 관리자 메뉴 안 보이고 `/admin/users.html` 직접 접근 시 차단되는지 확인.
- 전체 구현 후 `./mvnw test`로 기존 테스트 포함 전체 회귀 확인.

## 작업 상태
- Completed

### 구현 결과와의 차이점
- **`/admin/**` 화면 자체를 `hasRole("ADMIN")`으로 서버에서 막으려던 계획을 취소했다.** 이 앱은 JWT를 쿠키가 아니라 `localStorage`에 저장하고, `fetch`/`authFetch` 호출 시점에만 JS가 Authorization 헤더를 붙이는 구조다. 그런데 정적 HTML 페이지로의 일반 브라우저 내비게이션(주소창 입력, `<a>` 링크 클릭)은 `fetch`가 아니라서 Authorization 헤더가 전혀 붙지 않는다. 그 결과 관리자가 "관리자" 네비게이션 링크를 눌러도 서버 입장에서는 완전히 비로그인 요청으로 보여, 로그인 페이지로 리다이렉트되는 문제가 실제로 재현됐다(원인 분석 및 재현: Work Log 참고). 이 앱의 인증 구조상 정적 페이지를 서버에서 Role로 막는 건 애초에 불가능해서, 계획을 수정해 `/admin/**`는 다른 화면들과 동일하게 `PERMIT_ALL_PATHS`에 넣고(화면 자체는 로드됨), 실제 데이터가 오가는 `/api/admin/**`만 `hasRole("ADMIN")`으로 막았다. 관리자가 아닌 로그인 사용자가 화면에 들어와도 API가 403을 반환하므로 `users.js`가 에러 메시지를 보여주고 홈으로 돌려보낸다(이미 계획했던 클라이언트 쪽 방어 로직 그대로 활용).
- `index.js`(홈 화면)가 `nav.js`와 별개로 자체 네비게이션 렌더링 로직을 갖고 있다는 걸 깜빡해서, 처음엔 `nav.js`에만 "관리자" 링크 조건을 추가했다가 홈 화면에서는 링크가 안 뜨는 버그가 있었다(`003-my-page.md` 때도 같은 실수가 있었는데 이번에 또 반복했다). `index.js`에도 동일한 조건을 추가해 해결했다.
- 나머지 설계(회원 목록/검색, 권한 변경, 자기 자신 변경 방지, `AdminService`/`AdminController` 구조)는 계획대로 구현했다.
- **관리자 부트스트랩 추가**: 회원가입 API가 항상 `Role.USER`로 고정돼 있어서, 관리자 화면을 다 만들어도 정작 "최초의 관리자"를 만들 방법이 없다는 문제를 사용자가 지적했다. `admin.bootstrap-email` 설정값(로컬/환경별로 `application-local.properties`에 지정, 커밋 안 됨)과 일치하는 이메일로 가입하면(자체 가입 `AuthService.signup`, OAuth2 가입 `CustomOAuth2UserService.findOrCreateUser` 둘 다) 자동으로 ADMIN이 되도록 `AdminBootstrapPolicy`를 새로 만들어 양쪽 서비스에 공통으로 적용했다(중복 로직 제거).
