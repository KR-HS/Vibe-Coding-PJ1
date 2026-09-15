# 002. 게시판 CRUD (게시글 + 댓글 + 첨부파일, API + 화면) - Work Log

## 2026-09-10 — API 구현

### 작업 내용
`docs/plans/002-board-crud.md`에 따라 게시글/댓글/첨부파일 CRUD API를 구현했다. JPA는 단건 조회·저장·수정·삭제, MyBatis는 목록 검색/페이징(작성자 JOIN, 댓글 수 집계)에 사용했다.

### 변경된 파일

**신규**
```
src/main/java/com/example/board/
├── entity/BoardCategory.java, Board.java, Comment.java, Attachment.java
├── dto/request/BoardCreateRequest.java, BoardUpdateRequest.java, CommentCreateRequest.java, CommentUpdateRequest.java
├── dto/response/PageResponse.java, BoardListItemResponse.java, BoardDetailResponse.java, CommentResponse.java, AttachmentResponse.java
├── repository/BoardRepository.java, CommentRepository.java, AttachmentRepository.java
├── mapper/BoardMapper.java
├── service/BoardService.java, CommentService.java, FileStorageService.java
├── controller/BoardController.java, CommentController.java
├── exception/BoardNotFoundException.java, CommentNotFoundException.java, AttachmentNotFoundException.java, ForbiddenOperationException.java
└── config/MyBatisConfig.java

src/main/resources/mapper/BoardMapper.xml

src/test/java/com/example/board/
├── service/BoardServiceTest.java, CommentServiceTest.java, FileStorageServiceTest.java
├── controller/BoardControllerTest.java, CommentControllerTest.java
└── mapper/BoardMapperTest.java
```

**수정**
```
src/main/java/com/example/board/BoardApplication.java              # @MapperScan 제거 (MyBatisConfig로 이동)
src/main/java/com/example/board/exception/GlobalExceptionHandler.java  # NotFound(404)/ForbiddenOperation(403) 핸들러 추가
src/main/java/com/example/board/security/SecurityConfig.java       # GET /api/boards/** permitAll 추가
src/main/resources/application.properties                          # MyBatis mapper-locations, 파일 업로드 설정 추가
.gitignore                                                          # /uploads/ 추가
src/test/java/com/example/board/controller/AuthControllerTest.java # MybatisAutoConfiguration 제외 추가 (아래 문제 참고)
docs/plans/002-board-crud.md                                        # 요구사항 체크, 상태를 Completed로 변경, 구현 차이점 기록
```

### 주요 변경사항
- `Board`/`Comment`/`Attachment` 엔티티는 기존 `User` 엔티티와 동일하게 `@PrePersist`/`@PreUpdate`로 시각을 관리하고 `@Builder` + protected 기본 생성자 패턴을 따랐다.
- 게시글 목록/검색은 `BoardMapper`(MyBatis)에서 `users` JOIN + 댓글 수 서브쿼리로 N+1 없이 한 번에 조회한다.
- 게시글/댓글 삭제 시 Service 계층에서 첨부파일(실제 파일 포함) → 댓글 → 게시글 순으로 명시적으로 삭제한다(DB `ON DELETE CASCADE` 미사용, Plan대로).
- 파일 업로드는 `FileStorageService`가 `file.upload-dir`(기본 `./uploads/board`) 하위 `{boardId}/{UUID}_{원본파일명}`에 저장하며, 저장/삭제/다운로드 모두 업로드 루트 하위 경로인지 검증해 경로 조작을 방어한다.
- 조회 계열(`GET /api/boards/**`)만 `SecurityConfig`에서 `permitAll` 처리했고, 나머지 쓰기 작업은 인증이 필요하다.

### 테스트 결과
`./mvnw test` 실행 결과 총 52건 전체 통과 (BUILD SUCCESS).
- 신규: `BoardServiceTest`(8), `CommentServiceTest`(9), `FileStorageServiceTest`(3), `BoardControllerTest`(7), `CommentControllerTest`(3), `BoardMapperTest`(1, 로컬 Docker MySQL 127.0.0.1:3307 대상 실통합 테스트)
- 기존 `AuthServiceTest`, `AuthControllerTest`, `UserControllerTest`, `JwtTokenProviderTest`, `BoardApplicationTests` 포함 전체 회귀 통과.

