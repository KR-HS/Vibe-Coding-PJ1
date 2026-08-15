# 002. 게시판 CRUD (게시글 + 댓글 + 첨부파일)

## 작업 목적
`001-auth.md`에서 구축한 인증/인가 기반(`User` 엔티티, JWT 인증 컨텍스트, USER/ADMIN Role)을 전제로, 게시판의 핵심 기능인 게시글 CRUD를 구현한다.
- 게시글 작성/조회/수정/삭제, 목록 검색·페이징
- 댓글 작성/조회/수정/삭제
- 게시글 첨부파일 업로드/다운로드/삭제

게시판 화면(목록/상세/작성/수정 페이지)은 이번 Plan의 범위가 아니다. `001-auth.md`에서도 화면은 API에 대응하는 별도 관심사로 다뤘듯, 게시판 화면은 본 API 구현 완료 후 별도 Plan(`003-board-pages.md` 등)에서 다룬다.

## 요구사항
- [ ] 게시글 작성/상세조회/수정/삭제 (자체 CRUD)
- [ ] 게시글 목록 조회: 페이징 + 제목/내용 검색
- [ ] 게시글 카테고리 구분 (자유/공지/질문)
- [ ] 게시글 첨부파일 업로드(다중)/다운로드/개별 삭제
- [ ] 댓글 작성/조회/수정/삭제 (대댓글 없음, 단일 목록)
- [ ] 게시글/댓글 목록·상세 조회는 비로그인 사용자도 가능
- [ ] 게시글/댓글 작성은 로그인 필요, 수정은 작성자 본인만, 삭제는 작성자 본인 또는 ADMIN

### 확인된 사용자 결정 사항
- 이번 Plan 범위: 게시글 CRUD + 댓글 CRUD + 첨부파일을 함께 포함 (댓글/첨부파일을 별도 Plan으로 분리하지 않음)
- 목록 조회: 페이징 + 제목/내용 검색 지원
- 첨부파일: 이번 범위에 포함 (로컬 디스크 저장)
- 카테고리: 필드로 포함 (자유/공지/질문 — 아래 "구현 방법" 참고, 세부 항목은 구현 중 조정 가능)
- 조회(목록/상세/댓글목록/첨부파일 다운로드) 권한: 비로그인 사용자도 가능 (`permitAll`)
- 삭제 권한: 작성자 본인 + ADMIN. 수정 권한은 작성자 본인만 (ADMIN도 타인 글 수정은 불가, 삭제만 가능 — 운영자가 내용을 임의로 바꾸는 것은 막고 노출만 차단하는 취지)

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
- 정적 화면(`static/index.html`)에는 현재 "게시판 기능은 준비 중입니다." 문구만 있고 게시판 화면/링크는 없다. 이번 Plan에서는 API만 구현하고 이 문구는 그대로 둔다(화면 Plan에서 정리).

## 변경할 파일

### 신규 생성
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

### 수정
```
src/main/resources/application.properties         # 파일 업로드 경로/용량 제한, MyBatis mapper-locations 설정 추가
src/main/java/com/example/board/security/SecurityConfig.java   # 게시글/댓글/첨부파일 조회 GET 요청 permitAll 추가
src/main/java/com/example/board/exception/GlobalExceptionHandler.java  # 신규 예외 3종 핸들러 추가
.gitignore                                          # 업로드 파일 저장 디렉토리 추가
```

## 구현 방법

### 1. Board / Comment / Attachment 엔티티 (JPA)
- `Board`: `id`, `title`, `content`(TEXT), `category`(`BoardCategory` enum), `viewCount`(기본 0), `user`(`User` 다대일 FK), `createdAt`, `updatedAt`. `User.java`와 동일하게 `@PrePersist`/`@PreUpdate`로 시각 자동 관리.
- `BoardCategory`: `FREE`(자유), `NOTICE`(공지), `QNA`(질문).
- `Comment`: `id`, `board`(다대일 FK), `user`(다대일 FK), `content`, `createdAt`, `updatedAt`. 대댓글 미지원(단일 목록, `parentId` 없음).
- `Attachment`: `id`, `board`(다대일 FK), `originalFilename`, `storedFilename`(UUID 기반, 충돌 방지), `filePath`, `fileSize`, `contentType`, `createdAt`.
- `Board`/`Comment`/`Attachment` 모두 `User`와 마찬가지로 `@NoArgsConstructor(access = PROTECTED)` + `@Builder`로 생성.

