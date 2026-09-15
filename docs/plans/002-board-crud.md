# 002. 게시판 CRUD (게시글 + 댓글 + 첨부파일, API + 화면)

## 작업 목적
`001-auth.md`에서 구축한 인증/인가 기반(`User` 엔티티, JWT 인증 컨텍스트, USER/ADMIN Role)을 전제로, 게시판의 핵심 기능인 게시글 CRUD를 **API와 화면 양쪽 모두** 구현한다.
- 게시글 작성/조회/수정/삭제, 목록 검색·페이징
- 댓글 작성/조회/수정/삭제
- 게시글 첨부파일 업로드/다운로드/삭제
- 위 기능을 실제로 확인·사용할 수 있는 화면(목록/상세/작성/수정)

> 원래 이 작업은 API(1차, 2026-09-10)와 화면(2차, 원래 `003-board-pages.md`로 별도 Plan/Work Log 분리, 2026-09-15)으로 나눠 진행했다. 이후 "백엔드 기능은 이를 확인할 수 있는 화면까지 구현해야 완료로 본다"는 방침을 세우면서, 애초에 하나의 기능 단위였던 API+화면을 이 Plan/Work Log 하나로 합쳤다. 아래 내용은 두 단계를 합친 최종 결과이며, API 구현 이후 화면 작업 중 드러나 함께 수정한 버그 2건(인증 실패 응답 방식, 글쓰기 후 리다이렉트 주소)도 포함한다.

## 요구사항
- [x] 게시글 작성/상세조회/수정/삭제 (자체 CRUD)
- [x] 게시글 목록 조회: 페이징 + 제목/내용 검색
- [x] 게시글 카테고리 구분 (자유/공지/질문)
- [x] 게시글 첨부파일 업로드(다중)/다운로드/개별 삭제
- [x] 댓글 작성/조회/수정/삭제 (대댓글 없음, 단일 목록)
- [x] 게시글/댓글 목록·상세 조회는 비로그인 사용자도 가능
- [x] 게시글/댓글 작성은 로그인 필요, 수정은 작성자 본인만, 삭제는 작성자 본인 또는 ADMIN
- [x] 게시글 목록 화면: 페이징, 카테고리 필터, 키워드 검색, 작성 페이지로 이동
- [x] 게시글 상세 화면: 제목/내용/작성자/작성일/조회수, 첨부파일 목록(다운로드 링크), 댓글 목록+작성, 작성자 본인/ADMIN에게만 수정·삭제 버튼 노출
- [x] 게시글 작성 화면: 제목/내용/카테고리 입력 + 첨부파일 다중 업로드, 로그인 필요(비로그인 접근 시 로그인 페이지로 유도)
- [x] 게시글 수정 화면: 제목/내용/카테고리 수정(첨부파일 변경은 API 범위에 없으므로 화면에서도 다루지 않음), 작성자 본인만 접근 가능
- [x] 댓글: 상세 화면 내에서 목록 조회, 작성, 본인 댓글 수정/삭제
- [x] 첨부파일: 상세 화면에서 다운로드, 작성자/ADMIN은 개별 삭제
- [x] 홈 화면(`index.html`)에 게시판 목록으로 가는 네비게이션 추가, "게시판 기능은 준비 중입니다" 문구 제거

### 확인된 사용자 결정 사항
- 이번 Plan 범위: 게시글 CRUD + 댓글 CRUD + 첨부파일 + 화면(목록/상세/작성/수정)을 함께 포함
- 목록 조회: 페이징 + 제목/내용 검색 지원
- 첨부파일: 이번 범위에 포함 (로컬 디스크 저장)
- 카테고리: 필드로 포함 (자유/공지/질문 — 아래 "구현 방법" 참고, 세부 항목은 구현 중 조정 가능)
- 조회(목록/상세/댓글목록/첨부파일 다운로드) 권한: 비로그인 사용자도 가능 (`permitAll`)
- 삭제 권한: 작성자 본인 + ADMIN. 수정 권한은 작성자 본인만 (ADMIN도 타인 글 수정은 불가, 삭제만 가능 — 운영자가 내용을 임의로 바꾸는 것은 막고 노출만 차단하는 취지)
- 화면은 Thymeleaf 등 서버 템플릿이 아니라 `001-auth.md`에서 확립한 정적 HTML + vanilla JS + fetch API 패턴을 그대로 따른다(별도 컨트롤러 없이 Spring Boot 정적 리소스로 서빙).
- 앞으로 백엔드 기능을 구현할 때는 이를 확인할 수 있는 화면 구현까지 같은 작업 범위(또는 바로 이어지는 후속 작업)에 포함시키고, 화면 없이 기능만 완료된 상태로 남겨두지 않는다(사용자 결정 사항, 이후 다른 작업에도 동일 적용).