### 발생한 문제 및 해결 방법
1. **로컬 셸의 JAVA_HOME이 JDK 11을 가리켜 컴파일 실패** (`release version 17 not supported`). `JAVA_HOME`을 `C:\Program Files\Java\jdk-17`로 지정해 해결(프로젝트 파일 변경 아님, 실행 환경 문제).
2. **`@MapperScan`을 `BoardApplication`에 직접 붙였더니 `@WebMvcTest` 슬라이스 테스트(`AuthControllerTest`, `BoardControllerTest`, `CommentControllerTest`)가 전부 컨텍스트 로딩 실패** (`Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`). `MapperScannerConfigurer`가 슬라이스 테스트의 타입 필터를 우회해서 매퍼 빈을 등록하기 때문이었다. `@MapperScan`을 별도의 `config/MyBatisConfig.java`(`@Configuration` 클래스)로 분리해 `@WebMvcTest`가 이 설정 클래스 자체를 타입 필터로 제외하도록 해결했다. 기존 `AuthControllerTest`도 동일한 문제가 재현되어 `MybatisAutoConfiguration` 제외 설정을 함께 추가했다.
3. **`BoardMapperTest`의 페이징 테스트 케이스 작성 오류**: `offset=1, limit=2`로 호출하며 결과가 1건일 것으로 잘못 가정했다(실제로는 2건). `offset=2, limit=2`(서비스의 `offset = page * size` 계산과 동일한 방식)로 수정해 해결했다. 매퍼/서비스 코드 자체의 문제는 아니었다.

### Plan과 실제 구현의 차이점
`docs/plans/002-board-crud.md`의 "구현 결과와의 차이점" 절 참고 — `AttachmentNotFoundException` 추가, `@MapperScan` 위치를 `MyBatisConfig`로 변경한 두 가지가 Plan 대비 차이점이다. 나머지는 계획대로 구현했다.

## 2026-09-15 — 화면 구현 (목록/상세/작성/수정)

### 작업 내용
API만 있고 이를 확인할 수 있는 화면이 없다는 게 드러나(사용자 지적), `docs/plans/002-board-crud.md`에 화면 요구사항을 추가하고 목록/상세/작성/수정 화면을 구현했다. 정적 HTML + vanilla JS + fetch 방식으로, 기존 로그인/회원가입 화면과 동일한 패턴을 따랐다. (이 작업은 원래 별도 Plan/Work Log `003-board-pages.md`로 진행했다가, 이후 "백엔드 기능은 화면까지 포함해야 완료"라는 방침에 맞춰 이 문서로 합쳤다.)

### 변경된 파일

**신규**
```
src/main/resources/static/board/list.html, detail.html, write.html, edit.html
src/main/resources/static/js/board/common.js, list.js, detail.js, write.js, edit.js
src/main/resources/static/js/nav.js   # 계획에 없던 파일 — 아래 "발생한 문제" 참고
```

**수정**
```
src/main/resources/static/index.html                          # 준비중 문구 제거
src/main/resources/static/js/index.js                          # nav-links/home-actions에 게시판 링크 추가
src/main/resources/static/css/style.css                        # 목록 테이블/뱃지/페이지네이션/댓글/폼 스타일 추가
src/main/java/com/example/board/security/SecurityConfig.java   # PERMIT_ALL_PATHS에 "/board/**" 추가
```