### 2. JPA vs MyBatis 역할 분리
CLAUDE.md 규칙(단순 CRUD는 JPA, 복잡한 JOIN·동적 검색·페이징은 MyBatis, 동일 기능 중복 금지)에 따라 다음과 같이 분리한다.
- **JPA (Repository)**: 게시글/댓글/첨부파일의 단건 조회·저장·수정·삭제. `BoardService.create/update/delete`, `CommentService.create/update/delete`, 첨부파일 저장/삭제.
- **MyBatis (Mapper)**: 목록 조회 전용. `BoardMapper.findList(keyword, category, offset, limit)` — `users` 테이블과 JOIN해 작성자 이름을 함께 가져오고, 댓글 수는 서브쿼리로 집계해 N+1 없이 `BoardListItemResponse`로 바로 매핑한다. `BoardMapper.count(keyword, category)`로 전체 건수를 별도 조회해 페이징 메타데이터를 구성한다.
- 댓글 목록(`GET /api/boards/{boardId}/comments`)은 JOIN 없이 단순 조회이므로 `CommentRepository`(JPA, `findByBoardIdOrderByCreatedAtAsc` + `@EntityGraph` 또는 fetch join으로 작성자 함께 조회)로 처리하고 별도 Mapper를 만들지 않는다.

### 3. 게시글 목록/검색/페이징
- `GET /api/boards?page=0&size=10&keyword=&category=`
- `keyword`가 있으면 제목 또는 내용에 `LIKE '%keyword%'`로 검색(대소문자 구분은 DB 콜레이션 기본값 사용). `category`가 있으면 해당 카테고리로 필터링. 둘 다 없으면 전체 목록.
- 정렬은 최신순(`created_at DESC`) 고정.
- `BoardService.getList(...)`가 `BoardMapper.findList`/`count` 결과를 `PageResponse<BoardListItemResponse>`로 감싸 반환.

### 4. 게시글 상세 조회 및 조회수
- `GET /api/boards/{id}`: `BoardRepository.findById` → 없으면 `BoardNotFoundException`. 조회할 때마다 `viewCount`를 1 증가시켜 저장(동시성은 이번 범위에서 낙관적 락 등 별도 처리 없이 단순 증가로 구현 — 트래픽 증가 시 재검토).
- 첨부파일 목록은 `AttachmentRepository.findByBoardId`로 함께 조회해 `BoardDetailResponse.attachments`에 포함.

### 5. 게시글 작성/수정/삭제
- `POST /api/boards` (multipart/form-data): `@RequestPart("request") @Valid BoardCreateRequest`(title/content/category) + `@RequestPart(value = "files", required = false) List<MultipartFile> files`. 로그인한 사용자만 가능, 작성자는 `@AuthenticationPrincipal`에서 가져온다. 파일이 있으면 `FileStorageService`로 저장 후 `Attachment` 레코드 생성.
- `PUT /api/boards/{id}` (JSON): 제목/내용/카테고리만 수정. 첨부파일 추가/교체는 이번 범위에 포함하지 않는다(첨부파일은 개별 삭제만 지원, 아래 8번). 작성자 본인이 아니면 `ForbiddenOperationException`(403).
- `DELETE /api/boards/{id}`: 작성자 본인 또는 ADMIN만 가능. 삭제 시 연관된 댓글·첨부파일(DB 레코드 + 실제 파일)도 함께 삭제한다.

### 6. 댓글 작성/수정/삭제
- `GET /api/boards/{boardId}/comments`: 해당 게시글의 댓글을 작성일 오름차순으로 전체 반환(페이징 없음 — 댓글 수가 많지 않다는 가정, 필요 시 추후 확장).
- `POST /api/boards/{boardId}/comments`: 로그인 사용자만, 게시글 존재 여부 확인 후 생성.
- `PUT /api/comments/{id}`: 작성자 본인만 수정 가능.
- `DELETE /api/comments/{id}`: 작성자 본인 또는 ADMIN.