## 현재 구조 및 관련 코드
`001-auth.md` 완료 시점 기준 관련 코드:

```
src/main/java/com/example/board/
├── entity/
│   ├── User.java            # id, email, password, name, role, provider, providerId, createdAt, updatedAt
│   ├── Role.java            # USER, ADMIN
│   └── Provider.java        # LOCAL, GOOGLE, NAVER
├── repository/
│   └── UserRepository.java  # JPA
├── security/
│   ├── SecurityConfig.java  # PERMIT_ALL_PATHS 화이트리스트 + JWT STATELESS 인증
│   ├── CustomUserDetails.java   # getId(), getUser() 제공 — @AuthenticationPrincipal로 컨트롤러에서 사용
│   └── jwt/JwtAuthenticationFilter.java
├── exception/
│   ├── GlobalExceptionHandler.java  # @RestControllerAdvice, ErrorResponse(message) 반환
│   ├── DuplicateEmailException.java
│   └── InvalidTokenException.java
└── controller/
    ├── AuthController.java  # /api/auth/**
    └── UserController.java  # /api/users/me
```

- `SecurityConfig`의 `PERMIT_ALL_PATHS`에 없는 모든 `/api/**` 경로는 `anyRequest().authenticated()`로 인증이 필요하다. 게시글/댓글 조회 API를 비로그인 사용자에게 열어주려면 이 화이트리스트(또는 HTTP 메서드별 매처)를 조정해야 한다.
- 컨트롤러에서 현재 로그인 사용자는 `@AuthenticationPrincipal CustomUserDetails userDetails` → `userDetails.getId()`로 얻는다(`AuthController.logout` 참고).
- `pom.xml`에 `mybatis-spring-boot-starter`가 이미 포함되어 있으나 **아직 실제로 사용된 적이 없다**(Mapper 인터페이스/XML 없음). 본 작업이 MyBatis의 첫 실사용 사례가 된다.
- `spring.jpa.hibernate.ddl-auto=update`이므로 신규 Entity 추가만으로 테이블이 자동 생성된다(로컬 개발 환경 기준, `001-auth.md`와 동일한 방식 유지).
- 로컬 MySQL/Redis는 `001-auth.md`에서 구성한 Docker Compose 컨테이너(`127.0.0.1:3307` MySQL, `127.0.0.1:6379` Redis)를 그대로 사용한다. 추가 인프라 구성은 필요 없다.
- 정적 화면(`static/index.html`)에는 원래 "게시판 기능은 준비 중입니다." 문구만 있고 게시판 화면/링크가 없었다(화면 작업에서 정리).

### 화면 관련 기존 구조 (`001-auth.md`에서 확립된 패턴)
- 인증 관련 화면 예시: `static/auth/login.html`, `static/auth/signup.html` + `static/js/auth/login.js`, `static/js/auth/signup.js`. 공통 로직은 `static/js/auth.js`(`authFetch`: Access Token 만료 시 자동 재발급 후 재요청, 토큰은 `localStorage`).
- 홈 화면: `static/index.html` + `static/js/index.js` — 로그인 여부에 따라 `nav-links`/`home-actions` 영역을 JS로 교체 렌더링(정적 HTML 자체에는 빈 컨테이너만 있고, 브라우저가 JS를 실행해야 링크가 채워지는 구조).
- 공통 스타일: `static/css/style.css` (버튼/카드/폼 필드 스타일만 있고, 목록/테이블/페이지네이션/댓글 스타일은 화면 작업 전까지 없었음).

## 변경할 파일