### 주요 변경사항
- 목록: 페이지/키워드/카테고리를 쿼리스트링에 반영(`history.replaceState`)해 새로고침·뒤로가기에도 상태 유지.
- 상세: `/api/users/me`로 현재 사용자를 가져와 게시글 `authorId`/댓글 `authorId`와 비교해 수정·삭제 버튼을 조건부로 노출(서버가 어차피 재검증하므로 화면 노출은 UX 목적).
- 작성: `FormData` + `Blob(JSON)`으로 멀티파트 요청 구성, `authFetch`가 `Content-Type`을 강제하지 않아 브라우저가 boundary를 포함한 헤더를 자동 설정하도록 했다.
- 수정: 진입 시 상세 조회 + 현재 사용자 조회를 병렬로 수행해 작성자 본인이 아니면 상세 화면으로 되돌린다.

### 테스트 결과
- `./mvnw test` 전체 52건 통과(BUILD SUCCESS), 기존 테스트 회귀 없음.
- 로컬 앱을 별도 포트(8082)로 띄워 회원가입 → 로그인 → 첨부파일 포함 게시글 작성(multipart) → 목록(카테고리 필터)/상세 조회(조회수 증가 확인) → 첨부파일 다운로드(비로그인 가능) → 댓글 작성/조회/수정/삭제 → 게시글 수정/삭제(204) → 삭제 후 상세 404 확인 → 비로그인 쓰기 차단 → 4개 화면 정적 리소스가 인증 없이 200으로 응답하는지까지 curl로 전체 흐름을 검증했다.
- 신규/수정된 모든 JS 파일은 `node --check`로 구문 오류가 없는지 확인했다.
- 이 시점에는 브라우저 확장(Claude in Chrome) 미설치로 실제 화면 렌더링을 직접 확인하지 못했는데, 이후 사용자 제보로 실제 버그 2건(아래)이 발견되어 Playwright 헤드리스 브라우저로 재현·검증했다.

### 발생한 문제 및 해결 방법
1. **`index.js` 재사용 시 에러**: 다른 화면에서 `index.js`를 그대로 로드하면 `welcome-message`/`home-actions` 같은 홈 화면 전용 DOM을 찾지 못해 에러가 났다. 홈 화면 전용 로직과 공용 네비게이션 렌더링을 분리해야 한다고 판단해, `nav-links`만 다루는 `js/nav.js`를 신설했다(계획 대비 추가된 파일).
2. **curl 멀티바이트(한글) 테스트 시 인코딩 깨짐**: Windows 환경의 MSYS curl(mingw64 빌드)에 한글이 포함된 JSON을 커맨드라인 인자로 직접 넘기면 UTF-8이 깨져 서버에서 `Invalid UTF-8 middle byte` 400/500 에러가 났다. 요청 본문을 파일로 작성해 `--data-binary @file`/`-F key=@file`로 전달하도록 테스트 스크립트를 수정해 해결했다(애플리케이션 코드 문제 아님, 테스트 방법 문제).
3. **테스트용 앱 인스턴스 포트 충돌**: 사용자가 이미 IntelliJ로 8081 포트에 앱을 띄워둔 상태였다. 별도로 `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`로 독립된 인스턴스를 띄워 로그를 직접 확인하며 검증했고, 검증 후 8082 인스턴스는 종료했다(사용자의 8081 인스턴스는 그대로 유지).

## 2026-09-15 (추가) — 인증 실패 응답 방식 버그 수정

### 작업 내용
사용자가 Edge 브라우저에서 홈 화면에 로그인/게시판 링크가 전혀 뜨지 않는다고 제보했다. 원인은 `001-auth.md`에서부터 있던 기존 버그로, 이번 화면 작업으로 처음 드러났다.

**원인**: `SecurityConfig`에 `oauth2Login()`만 설정돼 있고 API 전용 인증 실패 처리가 없어서, 토큰이 없거나 유효하지 않은 상태로 `/api/**`를 호출하면 401이 아니라 `/auth/login.html`로 302 리다이렉트가 내려갔다. 브라우저의 `fetch()`는 기본적으로 리다이렉트를 자동으로 따라가므로, 프론트엔드 코드는 로그인 페이지의 HTML을 200 OK 응답으로 받게 되고, 그 응답을 `response.json()`으로 파싱하려다 `SyntaxError: Unexpected token '<'`가 발생했다. 크롬에서는 재현되지 않았던 이유는 해당 브라우저의 `localStorage`에 유효하지 않은 토큰이 없어 이 경로를 타지 않았기 때문이다(엣지 자체 문제가 아니었음 — 처음에 IE 모드를 의심했으나 오진이었다).