### 7. 첨부파일 저장/다운로드
- `FileStorageService`: `file.upload-dir`(기본값 `./uploads/board`) 하위에 `{boardId}/{UUID}_{원본파일명}` 형태로 저장. 저장 시 원본 파일명은 그대로 유지하되 경로 조작 방지를 위해 `Path` 정규화 후 업로드 루트 하위인지 검증.
- 용량 제한: `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=50MB`(파일당 10MB, 요청당 최대 5개 기준). 확장자 화이트리스트는 이번 범위에서는 적용하지 않는다(필요 시 추후 보안 강화 Plan에서 검토).
- `GET /api/boards/{boardId}/attachments/{attachmentId}`: `Resource`로 파일을 읽어 `Content-Disposition: attachment`로 다운로드 응답. 조회이므로 비로그인도 가능.
- `DELETE /api/boards/{boardId}/attachments/{attachmentId}`: 게시글 작성자 본인 또는 ADMIN만 가능. DB 레코드 삭제 + 실제 파일 삭제.
- 업로드 디렉토리(`uploads/`)는 `.gitignore`에 추가한다.

### 8. 권한(Role/작성자) 처리
- `SecurityConfig.PERMIT_ALL_PATHS`에 게시글/댓글 조회(GET)를 추가한다. HTTP 메서드를 구분해야 하므로 `requestMatchers(HttpMethod.GET, "/api/boards/**")`처럼 별도 매처를 추가하고, 그 외 `/api/boards/**`, `/api/comments/**`는 `anyRequest().authenticated()`에 걸리도록 한다(쓰기 작업은 인증 필요).
- 작성자 본인 여부 검증은 Service 계층에서 `board.getUser().getId().equals(userId)`로 처리하고, ADMIN이면(삭제에 한해) 통과시킨다. Controller는 HTTP 요청/응답만 담당하고 권한 로직은 Service에 둔다(CLAUDE.md 아키텍처 규칙 준수).

### 9. 예외 처리
- `BoardNotFoundException`, `CommentNotFoundException`: 404.
- `ForbiddenOperationException`: 403.
- `GlobalExceptionHandler`에 위 3종 핸들러 추가, 기존 `ErrorResponse(message)` 형식 그대로 사용.

## 데이터베이스 변경 사항
`boards`, `comments`, `attachments` 테이블 신규 생성 (JPA 엔티티 기반, `ddl-auto=update`).

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

## 테스트 계획
- **Service 단위 테스트** (Mockito)
  - `BoardService`: 작성 성공, 상세 조회 시 조회수 증가, 수정 성공/작성자 아님 예외, 삭제 성공(작성자/ADMIN)/권한 없음 예외, 존재하지 않는 게시글 조회 시 예외
  - `CommentService`: 작성/수정/삭제 성공, 권한 없음 예외, 존재하지 않는 게시글/댓글 예외
  - `FileStorageService`: 저장 후 경로 반환, 삭제 성공, (가능하면) 경로 조작 방어 검증
- **Mapper 통합 테스트**
  - `BoardMapper`는 실제 SQL 검증이 필요하므로 `001-auth.md`에서 구성한 로컬 Docker MySQL(`127.0.0.1:3307`)에 대해 `@SpringBootTest` 기반으로 검색어/카테고리/페이징 조합을 검증한다(별도 인메모리 DB는 도입하지 않음 — 기존 인프라 재사용).
- **Controller 테스트**
  - 목록/상세/댓글목록/첨부파일 다운로드(permitAll GET)는 `@WebMvcTest` + `MockMvc`로 상태 코드·응답 검증.
  - 작성/수정/삭제 등 `@AuthenticationPrincipal`이 필요한 엔드포인트는 `001-auth.md`에서 확인된 제약(슬라이스 테스트에서 `SecurityConfig`를 온전히 로드할 수 없는 문제)과 동일한 사유로, 컨트롤러 메서드 직접 호출 방식의 단위 테스트를 우선 적용하고 필요 시 조정한다.
- 전체 구현 후 `./mvnw test`로 기존 21건(인증 관련) 포함 전체 회귀 확인.

## 작업 상태
- Planned