### 신규 생성 — API
```
src/main/java/com/example/board/
├── entity/
│   ├── Board.java                      # 게시글 Entity (JPA)
│   ├── BoardCategory.java              # enum: FREE, NOTICE, QNA
│   ├── Comment.java                    # 댓글 Entity (JPA)
│   └── Attachment.java                 # 첨부파일 Entity (JPA)
├── dto/
│   ├── request/
│   │   ├── BoardCreateRequest.java     # title, content, category
│   │   ├── BoardUpdateRequest.java     # title, content, category
│   │   ├── CommentCreateRequest.java   # content
│   │   └── CommentUpdateRequest.java   # content
│   └── response/
│       ├── BoardListItemResponse.java  # id, title, category, authorName, viewCount, commentCount, createdAt (MyBatis 결과 매핑)
│       ├── BoardDetailResponse.java    # id, title, content, category, authorId, authorName, viewCount, attachments, createdAt, updatedAt
│       ├── CommentResponse.java        # id, content, authorId, authorName, createdAt, updatedAt
│       ├── AttachmentResponse.java     # id, originalFilename, fileSize, downloadUrl
│       └── PageResponse.java           # content, page, size, totalElements, totalPages (공통 페이징 응답 래퍼)
├── repository/
│   ├── BoardRepository.java            # JPA (단건 CRUD)
│   ├── CommentRepository.java          # JPA (단건 CRUD)
│   └── AttachmentRepository.java       # JPA (단건 CRUD)
├── mapper/
│   └── BoardMapper.java                # MyBatis 인터페이스 — 목록 검색/페이징(작성자 이름 JOIN), 댓글 수 집계
├── service/
│   ├── BoardService.java
│   ├── CommentService.java
│   └── FileStorageService.java         # 로컬 디스크 저장/조회/삭제
├── controller/
│   ├── BoardController.java            # /api/boards/**
│   └── CommentController.java          # /api/boards/{boardId}/comments, /api/comments/{id}
└── exception/
    ├── BoardNotFoundException.java
    ├── CommentNotFoundException.java
    └── ForbiddenOperationException.java  # 작성자 본인/ADMIN이 아닌 사용자의 수정·삭제 시도

src/main/resources/mapper/
└── BoardMapper.xml                     # 목록 검색/페이징 동적 SQL
```

### 신규 생성 — 화면
```
src/main/resources/static/board/
├── list.html
├── detail.html
├── write.html
└── edit.html

src/main/resources/static/js/board/
├── common.js     # 카테고리 라벨, 날짜 포맷, HTML 이스케이프, 페이지네이션 렌더링 등 공통 유틸
├── list.js
├── detail.js
├── write.js
└── edit.js

src/main/resources/static/js/nav.js   # index.html 외 다른 화면에서 공용으로 쓰는 nav-links 렌더링(아래 "구현 결과와의 차이점" 참고)
```

### 수정
```
src/main/resources/application.properties         # 파일 업로드 경로/용량 제한, MyBatis mapper-locations 설정 추가
src/main/java/com/example/board/security/SecurityConfig.java   # (1) 게시글/댓글/첨부파일 조회 GET permitAll, (2) 화면 정적 리소스 "/board/**" permitAll, (3) /api/** 인증 실패 시 401 반환하도록 exceptionHandling 추가(버그 수정, 아래 참고)
src/main/java/com/example/board/exception/GlobalExceptionHandler.java  # 신규 예외 3종 핸들러 추가
.gitignore                                          # 업로드 파일 저장 디렉토리 추가
src/main/resources/static/index.html                # "게시판" 네비게이션 추가, 준비중 문구 제거
src/main/resources/static/js/index.js                # nav-links/home-actions에 게시판 링크 추가
src/main/resources/static/css/style.css              # 목록 테이블, 페이지네이션, 댓글, textarea, select, 파일 입력 스타일 추가
```

## 구현 방법

### API