### 변경된 파일
```
src/main/java/com/example/board/security/SecurityConfig.java
```

### 주요 변경사항
- `exceptionHandling().defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), PathPatternRequestMatcher.pathPattern("/api/**"))` 추가 — `/api/**` 경로는 인증 실패 시 리다이렉트 대신 401을 반환하도록 했다. `/oauth2/authorization/**` 같은 실제 OAuth2 로그인 흐름(브라우저 내비게이션)은 영향받지 않는다.
- 이 프로젝트가 사용하는 Spring Security 7.0.7-SNAPSHOT에서는 `AntPathRequestMatcher`가 제거되었고 `org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher`로 대체되었다(정적 팩토리 메서드 `pathPattern(String)` 사용).

### 테스트 결과
- `./mvnw test` 52건 전체 통과.
- curl로 확인: 토큰 없이/무효한 토큰으로 `/api/users/me`, `/api/boards`(POST) 호출 시 401(기존 302에서 변경) 확인. `GET /api/boards`, `/`, `/oauth2/authorization/google`은 기존과 동일하게 동작.
- Playwright 헤드리스 브라우저로 `localStorage`에 무효한 토큰을 미리 심어두고 홈 화면을 로드하는 시나리오를 재현 — 수정 전에는 이 케이스에서 크래시가 났을 것이고, 수정 후에는 에러 없이 `/auth/login.html`로 깔끔하게 리다이렉트되는 것을 확인했다.

### 발생한 문제 및 해결 방법
- 처음에 `org.springframework.security.web.util.matcher.AntPathRequestMatcher`를 사용해 컴파일했으나, 이 프로젝트의 Spring Security 버전(7.0.7-SNAPSHOT)에서 해당 클래스가 제거되어 컴파일 에러가 났다. `PathPatternRequestMatcher.pathPattern("/api/**")`로 대체해 해결했다.

## 2026-09-15 (추가) — 글쓰기 성공 후 잘못된 주소로 리다이렉트되는 버그 수정

### 작업 내용
사용자가 게시글 작성 후 상세 화면이 아니라 `/api/boards/58` 같은 API 주소로 이동해 JSON이 그대로 화면에 뜬다고 제보했다.

**원인**: `write.js`가 등록 성공(201) 응답의 `Location` 헤더 값(`/api/boards/{id}`, REST 리소스 주소)을 그대로 `window.location.href`에 대입하고 있었다. 이 값은 화면 주소가 아니라 API 엔드포인트라서, 브라우저가 그 주소로 이동하면 `BoardController.getDetail`이 반환하는 JSON이 그대로 렌더링됐다.

### 변경된 파일
```
src/main/resources/static/js/board/write.js
```

### 주요 변경사항
- `Location` 헤더에서 게시글 id만 추출해 `/board/detail.html?id={id}`로 이동하도록 수정.

### 테스트 결과
- Playwright로 회원가입 → 로그인 → 글쓰기 제출까지 실제 브라우저에서 재현: 수정 후 `/board/detail.html?id=62`로 정상 이동하고 상세 화면(제목/카테고리/작성자 본인 수정·삭제 버튼)이 올바르게 렌더링되는 것을 확인했다.
- 정적 리소스만 변경되어 Java 테스트 영향 없음(`./mvnw test`는 원래 이 파일을 커버하지 않음).

### Plan과 실제 구현의 차이점
`docs/plans/002-board-crud.md`의 "구현 결과와의 차이점" 절 참고 — `js/nav.js` 신설, 그리고 화면 검증 과정에서 발견해 함께 수정한 버그 2건(인증 실패 응답, 글쓰기 리다이렉트)이 Plan 대비 차이점이다. 나머지는 계획대로 구현했다.