#### 1. Board / Comment / Attachment 엔티티 (JPA)
- `Board`: `id`, `title`, `content`(TEXT), `category`(`BoardCategory` enum), `viewCount`(기본 0), `user`(`User` 다대일 FK), `createdAt`, `updatedAt`. `User.java`와 동일하게 `@PrePersist`/`@PreUpdate`로 시각 자동 관리.
- `BoardCategory`: `FREE`(자유), `NOTICE`(공지), `QNA`(질문).
- `Comment`: `id`, `board`(다대일 FK), `user`(다대일 FK), `content`, `createdAt`, `updatedAt`. 대댓글 미지원(단일 목록, `parentId` 없음).
- `Attachment`: `id`, `board`(다대일 FK), `originalFilename`, `storedFilename`(UUID 기반, 충돌 방지), `filePath`, `fileSize`, `contentType`, `createdAt`.
- `Board`/`Comment`/`Attachment` 모두 `User`와 마찬가지로 `@NoArgsConstructor(access = PROTECTED)` + `@Builder`로 생성.

#### 2. JPA vs MyBatis 역할 분리
CLAUDE.md 규칙(단순 CRUD는 JPA, 복잡한 JOIN·동적 검색·페이징은 MyBatis, 동일 기능 중복 금지)에 따라 다음과 같이 분리한다.
- **JPA (Repository)**: 게시글/댓글/첨부파일의 단건 조회·저장·수정·삭제. `BoardService.create/update/delete`, `CommentService.create/update/delete`, 첨부파일 저장/삭제.
- **MyBatis (Mapper)**: 목록 조회 전용. `BoardMapper.findList(keyword, category, offset, limit)` — `users` 테이블과 JOIN해 작성자 이름을 함께 가져오고, 댓글 수는 서브쿼리로 집계해 N+1 없이 `BoardListItemResponse`로 바로 매핑한다. `BoardMapper.count(keyword, category)`로 전체 건수를 별도 조회해 페이징 메타데이터를 구성한다.
- 댓글 목록(`GET /api/boards/{boardId}/comments`)은 JOIN 없이 단순 조회이므로 `CommentRepository`(JPA, `findByBoardIdOrderByCreatedAtAsc` + `@EntityGraph` 또는 fetch join으로 작성자 함께 조회)로 처리하고 별도 Mapper를 만들지 않는다.

#### 3. 게시글 목록/검색/페이징
- `GET /api/boards?page=0&size=10&keyword=&category=`
- `keyword`가 있으면 제목 또는 내용에 `LIKE '%keyword%'`로 검색(대소문자 구분은 DB 콜레이션 기본값 사용). `category`가 있으면 해당 카테고리로 필터링. 둘 다 없으면 전체 목록.
- 정렬은 최신순(`created_at DESC`) 고정.
- `BoardService.getList(...)`가 `BoardMapper.findList`/`count` 결과를 `PageResponse<BoardListItemResponse>`로 감싸 반환.

#### 4. 게시글 상세 조회 및 조회수
- `GET /api/boards/{id}`: `BoardRepository.findById` → 없으면 `BoardNotFoundException`. 조회할 때마다 `viewCount`를 1 증가시켜 저장(동시성은 이번 범위에서 낙관적 락 등 별도 처리 없이 단순 증가로 구현 — 트래픽 증가 시 재검토).
- 첨부파일 목록은 `AttachmentRepository.findByBoardId`로 함께 조회해 `BoardDetailResponse.attachments`에 포함.

#### 5. 게시글 작성/수정/삭제
- `POST /api/boards` (multipart/form-data): `@RequestPart("request") @Valid BoardCreateRequest`(title/content/category) + `@RequestPart(value = "files", required = false) List<MultipartFile> files`. 로그인한 사용자만 가능, 작성자는 `@AuthenticationPrincipal`에서 가져온다. 파일이 있으면 `FileStorageService`로 저장 후 `Attachment` 레코드 생성.
- `PUT /api/boards/{id}` (JSON): 제목/내용/카테고리만 수정. 첨부파일 추가/교체는 이번 범위에 포함하지 않는다(첨부파일은 개별 삭제만 지원, 아래 8번). 작성자 본인이 아니면 `ForbiddenOperationException`(403).
- `DELETE /api/boards/{id}`: 작성자 본인 또는 ADMIN만 가능. 삭제 시 연관된 댓글·첨부파일(DB 레코드 + 실제 파일)도 함께 삭제한다.

#### 6. 댓글 작성/수정/삭제
- `GET /api/boards/{boardId}/comments`: 해당 게시글의 댓글을 작성일 오름차순으로 전체 반환(페이징 없음 — 댓글 수가 많지 않다는 가정, 필요 시 추후 확장).
- `POST /api/boards/{boardId}/comments`: 로그인 사용자만, 게시글 존재 여부 확인 후 생성.
- `PUT /api/comments/{id}`: 작성자 본인만 수정 가능.
- `DELETE /api/comments/{id}`: 작성자 본인 또는 ADMIN.

#### 7. 첨부파일 저장/다운로드
- `FileStorageService`: `file.upload-dir`(기본값 `./uploads/board`) 하위에 `{boardId}/{UUID}_{원본파일명}` 형태로 저장. 저장 시 원본 파일명은 그대로 유지하되 경로 조작 방지를 위해 `Path` 정규화 후 업로드 루트 하위인지 검증.
- 용량 제한: `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=50MB`(파일당 10MB, 요청당 최대 5개 기준). 확장자 화이트리스트는 이번 범위에서는 적용하지 않는다(필요 시 추후 보안 강화 Plan에서 검토).
- `GET /api/boards/{boardId}/attachments/{attachmentId}`: `Resource`로 파일을 읽어 `Content-Disposition: attachment`로 다운로드 응답. 조회이므로 비로그인도 가능.
- `DELETE /api/boards/{boardId}/attachments/{attachmentId}`: 게시글 작성자 본인 또는 ADMIN만 가능. DB 레코드 삭제 + 실제 파일 삭제.
- 업로드 디렉토리(`uploads/`)는 `.gitignore`에 추가한다.

#### 8. 권한(Role/작성자) 처리
- `SecurityConfig.PERMIT_ALL_PATHS`에 게시글/댓글 조회(GET)를 추가한다. HTTP 메서드를 구분해야 하므로 `requestMatchers(HttpMethod.GET, "/api/boards/**")`처럼 별도 매처를 추가하고, 그 외 `/api/boards/**`, `/api/comments/**`는 `anyRequest().authenticated()`에 걸리도록 한다(쓰기 작업은 인증 필요).
- 작성자 본인 여부 검증은 Service 계층에서 `board.getUser().getId().equals(userId)`로 처리하고, ADMIN이면(삭제에 한해) 통과시킨다. Controller는 HTTP 요청/응답만 담당하고 권한 로직은 Service에 둔다(CLAUDE.md 아키텍처 규칙 준수).

#### 9. 예외 처리
- `BoardNotFoundException`, `CommentNotFoundException`: 404.
- `ForbiddenOperationException`: 403.
- `GlobalExceptionHandler`에 위 3종 핸들러 추가, 기존 `ErrorResponse(message)` 형식 그대로 사용.

### 화면

#### 10. 공통 유틸 (`js/board/common.js`)
- `CATEGORY_LABELS = { FREE: "자유", NOTICE: "공지", QNA: "질문" }`
- `formatDateTime(isoString)`, `escapeHtml(str)`(사용자 입력을 `innerHTML`로 렌더링하는 목록/상세/댓글에서 XSS 방지 목적으로 반드시 사용)
- `renderPagination(container, pageResponse, onPageClick)` — 이전/다음 + 페이지 번호 버튼 공통 렌더링

#### 11. 게시글 목록 (`board/list.html` + `list.js`)
- 쿼리 파라미터(`page`, `keyword`, `category`)를 URL에 반영해 새로고침/뒤로가기에도 상태 유지.
- `fetch("/api/boards?...")`(비로그인도 가능하므로 `authFetch` 불필요, 그냥 `fetch`).
- 각 행: 제목(상세 링크), 카테고리 뱃지, 작성자명, 작성일, 조회수, 댓글수.
- 로그인 상태일 때만 "글쓰기" 버튼 노출(`isLoggedIn()`은 `auth.js`의 기존 함수 재사용).

#### 12. 게시글 상세 (`board/detail.html` + `detail.js`)
- `fetch(/api/boards/{id})`로 상세 로드, 첨부파일은 `<a href="/api/boards/{id}/attachments/{attachmentId}">원본파일명</a>`로 다운로드(브라우저 기본 다운로드 동작 사용, JS fetch 불필요).
- 로그인 사용자의 `id`(`/api/users/me`)와 `authorId`가 같으면 수정/삭제 버튼, ADMIN이면 삭제 버튼만 추가 노출.
- 삭제: `authFetch(DELETE)` 성공 시 목록으로 이동.
- 첨부파일 삭제 버튼(작성자/ADMIN): `authFetch(DELETE)` 후 목록 갱신.
- 댓글: `GET /api/boards/{id}/comments`로 목록 로드 → 작성 폼(로그인 시에만 노출) → `authFetch(POST)` → 목록 갱신. 각 댓글에 본인 것이면 수정/삭제 버튼(ADMIN은 삭제만).

#### 13. 게시글 작성 (`board/write.html` + `write.js`)
- 진입 시 `isLoggedIn()` 아니면 `location.href = "/auth/login.html"`.
- `FormData`로 `request`(Blob, JSON) + `files`(다중 input) 구성 후 `authFetch(POST, multipart)` — `authFetch`는 기본적으로 `Content-Type` 헤더를 강제하지 않으므로 FormData 사용 시 브라우저가 자동으로 boundary 포함 헤더를 설정하도록 명시적 `Content-Type`을 넘기지 않는다.
- 성공(201) 시 응답의 `Location` 헤더(`/api/boards/{id}`, REST 리소스 주소)에서 id만 추출해 화면 주소 `/board/detail.html?id={id}`로 이동한다. (`Location` 값을 그대로 리다이렉트에 쓰면 API 엔드포인트로 가서 JSON이 그대로 뜨는 버그가 있었다 — 아래 "구현 결과와의 차이점" 참고.)

#### 14. 게시글 수정 (`board/edit.html` + `edit.js`)
- URL `?id=`로 대상 게시글 조회 후 폼에 프리필.
- 작성자 본인이 아니면(=`/api/users/me`의 `id` !== `authorId`) 상세로 리다이렉트.
- `authFetch(PUT, JSON)` 성공(204) 시 상세로 이동.

#### 15. 홈 화면/네비게이션
- `index.js`의 `renderLoggedIn`/`renderLoggedOut` 양쪽 `nav-links`에 `<a href="/board/list.html">게시판</a>` 추가.
- `index.html`의 `.board-notice` 문구 제거.

#### 16. SecurityConfig — 화면 접근 허용 + 인증 실패 응답 수정
- `PERMIT_ALL_PATHS`에 `"/board/**"` 추가(화면 정적 리소스 접근 허용 — API 인증/인가는 기존 `BoardController`/`CommentController` 쪽 설정을 그대로 사용, 변경 없음).
- (화면 작업 중 발견한 버그 수정) `oauth2Login()`만 설정돼 있으면 `/api/**` 요청도 인증 실패 시 401이 아니라 `/auth/login.html`로 302 리다이렉트된다. `fetch()`가 이를 자동으로 따라가 로그인 페이지 HTML을 200 OK로 받아버리고, 이를 JSON으로 파싱하려는 프론트 코드가 `SyntaxError`로 깨지는 문제가 있었다. `exceptionHandling().defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), PathPatternRequestMatcher.pathPattern("/api/**"))`를 추가해 `/api/**`는 인증 실패 시 401을 반환하도록 수정했다(`/oauth2/authorization/**` 같은 실제 OAuth2 로그인 흐름은 영향받지 않음). 이 프로젝트가 쓰는 Spring Security 7.0.7-SNAPSHOT에서는 `AntPathRequestMatcher`가 제거되어 `org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern(String)`을 대신 사용했다.

## 데이터베이스 변경 사항
`boards`, `comments`, `attachments` 테이블 신규 생성 (JPA 엔티티 기반, `ddl-auto=update`). 화면 작업으로 인한 DB 변경은 없음.

**boards**

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| title | VARCHAR(200) | NOT NULL |
| content | TEXT | NOT NULL |
| category | VARCHAR(20) | NOT NULL |
| view_count | INT | NOT NULL, DEFAULT 0 |
| user_id | BIGINT | NOT NULL, FK → users.id |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

- 인덱스: `(category, created_at)` — 카테고리 필터 + 최신순 정렬 조합 조회용. 제목/내용 검색은 `LIKE` 기반이라 별도 인덱스 효과가 제한적이므로 이번 범위에서는 추가하지 않는다(데이터량이 커지면 MySQL FULLTEXT 인덱스 도입을 추후 검토).

**comments**

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| board_id | BIGINT | NOT NULL, FK → boards.id |
| user_id | BIGINT | NOT NULL, FK → users.id |
| content | VARCHAR(1000) | NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

- 인덱스: `board_id` (댓글 목록 조회용).

**attachments**

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT |
| board_id | BIGINT | NOT NULL, FK → boards.id |
| original_filename | VARCHAR(255) | NOT NULL |
| stored_filename | VARCHAR(255) | NOT NULL |
| file_path | VARCHAR(500) | NOT NULL |
| file_size | BIGINT | NOT NULL |
| content_type | VARCHAR(100) | NULL 허용 |
| created_at | DATETIME | NOT NULL |

- 인덱스: `board_id` (첨부파일 목록/삭제용).
- 게시글 삭제 시 댓글/첨부파일도 애플리케이션 코드에서 함께 삭제한다(DB 레벨 `ON DELETE CASCADE`는 이번 범위에서 설정하지 않고, Service 계층에서 명시적으로 삭제 — 삭제 흐름을 코드로 추적 가능하게 하기 위함).

## API 변경 사항

| Method | URI | 설명 | 인증 필요 |
|---|---|---|---|
| GET | /api/boards | 게시글 목록 (페이징/검색/카테고리 필터) | X |
| GET | /api/boards/{id} | 게시글 상세 (조회수 증가) | X |
| POST | /api/boards | 게시글 작성 (multipart, 첨부파일 포함 가능) | O |
| PUT | /api/boards/{id} | 게시글 수정 (작성자 본인만) | O |
| DELETE | /api/boards/{id} | 게시글 삭제 (작성자 본인 또는 ADMIN) | O |
| GET | /api/boards/{boardId}/comments | 댓글 목록 | X |
| POST | /api/boards/{boardId}/comments | 댓글 작성 | O |
| PUT | /api/comments/{id} | 댓글 수정 (작성자 본인만) | O |
| DELETE | /api/comments/{id} | 댓글 삭제 (작성자 본인 또는 ADMIN) | O |
| GET | /api/boards/{boardId}/attachments/{attachmentId} | 첨부파일 다운로드 | X |
| DELETE | /api/boards/{boardId}/attachments/{attachmentId} | 첨부파일 삭제 (게시글 작성자 본인 또는 ADMIN) | O |

화면 작업으로 신규/변경된 엔드포인트는 없다. 다만 **인증 실패 시 응답 방식이 변경**됐다(신규 엔드포인트 아님, 기존 엔드포인트 전체의 공통 동작): `/api/**` 인증 필요 엔드포인트에 토큰 없이/무효한 토큰으로 접근하면 이전에는 `/auth/login.html`로 302 리다이렉트했으나, 이제 401을 반환한다(위 "구현 방법 16" 참고).

## 테스트 계획

### API
- **Service 단위 테스트** (Mockito)
  - `BoardService`: 작성 성공, 상세 조회 시 조회수 증가, 수정 성공/작성자 아님 예외, 삭제 성공(작성자/ADMIN)/권한 없음 예외, 존재하지 않는 게시글 조회 시 예외
  - `CommentService`: 작성/수정/삭제 성공, 권한 없음 예외, 존재하지 않는 게시글/댓글 예외
  - `FileStorageService`: 저장 후 경로 반환, 삭제 성공, (가능하면) 경로 조작 방어 검증
- **Mapper 통합 테스트**
  - `BoardMapper`는 실제 SQL 검증이 필요하므로 `001-auth.md`에서 구성한 로컬 Docker MySQL(`127.0.0.1:3307`)에 대해 `@SpringBootTest` 기반으로 검색어/카테고리/페이징 조합을 검증한다(별도 인메모리 DB는 도입하지 않음 — 기존 인프라 재사용).
- **Controller 테스트**
  - 목록/상세/댓글목록/첨부파일 다운로드(permitAll GET)는 `@WebMvcTest` + `MockMvc`로 상태 코드·응답 검증.
  - 작성/수정/삭제 등 `@AuthenticationPrincipal`이 필요한 엔드포인트는 `001-auth.md`에서 확인된 제약(슬라이스 테스트에서 `SecurityConfig`를 온전히 로드할 수 없는 문제)과 동일한 사유로, 컨트롤러 메서드 직접 호출 방식의 단위 테스트를 우선 적용하고 필요 시 조정한다.
- 전체 구현 후 `./mvnw test`로 기존 인증 관련 테스트 포함 전체 회귀 확인.

### 화면
- 정적 화면 + vanilla JS이므로 별도 Java 단위/통합 테스트 대상 코드가 없다(기존 로그인/회원가입 화면도 동일하게 자동화 테스트 없이 수동 확인으로 처리됨).
- 앱 기동 후 브라우저로 다음 골든 패스를 직접 확인한다:
  1. 비로그인 상태로 목록/상세 조회, 첨부파일 다운로드 가능 여부
  2. 로그인 → 글쓰기(첨부파일 포함) → 목록/상세에 반영 확인, 작성 후 상세 화면으로 정상 이동하는지 확인
  3. 댓글 작성/수정/삭제
  4. 본인 글 수정/삭제, 타인 글에는 수정/삭제 버튼 미노출 확인
  5. 페이징 + 키워드/카테고리 검색 동작 확인
  6. 만료/무효 토큰이 남아있는 상태에서도 홈 화면이 깨지지 않고 로그인 화면으로 자연스럽게 넘어가는지 확인
- 기존 `./mvnw test` 회귀 실행(정적 리소스/`SecurityConfig` 변경이 `AuthControllerTest` 등 기존 슬라이스 테스트를 깨지 않는지 확인).

## 작업 상태
- Completed

### 구현 결과와의 차이점
- 첨부파일 조회 시 예외 처리를 위해 계획에 없던 `AttachmentNotFoundException`을 추가했다(다운로드/삭제 시 게시글에 속하지 않거나 존재하지 않는 첨부파일을 404로 처리하기 위해 필요).
- `@MapperScan`은 `BoardApplication`이 아닌 `config/MyBatisConfig.java`에 별도로 두었다. `BoardApplication`에 직접 붙이면 `@WebMvcTest` 슬라이스 테스트가 `sqlSessionFactory` 빈을 찾지 못해 컨텍스트 로딩에 실패하는 문제가 있었다(MapperScannerConfigurer는 슬라이스 테스트의 타입 필터를 우회해서 등록되기 때문). 이 변경에 따라 기존 `AuthControllerTest`도 `MybatisAutoConfiguration`을 제외 목록에 추가해 함께 수정했다.
- `index.js`를 화면 전체에서 재사용하지 않고, 공용 네비게이션 렌더링을 위해 `js/nav.js`를 별도로 신설했다. `index.js`의 `renderLoggedIn`/`renderLoggedOut`은 `welcome-message`/`home-actions` 등 홈 화면 전용 DOM을 직접 참조하므로, 그 요소가 없는 다른 화면에서 그대로 로드하면 에러가 난다. `nav.js`는 `nav-links` 영역만 다루는 축소판이다(계획에는 없던 파일).
- 화면 검증 과정에서 실제 버그 2건을 발견해 함께 수정했다(둘 다 이 Plan/Work Log 범위로 포함):
  1. **인증 실패 시 302 리다이렉트 → 401 미반환 버그**: 위 "구현 방법 16" 참고. `SecurityConfig`에 `exceptionHandling` 추가로 수정.
  2. **글쓰기 성공 후 API 주소로 리다이렉트되는 버그**: `write.js`가 `Location` 응답 헤더(`/api/boards/{id}`)를 화면 주소로 착각해 그대로 이동시켰다. 게시글 id만 추출해 `/board/detail.html?id={id}`로 이동하도록 수정했다.
- 나머지 설계(엔티티, JPA/MyBatis 역할 분리, API, 화면 구성, 권한 처리)는 계획대로 구현했다.
